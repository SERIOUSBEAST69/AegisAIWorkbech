'use strict';

const ID_CARD = /[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/g;
const PHONE = /(?<!\d)1[3-9]\d{9}(?!\d)/g;
const BANK_CARD = /(?<!\d)\d{16,19}(?!\d)/g;
const COMPANY_CODE = /(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/g;

const PROMPT_INJECTION_PATTERNS = [
  /ignore\s+(all|previous|prior)\s+instructions?/i,
  /system\s+prompt/i,
  /developer\s+mode/i,
  /bypass\s+(policy|guard|safety|filter)/i,
  /do\s+not\s+follow\s+safety/i,
  /reveal\s+(hidden|internal)\s+rules/i,
];

const EXFIL_SIGNAL_PATTERNS = [
  /api[_-]?key/i,
  /access[_-]?token/i,
  /private[_-]?key/i,
  /authorization:\s*bearer/i,
  /BEGIN\s+(RSA|EC|OPENSSH)\s+PRIVATE\s+KEY/i,
  /password\s*[:=]/i,
];

const ENCODED_SIGNAL_PATTERNS = [
  /[A-Za-z0-9+/=]{220,}/,
  /\\x[0-9a-fA-F]{2}/,
  /%[0-9a-fA-F]{2}/,
];

function estimateEntropy(text) {
  const value = String(text || '');
  if (!value) return 0;
  const freq = new Map();
  for (const ch of value) {
    freq.set(ch, (freq.get(ch) || 0) + 1);
  }
  let entropy = 0;
  for (const [, count] of freq.entries()) {
    const p = count / value.length;
    entropy -= p * Math.log2(p);
  }
  return Number(entropy.toFixed(3));
}

function detectTypes(text) {
  const value = String(text || '');
  const types = [];
  if (/[1-9]\d{5}(19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[0-9Xx]/.test(value)) {
    types.push('id_card');
  }
  if (/(?<!\d)1[3-9]\d{9}(?!\d)/.test(value)) {
    types.push('phone');
  }
  if (/(?<!\d)\d{16,19}(?!\d)/.test(value)) {
    types.push('bank_card');
  }
  if (/(?<![A-Z0-9])[0-9A-Z]{18}(?![A-Z0-9])/.test(value)) {
    types.push('company_code');
  }
  return types;
}

function assessTextRisk(text) {
  const value = String(text || '');
  const reasons = [];
  let score = 0;

  const piiTypes = detectTypes(value);
  if (piiTypes.length > 0) {
    score += 4;
    reasons.push('sensitive_data_signal');
  }
  if (PROMPT_INJECTION_PATTERNS.some(pattern => pattern.test(value))) {
    score += 4;
    reasons.push('prompt_injection_signal');
  }
  if (EXFIL_SIGNAL_PATTERNS.some(pattern => pattern.test(value))) {
    score += 4;
    reasons.push('secret_exfil_signal');
  }
  if (ENCODED_SIGNAL_PATTERNS.some(pattern => pattern.test(value))) {
    score += 2;
    reasons.push('encoded_payload_signal');
  }

  const entropy = estimateEntropy(value);
  if (value.length > 180 && entropy > 4.2) {
    score += 2;
    reasons.push('high_entropy_signal');
  }

  const level = score >= 8 ? 'high' : (score >= 4 ? 'medium' : (score > 0 ? 'low' : 'none'));
  return {
    level,
    score,
    reasons: Array.from(new Set(reasons)),
    entropy,
    piiTypes,
  };
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

function maskContent(text) {
  const value = String(text || '');
  return value
    .replace(ID_CARD, (m) => maskIdCard(m))
    .replace(PHONE, (m) => maskPhone(m))
    .replace(BANK_CARD, (m) => maskBankCard(m))
    .replace(COMPANY_CODE, (m) => maskCompanyCode(m));
}

module.exports = {
  detectTypes,
  maskContent,
  assessTextRisk,
};
