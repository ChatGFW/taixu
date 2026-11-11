(function () {
  'use strict';

  var doc = document;
  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var header = doc.querySelector('[data-header]');
  var nav = doc.getElementById('site-nav');
  var toggle = doc.querySelector('[data-nav-toggle]');
  var year = doc.querySelector('[data-year]');

  if (year) year.textContent = String(new Date().getFullYear());

  function onScroll() {
    if (header) header.classList.toggle('is-scrolled', window.scrollY > 12);
  }

  onScroll();
  window.addEventListener('scroll', onScroll, { passive: true });

  function closeNav() {
    if (!nav || !toggle) return;
    nav.classList.remove('is-open');
    toggle.setAttribute('aria-expanded', 'false');
    toggle.setAttribute('aria-label', '打开导航');
    doc.body.style.removeProperty('overflow');
  }

  function openNav() {
    if (!nav || !toggle) return;
    nav.classList.add('is-open');
    toggle.setAttribute('aria-expanded', 'true');
    toggle.setAttribute('aria-label', '关闭导航');
    doc.body.style.overflow = 'hidden';
  }

  if (toggle && nav) {
    toggle.addEventListener('click', function () {
      if (toggle.getAttribute('aria-expanded') === 'true') closeNav();
      else openNav();
    });
    nav.addEventListener('click', function (event) {
      if (event.target.closest('a')) closeNav();
    });
    doc.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') closeNav();
    });
    window.addEventListener('resize', function () {
      if (window.innerWidth > 980) closeNav();
    });
  }

  var revealElements = Array.prototype.slice.call(doc.querySelectorAll('.reveal'));
  if (!('IntersectionObserver' in window) || reduceMotion) {
    revealElements.forEach(function (element) { element.classList.add('in'); });
  } else {
    var revealObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        entry.target.classList.add('in');
        revealObserver.unobserve(entry.target);
      });
    }, { threshold: 0.08, rootMargin: '0px 0px -5% 0px' });
    revealElements.forEach(function (element) { revealObserver.observe(element); });
  }

  var navLinks = Array.prototype.slice.call(doc.querySelectorAll('.site-nav a[href^="#"]'));
  var sections = navLinks.map(function (link) {
    return doc.getElementById(link.getAttribute('href').slice(1));
  }).filter(Boolean);

  if ('IntersectionObserver' in window && sections.length) {
    var sectionObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        navLinks.forEach(function (link) {
          link.classList.toggle('is-current', link.getAttribute('href') === '#' + entry.target.id);
        });
      });
    }, { rootMargin: '-42% 0px -52% 0px', threshold: 0 });
    sections.forEach(function (section) { sectionObserver.observe(section); });
  }

  var scenarios = {
    fix: {
      id: 'TASK / 001',
      label: '修复构建执行链演示',
      prompt: '把这个项目跑起来，修好构建错误，再告诉我改了什么。',
      env: 'Ubuntu 24.04 · Node.js',
      policy: '写入前审批',
      workspace: '/workspace/web-app',
      steps: [
        ['扫描项目结构', '识别包管理器、入口与现有脚本'],
        ['复现构建错误', '在 Linux 沙箱中运行构建并保留日志'],
        ['修改并重新验证', '生成行级 Diff，经审批后写入'],
        ['汇总变更与产物', '回传构建结果、修改摘要与后续建议']
      ],
      result: '构建通过 · 修复 2 处依赖与配置问题 · 生成 18 行 Diff · 产物位于 dist/'
    },
    release: {
      id: 'TASK / 002',
      label: '准备发布执行链演示',
      prompt: '核对这次发布产物，整理变更，并准备一份发版说明。',
      env: 'Ubuntu 24.04 · Android SDK',
      policy: '安装与推送需审批',
      workspace: '/workspace/android-app',
      steps: [
        ['读取发布目标', '确认版本、分支与发布检查清单'],
        ['并行核对产物', '校验 APK、SHA-256 与构建日志'],
        ['整理变更与说明', '读取提交历史，生成可读的发版摘要'],
        ['等待最终确认', '汇总发布包与说明，人工确认后交付']
      ],
      result: '发布检查完成 · APK 与 SHA-256 已核对 · 发版说明已生成 · 等待最终确认'
    },
    review: {
      id: 'TASK / 003',
      label: '评审改动执行链演示',
      prompt: '看看这次改动有没有问题，按严重程度给我评审意见。',
      env: 'Debian 12 · Git Workbench',
      policy: 'PLAN 模式 · 只读',
      workspace: '/workspace/runtime',
      steps: [
        ['确定评审范围', '读取分支差异、提交意图与项目约束'],
        ['检查关键路径', '追踪权限、状态恢复与错误处理链路'],
        ['运行针对性验证', '执行现有测试并核对边界条件'],
        ['输出分级评审', '按严重程度回传行级意见与修复建议']
      ],
      result: '评审完成 · 2 项需修复 · 1 项改进建议 · 已生成行级评论与修复计划'
    }
  };

  var scenarioTabs = Array.prototype.slice.call(doc.querySelectorAll('[data-scenario]'));
  var scenarioPanel = doc.getElementById('scenario-panel');
  var scenarioId = doc.querySelector('[data-scenario-id]');
  var scenarioPrompt = doc.querySelector('[data-scenario-prompt]');
  var scenarioEnv = doc.querySelector('[data-scenario-env]');
  var scenarioPolicy = doc.querySelector('[data-scenario-policy]');
  var scenarioWorkspace = doc.querySelector('[data-scenario-workspace]');
  var stepsList = doc.querySelector('[data-execution-steps]');
  var runStatus = doc.querySelector('[data-run-status]');
  var runProgress = doc.querySelector('[data-run-progress]');
  var runResult = doc.querySelector('[data-run-result] p');
  var playButton = doc.querySelector('[data-play]');
  var playLabel = doc.querySelector('[data-play-label]');
  var activeScenario = 'fix';
  var timers = [];
  var playToken = 0;
  var playing = false;

  function clearPlayback() {
    playToken += 1;
    timers.forEach(window.clearTimeout);
    timers = [];
    playing = false;
    if (playButton) playButton.disabled = false;
  }

  function createStep(step, index) {
    var item = doc.createElement('li');
    var number = doc.createElement('span');
    var content = doc.createElement('div');
    var title = doc.createElement('b');
    var description = doc.createElement('small');
    var state = doc.createElement('em');
    number.textContent = String(index + 1).padStart(2, '0');
    title.textContent = step[0];
    description.textContent = step[1];
    state.textContent = '等待';
    content.appendChild(title);
    content.appendChild(description);
    item.appendChild(number);
    item.appendChild(content);
    item.appendChild(state);
    item.setAttribute('data-step', '');
    return item;
  }

  function renderScenario(key) {
    var scenario = scenarios[key];
    if (!scenario || !stepsList) return;
    clearPlayback();
    activeScenario = key;
    scenarioTabs.forEach(function (tab) {
      var selected = tab.getAttribute('data-scenario') === key;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
    });
    if (scenarioPanel) scenarioPanel.setAttribute('aria-label', scenario.label);
    if (scenarioId) scenarioId.textContent = scenario.id;
    if (scenarioPrompt) scenarioPrompt.textContent = scenario.prompt;
    if (scenarioEnv) scenarioEnv.textContent = scenario.env;
    if (scenarioPolicy) scenarioPolicy.textContent = scenario.policy;
    if (scenarioWorkspace) scenarioWorkspace.textContent = scenario.workspace;
    stepsList.replaceChildren();
    scenario.steps.forEach(function (step, index) {
      stepsList.appendChild(createStep(step, index));
    });
    if (runStatus) runStatus.textContent = '准备就绪';
    if (runProgress) runProgress.textContent = '0 / 4';
    if (runResult) runResult.textContent = '点击播放，查看完整执行轨迹与交付结果。';
    if (playLabel) playLabel.textContent = '播放执行轨迹';
  }

  function finishPlayback(token) {
    if (token !== playToken) return;
    var items = Array.prototype.slice.call(stepsList.querySelectorAll('[data-step]'));
    items.forEach(function (item) {
      item.classList.remove('is-active');
      item.classList.add('is-complete');
      item.querySelector('em').textContent = '完成';
    });
    playing = false;
    playButton.disabled = false;
    playLabel.textContent = '重新播放';
    runStatus.textContent = '任务完成 · 产物已回传';
    runProgress.textContent = '4 / 4';
    runResult.textContent = scenarios[activeScenario].result;
  }

  function playScenario() {
    if (playing || !stepsList) return;
    renderScenario(activeScenario);
    var token = playToken;
    var items = Array.prototype.slice.call(stepsList.querySelectorAll('[data-step]'));
    playing = true;
    playButton.disabled = true;
    playLabel.textContent = '执行中…';
    runResult.textContent = '执行轨迹正在推进，关键写入操作仍需经过审批。';

    if (reduceMotion) {
      finishPlayback(token);
      return;
    }

    items.forEach(function (item, index) {
      timers.push(window.setTimeout(function () {
        if (token !== playToken) return;
        items.forEach(function (other, otherIndex) {
          other.classList.toggle('is-complete', otherIndex < index);
          other.classList.toggle('is-active', otherIndex === index);
          other.querySelector('em').textContent = otherIndex < index ? '完成' : (otherIndex === index ? '执行中' : '等待');
        });
        runStatus.textContent = '正在执行 · ' + scenarios[activeScenario].steps[index][0];
        runProgress.textContent = index + ' / 4';
      }, index * 720));
    });
    timers.push(window.setTimeout(function () { finishPlayback(token); }, items.length * 720));
  }

  scenarioTabs.forEach(function (tab, index) {
    tab.addEventListener('click', function () { renderScenario(tab.getAttribute('data-scenario')); });
    tab.addEventListener('keydown', function (event) {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      var direction = event.key === 'ArrowRight' ? 1 : -1;
      var next = scenarioTabs[(index + direction + scenarioTabs.length) % scenarioTabs.length];
      renderScenario(next.getAttribute('data-scenario'));
      next.focus();
    });
  });
  if (playButton) playButton.addEventListener('click', playScenario);

  var screens = {
    agent: {
      index: '01 / 智枢',
      watermark: 'AGENT',
      image: 'assets/screen-agent-dark-sm.jpg',
      alt: '智枢 Agent 对话界面实机截图',
      title: '让意图进入执行链。',
      description: '对话、计划、工具调用与审批集中在同一个任务现场。你可以看见 Agent 正在做什么，并在关键操作前介入。',
      points: ['流式对话与推理过程', '任务计划与工具调用', '分级审批与检查点回退']
    },
    workspace: {
      index: '02 / 工坊',
      watermark: 'WORKSPACE',
      image: 'assets/screen-workspace-dark-sm.jpg',
      alt: '工坊项目工作区界面实机截图',
      title: '项目，不再只是文件列表。',
      description: '新建、导入或克隆工程，在共享存储与隔离工作区之间各取所需。源码、Diff、Git、构建和产物围绕同一个项目组织。',
      points: ['项目模板与 GitHub 克隆', '源码浏览与行级 Diff', '后台构建、签名与安装 APK']
    },
    settings: {
      index: '03 / 乾坤',
      watermark: 'CONTROL',
      image: 'assets/screen-settings-dark-sm.jpg',
      alt: '乾坤系统设置界面实机截图',
      title: '把运行环境握在手里。',
      description: '模型档案、插件工具、Linux 容器、外观与系统保活集中管理。环境状态与诊断信息清楚可见，配置不再藏在命令行深处。',
      points: ['模型、技能与插件中心', '多发行版与存储映射', '环境体检、自愈与诊断']
    },
    theme: {
      index: '04 / 澄明',
      watermark: 'CLARITY',
      image: 'assets/theme-chengming-light-sm.jpg',
      alt: '太墟澄明浅色主题界面实机截图',
      title: '技术工具，也可以清澈好用。',
      description: '澄明主题把运行状态、资源监控与关键入口放在清晰层级里。深浅色主题与壁纸取色，让复杂系统仍然易于阅读。',
      points: ['运行仪表盘与资源监控', '环境体检与自愈入口', '深浅色主题与壁纸取色']
    }
  };

  var screenTabs = Array.prototype.slice.call(doc.querySelectorAll('[data-screen]'));
  var screenPanel = doc.getElementById('screen-panel');
  var screenImage = doc.querySelector('[data-screen-image]');
  var screenWatermark = doc.querySelector('[data-screen-watermark]');
  var screenIndex = doc.querySelector('[data-screen-index]');
  var screenTitle = doc.querySelector('[data-screen-title]');
  var screenDescription = doc.querySelector('[data-screen-description]');
  var screenPoints = doc.querySelector('[data-screen-points]');
  var screenToken = 0;

  function selectScreen(key) {
    var screen = screens[key];
    if (!screen || !screenImage) return;
    screenToken += 1;
    var token = screenToken;
    screenTabs.forEach(function (tab) {
      var selected = tab.getAttribute('data-screen') === key;
      tab.setAttribute('aria-selected', String(selected));
      tab.tabIndex = selected ? 0 : -1;
      if (selected && screenPanel) screenPanel.setAttribute('aria-labelledby', tab.id);
    });
    screenImage.classList.add('is-switching');
    window.setTimeout(function () {
      if (token !== screenToken) return;
      screenImage.src = screen.image;
      screenImage.alt = screen.alt;
      screenWatermark.textContent = screen.watermark;
      screenIndex.textContent = screen.index;
      screenTitle.textContent = screen.title;
      screenDescription.textContent = screen.description;
      screenPoints.replaceChildren();
      screen.points.forEach(function (point) {
        var item = doc.createElement('li');
        item.textContent = point;
        screenPoints.appendChild(item);
      });
      window.requestAnimationFrame(function () { screenImage.classList.remove('is-switching'); });
    }, reduceMotion ? 0 : 120);
  }

  screenTabs.forEach(function (tab, index) {
    tab.addEventListener('click', function () { selectScreen(tab.getAttribute('data-screen')); });
    tab.addEventListener('keydown', function (event) {
      if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
      event.preventDefault();
      var direction = event.key === 'ArrowRight' ? 1 : -1;
      var next = screenTabs[(index + direction + screenTabs.length) % screenTabs.length];
      selectScreen(next.getAttribute('data-screen'));
      next.focus();
    });
  });

  Object.keys(screens).forEach(function (key) {
    var image = new Image();
    image.src = screens[key].image;
  });

  var tilt = doc.querySelector('[data-tilt]');
  if (tilt && !reduceMotion && window.matchMedia('(pointer: fine)').matches) {
    var tiltFrame = 0;
    tilt.addEventListener('pointermove', function (event) {
      if (tiltFrame) return;
      tiltFrame = window.requestAnimationFrame(function () {
        var rect = tilt.getBoundingClientRect();
        var x = (event.clientX - rect.left) / rect.width - .5;
        var y = (event.clientY - rect.top) / rect.height - .5;
        tilt.style.setProperty('--tilt-x', (-y * 2.2).toFixed(2) + 'deg');
        tilt.style.setProperty('--tilt-y', (x * 2.2).toFixed(2) + 'deg');
        tiltFrame = 0;
      });
    });
    tilt.addEventListener('pointerleave', function () {
      tilt.style.setProperty('--tilt-x', '0deg');
      tilt.style.setProperty('--tilt-y', '0deg');
    });
  }
})();
