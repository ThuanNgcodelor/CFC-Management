import { test } from 'node:test';
import assert from 'node:assert/strict';
import { buildHrListParams, employeeAfterSavePath, parseHrListState, safeHrPath, withHrReturnTo } from './hrListState.js';

const defaults = { keyword: '', status: '', page: 0, size: 20, sort: 'employeeCode,asc' };
const allowed = { status: ['', 'DRAFT', 'ACTIVE', 'INACTIVE'], size: [20, 50], sort: ['employeeCode,asc', 'fullName,desc'] };

test('list URLs restore applied filters and reject invalid paging and sort', () => {
  assert.deepEqual(parseHrListState('keyword=NV&status=DRAFT&page=3&size=50&sort=fullName,desc', defaults, allowed), {
    keyword: 'NV', status: 'DRAFT', page: 3, size: 50, sort: 'fullName,desc',
  });
  assert.deepEqual(parseHrListState('page=-1&size=9999&status=OTHER&sort=unexpected', defaults, allowed), defaults);
  assert.equal(parseHrListState('page=Infinity', defaults).page, 0);
});

test('changing filters resets page while paging preserves filters, tabs and origin', () => {
  const start = 'keyword=NV&status=DRAFT&page=3&tab=history&returnTo=%2Fmanager%2Fhr';
  const filtered = buildHrListParams(start, defaults, { keyword: ' AN ', status: 'ACTIVE' });
  assert.equal(filtered.get('keyword'), 'AN');
  assert.equal(filtered.get('page'), null);
  assert.equal(filtered.get('tab'), 'history');
  assert.equal(filtered.get('returnTo'), '/manager/hr');
  const paged = buildHrListParams(filtered, defaults, { page: 2 }, false);
  assert.equal(paged.get('status'), 'ACTIVE');
  assert.equal(paged.get('page'), '2');
});

test('detail and edit return to the original list including paging and filter', () => {
  const list = '/manager/hr/general-labor?keyword=AN&page=2&status=DRAFT';
  const detail = withHrReturnTo('/manager/hr/employees/id?tab=history', list);
  const edit = withHrReturnTo('/manager/hr/employees/id/edit', detail);
  const editSource = new URL(edit, 'https://hr.invalid').searchParams.get('returnTo');
  assert.equal(employeeAfterSavePath('id', editSource), detail);
  assert.equal(new URL(employeeAfterSavePath('id', list), 'https://hr.invalid').searchParams.get('returnTo'), list);
});

test('return paths cannot redirect outside HR or contain controls and backslashes', () => {
  for (const value of ['https://evil.test', '//evil.test', '/manager/hr/../../admin', '/manager/hr\\evil', '/manager/hr\n', '/manager/hr-evil']) {
    assert.equal(safeHrPath(value), '/manager/hr/employees', value);
  }
  assert.equal(safeHrPath('/manager/hr/probation?status=IN_PROBATION'), '/manager/hr/probation?status=IN_PROBATION');
});
