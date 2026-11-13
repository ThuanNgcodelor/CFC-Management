import './health-phase1-harness';
import { healthImportApi } from '../src/api/healthImportApi';

// Isolated UI contract fixture. All names, identities and results below are fictitious.
window.healthTest.rows.campaigns.push({ id: 'campaign-dev', code: 'KSK_DEV', name: 'Đợt khám giả lập', reportYear: 2026, provider: 'Cơ sở giả lập', workforceGroup: 'BOTH', status: 'OPEN', rowVersion: 0 });
window.healthTest.rows.indicators.push({ id: 'indicator-dev', code: 'AST_DEV', name: 'Chỉ tiêu giả lập', category: 'Sinh hóa', valueType: 'NUMBER', status: 'ACTIVE', aliases: [] });
const state = { batches: [], rows: [], inspectCalls: [], searchCalls: [], confirmCalls: [], stageCalls: [], failConfirm: false };
window.healthImportTest = state;
const person = { id: 'person-dev', code: 'DEV001', name: 'Nhân viên giả lập', birthDate: null };
const paged = (values, page) => ({ content: structuredClone(values.slice(page * 20, page * 20 + 20)), page, size: 20, totalElements: values.length, totalPages: Math.ceil(values.length / 20) });
healthImportApi.inspect = async (_file, params, signal) => {
  state.inspectCalls.push(params); await new Promise((resolve) => setTimeout(resolve, 30));
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
  return { fileHash: 'fake-hash', sheets: [{ name: 'Kết quả', hidden: false, headerRow: params.headerRow || 1, dataRows: 22, resultSheet: true, fingerprint: 'fake-fingerprint', columns: [
    { index: 0, letter: 'A', name: 'Họ tên', suggested: 'NAME' }, { index: 1, letter: 'B', name: 'Mã nhân viên', suggested: 'EMPLOYEE_CODE' },
    { index: 2, letter: 'C', name: 'SID', suggested: 'SID' }, { index: 3, letter: 'D', name: 'AST', suggested: 'RESULT', indicatorId: 'indicator-dev' },
    { index: 4, letter: 'E', name: 'Cột chưa biết', suggested: 'IGNORE' },
  ] }, { name: 'Nguồn ẩn', hidden: true, headerRow: 3, dataRows: 2, resultSheet: false, fingerprint: 'hidden', columns: [] }] };
};
healthImportApi.profiles = async () => paged([], 0);
healthImportApi.stage = async (request) => {
  state.stageCalls.push(request.request);
  const batch = { id: 'batch-dev', campaignId: 'campaign-dev', sheet: 'Kết quả', status: 'REVIEW', totalRows: 22, importedRows: 0, pendingRows: 22, rowVersion: 0 };
  state.batches.push(batch);
  state.rows = Array.from({ length: 22 }, (_, index) => ({ id: `row-${index}`, sourceRow: index + 2, name: index === 1 ? 'Tên nguồn chưa ghép' : 'Nhân viên giả lập', code: index === 1 ? '' : 'DEV001', sid: `SID-${index}`, examinedOn: '2026-01-01', state: 'PENDING', employeeId: index === 1 ? null : person.id, candidates: [person], errors: index === 1 ? ['Chưa ghép hồ sơ nhân sự.'] : [], warnings: [], previewToken: `token-${index}`, rowVersion: 0,
    cells: [{ key: 'cell', indicatorId: 'indicator-dev', label: 'AST', incoming: '<0.5', existing: index === 2 ? '20' : null, incomingReference: '<34', existingReference: '', unit: 'U/L', method: 'Máy giả lập', action: index === 2 ? 'CONFLICT' : 'ADD' }] }));
  if (state.classificationMode) {
    state.rows[0].cells.push({ key: 'classification', label: 'Phân loại', incoming: 'Nội dung sai ô giả lập', action: 'ADD' });
    state.rows[0].errors = ['Phân loại sức khỏe ngoài I–V.'];
  }
  state.rows.forEach((row) => { row.sourceBirth = '1990'; });
  return structuredClone(batch);
};
healthImportApi.batches = async (_id, page) => paged(state.batches, page);
healthImportApi.rows = async (_id, page, _signal, filter = 'ALL') => paged(state.rows.filter((row) => filter === 'ALL' || row.state === filter), page);
healthImportApi.employees = async (keyword, signal) => {
  state.searchCalls.push(keyword); await new Promise((resolve) => setTimeout(resolve, 60));
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
  return [person];
};
healthImportApi.resolve = async ({ rowId, employeeId, reason }) => {
  if (!reason.trim()) throw new Error('Resolution must have a reason');
  const row = state.rows.find((value) => value.id === rowId); row.employeeId = employeeId; row.errors = []; row.rowVersion++; row.previewToken += '-resolved';
  state.batches[0].rowVersion++; return structuredClone(state.batches[0]);
};
healthImportApi.confirm = async ({ rows }) => {
  state.confirmCalls.push(rows);
  if (state.failConfirm) throw { response: { data: { message: 'Dữ liệu đã thay đổi sau xem trước. Vui lòng tải lại và chọn lại quyết định.' } } };
  for (const decision of rows) {
    const row = state.rows.find((value) => value.id === decision.rowId);
    if (row.errors.length || row.previewToken !== decision.previewToken) throw new Error('Invalid row');
    if (row.cells.some((cell) => cell.action === 'CONFLICT') && !decision.choices.cell) throw new Error('Conflict choice missing');
    row.state = 'IMPORTED';
  }
  const batch = state.batches[0]; batch.importedRows = state.rows.filter((row) => row.state === 'IMPORTED').length; batch.pendingRows = 22 - batch.importedRows; batch.rowVersion++; batch.status = 'PARTIAL';
  return { batch: structuredClone(batch), importedRows: rows.length, skippedRows: 0 };
};
healthImportApi.cancel = async () => {
  state.rows.filter((row) => row.state === 'PENDING').forEach((row) => { row.state = 'CANCELLED'; });
  state.batches[0].pendingRows = 0; state.batches[0].rowVersion++; state.batches[0].status = 'IMPORTED'; return structuredClone(state.batches[0]);
};
healthImportApi.correctClassification = async ({ rowId, key, value, reason, rowVersion }) => {
  const row = state.rows.find((item) => item.id === rowId);
  if (!reason.trim() || row.rowVersion !== rowVersion) throw new Error('Correction requires reason and current version');
  const cell = row.cells.find((item) => item.key === key);
  row.corrections = [{ key, label: cell.label, original: cell.incoming, corrected: value, reason, actor: 'Fixture operator', at: '2026-11-11T09:00:00Z' }];
  cell.incoming = value; row.errors = []; row.rowVersion++; row.previewToken += '-corrected';
  state.batches[0].rowVersion++;
  return structuredClone(state.batches[0]);
};
