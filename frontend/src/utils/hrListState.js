export function parseHrListState(search, defaults, allowed = {}) {
  const params = new URLSearchParams(search);
  return Object.fromEntries(Object.entries(defaults).map(([key, fallback]) => {
    const raw = params.get(key)?.trim();
    if (!raw) return [key, fallback];
    const value = typeof fallback === 'number' ? Number(raw) : raw;
    if (typeof fallback === 'number' && (!Number.isSafeInteger(value) || value < 0 || (key === 'size' && value === 0))) return [key, fallback];
    if (allowed[key] && !allowed[key].includes(value)) return [key, fallback];
    return [key, value];
  }));
}

export function buildHrListParams(search, defaults, updates, resetPage = true) {
  const params = new URLSearchParams(search);
  if (resetPage && !Object.hasOwn(updates, 'page')) params.delete('page');
  for (const [key, value] of Object.entries(updates)) {
    const normalized = typeof value === 'string' ? value.trim() : value;
    if (normalized === '' || normalized == null || normalized === defaults[key]) params.delete(key);
    else params.set(key, String(normalized));
  }
  return params;
}

export function safeHrPath(value, fallback = '/manager/hr/employees') {
  if (typeof value !== 'string' || value.length > 8192 || value.includes('\\') || [...value].some((char) => char.charCodeAt(0) < 32)) return fallback;
  if (!/^\/manager\/hr(?:\/|\?|$)/.test(value)) return fallback;
  const url = new URL(value, 'https://hr.invalid');
  return url.origin === 'https://hr.invalid' && /^\/manager\/hr(?:\/|$)/.test(url.pathname)
    ? `${url.pathname}${url.search}${url.hash}` : fallback;
}

export function withHrReturnTo(destination, source) {
  const url = new URL(safeHrPath(destination), 'https://hr.invalid');
  url.searchParams.set('returnTo', safeHrPath(source));
  return `${url.pathname}${url.search}${url.hash}`;
}

export function employeeAfterSavePath(id, source) {
  const path = `/manager/hr/employees/${encodeURIComponent(id)}`;
  const safeSource = safeHrPath(source);
  const previous = new URL(safeSource, 'https://hr.invalid');
  return previous.pathname === path ? safeSource : withHrReturnTo(path, safeSource);
}
