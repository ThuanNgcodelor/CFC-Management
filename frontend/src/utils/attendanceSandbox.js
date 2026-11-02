const WORK_VALUES = new Set([0, 0.5, 1, 1.5, 2]);

export function validWorkValue(value) {
  return WORK_VALUES.has(Number(value));
}

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
      days: 0, workValue: 0, proposedNightShifts: 0, confirmedNightShifts: 0, reviewDays: 0,
    };
    item.days += 1;
    item.workValue += Number(day.workValue || 0);
    if (day.status === 'NEEDS_REVIEW' || day.status === 'NO_PUNCH') item.reviewDays += 1;
    if (day.countsTowardNightReward && Number(day.workValue) > 0) {
      item.proposedNightShifts += 1;
      if (day.status === 'CONFIRMED') item.confirmedNightShifts += 1;
    }
    byCode.set(day.employeeCode, item);
  }
  return [...byCode.values()].sort((left, right) => left.employeeCode.localeCompare(right.employeeCode));
}

export function reconcileSandbox(factoryRows, summaries) {
  const attendance = new Map(summaries.map((row) => [row.employeeCode, row]));
  const factory = new Map(factoryRows.map((row) => [row.employeeCode, row]));
  return {
    matched: factoryRows.filter((row) => attendance.has(row.employeeCode)).map((row) => {
      const system = attendance.get(row.employeeCode);
      return { ...row, systemNightShifts: system.proposedNightShifts,
        confirmedNightShifts: system.confirmedNightShifts, unresolvedDays: system.reviewDays,
        difference: row.nightShifts - system.proposedNightShifts };
    }),
    onlyInFactory: factoryRows.filter((row) => !attendance.has(row.employeeCode)),
    onlyInAttendance: summaries.filter((row) => !factory.has(row.employeeCode)),
  };
}
