"""Prepare a separate headless build from the pinned official CC Switch source.

The upstream checkout is never modified. Business modules are copied verbatim;
desktop integration is replaced by the reviewed files beside this script.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import tomllib

COMMIT = "3a0b86a26649f61d86dfbc3878d73220199f70b2"
HERE = Path(__file__).resolve().parent


def prepare(source: Path, target: Path, refresh=False):
    actual = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    if actual != COMMIT:
        raise ValueError(f"Expected upstream {COMMIT}, got {actual}")
    subprocess.run(["git", "-C", str(source), "diff", "--exit-code", "HEAD", "--"], check=True, stdout=subprocess.DEVNULL)
    if target.exists() and not (refresh and (target / "UPSTREAM.json").is_file() and json.loads((target / "UPSTREAM.json").read_text())["commit"] == COMMIT):
        raise ValueError("Use a fresh build directory; existing output is never overwritten")
    target.mkdir(parents=True, exist_ok=True)
    shutil.copytree(source / "src-tauri/src", target / "src", dirs_exist_ok=True)
    shutil.copy2(source / "LICENSE", target / "LICENSE")
    cargo = (source / "src-tauri/Cargo.toml").read_text(encoding="utf-8")
    # Keep upstream business dependencies, excluding GUI and OS integration crates.
    tomllib.loads(cargo)  # Refuse malformed upstream metadata.
    desktop = {"tauri", "arboard", "auto-launch"}
    dependencies = cargo.split("[dependencies]\n", 1)[1].split("[target.", 1)[0]
    dependencies = "\n".join(line for line in dependencies.splitlines()
                             if not line.startswith("tauri") and not any(line.startswith(x + " =") for x in desktop))
    (target / "Cargo.toml").write_text('''[package]
name = "cc-switch-web"
version = "4.0.30"
edition = "2021"
autobins = false

[lib]
name = "cc_switch_lib"

[[bin]]
name = "cc-switch-server"
path = "src/web_main.rs"

[features]
test-hooks = []

[dependencies]
''' + dependencies + '''
tauri = { path = "headless" }
libc = "0.2"
env_logger = "0.11"
tower-http = { version = "0.5", features = ["fs"] }
tokio-stream = { version = "0.1", features = ["sync"] }
'''.replace('tower-http = { version = "0.5", features = ["fs"] }', '') , encoding="utf-8")
    text = (target / "Cargo.toml").read_text(encoding="utf-8").replace('features = ["cors"]', 'features = ["cors", "fs"]')
    text = text.replace('reqwest = { version = "0.12",', 'reqwest = { version = "0.12", default-features = false,')
    text = text.replace('"sync", "net", "process"', '"sync", "net", "process", "signal"')
    (target / "Cargo.toml").write_text(text, encoding="utf-8")
    if (HERE / "Cargo.lock").is_file():
        shutil.copy2(HERE / "Cargo.lock", target / "Cargo.lock")
    lib = (target / "src/lib.rs").read_text(encoding="utf-8")
    declarations = lib[:lib.index("use tauri_plugin_deep_link")]
    for module in ("auto_launch", "lightweight", "linux_fix", "tray_compat", "tray_panel", "window_fit"):
        declarations = re.sub(r'(?:#\[cfg\([^\n]+\)\]\n)?mod ' + module + r';\n', '', declarations)
    redaction = lib[lib.index("pub(crate) struct RedactedUrl"):lib.index("fn runtime_log_level_allows")]
    (target / "src/lib.rs").write_text(declarations + "\nuse std::fmt;\n" + redaction + '\npub mod web;\n', encoding="utf-8")
    for path in (HERE / "overlay").rglob("*"):
        if path.is_file():
            dest = target / path.relative_to(HERE / "overlay")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, dest)
    tray = (source / "src-tauri/src/tray.rs").read_text(encoding="utf-8")
    routing = tray[tray.index("const MANAGED_OAUTH_PROVIDER_TYPES"):tray.index("// ─── 快照")]
    with (target / "src/tray.rs").open("a", encoding="utf-8") as out:
        out.write("\nuse crate::{AppType, Provider};\n" + routing)
    # These pinned upstream startup blocks are platform-independent. Keep their
    # recovery/import ordering and business migrations instead of reimplementing them.
    startup = lib[lib.index("            crate::mode::operation::recover_on_startup"):lib.index("            // 迁移旧的 app_config_dir 配置到 Store")]
    common = lib[lib.index("fn initialize_common_config_snippets"):lib.index("// 迁移错误对话框辅助函数")]
    common = common[:common.rfind("// ============================================================")]
    (target / "src/web_startup.rs").write_text("use crate::{AppState, AppError, store};\npub fn initialize(app_state: &AppState) {\n" + startup + "\n}\n" + common.replace("fn initialize_common_config_snippets", "pub fn initialize_common_config_snippets"), encoding="utf-8")
    with (target / "src/lib.rs").open("a", encoding="utf-8") as out:
        out.write("\nmod web_startup;\n")
    handler_list = lib.split(".invoke_handler(tauri::generate_handler![", 1)[1].split("])", 1)[0]
    names = sorted(set(re.findall(r"commands::(?:\w+::)*(\w+)", handler_list)))
    (target / "src/web_commands.json").write_text(json.dumps(names), encoding="utf-8")
    # Command attributes are implemented by our transport macro. Desktop-only
    # command bodies are rejected explicitly, never reported as successful.
    for path in (target / "src/commands").rglob("*.rs"):
        text = path.read_text(encoding="utf-8")
        text = re.sub(r'^use tauri_plugin_[^;]+;\n', '', text, flags=re.M)
        text = text.replace('<R: tauri::Runtime>', '').replace('tauri::AppHandle<R>', 'tauri::AppHandle')
        path.write_text(text, encoding="utf-8")
    (target / "UPSTREAM.json").write_text(json.dumps({"version": "4.0.30", "commit": COMMIT}, indent=2) + "\n")
    renderer = target / "renderer"
    renderer.mkdir(exist_ok=True)
    for name in ("src", "public"):
        if (source / name).is_dir():
            shutil.copytree(source / name, renderer / name, dirs_exist_ok=True)
    for name in ("package.json", "pnpm-lock.yaml", "pnpm-workspace.yaml", "postcss.config.cjs", "tailwind.config.cjs", "tsconfig.json", "tsconfig.node.json"):
        shutil.copy2(source / name, renderer / name)
    shutil.copy2(HERE / "frontend/bridge.ts", renderer / "web-bridge.ts")
    shutil.copy2(HERE / "frontend/vite.config.ts", renderer / "vite.config.ts")
    # Window chrome belongs to the browser/Android host.
    controls = renderer / "src/components/shell/WindowControls.tsx"
    controls.write_text("export function WindowControls() { return null; }\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("target", type=Path)
    parser.add_argument("--refresh", action="store_true", help="Refresh an owned generated tree after editing this adapter")
    args = parser.parse_args()
    prepare(args.source.resolve(), args.target.resolve(), args.refresh)
