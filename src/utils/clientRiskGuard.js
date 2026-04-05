const JAILBREAK_PATTERNS = [
  /ignore\s+(all|previous|prior)\s+instructions?/i,
  /system\s+prompt/i,
  /developer\s+mode/i,
  /do\s+not\s+follow\s+safety/i,
  /reveal\s+(hidden|internal)\s+rules/i,
  /bypass\s+(policy|guard|filter)/i,
];

const EXFIL_PATTERNS = [
  /api[_-]?key/i,
  /secret[_-]?key/i,
  /access[_-]?token/i,
  /private[_-]?key/i,
  /authorization:\s*bearer/i,
  /BEGIN\s+(RSA|EC|OPENSSH)\s+PRIVATE\s+KEY/i,
  /password\s*[:=]/i,
];

const ENCODED_PAYLOAD_PATTERNS = [
  /[A-Za-z0-9+/=]{240,}/,
  /\\x[0-9a-fA-F]{2}/,
  /%[0-9a-fA-F]{2}/,
  /(?:[A-Za-z0-9_-]{40,}\.){2}[A-Za-z0-9_-]{20,}/,
];

const SOCIAL_ENGINEERING_PATTERNS = [
  /urgent/i,
  /wire\s+transfer/i,
  /change\s+beneficiary/i,
  /verify\s+invoice/i,
  /executive\s+request/i,
  /confidential\s+payment/i,
];

const HIGH_RISK_ENDPOINT_PATTERNS = [
  /\/governance-change\//i,
  /\/role/i,
  /\/permission/i,
  /\/approval/i,
  /\/audit/i,
  /\/security\//i,
];

const ENDPOINT_POLICY = [
  { category: 'governance', pattern: /\/governance-change\//i, mediumAction: 'challenge', highAction: 'block' },
  { category: 'role_permission', pattern: /\/(roles?|role|permissions?|permission)/i, mediumAction: 'challenge', highAction: 'block' },
  { category: 'approval_audit', pattern: /\/(approval|audit)/i, mediumAction: 'challenge', highAction: 'block' },
  { category: 'security_ops', pattern: /\/security\//i, mediumAction: 'warn', highAction: 'block' },
  { category: 'default', pattern: /.*/i, mediumAction: 'warn', highAction: 'block' },
];

function safeStringify(value) {
  if (value == null) return '';
  if (typeof value === 'string') return value;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function createTraceId() {
  const now = Date.now().toString(36);
  const rand = Math.random().toString(36).slice(2, 10);
  return `cg-${now}-${rand}`;
}

function collectHits(text, patterns, label, scorePerHit, acc) {
  for (const pattern of patterns) {
    if (pattern.test(text)) {
      acc.reasons.push(label);
      acc.score += scorePerHit;
      return;
    }
  }
}

function classifyLevel(score, hasHighRiskEndpoint) {
  if (score >= 8 || (score >= 6 && hasHighRiskEndpoint)) return 'high';
  if (score >= 4) return 'medium';
  if (score > 0) return 'low';
  return 'none';
}

function matchEndpointPolicy(endpoint) {
  const target = String(endpoint || '');
  return ENDPOINT_POLICY.find((item) => item.pattern.test(target)) || ENDPOINT_POLICY[ENDPOINT_POLICY.length - 1];
}

export function assessOutboundRisk({ url, method, data, params }) {
  const joined = [safeStringify(data), safeStringify(params)].filter(Boolean).join('\n');
  const normalizedText = joined.slice(0, 20000);
  const endpoint = String(url || '');
  const upperMethod = String(method || 'GET').toUpperCase();

  const result = {
    score: 0,
    level: 'none',
    reasons: [],
    traceId: createTraceId(),
  };

  const isWriteMethod = upperMethod === 'POST' || upperMethod === 'PUT' || upperMethod === 'PATCH' || upperMethod === 'DELETE';
  const hasHighRiskEndpoint = HIGH_RISK_ENDPOINT_PATTERNS.some((pattern) => pattern.test(endpoint));
  const endpointPolicy = matchEndpointPolicy(endpoint);

  if (isWriteMethod && hasHighRiskEndpoint) {
    result.score += 2;
    result.reasons.push('sensitive_operation');
  }

  collectHits(normalizedText, JAILBREAK_PATTERNS, 'prompt_injection_signal', 4, result);
  collectHits(normalizedText, EXFIL_PATTERNS, 'secret_exfil_signal', 4, result);
  collectHits(normalizedText, ENCODED_PAYLOAD_PATTERNS, 'encoded_payload_signal', 2, result);
  collectHits(normalizedText, SOCIAL_ENGINEERING_PATTERNS, 'social_engineering_signal', 2, result);

  const longBody = normalizedText.length > 8000;
  if (longBody) {
    result.score += 1;
    result.reasons.push('oversized_payload');
  }

  result.level = classifyLevel(result.score, hasHighRiskEndpoint);
  if (result.level === 'high') {
    result.action = endpointPolicy.highAction;
  } else if (result.level === 'medium') {
    result.action = endpointPolicy.mediumAction;
  } else if (result.level === 'low') {
    result.action = 'warn';
  } else {
    result.action = 'allow';
  }
  result.category = endpointPolicy.category;
  result.reasons = Array.from(new Set(result.reasons));
  return result;
}
