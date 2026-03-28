const { test, expect } = require('@playwright/test');

test.beforeEach(async ({ page, request }) => {
  const username = process.env.E2E_USERNAME || 'admin';
  const password = process.env.E2E_PASSWORD || 'admin';
  const loginResponse = await request.post('/api/auth/login', {
    data: { username, password },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const loginPayload = await loginResponse.json();
  const sessionData = loginPayload?.data || loginPayload;
  const token = sessionData?.token;
  expect(token).toBeTruthy();

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
});

test('home and command deck should be reachable after login', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/(home)?$/);

  await page.keyboard.press('Control+KeyK');
  const paletteInput = page.getByPlaceholder(/搜索页面与动作\.\.\.|输入关键字快速跳转/);
  await expect(paletteInput).toBeVisible();
  await paletteInput.fill('运营指挥台');
  await page.getByRole('button', { name: /运营指挥台/ }).first().click();

  await expect(page).toHaveURL(/operations-command/);
  await expect(page.getByRole('heading', { name: '运营指挥台' })).toBeVisible();
});

test('operations command can switch lanes', async ({ page }) => {
  await page.goto('/operations-command');
  await expect(page.getByRole('heading', { name: '运营指挥台' })).toBeVisible();

  await page.locator('.lane-switch .lane-button', { hasText: '风险事件' }).first().click();
  await expect(page.locator('.dispatch-card .card-header').first()).toHaveText('风险闭环队列');

  await page.locator('.lane-switch .lane-button', { hasText: '审批流' }).first().click();
  await expect(page.locator('.dispatch-card .card-header').first()).toHaveText('审批卡点队列');
});
