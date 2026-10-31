/* 太墟官网：主题切换 / 移动端导航 / 入场动画 / 年份 */
(function () {
  // ---------- 主题 ----------
  const THEME_KEY = 'taixu-theme';
  const root = document.documentElement;

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    document.querySelectorAll('[data-theme-toggle]').forEach((btn) => {
      btn.setAttribute('aria-label', theme === 'dark' ? '切换到浅色模式' : '切换到深色模式');
    });
  }

  let stored = null;
  try { stored = localStorage.getItem(THEME_KEY); } catch (e) { /* 隐私模式忽略 */ }
  applyTheme(stored === 'dark' || stored === 'light'
    ? stored
    : (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));

  document.addEventListener('click', (event) => {
    const btn = event.target.closest('[data-theme-toggle]');
    if (!btn) return;
    const next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem(THEME_KEY, next); } catch (e) { /* 忽略 */ }
  });

  // ---------- 移动端导航 ----------
  const nav = document.querySelector('[data-nav]');
  const menuToggle = document.querySelector('[data-menu-toggle]');

  function closeMenu() {
    if (!nav || !menuToggle) return;
    nav.classList.remove('open');
    menuToggle.setAttribute('aria-expanded', 'false');
  }

  menuToggle?.addEventListener('click', () => {
    const open = !nav.classList.contains('open');
    nav.classList.toggle('open', open);
    menuToggle.setAttribute('aria-expanded', String(open));
  });

  nav?.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeMenu));
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') closeMenu();
  });

  // ---------- 入场动画 ----------
  const observer = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.1, rootMargin: '0px 0px -30px' });

  document.querySelectorAll('.reveal').forEach((el) => observer.observe(el));

  // ---------- 年份 ----------
  document.querySelectorAll('[data-year]').forEach((el) => {
    el.textContent = new Date().getFullYear();
  });
})();
