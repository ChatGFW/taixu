// node browser-smoke.mjs <directory containing playwright> <output directory>
import { createRequire } from 'node:module';
import path from 'node:path';
import fs from 'node:fs/promises';
const require = createRequire(path.resolve(process.argv[2], 'package.json'));
const { chromium } = require('playwright');
const output = path.resolve(process.argv[3]);
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge', headless: true });
try {
  const context = await browser.newContext({
    httpCredentials: { username: 'admin', password: 'isolated-ui-password' },
    viewport: { width: 412, height: 915 },
  });
  const page = await context.newPage();
  const errors = [];
  const failures = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('response', response => {
    if (response.url().includes('/api/') && response.status() >= 400) failures.push({url:response.url(),status:response.status()});
  });
  await page.goto('http://127.0.0.1:19871/', {waitUntil:'domcontentloaded'});
  await page.getByRole('button').first().waitFor({timeout:30000});
  await page.waitForTimeout(2000);
  const welcome = page.getByRole('button', {name:'我知道了',exact:true});
  if (await welcome.isVisible()) await welcome.click();
  await page.screenshot({path:path.join(output,'mobile.png'),fullPage:true});
  await page.getByRole('button', {name:'添加供应商',exact:true}).click();
  await page.waitForTimeout(1500);
  await page.screenshot({path:path.join(output,'provider-dialog.png'),fullPage:true});
  await fs.writeFile(path.join(output,'result.json'),JSON.stringify({errors,failures,text:await page.locator('body').innerText()},null,2));
  console.log(JSON.stringify({errors,failures,text:(await page.locator('body').innerText()).slice(0,1800)},null,2));
  if(errors.length || failures.length) process.exitCode=1;
} finally { await browser.close(); }
