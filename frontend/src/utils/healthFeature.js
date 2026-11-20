export function resolveHealthFeatureEnabled(value = 'true') {
  return value === 'true';
}

export const healthFeatureEnabled = resolveHealthFeatureEnabled(import.meta.env?.VITE_HEALTH_ENABLED);
