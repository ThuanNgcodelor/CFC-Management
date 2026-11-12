import test from 'node:test';
import assert from 'node:assert/strict';
import { canAccessHrPath } from './hrPermissions.js';
import { healthFeatureEnabled } from './healthFeature.js';

test('health routes belong only to ADMIN and MANAGER, including deep links', () => {
  for (const path of ['/manager/hr/health', '/manager/hr/health/campaigns/example']) {
    for (const role of ['ADMIN', 'MANAGER']) assert.equal(canAccessHrPath(role, path), true);
    for (const role of ['HR_STAFF', 'EMPLOYEE', null]) assert.equal(canAccessHrPath(role, path), false);
  }
});
test('health is opt-in; existing HR staff personnel and attendance access remains available', () => {
  assert.equal(healthFeatureEnabled, false);
  assert.equal(canAccessHrPath('HR_STAFF', '/manager/hr/employees/example'), true);
  assert.equal(canAccessHrPath('HR_STAFF', '/manager/hr/attendance/production'), true);
});
