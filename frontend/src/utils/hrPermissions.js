export const LOGIN_ROLES = new Set(['ADMIN', 'MANAGER', 'HR_STAFF']);

export function canEditPersonnel(role) {
  return role === 'ADMIN' || role === 'MANAGER';
}

export function canApproveNightRewards(role) {
  return role === 'ADMIN' || role === 'HR_STAFF';
}

export function canAccessHrPath(role, pathname) {
  if (canEditPersonnel(role)) return true;
  if (role !== 'HR_STAFF') return false;
  return /^\/manager\/hr\/?$/.test(pathname)
    || /^\/manager\/hr\/employees(?:\/(?!new(?:\/|$))[^/]+)?\/?$/.test(pathname)
    || /^\/manager\/hr\/attendance(?:\/(?:production|night-rewards|sandbox))?\/?$/.test(pathname);
}
