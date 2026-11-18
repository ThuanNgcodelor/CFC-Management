import assert from 'node:assert/strict';
import test from 'node:test';
import { reviewHealthMapping } from './healthMappingReview.js';
const columns = Array.from({ length: 25 }, (_, index) => ({ index, letter: String(index + 1), name: `Cột ${index + 1}` }));
const mapped = Object.fromEntries(columns.map(({ index }) => [index, { field: 'IGNORE' }]));
mapped[0] = { field: 'NAME' }; mapped[1] = { field: 'RESULT', indicatorId: 'ast' };

test('new columns beyond the current page remain unresolved despite an older profile', () => {
  const source = [...columns, { index: 25, letter: 'Z', name: 'Chỉ tiêu bổ sung' }];
  assert.deepEqual(reviewHealthMapping(source, mapped).unresolved.map(c => c.index), [25]);
  assert.equal(reviewHealthMapping(columns, mapped).issues.length, 0);
});
test('result and reference without an indicator block staging', () => {
  const changed = { ...mapped, 1: { field: 'RESULT' }, 23: { field: 'REFERENCE', indicatorId: ' ' } };
  assert.equal(reviewHealthMapping(columns, changed).missingIndicators.length, 2);
});
test('all ignored or missing name cannot become a result batch', () => {
  const changed = { ...mapped, 0: { field: 'IGNORE' }, 1: { field: 'IGNORE' } };
  assert.equal(reviewHealthMapping(columns, changed).issues.length, 2);
});
test('ignored columns are listed explicitly and input mappings remain unchanged', () => {
  const before = JSON.stringify(mapped);
  const review = reviewHealthMapping(columns, mapped);
  assert.equal(review.ignored.length, 23);
  assert.equal(JSON.stringify(mapped), before);
});
