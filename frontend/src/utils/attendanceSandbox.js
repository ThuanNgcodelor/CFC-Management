export async function parseFactoryNightReward(file) {
  if (!file?.name?.toLowerCase().endsWith('.xlsx')) {
    throw new Error('Chọn file đối soát định dạng .xlsx.');
  }
  const XLSX = await import('xlsx');
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array', cellFormula: false });
  const sheet = workbook.Sheets[workbook.SheetNames[0]];
  const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: null });
  const headerIndex = rows.findIndex((row) => row?.some((cell) => String(cell || '').trim().toUpperCase() === 'MS')
    && row.some((cell) => String(cell || '').toLowerCase().includes('công ca đêm')));
  if (headerIndex < 0) throw new Error('Không tìm thấy cột MS và Công ca đêm trong file xí nghiệp.');
  const header = rows[headerIndex].map((cell) => String(cell || '').trim().toLowerCase());
  const codeIndex = header.indexOf('ms');
  const nameIndex = header.findIndex((cell, index) => index > codeIndex && cell.includes('họ và tên'));
  const nightIndex = header.findIndex((cell, index) => index > codeIndex && cell.includes('công ca đêm'));
  const seen = new Set();
  const result = [];
  for (const row of rows.slice(headerIndex + 1)) {
    const code = String(row?.[codeIndex] || '').trim().toUpperCase();
    if (!code) continue;
    if (seen.has(code)) throw new Error(`Mã nhân viên ${code} bị trùng trong file xí nghiệp.`);
    seen.add(code);
    const count = Number(row[nightIndex]);
    if (!Number.isFinite(count) || count < 0) throw new Error(`Công ca đêm của ${code} không hợp lệ.`);
    result.push({ employeeCode: code, employeeName: String(row[nameIndex] || '').trim(), nightShifts: count });
  }
  if (!result.length) throw new Error('File xí nghiệp không có nhân viên hợp lệ.');
  return result;
}

export function summarizeSandbox(days) {
  const byCode = new Map();
  for (const day of days) {
    const item = byCode.get(day.employeeCode) || {
      employeeCode: day.employeeCode, employeeName: day.employeeName,
      policyGroup: day.policyGroup || 'PRODUCTION_WORKER', totalDays: 0, proposedWorkValue: 0,
      nightShifts: 0, overtimeShifts: 0, nightAndOvertimeShifts: 0, reviewShifts: 0,
      readyShifts: 0, confirmedShifts: 0, noPunchRows: 0,
    };
    item.totalDays += 1;
    if (day.status === 'NEEDS_REVIEW') item.reviewShifts += 1;
    if (day.status === 'AUTO_MATCHED') item.readyShifts += 1;
    if (day.status === 'CONFIRMED') item.confirmedShifts += 1;
    if (day.status === 'NO_PUNCH') item.noPunchRows += 1;
    if (!['EXCLUDED', 'REJECTED'].includes(day.status)) item.proposedWorkValue += Number(day.workValue || 0);
    if (Number(day.nightAllowanceAmount || 0) > 0) item.nightShifts += 1;
    if (!['EXCLUDED', 'REJECTED'].includes(day.status) && Number(day.workValue) === 2) item.overtimeShifts += 1;
    item.nightAndOvertimeShifts = item.nightShifts + item.overtimeShifts;
    byCode.set(day.employeeCode, item);
  }
  return [...byCode.values()].sort((left, right) => left.employeeCode.localeCompare(right.employeeCode));
}
