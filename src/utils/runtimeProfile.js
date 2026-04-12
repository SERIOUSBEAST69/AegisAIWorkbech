function hasWindow() {
  return typeof window !== 'undefined';
}

export function isElectronClient() {
  return hasWindow() && Boolean(window.aegisClient);
}

export function isClientLiteMode() {
  if (!hasWindow()) return false;
  const params = new URLSearchParams(window.location.search || '');
  if (params.get('clientLite') === '1') return true;
  return Boolean(window.__AEGIS_CLIENT_LITE__);
}

export function getRuntimeProfile() {
  return {
    electronClient: isElectronClient(),
    clientLite: isClientLiteMode(),
  };
}
