'use strict';

const DEFAULT_CONFIG = {
  monitorEnabled: true,
  predictEnabled: true,
  predictEndpoint: 'http://localhost:5000/predict',
  backendBaseUrl: 'http://localhost:8080',
  clientIngressToken: '',
  companyId: 1,
  configVersion: 1,
  configChecksum: '',
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

const JAILBREAK_PATTERNS = [
  /ignore\s+(all|previous|prior)\s+instructions?/i,
  /system\s+prompt/i,
  /developer\s+mode/i,
  /bypass\s+(policy|guard|safety|filter)/i,
  /do\s+not\s+follow\s+safety/i,
  /reveal\s+(hidden|internal)\s+rules/i,
];

const SOCIAL_ENGINEERING_PATTERNS = [
  /urgent/i,
  /wire\s+transfer/i,
  /change\s+beneficiary/i,
  /verify\s+invoice/i,
  /executive\s+request/i,
];

const ENCODED_PATTERNS = [
  /[A-Za-z0-9+/=]{220,}/,
  /(?:[A-Za-z0-9_-]{40,}\.){2}[A-Za-z0-9_-]{20,}/,
  /\\x[0-9a-fA-F]{2}/,
];

const TAB_RISK_STATE = new Map();

function getSenderKey(sender) {
  const tabId = sender?.tab?.id;
  if (Number.isFinite(tabId)) {
    return `tab:${tabId}`;
  }
  return `ctx:${sender?.id || 'unknown'}`;
}

function updateRiskChain(senderKey, riskCategory, reasons) {
  const now = Date.now();
  const prev = TAB_RISK_STATE.get(senderKey) || {
    chainId: `ext-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    score: 0,
    lastAt: now,
    reasons: [],
  };
  const elapsed = now - prev.lastAt;
  const decay = elapsed > 45000 ? 0.4 : (elapsed > 25000 ? 0.7 : 1);
  let score = Math.max(0, prev.score * decay);
  if (riskCategory === 'high') score += 4;
  if (riskCategory === 'medium') score += 2;
  if (riskCategory === 'low') score += 1;
  const mergedReasons = Array.from(new Set([...(prev.reasons || []), ...(reasons || [])])).slice(-8);
  const state = {
    chainId: prev.chainId,
    score,
    lastAt: now,
    reasons: mergedReasons,
    level: score >= 8 ? 'high' : (score >= 4 ? 'medium' : (score > 0 ? 'low' : 'none')),
  };
  if (elapsed > 90000) {
    state.chainId = `ext-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    state.score = riskCategory === 'high' ? 4 : (riskCategory === 'medium' ? 2 : 1);
    state.reasons = Array.from(new Set(reasons || [])).slice(-8);
    state.level = state.score >= 8 ? 'high' : (state.score >= 4 ? 'medium' : (state.score > 0 ? 'low' : 'none'));
  }
  TAB_RISK_STATE.set(senderKey, state);
  return state;
}

async function getLocalConfig() {
  const data = await chrome.storage.local.get(['privacyConfig']);
  return { ...DEFAULT_CONFIG, ...(data.privacyConfig || {}) };
}

async function saveLocalConfig(config) {
  await chrome.storage.local.set({ privacyConfig: config });
}

async function fetchRemoteConfig(baseUrl, sinceVersion, sinceChecksum) {
  try {
    const params = [];
    if (Number.isFinite(Number(sinceVersion))) {
      params.push(`sinceVersion=${encodeURIComponent(String(sinceVersion))}`);
    }
    if (sinceChecksum) {
      params.push(`sinceChecksum=${encodeURIComponent(String(sinceChecksum))}`);
    }
    const query = params.length > 0 ? `?${params.join('&')}` : '';
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
  const localChecksum = String(local.configChecksum || '');
  const remote = await fetchRemoteConfig(local.backendBaseUrl, localVersion, localChecksum);
  let merged = local;
  if (remote && remote.changed === false) {
    merged = {
      ...local,
      configVersion: Number(remote.configVersion || localVersion || 1),
      configChecksum: String(remote.configChecksum || localChecksum || ''),
      syncIntervalSec: Number(remote.syncIntervalSec || local.syncIntervalSec || 60),
    };
  } else if (remote) {
    merged = { ...local, ...remote };
  }
  if (!merged.configVersion) {
    merged.configVersion = localVersion || 1;
  }
  if (!merged.configChecksum) {
    merged.configChecksum = localChecksum || '';
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

async function detectSensitive(text, context = {}) {
  const config = await ensureConfig();
  const safeText = String(text || '').trim();
  if (!safeText || !config.monitorEnabled) {
    return { blocked: false, detected: [], maskedText: safeText, riskCategory: 'none', confidence: 0, reasons: [], config };
  }

  const regexDetected = detectByRegex(safeText);
  const predictDetected = config.predictEnabled
    ? await detectByPredict(config.predictEndpoint, safeText)
    : [];
  const detected = Array.from(new Set([...regexDetected, ...predictDetected]));

  const reasons = [];
  let score = 0;
  if (regexDetected.length > 0 || predictDetected.length > 0) {
    score += 4;
    reasons.push('sensitive_data_signal');
  }
  if (JAILBREAK_PATTERNS.some((pattern) => pattern.test(safeText))) {
    score += 4;
    reasons.push('prompt_injection_signal');
  }
  if (SOCIAL_ENGINEERING_PATTERNS.some((pattern) => pattern.test(safeText))) {
    score += 2;
    reasons.push('social_engineering_signal');
  }
  if (ENCODED_PATTERNS.some((pattern) => pattern.test(safeText))) {
    score += 2;
    reasons.push('encoded_payload_signal');
  }

  const fragmentSignals = Array.isArray(context.fragmentSignals) ? context.fragmentSignals : [];
  if (fragmentSignals.length > 0) {
    score += 2;
    reasons.push('fragmented_composition_signal');
  }
  const mutationSignals = Array.isArray(context.mutationSignals) ? context.mutationSignals : [];
  if (mutationSignals.length > 0) {
    score += 2;
    reasons.push('dom_injection_signal');
  }

  const riskCategory = score >= 8 ? 'high' : (score >= 4 ? 'medium' : (score > 0 ? 'low' : 'none'));
  const confidence = Math.min(1, score / 10);

  return {
    blocked: detected.length > 0 || score >= 4,
    detected,
    maskedText: desensitizeText(safeText),
    riskCategory,
    confidence,
    reasons: Array.from(new Set(reasons)),
    config,
  };
}

async function reportEvent(payload) {
  const config = await ensureConfig();
  const reasonList = Array.isArray(payload.reasons) ? payload.reasons : [];
  const typeList = Array.isArray(payload.matchedTypes) ? payload.matchedTypes : [];
  const mergedMatched = Array.from(new Set([
    ...typeList,
    ...reasonList,
    payload.riskCategory ? `risk:${payload.riskCategory}` : '',
    payload.chainId ? `chain:${payload.chainId}` : '',
    payload.chainLevel ? `chain_level:${payload.chainLevel}` : '',
  ].filter(Boolean)));
  const event = {
    userId: payload.userId || 'unknown',
    eventType: payload.eventType || 'EXTENSION_SENSITIVE',
    content: desensitizeText(payload.content || ''),
    source: 'extension',
    timestamp: new Date().toISOString(),
    action: payload.action || (payload.riskCategory === 'high' ? 'block' : 'detect'),
    matchedTypes: mergedMatched.join(','),
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
    detectSensitive(message.text || '', message.context || {}).then((result) => {
      const senderKey = getSenderKey(sender);
      const chain = updateRiskChain(senderKey, result.riskCategory, result.reasons);
      sendResponse({ ok: true, ...result, chain });
    });
    return true;
  }

  if (message?.type === 'report-event') {
    reportEvent(message.payload || {}).then(() => sendResponse({ ok: true }));
    return true;
  }

  return false;
});
