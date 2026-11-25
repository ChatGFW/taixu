package top.wkbin.taixu.ui.workspace

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import top.wkbin.taixu.runtime.shell.ShellCommand

internal const val WORKSPACE_RUN_FILE = ".taixu-run.json"
internal const val WORKSPACE_RUN_MAX_BYTES = 64 * 1024
internal val workspaceRunSample = """
    {
      "configurations": [
        {
          "name": "工作区检查",
          "command": "pwd",
          "cwd": "."
        }
      ]
    }
""".trimIndent() + "\n"

@Serializable
internal data class WorkspaceRunConfig(
    val name: String,
    val command: String,
    val cwd: String = ".",
    val previewPort: Int? = null,
) {
    init {
        require(name.isNotBlank() && name.length <= 80 && name.none(Char::isISOControl)) { "运行配置名称无效" }
        require(command.isNotBlank() && command.length <= 4_096 && '\u0000' !in command) { "运行命令为空或超出限制" }
        require(cwd.isNotBlank() && cwd.length <= 1_024 && !cwd.startsWith('/') && cwd.none { it == '\\' || it.isISOControl() } &&
            cwd.split('/').none { it == ".." }) { "工作目录必须位于项目内" }
        require(previewPort == null || previewPort in 1..65_535) { "预览端口必须在 1–65535 之间" }
    }

    val previewUrl: String? get() = previewPort?.let { "http://127.0.0.1:$it/" }

    fun shellCommand(projectPath: String) = ShellCommand(
        commandLine = command,
        workingDirectory = listOf(projectPath.trimEnd('/'), cwd)
            .joinToString("/").split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/", prefix = "/"),
    )
}

@Serializable
private data class WorkspaceRunFile(
    val version: Int = 1,
    val configurations: List<WorkspaceRunConfig>,
)

internal fun parseWorkspaceRunConfigs(text: String): List<WorkspaceRunConfig> {
    require(text.length <= WORKSPACE_RUN_MAX_BYTES) { "运行配置文件过大" }
    val file = Json.decodeFromString<WorkspaceRunFile>(text)
    require(file.version == 1) { "不支持此运行配置版本" }
    require(file.configurations.size <= 24) { "运行配置超过 24 项" }
    require(file.configurations.distinctBy { it.name }.size == file.configurations.size) { "运行配置名称重复" }
    return file.configurations
}
