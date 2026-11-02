import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseFactoryNightReward, reconcileSandbox, summarizeSandbox, validWorkValue } from './attendanceSandbox.js';

test('reads the factory MS table instead of the empty MV comparison columns', async () => {
  const XLSX = await import('xlsx');
  const book = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(book, XLSX.utils.aoa_to_sheet([
    ['ĐỦ ĐIỀU KIỆN tháng 9-2026'],
    ['STT', 'MS', 'Họ và tên', 'Công ca đêm', null, null, null, 'MV'],
    [1, 'C743', 'Phùng Ngọc Ẩn', 11],
    [2, 'B124', 'Đỗ Đình Cường', 2],
  ]), 'Sheet1');
  const bytes = XLSX.write(book, { type: 'buffer', bookType: 'xlsx' });
  const result = await parseFactoryNightReward({ name: 'Gửi thuận.xlsx', arrayBuffer: async () => bytes });
  assert.deepEqual(result.map((row) => [row.employeeCode, row.nightShifts]), [['C743', 11], ['B124', 2]]);
});

test('sandbox reconciliation retains attendance-only employees and counts confirmed night shifts', () => {
  const days = [
    { employeeCode: 'C743', employeeName: 'Phùng Ngọc Ẩn', workValue: 1, status: 'CONFIRMED', countsTowardNightReward: true },
    { employeeCode: 'B124', employeeName: 'Đỗ Đình Cường', workValue: 1, status: 'NEEDS_REVIEW', countsTowardNightReward: true },
    { employeeCode: 'A001', employeeName: 'Nguyễn Trung Thuận', workValue: 1, status: 'CONFIRMED', countsTowardNightReward: true },
  ];
  const summaries = summarizeSandbox(days);
  const result = reconcileSandbox([
    { employeeCode: 'C743', employeeName: 'Phùng Ngọc Ẩn', nightShifts: 1 },
    { employeeCode: 'B124', employeeName: 'Đỗ Đình Cường', nightShifts: 2 },
  ], summaries);
  assert.equal(result.matched[0].difference, 0);
  assert.equal(result.matched[1].difference, 1);
  assert.equal(result.matched[1].confirmedNightShifts, 0);
  assert.equal(result.matched[1].unresolvedDays, 1);
  assert.deepEqual(result.onlyInAttendance.map((row) => row.employeeCode), ['A001']);
  assert.equal(result.onlyInFactory.length, 0);
  assert.equal(validWorkValue(1.5), true);
  assert.equal(validWorkValue(1.25), false);
});
