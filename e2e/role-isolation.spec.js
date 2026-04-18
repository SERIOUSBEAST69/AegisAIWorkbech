const { test, expect } = require('@playwright/test');

async function loginAndSession(page, request, username, password) {
  const loginResponse = await request.post('/api/auth/login', {
    data: { username, password },
  });
  expect(loginResponse.ok()).toBeTruthy();
  const payload = await loginResponse.json();
  const session = payload?.data || payload;
  expect(session?.token).toBeTruthy();

  await page.addInitScript((sessionData) => {
    const now = Date.now();
    const normalized = {
      token: sessionData.token,
      mode: 'real',
      createdAt: now,
      expiresAt: now + 4 * 60 * 60 * 1000,
      user: sessionData.user || null,
    };
    localStorage.setItem('aegis.session', JSON.stringify(normalized));
    localStorage.setItem('token', normalized.token);
  }, session);

  return session;
}

function authHeader(token) {
  return { Authorization: `Bearer ${token}` };
}

async function expectDenied(response, label) {
  const httpDenied = [401, 403].includes(response.status());
  if (httpDenied) {
    expect(httpDenied, `${label} should be denied by HTTP status`).toBeTruthy();
    return;
  }

  const payload = await response.json().catch(() => ({}));
  const code = String(payload?.code || '');
  const bizDenied = code.length > 0 && code !== '20000';
  expect(
    bizDenied,
    `${label} should be denied (status=${response.status()}, code=${code || 'N/A'})`
  ).toBeTruthy();
}

test('admin and secops UI buttons should be isolated', async ({ page, request }) => {
  await loginAndSession(page, request, process.env.E2E_USERNAME || 'admin', process.env.E2E_PASSWORD || 'admin');

  await page.goto('/policy-manage');
  await expect(page.getByRole('heading', { name: /合规策略列表|策略管理/ })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增策略' })).toBeVisible();

  await page.goto('/approval-center');
  await expect(page.getByRole('heading', { name: '审批中心' })).toBeVisible();
  await expect(page.getByRole('button', { name: '发起治理变更' })).toBeVisible();

  await page.goto('/permission-manage');
  await expect(page.getByRole('heading', { name: '权限管理' })).toBeVisible();
  await page.getByRole('tab', { name: '职责分离规则' }).click();
  await expect(page.getByRole('button', { name: '新增规则' })).toBeVisible();

  await loginAndSession(page, request, 'secops', 'Passw0rd!');

  await page.goto('/policy-manage');
  await expect(page.getByRole('heading', { name: /合规策略列表|策略管理/ })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增策略' })).toHaveCount(0);

  await page.goto('/approval-center');
  await expect(page.getByRole('heading', { name: '审批中心' })).toBeVisible();

  await page.goto('/threat-monitor');
  await expect(page.getByRole('heading', { name: /AI攻击实时防御|AI数据防泄漏/ })).toBeVisible();
});

test('admin and secops backend permissions should be isolated', async ({ request }) => {
  const adminSession = await (async () => {
    const res = await request.post('/api/auth/login', { data: { username: 'admin', password: 'admin' } });
    expect(res.ok()).toBeTruthy();
    const payload = await res.json();
    return payload?.data || payload;
  })();

  const secopsSession = await (async () => {
    const res = await request.post('/api/auth/login', { data: { username: 'secops', password: 'Passw0rd!' } });
    expect(res.ok()).toBeTruthy();
    const payload = await res.json();
    return payload?.data || payload;
  })();

  const adminToggle = await request.post('/api/policy/toggle-status', {
    headers: authHeader(adminSession.token),
    data: { id: 1, status: 'ENABLED', confirmPassword: 'admin' },
  });
  expect(adminToggle.ok()).toBeTruthy();

  const adminDataAssetList = await request.get('/api/data-asset/list', {
    headers: authHeader(adminSession.token),
  });
  expect(adminDataAssetList.ok()).toBeTruthy();

  const secopsDataAssetList = await request.get('/api/data-asset/list', {
    headers: authHeader(secopsSession.token),
  });
  await expectDenied(secopsDataAssetList, 'secops data asset list');

  const secopsSodSave = await request.post('/api/sod-rules/save', {
    headers: authHeader(secopsSession.token),
    data: {
      scenario: `SOD_${Date.now()}`,
      roleCodeA: 'ADMIN',
      roleCodeB: 'SECOPS',
      enabled: 1,
      description: 'e2e isolation check',
    },
  });
  await expectDenied(secopsSodSave, 'secops sod rule save');

  const secopsGovernSubmit = await request.post('/api/governance-change/submit', {
    headers: authHeader(secopsSession.token),
    data: {
      module: 'ROLE',
      action: 'ADD',
      payloadJson: '{"name":"secops_submit_probe","code":"SECOPS_SUBMIT_PROBE"}',
      confirmPassword: 'Passw0rd!',
    },
  });
  expect(secopsGovernSubmit.ok()).toBeTruthy();
  const secopsSubmitPayload = await secopsGovernSubmit.json();
  expect(String(secopsSubmitPayload?.code || '')).toBe('20000');

  const submittedRequestId = secopsSubmitPayload?.data?.id || secopsSubmitPayload?.data?.requestId;
  if (submittedRequestId) {
    const secopsApprove = await request.post('/api/governance-change/approve', {
      headers: authHeader(secopsSession.token),
      data: {
        requestId: submittedRequestId,
        approve: true,
        note: 'secops should not approve',
        confirmPassword: 'Passw0rd!',
      },
    });
    await expectDenied(secopsApprove, 'secops governance approve');
  }

  const adminSecurityEvents = await request.get('/api/security/events?page=1&pageSize=5', {
    headers: authHeader(adminSession.token),
  });
  expect(adminSecurityEvents.ok()).toBeTruthy();

  const secopsSecurityEvents = await request.get('/api/security/events?page=1&pageSize=5', {
    headers: authHeader(secopsSession.token),
  });
  expect(secopsSecurityEvents.ok()).toBeTruthy();
});
