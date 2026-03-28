import { getSession } from '../utils/auth';

export function shouldUseApiFallback(error) {
  const message = String(error?.message || '').toLowerCase();
  return Boolean(
    error?.status === 404
      || error?.network
      || message.includes('no static resource')
      || message.includes('network connection failed')
      || message.includes('failed to fetch')
  );
}

export function getSessionUserFallback(patch = null) {
  const user = getSession()?.user || null;
  if (!user) {
    return patch || null;
  }
  return patch ? { ...user, ...patch } : user;
}