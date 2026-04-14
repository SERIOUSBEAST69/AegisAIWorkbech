const { test, expect } = require('@playwright/test');

async function loginByApi(page, request) {
  const username = process.env.E2E_USERNAME || 'admin';
  const password = process.env.E2E_PASSWORD || 'admin';
  const loginResponse = await request.post('/api/auth/login', {
    data: { username, password },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const loginPayload = await loginResponse.json();
  const sessionData = loginPayload?.data || loginPayload;
  expect(sessionData?.token).toBeTruthy();

  await page.addInitScript((session) => {
    const now = Date.now();
    const normalized = {
      token: session.token,
      mode: 'real',
      createdAt: now,
      expiresAt: now + 4 * 60 * 60 * 1000,
      user: session.user || null,
    };
    localStorage.setItem('aegis.session', JSON.stringify(normalized));
    localStorage.setItem('token', normalized.token);
  }, sessionData);
}

async function clearRevealOverlayIfPresent(page) {
  const enterBtn = page.locator('.home-logo-enter-btn, .home-reveal-logo-enter').first();
  if (await enterBtn.isVisible().catch(() => false)) {
    await enterBtn.click({ force: true });
  }
}

test('menu navigation to anomaly page should be smooth and render gallery', async ({ page, request }) => {
  const pageErrors = [];
  page.on('pageerror', (err) => pageErrors.push(String(err?.message || err)));

  await loginByApi(page, request);
  await page.goto('/');
  await expect(page).not.toHaveURL(/\/login/);
  await clearRevealOverlayIfPresent(page);
  await page.waitForSelector('header.app-header, .app-header', { timeout: 15000 });

  const menuCandidates = [
    '.sm-panel-item:has-text("AI使用合规监控")',
    '.sm-panel-item:has-text("员工AI异常行为监控")',
    '.sm-panel-item:has-text("异常")',
    '.sm-submenu-item:has-text("AI使用合规监控")',
    'a[href="/ai/anomaly"]',
    '.el-menu-item a[href="/ai/anomaly"]',
    '.el-menu-item:has-text("员工AI")',
    '.el-menu-item:has-text("异常")',
  ];

  let clicked = false;
  const t0 = Date.now();

  const menuToggle = page.getByRole('button', { name: /Open navigation menu|Close navigation menu/i }).first();
  if (await menuToggle.isVisible().catch(() => false)) {
    await menuToggle.click({ force: true });
    await page.waitForTimeout(450);
    const anomalyMenu = page.locator('.sm-panel-item:has-text("AI使用合规监控"), .sm-panel-item:has-text("异常"), .sm-submenu-item:has-text("AI使用合规监控")').first();
    if (await anomalyMenu.isVisible().catch(() => false)) {
      await anomalyMenu.evaluate((el) => el.click());
      clicked = true;
    }
  }

  for (const selector of menuCandidates) {
    if (clicked) break;
    const node = page.locator(selector).first();
    if (await node.isVisible().catch(() => false)) {
      await node.click({ force: true });
      clicked = true;
      break;
    }
  }

  if (!clicked) {
    await page.goto('/ai/anomaly');
  }

  await expect(page).toHaveURL(/\/ai\/anomaly/);
  await clearRevealOverlayIfPresent(page);
  await page.waitForSelector('.sim-canvas-wrap, .circular-gallery-react-host', { timeout: 30000 });

  const elapsedMs = Date.now() - t0;
  const cardsText = await page.locator('.sim-actions').first().innerText().catch(() => '');
  const summary = {
    navigationElapsedMs: elapsedMs,
    usedMenuClick: clicked,
    cardsText,
    pageErrorsCount: pageErrors.length,
    pageErrors,
  };

  console.log('ANOMALY_NAV_SMOKE_RESULT=' + JSON.stringify(summary));
  expect(pageErrors.length).toBe(0);
  expect(elapsedMs).toBeLessThan(15000);
  expect(clicked).toBeTruthy();
});
