/* 太墟使用文档：侧边栏 / 目录 / 上一页下一页 / 搜索 / 主题 */
(function () {
  "use strict";

  // ---------- 页面注册表（顺序即上一页/下一页顺序） ----------
  var PAGES = [
    { file: "index.html", group: "快速开始", title: "介绍：太墟是什么", keywords: "简介 理念 归墟 智枢 工坊 乾坤 入口 术语 rootfs mcp provider bot 工作流 概念" },
    { file: "download.html", group: "快速开始", title: "下载与安装", keywords: "apk 安装 releases sha-256 未知来源 系统要求 android10 空间 升级 覆盖安装" },
    { file: "quickstart.html", group: "快速开始", title: "快速上手：第一条 AI 任务", keywords: "五分钟 新手 第一次 启程向导 rootfs ubuntu api key deepseek 第一条消息 发消息 任务" },
    { file: "providers.html", group: "模型与服务", title: "配置模型服务商", keywords: "服务商 provider base url api key 密钥 deepseek openai claude anthropic gemini 中转 ollama 本地模型 401 报错 chat completions responses messages" },
    { file: "models.html", group: "模型与服务", title: "管理模型与默认设置", keywords: "模型 列表 默认模型 切换 上下文 压缩 用量 usage 模型档案 导入 导出 迁移" },
    { file: "chat.html", group: "对话与工具", title: "智枢对话基础", keywords: "聊天 会话 发消息 图片 多模态 过程链 思考 工具 停止 继续 分支 回退 rewind 悬浮助手 webchat 上下文" },
    { file: "tools.html", group: "对话与工具", title: "工具与 MCP", keywords: "mcp 工具 内置 浏览器 sqlite git websearch codegraph stdio http 服务器 添加 工具不可用 排查 提及" },
    { file: "browser.html", group: "对话与工具", title: "浏览器与网页自动化", keywords: "浏览器 网页 自动化 webview 多标签 browser mcp hook cdp 断点 网络时间线 fetch 拦截 读页面 点击 爬" },
    { file: "approval.html", group: "对话与工具", title: "审批模式与安全", keywords: "审批 权限 完全访问 询问 每次 plan 只读 检查点 checkpoint 回退 安全 密钥 风险" },
    { file: "bots.html", group: "Bot 与团队", title: "认识 Bot：人格与能力", keywords: "bot 智能体 人格 提示词 角色 创建 能力库 助手 区别 会话" },
    { file: "memory.html", group: "Bot 与团队", title: "Bot 记忆", keywords: "记忆 markdown 编辑 历史 冲突 恢复 长期 偏好 该记什么" },
    { file: "team.html", group: "Bot 与团队", title: "群聊与团队协作", keywords: "群聊 团队 协作 提及 分工 主控 独立任务 续办 回报 状态" },
    { file: "workflows.html", group: "自动化", title: "可视化工作流", keywords: "工作流 dag 节点 条件 并行 审批 bash agent 构建 模板 运行历史 导入 导出 json 恢复" },
    { file: "schedules.html", group: "自动化", title: "定时计划与后台运行", keywords: "定时 计划 调度 每日 间隔 workmanager 后台 通知 电池优化 保活 被杀" },
    { file: "terminal.html", group: "自动化", title: "终端与 Linux 环境", keywords: "终端 pty ctrl+c 多会话 命令 apt pacman 包管理 rootfs 发行版 切换 回滚 挂载 共享存储 ssh 远程" },
    { file: "workspace.html", group: "进阶与维护", title: "工坊与 Git", keywords: "工坊 项目 导入 zip github 克隆 源码 diff 构建 gradle flutter apk 签名 安装 git 提交 分支 推送 token" },
    { file: "plugins.html", group: "进阶与维护", title: "插件与技能", keywords: "插件 txplugin process web 包 技能 导入 能力 审批 安装 开发" },
    { file: "more.html", group: "进阶与维护", title: "更多能力与特色功能", keywords: "悬浮助手 快捷指令 webchat 电脑 大屏 ftp 传输 一句话做 app 圆桌会议 晨报哨兵 a2ui 无线 adb shizuku 自动化 权限" },
    { file: "backup.html", group: "进阶与维护", title: "备份与迁移", keywords: "备份 恢复 迁移 换机 导出 导入 包含 不包含 rootfs 密钥 模型档案" },
    { file: "faq.html", group: "进阶与维护", title: "常见问题", keywords: "faq 没反应 不回复 报错 401 429 超时 闪退 内存 卡 死机 卸载 存储 安全 退出去哪 提问" }
  ];

  var current = location.pathname.split("/").pop() || "index.html";
  var meta = PAGES.find(function (p) { return p.file === current; }) || PAGES[0];
  var index = PAGES.indexOf(meta);

  // ---------- 主题（与主站共用 localStorage 键） ----------
  var root = document.documentElement;
  function applyTheme(theme) {
    root.setAttribute("data-theme", theme);
    document.querySelectorAll("[data-theme-toggle]").forEach(function (btn) {
      btn.setAttribute("aria-label", theme === "dark" ? "切换到浅色模式" : "切换到深色模式");
    });
  }
  var stored = null;
  try { stored = localStorage.getItem("taixu-theme"); } catch (e) { /* 忽略 */ }
  applyTheme(stored === "dark" || stored === "light" ? stored
    : (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"));
  document.addEventListener("click", function (event) {
    var btn = event.target.closest("[data-theme-toggle]");
    if (!btn) return;
    var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
    applyTheme(next);
    try { localStorage.setItem("taixu-theme", next); } catch (e) { /* 忽略 */ }
  });

  // ---------- 侧边栏 ----------
  var sidebar = document.querySelector("[data-docs-sidebar]");
  if (sidebar) {
    var groups = [];
    PAGES.forEach(function (p) {
      var g = groups.find(function (x) { return x.name === p.group; });
      if (!g) { g = { name: p.group, pages: [] }; groups.push(g); }
      g.pages.push(p);
    });
    var html = '<div class="sidebar-close"><span>使用文档</span><button type="button" class="theme-toggle" data-sidebar-close aria-label="关闭目录">✕</button></div>';
    groups.forEach(function (g) {
      html += '<nav class="doc-sidebar-group" aria-label="' + g.name + '"><b>' + g.name + "</b>";
      g.pages.forEach(function (p) {
        html += '<a href="' + p.file + '"' + (p.file === current ? ' class="active" aria-current="page"' : "") + ">" + p.title + "</a>";
      });
      html += "</nav>";
    });
    sidebar.innerHTML = html;
  }

  // ---------- 移动端抽屉 ----------
  var menuBtn = document.querySelector("[data-docs-menu]");
  var backdrop = document.querySelector("[data-docs-backdrop]");
  function openDrawer(open) {
    sidebar && sidebar.classList.toggle("open", open);
    backdrop && backdrop.classList.toggle("show", open);
    menuBtn && menuBtn.setAttribute("aria-expanded", String(open));
  }
  menuBtn && menuBtn.addEventListener("click", function () { openDrawer(!sidebar.classList.contains("open")); });
  backdrop && backdrop.addEventListener("click", function () { openDrawer(false); });
  sidebar && sidebar.addEventListener("click", function (e) {
    if (e.target.closest("[data-sidebar-close]") || (e.target.closest("a") && e.target.closest("a").classList.contains("active"))) openDrawer(false);
    else if (e.target.closest("a") && matchMedia("(max-width: 860px)").matches) openDrawer(false);
  });

  // ---------- 右侧目录 ----------
  var toc = document.querySelector("[data-docs-toc]");
  var article = document.querySelector(".doc-article");
  if (toc && article) {
    var heads = article.querySelectorAll("h2, h3");
    var out = "";
    heads.forEach(function (h, i) {
      if (!h.id) h.id = "h-" + i;
      out += '<a class="' + (h.tagName === "H3" ? "lv3" : "") + '" href="#' + h.id + '">' + h.textContent.replace(/↗$/, "") + "</a>";
    });
    if (out) toc.innerHTML = "<b>在此页面</b>" + out;
    else toc.closest(".doc-shell") && (toc.style.display = "none");
  }

  // ---------- 上一页 / 下一页 ----------
  if (article) {
    var prev = PAGES[index - 1];
    var next = PAGES[index + 1];
    if (prev || next) {
      var pager = '<nav class="doc-pager" aria-label="翻页">';
      pager += prev
        ? '<a class="prev" href="' + prev.file + '"><small>← 上一页</small><b>' + prev.title + "</b></a>"
        : "<span></span>";
      pager += next
        ? '<a class="next" href="' + next.file + '"><small>下一页 →</small><b>' + next.title + "</b></a>"
        : "<span></span>";
      pager += "</nav>";
      article.insertAdjacentHTML("beforeend", pager);
    }
  }

  // ---------- 搜索（弹窗由脚本注入，避免每个页面重复标记） ----------
  var overlay = document.querySelector("[data-search-overlay]");
  if (!overlay) {
    document.body.insertAdjacentHTML(
      "beforeend",
      '<div class="search-overlay" data-search-overlay role="dialog" aria-modal="true" aria-label="搜索文档">' +
        '<div class="search-panel">' +
          '<input type="text" data-search-input placeholder="搜索文档…（标题 / 主题）" aria-label="搜索文档">' +
          '<div class="search-results" data-search-results></div>' +
          '<div class="search-hint"><span><kbd>↑↓</kbd> 选择</span><span><kbd>Enter</kbd> 打开</span><span><kbd>Esc</kbd> 关闭</span></div>' +
        "</div></div>"
    );
    overlay = document.querySelector("[data-search-overlay]");
  }
  var input = document.querySelector("[data-search-input]");
  var results = document.querySelector("[data-search-results]");
  var selectedIdx = -1;

  function openSearch() {
    overlay && overlay.classList.add("open");
    input && (input.value = "", renderResults(""));
    setTimeout(function () { input && input.focus(); }, 30);
  }
  function closeSearch() { overlay && overlay.classList.remove("open"); }

  function renderResults(q) {
    if (!results) return;
    q = q.trim().toLowerCase();
    var matched = PAGES.filter(function (p) {
      if (!q) return true;
      return (p.title + " " + p.group + " " + p.keywords).toLowerCase().indexOf(q) !== -1;
    });
    selectedIdx = -1;
    results.innerHTML = matched.map(function (p) {
      return '<a href="' + p.file + '"><b>' + p.title + "</b><span>" + p.group + "</span></a>";
    }).join("");
  }

  document.addEventListener("keydown", function (e) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "k") {
      e.preventDefault();
      overlay && overlay.classList.contains("open") ? closeSearch() : openSearch();
    }
    if (e.key === "Escape") closeSearch();
    if (overlay && overlay.classList.contains("open") && (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Enter")) {
      var items = results ? Array.prototype.slice.call(results.querySelectorAll("a")) : [];
      if (!items.length) return;
      if (e.key === "ArrowDown") { e.preventDefault(); selectedIdx = (selectedIdx + 1) % items.length; }
      else if (e.key === "ArrowUp") { e.preventDefault(); selectedIdx = (selectedIdx - 1 + items.length) % items.length; }
      else if (e.key === "Enter" && items[selectedIdx]) { e.preventDefault(); location.href = items[selectedIdx].getAttribute("href"); return; }
      items.forEach(function (a, i) { a.classList.toggle("selected", i === selectedIdx); });
    }
  });
  document.querySelector("[data-search-open]") && document.querySelector("[data-search-open]").addEventListener("click", openSearch);
  overlay && overlay.addEventListener("click", function (e) { if (e.target === overlay) closeSearch(); });
  input && input.addEventListener("input", function () { renderResults(input.value); });

  // ---------- 年份 ----------
  document.querySelectorAll("[data-year]").forEach(function (el) {
    el.textContent = new Date().getFullYear();
  });
})();
