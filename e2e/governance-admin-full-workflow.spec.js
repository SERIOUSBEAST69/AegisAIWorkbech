const { test, expect } = require('@playwright/test');

async function loginAs(page, request, username, password) {
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

async function clickButtonIfVisible(page, namePattern) {
  const button = page.getByRole('button', { name: namePattern }).first();
  if (await button.isVisible().catch(() => false)) {
    await button.click();
    return true;
  }
  return false;
}

async function assertSessionAlive(page, route) {
  await expect(page, `route ${route} should not redirect to login`).not.toHaveURL(/\/login/);
}

async function dismissDeckBackdropIfPresent(page) {
  const backdrop = page.locator('button.deck-backdrop').first();
  if (await backdrop.isVisible().catch(() => false)) {
    await backdrop.click({ force: true });
  }
}

async function doCommonOps(page) {
  await dismissDeckBackdropIfPresent(page);
  await clickButtonIfVisible(page, /查询|搜索|刷新/);
  await dismissDeckBackdropIfPresent(page);
  const nextPage = page.locator('.el-pagination .btn-next:not([disabled]):not(.is-disabled)').first();
  if (await nextPage.isVisible().catch(() => false)) {
    await nextPage.click({ force: true });
  }
}

test('governance admin pages and key buttons should keep session alive', async ({ page, request }) => {
  const badResponses = [];
  page.on('response', (resp) => {
    const url = resp.url();
    if (!url.includes('/api/')) return;
    const status = resp.status();
    if (status === 401 || status >= 500) {
      badResponses.push({ url, status });
    }
  });

  await loginAs(page, request, process.env.E2E_USERNAME || 'admin', process.env.E2E_PASSWORD || 'admin');

  const routes = [
    '/',
    '/operations-command',
    '/data-asset',
    '/sensitive-scan',
    '/approval-manage',
    '/governance-change-manage',
    '/sod-rule-manage',
    '/policy-manage',
    '/user-manage',
    '/role-manage',
    '/permission-manage',
    '/subject-request',
    '/audit-log',
    '/audit-report',
    '/ai/risk-rating',
    '/threat-monitor',
    '/profile',
    '/settings',
  ];

  for (const route of routes) {
    await page.goto(route);
    await assertSessionAlive(page, route);
    await doCommonOps(page);
    await assertSessionAlive(page, route);
  }

  await page.goto('/governance-change-manage');
  await assertSessionAlive(page, '/governance-change-manage');

  const canOpenSubmit = await clickButtonIfVisible(page, /发起治理变更/);
  if (canOpenSubmit) {
    await page.locator('textarea[placeholder*="例如"]').fill(JSON.stringify({
      name: 'E2E治理角色',
      code: `E2E_GOV_${Date.now()}`,
      description: 'governance e2e',
    }));

    await clickButtonIfVisible(page, /提交申请/);

    const pwdInput = page.locator('.el-message-box__input input').first();
    if (await pwdInput.isVisible().catch(() => false)) {
      await pwdInput.fill('admin');
      await page.getByRole('button', { name: /确认提交|确定|确认/ }).last().click();
      await expect(page.locator('.el-message--success').first()).toBeVisible({ timeout: 10000 });
    }
  }

  await assertSessionAlive(page, '/governance-change-manage-submit');
  expect(badResponses, `unexpected unauthorized/server errors: ${JSON.stringify(badResponses)}`).toEqual([]);
});
