const STORAGE_KEY = 'aegis.session';
const LEGACY_TOKEN_KEY = 'token';

function safeJsonParse(value) {
  if (!value) return null;
  try {
    return JSON.parse(value);
  } catch {
    return null;
  }
}

function normalizeMode(mode) {
  return mode === 'mock' ? 'mock' : 'real';
}

function decodeJwtPayload(token) {
  if (!token || token.split('.').length < 2) return null;
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const normalized = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    return JSON.parse(window.atob(normalized));
  } catch {
    return null;
  }
}

function deriveExpiresAt(token, mode) {
  if (!token) return null;
  if (mode === 'mock') return Date.now() + 8 * 60 * 60 * 1000;
  const payload = decodeJwtPayload(token);
  return payload?.exp ? payload.exp * 1000 : null;
}

function persistSession(session) {
  if (!session?.token) {
    localStorage.removeItem(STORAGE_KEY);
    localStorage.removeItem(LEGACY_TOKEN_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  localStorage.setItem(LEGACY_TOKEN_KEY, session.token);
}

function migrateLegacyToken() {
  const token = localStorage.getItem(LEGACY_TOKEN_KEY);
  if (!token) return null;
  const mode = token.startsWith('mock-jwt-token-') ? 'mock' : 'real';
  const session = {
    token,
    user: null,
    mode,
    createdAt: Date.now(),
    expiresAt: deriveExpiresAt(token, mode),
  };
  persistSession(session);
  return session;
}

export function getSession() {
  const stored = safeJsonParse(localStorage.getItem(STORAGE_KEY));
  return stored?.token ? stored : migrateLegacyToken();
}

export function setSession({ token, user = null, mode = 'real' }) {
  const normalizedMode = normalizeMode(mode);
  const session = {
    token,
    user,
    mode: normalizedMode,
    createdAt: Date.now(),
    expiresAt: deriveExpiresAt(token, normalizedMode),
  };
  persistSession(session);
  return session;
}

export function clearSession(reason = 'manual') {
  persistSession(null);
  sessionStorage.setItem('aegis.session.reason', reason);
}

export function isSessionExpired(session = getSession()) {
  if (!session?.expiresAt) return false;
  return Date.now() >= session.expiresAt - 30_000;
}

export function hasActiveSession() {
  const session = getSession();
  if (!session?.token) return false;
  if (isSessionExpired(session)) {
    clearSession('expired');
    return false;
  }
  return true;
}

export function isMockSession() {
  return getSession()?.mode === 'mock';
}

export function getAuthHeaderToken() {
  const session = getSession();
  if (!session?.token || session.mode === 'mock' || isSessionExpired(session)) {
    return null;
  }
  return session.token;
}
