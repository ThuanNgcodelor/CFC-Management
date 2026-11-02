import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseFactoryNightReward, summarizeSandbox } from './attendanceSandbox.js';

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

test('preview summary follows the production overview columns and review rule', () => {
  const days = [
    { employeeCode: 'C743', employeeName: 'Phùng Ngọc Ẩn', workValue: 1, status: 'CONFIRMED', nightAllowanceAmount: 10000 },
    { employeeCode: 'B124', employeeName: 'Đỗ Đình Cường', workValue: 1, status: 'NEEDS_REVIEW', nightAllowanceAmount: 10000 },
    { employeeCode: 'A001', employeeName: 'Nguyễn Trung Thuận', workValue: 1, status: 'CONFIRMED', nightAllowanceAmount: 10000 },
  ];
  const summaries = summarizeSandbox(days);
  assert.equal(summaries.find((row) => row.employeeCode === 'B124').nightShifts, 1);
  assert.equal(summaries.find((row) => row.employeeCode === 'B124').reviewShifts, 1);
  assert.equal(summaries.find((row) => row.employeeCode === 'A001').totalDays, 1);
  assert.equal(summaries.find((row) => row.employeeCode === 'A001').nightAndOvertimeShifts, 1);
});

test('unknown A001 with one no-punch day has 29 days needing review', () => {
  const days = Array.from({ length: 30 }, (_, index) => ({
    employeeCode: 'A001', employeeName: 'Nguyễn Trung Thuận',
    status: index === 29 ? 'NO_PUNCH' : 'NEEDS_REVIEW',
    workValue: index === 29 ? 0 : 1, nightAllowanceAmount: index < 6 ? 10000 : 0,
  }));
  const [summary] = summarizeSandbox(days);
  assert.equal(summary.totalDays, 30);
  assert.equal(summary.reviewShifts, 29);
  assert.equal(summary.noPunchRows, 1);
  assert.equal(summary.nightShifts, 6);
});
