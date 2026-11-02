import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LOGIN_ROLES, canAccessHrPath, canApproveNightRewards, canEditPersonnel } from './hrPermissions.js';

test('HR staff can open the isolated attendance sandbox but cannot enter personnel write routes', () => {
  for (const path of ['/manager/hr', '/manager/hr/employees', '/manager/hr/employees/e1',
    '/manager/hr/attendance', '/manager/hr/attendance/production', '/manager/hr/attendance/night-rewards',
    '/manager/hr/attendance/sandbox']) {
    assert.equal(canAccessHrPath('HR_STAFF', path), true, path);
  }
  for (const path of ['/manager/hr/employees/new', '/manager/hr/employees/new/',
    '/manager/hr/employees/e1/edit', '/manager/hr/payroll', '/manager/hr/salary-raises',
    '/manager/hr/general-labor', '/manager/hr/probation', '/manager/hr/catalogs',
    '/manager/hr/word-editor/s1', '/manager/hr/rosters', '/manager/hr/imports']) {
    assert.equal(canAccessHrPath('HR_STAFF', path), false, path);
  }
  assert.equal(canEditPersonnel('HR_STAFF'), false);
  assert.equal(canApproveNightRewards('HR_STAFF'), true);
  assert.equal(LOGIN_ROLES.has('HR_STAFF'), true);
  assert.equal(LOGIN_ROLES.has('EMPLOYEE'), false);
  assert.equal(canAccessHrPath('EMPLOYEE', '/manager/hr'), false);
  assert.equal(canAccessHrPath(null, '/manager/hr'), false);
});

test('existing admin and manager permissions stay intact', () => {
  for (const role of ['ADMIN', 'MANAGER']) {
    assert.equal(canEditPersonnel(role), true);
    assert.equal(canAccessHrPath(role, '/manager/hr/employees/e1/edit'), true);
    assert.equal(canAccessHrPath(role, '/manager/hr/payroll'), true);
  }
  assert.equal(canApproveNightRewards('ADMIN'), true);
  assert.equal(canApproveNightRewards('MANAGER'), false);
});
