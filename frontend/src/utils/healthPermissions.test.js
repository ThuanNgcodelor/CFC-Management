import test from 'node:test';
import assert from 'node:assert/strict';
import { canAccessHrPath } from './hrPermissions.js';
import { healthFeatureEnabled, resolveHealthFeatureEnabled } from './healthFeature.js';

test('health routes belong only to ADMIN and MANAGER, including deep links', () => {
  for (const path of ['/manager/hr/health', '/manager/hr/health/campaigns/example']) {
    for (const role of ['ADMIN', 'MANAGER']) assert.equal(canAccessHrPath(role, path), true);
    for (const role of ['HR_STAFF', 'EMPLOYEE', null]) assert.equal(canAccessHrPath(role, path), false);
  }
});
test('internal health is enabled without env; existing HR staff access is unchanged', () => {
  assert.equal(healthFeatureEnabled, true);
  assert.equal(canAccessHrPath('HR_STAFF', '/manager/hr/employees/example'), true);
  assert.equal(canAccessHrPath('HR_STAFF', '/manager/hr/attendance/production'), true);
});

test('an explicit frontend feature flag can still disable internal health', () => {
  assert.equal(resolveHealthFeatureEnabled(undefined), true);
  assert.equal(resolveHealthFeatureEnabled('true'), true);
  for (const value of ['false', '', 'invalid']) assert.equal(resolveHealthFeatureEnabled(value), false);
});
