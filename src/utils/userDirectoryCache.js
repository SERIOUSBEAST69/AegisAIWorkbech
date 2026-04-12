import request from '../api/request';

const STORAGE_KEY = 'aegis.user-directory.cache.v1';
const CACHE_TTL_MS = 5 * 60 * 1000;

let memoryMap = null;
let memoryAt = 0;

function normalizeUsers(users) {
  const map = new Map();
  (Array.isArray(users) ? users : []).forEach((item) => {
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

function tryLoadStorage() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
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
  if (!force && memoryMap && Date.now() - memoryAt <= CACHE_TTL_MS) {
    return memoryMap;
  }

  if (!force) {
    const cached = tryLoadStorage();
    if (cached && cached.size > 0) {
      memoryMap = cached;
      memoryAt = Date.now();
      return cached;
    }
  }

  const users = await request.get('/user/directory');
  const map = normalizeUsers(users);
  memoryMap = map;
  memoryAt = Date.now();
  try {
    sessionStorage.setItem(STORAGE_KEY, serializeForStorage(Array.isArray(users) ? users : []));
  } catch {
    // ignore session storage failures
  }
  return map;
}
