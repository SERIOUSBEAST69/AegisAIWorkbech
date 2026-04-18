import request from '../api/request';
import { getSession } from './auth';

const STORAGE_KEY = 'aegis.user-directory.cache.v1';
const CACHE_TTL_MS = 5 * 60 * 1000;

let memoryMap = null;
let memoryAt = 0;
let memoryScope = '';

function normalizeScope() {
  const session = getSession() || {};
  const user = session.user || {};
  const userId = user.id == null ? '-' : String(user.id);
  const companyId = user.companyId == null ? '-' : String(user.companyId);
  const roleCode = String(user.roleCode || '').trim().toUpperCase() || '-';
  return `${userId}:${companyId}:${roleCode}`;
}

function scopedStorageKey(scope) {
  return `${STORAGE_KEY}:${scope}`;
}

function normalizeUsers(users) {
  const map = new Map();
  (Array.isArray(users) ? users : []).forEach((item) => {
    const idText = String(item?.idStr || (item?.id == null ? '' : item.id)).trim();
    if (idText) {
      map.set(idText, item);
    }
    if (item?.id != null) {
      map.set(String(item.id), item);
    }
    if (item?.username) {
      map.set(`username:${String(item.username).toLowerCase()}`, item);
    }
  });
  return map;
}

function serializeForStorage(users) {
  return JSON.stringify({
    at: Date.now(),
    users: Array.isArray(users) ? users : [],
  });
}

function tryLoadStorage(scope) {
  try {
    const raw = sessionStorage.getItem(scopedStorageKey(scope));
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed?.at || !Array.isArray(parsed?.users)) return null;
    if (Date.now() - Number(parsed.at) > CACHE_TTL_MS) return null;
    return normalizeUsers(parsed.users);
  } catch {
    return null;
  }
}

export async function getUserDirectory({ force = false } = {}) {
  const scope = normalizeScope();
  if (!force && memoryScope === scope && memoryMap && Date.now() - memoryAt <= CACHE_TTL_MS) {
    return memoryMap;
  }

  if (!force) {
    const cached = tryLoadStorage(scope);
    if (cached && cached.size > 0) {
      memoryMap = cached;
      memoryAt = Date.now();
      memoryScope = scope;
      return cached;
    }
  }

  const users = await request.get('/user/directory');
  const map = normalizeUsers(users);
  memoryMap = map;
  memoryAt = Date.now();
  memoryScope = scope;
  try {
    sessionStorage.setItem(scopedStorageKey(scope), serializeForStorage(Array.isArray(users) ? users : []));
  } catch {
    // ignore session storage failures
  }
  return map;
}
