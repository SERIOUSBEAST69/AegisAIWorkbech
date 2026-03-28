'use strict';

const DEFAULT_CONFIG = {
  monitorEnabled: true,
  predictEnabled: true,
  predictEndpoint: 'http://localhost:5000/predict',
  backendBaseUrl: 'http://localhost:8080',
  clientIngressToken: '',
  companyId: 1,
  configVersion: 1,
  syncIntervalSec: 60,
  siteSelectors: [
    {
      siteId: 'chatgpt',
      hosts: ['chat.openai.com', 'chatgpt.com'],
      inputSelectors: ['#prompt-textarea', "textarea[data-testid='prompt-textarea']", 'textarea'],
    },
    {
      siteId: 'doubao',
      hosts: ['doubao.com', 'www.doubao.com'],
      inputSelectors: ['textarea', "div[contenteditable='true']", "[data-testid='chat-input']"],
    },
    {
      siteId: 'yiyan',
      hosts: ['yiyan.baidu.com'],
      inputSelectors: ['textarea', "div[contenteditable='true']", '#chat-input'],
    },
  ],
};

const REGEX_RULES = {
  id_card: /[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/g,
  phone: /(?<!\d)1[3-9]\d{9}(?!\d)/g,
  bank_card: /(?<!\d)\d{16,19}(?!\d)/g,
  company_code: /(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/g,
};

async function getLocalConfig() {
  const data = await chrome.storage.local.get(['privacyConfig']);
  return { ...DEFAULT_CONFIG, ...(data.privacyConfig || {}) };
}

async function saveLocalConfig(config) {
  await chrome.storage.local.set({ privacyConfig: config });
}

async function fetchRemoteConfig(baseUrl, sinceVersion) {
  try {
    const query = Number.isFinite(Number(sinceVersion))
      ? `?sinceVersion=${encodeURIComponent(String(sinceVersion))}`
      : '';
    const resp = await fetch(`${baseUrl}/api/privacy/config/public${query}`, {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
    });
    if (!resp.ok) return null;
    const body = await resp.json();
    return body?.data || null;
  } catch {
    return null;
  }
}

async function ensureConfig() {
  const local = await getLocalConfig();
  const localVersion = Number(local.configVersion || 0);
  const remote = await fetchRemoteConfig(local.backendBaseUrl, localVersion);
  let merged = local;
  if (remote && remote.changed === false) {
    merged = {
      ...local,
      configVersion: Number(remote.configVersion || localVersion || 1),
      syncIntervalSec: Number(remote.syncIntervalSec || local.syncIntervalSec || 60),
    };
  } else if (remote) {
    merged = { ...local, ...remote };
  }
  if (!merged.configVersion) {
    merged.configVersion = localVersion || 1;
  }
  await saveLocalConfig(merged);
  return merged;
}

function maskIdCard(value) {
  if (!value || value.length < 10) return value;
  return value.slice(0, 6) + '******' + value.slice(-4);
}

function maskPhone(value) {
  if (!value || value.length !== 11) return value;
  return value.slice(0, 3) + '****' + value.slice(-4);
}

function maskBankCard(value) {
  if (!value || value.length < 10) return value;
  return value.slice(0, 4) + '****' + value.slice(-4);
}

function maskCompanyCode(value) {
  if (!value || value.length < 8) return value;
  return value.slice(0, 4) + '******' + value.slice(-4);
}

function detectByRegex(text) {
  const found = [];
  if (/[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/.test(text)) found.push('id_card');
  if (/(?<!\d)1[3-9]\d{9}(?!\d)/.test(text)) found.push('phone');
  if (/(?<!\d)\d{16,19}(?!\d)/.test(text)) found.push('bank_card');
  if (/(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/.test(text)) found.push('company_code');
  return found;
}

function desensitizeText(text) {
  return String(text || '')
    .replace(REGEX_RULES.id_card, (m) => maskIdCard(m))
    .replace(REGEX_RULES.phone, (m) => maskPhone(m))
    .replace(REGEX_RULES.bank_card, (m) => maskBankCard(m))
    .replace(REGEX_RULES.company_code, (m) => maskCompanyCode(m));
}

async function detectByPredict(endpoint, text) {
  try {
    const resp = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ text }),
    });
    if (!resp.ok) return [];
    const body = await resp.json();
    const label = String(body?.label || '').toLowerCase();
    if (label && label !== 'unknown') {
      return [label];
    }
  } catch {
    // fallback to regex only
  }
  return [];
}

async function detectSensitive(text) {
  const config = await ensureConfig();
  const safeText = String(text || '').trim();
  if (!safeText || !config.monitorEnabled) {
    return { blocked: false, detected: [], maskedText: safeText, config };
  }

  const regexDetected = detectByRegex(safeText);
  const predictDetected = config.predictEnabled
    ? await detectByPredict(config.predictEndpoint, safeText)
    : [];
  const detected = Array.from(new Set([...regexDetected, ...predictDetected]));

  return {
    blocked: detected.length > 0,
    detected,
    maskedText: desensitizeText(safeText),
    config,
  };
}

async function reportEvent(payload) {
  const config = await ensureConfig();
  const event = {
    userId: payload.userId || 'unknown',
    eventType: payload.eventType || 'EXTENSION_SENSITIVE',
    content: desensitizeText(payload.content || ''),
    source: 'extension',
    timestamp: new Date().toISOString(),
    action: payload.action || 'detect',
    matchedTypes: Array.isArray(payload.matchedTypes) ? payload.matchedTypes.join(',') : (payload.matchedTypes || ''),
  };

  try {
    const headers = { 'Content-Type': 'application/json' };
    if (config.clientIngressToken) {
      headers['X-Client-Token'] = String(config.clientIngressToken);
    }
    if (Number.isFinite(Number(config.companyId)) && Number(config.companyId) > 0) {
      headers['X-Company-Id'] = String(config.companyId);
    }
    await fetch(`${config.backendBaseUrl}/api/privacy/events`, {
      method: 'POST',
      headers,
      body: JSON.stringify(event),
    });
  } catch {
    // avoid breaking user workflow
  }
}

chrome.runtime.onInstalled.addListener(async () => {
  await ensureConfig();
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message?.type === 'get-config') {
    ensureConfig().then((config) => sendResponse({ ok: true, config }));
    return true;
  }

  if (message?.type === 'detect-content') {
    detectSensitive(message.text || '').then((result) => sendResponse({ ok: true, ...result }));
    return true;
  }

  if (message?.type === 'report-event') {
    reportEvent(message.payload || {}).then(() => sendResponse({ ok: true }));
    return true;
  }

  return false;
});
