// Review every source column, including columns outside the visible mapping page.
export function reviewHealthMapping(columns, mapping) {
  const unresolved = [];
  const missingIndicators = [];
  const ignored = [];
  let results = 0;
  let hasName = false;
  for (const column of columns) {
    const target = mapping[column.index];
    if (!target?.field) unresolved.push(column);
    if (target?.field === 'IGNORE') ignored.push(column);
    if (['RESULT', 'REFERENCE'].includes(target?.field) && !target.indicatorId?.trim()) missingIndicators.push(column);
    if (target?.field === 'RESULT') results += 1;
    if (target?.field === 'NAME') hasName = true;
  }
  const issues = [];
  if (unresolved.length) issues.push(`Còn ${unresolved.length} cột chưa chọn cách xử lý.`);
  if (missingIndicators.length) issues.push(`Còn ${missingIndicators.length} cột kết quả/tham chiếu chưa chọn chỉ tiêu.`);
  if (!hasName) issues.push('Cần chọn cột họ tên.');
  if (!results) issues.push('Cần ít nhất một cột kết quả.');
  return { unresolved, missingIndicators, ignored, results, issues };
}
