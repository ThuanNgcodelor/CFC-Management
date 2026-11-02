import { test } from 'node:test';
import assert from 'node:assert/strict';
import { parseNightRewardRows, parseNightRewardXlsx } from './nightRewardReconcile.js';

const title = [
  ['DANH SÁCH CNXNPBHC'],
  ['ĐỦ ĐIỀU KIỆN tháng 9-2026'],
  ['Chương trình thưởng gắn bó ca đêm'],
];

test('reads the MS table when no MV column exists', () => {
  const rows = [...title,
    ['STT', 'MS', 'Họ và tên ', 'Công ca đêm ', 'Tích 12 ', 'Tích 24'],
    [1, 'B109', 'Lê Thị Thu Hoài', 17, 500000, 1000000],
    [2, 'B126', 'Trần Thanh Nhàn', 16, 500000, 1000000],
  ];
  const parsed = parseNightRewardRows(rows);
  assert.deepEqual(parsed.map((row) => [row.maNV, row.caDem]), [['B109', 17], ['B126', 16]]);
});

test('prefers MS over an empty MV comparison table in the same header row', () => {
  const rows = [...title, [],
    ['STT', 'MS', 'Họ và tên ', 'Công ca đêm ', 'Tích 12 ', 'Tích 24', null, 'MV', 'Họ và tên '],
    [1, 'C743', 'Phùng Ngọc Ẩn', 11, 500000, 1000000],
    [2, 'B124', 'Đỗ Đình Cường', 2, 500000, 1000000],
  ];
  const parsed = parseNightRewardRows(rows);
  assert.deepEqual(parsed.map((row) => [row.maNV, row.caDem]), [['C743', 11], ['B124', 2]]);
});

test('accepts a complete MV table below the old six-row limit', () => {
  const rows = [...Array.from({ length: 7 }, () => []),
    ['STT', 'MV', 'Họ và tên', 'Công ca đêm'],
    [1, 'A001', 'Nguyễn Trung Thuận', 6],
  ];
  assert.equal(parseNightRewardRows(rows)[0].maNV, 'A001');
});

test('rejects a code header without the night-shift column', () => {
  assert.throws(() => parseNightRewardRows([
    ['STT', 'MS', 'Họ và tên'], [1, 'B109', 'Lê Thị Thu Hoài'],
  ]), /MS\/MV.*Công ca đêm/);
});

test('reads workbook bytes through the same file path used by the modal', async () => {
  const { default: XLSX } = await import('xlsx-js-style');
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, XLSX.utils.aoa_to_sheet([
    ...title, ['STT', 'MS', 'Họ và tên', 'Công ca đêm'], [1, 'B109', 'Lê Thị Thu Hoài', 17],
  ]), 'Sheet1');
  const bytes = XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' });
  const parsed = await parseNightRewardXlsx({ arrayBuffer: async () => bytes });
  assert.deepEqual(parsed.map((row) => [row.maNV, row.caDem]), [['B109', 17]]);
});
