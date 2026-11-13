import './health-phase4-harness';
import { healthApi } from '../src/api/healthApi';
import { healthPublicationApi } from '../src/api/healthPublicationApi';

// Browser-only fictional state. Never loaded by App or seeded into the baseline dev database.
const state = { calls: [], status: 'OPEN', version: 0, released: [], edited: 'Kết luận giả lập <0.5', reviewed: false };
window.healthPublicationTest = state;
const campaign = { id: 'fake-campaign', code: 'FAKE2025', name: 'Đợt công bố giả lập', reportYear: 2025, year: 2025, provider: 'Cơ sở giả lập', workforceGroup: 'BOTH', status: 'OPEN', rowVersion: 0 };
const people = [{ employeeId: 'record-0', code: 'PUBLICDEV01', name: 'Nhân viên công bố giả lập 1' }, { employeeId: 'record-1', code: 'PUBLICDEV02', name: 'Nhân viên công bố giả lập 2' }];
const latest = () => state.released.at(-1) || null;
const candidate = (person) => {
  const current = person.employeeId === 'record-1' ? null : latest();
  return { visitId: `pub-${person.employeeId}`, sid: `FAKE-${person.code}`, rowVersion: state.version, token: String(state.version).padStart(64, '0'), state: current ? current.status === 'REVOKED' ? 'REVOKED' : current.value === state.edited ? 'PUBLISHED' : 'CHANGED' : 'UNPUBLISHED', results: person.employeeId === 'record-1' ? 0 : 1, reviewed: state.reviewed && person.employeeId !== 'record-1', publishable: state.reviewed && person.employeeId !== 'record-1', blockers: person.employeeId === 'record-1' ? ['Chưa có kết quả đã nhập.'] : [], warnings: ['Chưa có ngày khám; chỉ hiển thị năm đã xác nhận.', 'Bản công bố gắn nhãn dữ liệu chưa đầy đủ.'], latest: current };
};
const rows = () => people.map((p) => ({ ...p, visits: [candidate(p)] }));
healthApi.list = async () => ({ content: [{ ...campaign, status: state.status, rowVersion: state.version }], page: 0, size: 20, totalElements: 1, totalPages: 1 });
healthPublicationApi.candidates = async (params) => {
  state.calls.push({ type: 'candidates', params }); const content = rows().filter((p) => !params.keyword || `${p.code} ${p.name}`.toLowerCase().includes(params.keyword.toLowerCase()));
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 };
};
healthPublicationApi.preview = async (body) => {
  state.calls.push({ type: 'preview', body: structuredClone(body) }); const content = rows().filter((p) => (!body.employeeIds || body.employeeIds.includes(p.employeeId)) && (!body.keyword || `${p.code} ${p.name}`.toLowerCase().includes(body.keyword.toLowerCase())));
  return { campaign: { ...campaign, status: state.status, rowVersion: state.version }, people: content.length, visits: content.length, publishable: content.filter((p) => p.visits[0].publishable).length, blocked: content.filter((p) => !p.visits[0].publishable).length, rows: content };
};
healthPublicationApi.apply = async (action, body) => {
  state.calls.push({ type: action, body: structuredClone(body) });
  const outcomes = body.people.map((p) => {
    if (p.employeeId === 'record-1') return { employeeId: p.employeeId, status: 'BLOCKED', affected: 0, message: 'Lượt còn lỗi chặn: Chưa có kết quả đã nhập.' };
    if (action === 'review') { state.reviewed = true; state.version++; }
    if (action === 'publish') {
      if (!state.reviewed) return { employeeId: p.employeeId, status: 'BLOCKED', affected: 0, message: 'Kết quả chưa được rà soát.' };
      if (latest()?.status === 'PUBLISHED' && latest().value === state.edited) return { employeeId: p.employeeId, status: 'SKIPPED', affected: 0, message: 'Không có thay đổi.' };
      state.released.push({ id: `release-${state.released.length + 1}`, visitId: 'pub-record-0', revision: state.released.length + 1, rowVersion: 0, status: 'PUBLISHED', value: state.edited, publishReason: body.reason, createdAt: '2026-11-11T09:00:00', createdByActor: 'USER:fake' }); state.version++;
    }
    if (action === 'revoke-batch' && latest()) { latest().status = 'REVOKED'; latest().revokeReason = body.reason; state.version++; }
    return { employeeId: p.employeeId, status: 'SUCCESS', affected: 1, message: 'Đã xử lý toàn bộ lượt được chọn của người này.' };
  });
  return { succeeded: outcomes.filter((p) => p.status === 'SUCCESS').length, failed: outcomes.filter((p) => p.status === 'BLOCKED').length, skipped: outcomes.filter((p) => p.status === 'SKIPPED').length, outcomes };
};
healthPublicationApi.history = async () => ({ content: structuredClone(state.released.toReversed()), page: 0, totalElements: state.released.length, totalPages: state.released.length ? 1 : 0 });
healthPublicationApi.results = async (id) => {
  if (!latest() || latest().id !== id || latest().status !== 'PUBLISHED') throw { response: { data: { message: 'Phiên bản không còn được phép hiển thị.' } } };
  return { publication: structuredClone(latest()), person: people[0], visit: { year: 2025, sid: 'FAKE-PUBLICDEV01', classification: 'I' }, results: { content: [{ key: 'fake', label: 'Chỉ tiêu giả lập', raw: latest().value, sourceSheet: 'Sheet giả lập', sourceRow: 2, sourceColumn: 'D' }], page: 0, totalElements: 1, totalPages: 1 } };
};
healthPublicationApi.revoke = async (_id, body) => { latest().status = 'REVOKED'; latest().revokeReason = body.reason; latest().rowVersion++; state.version++; return structuredClone(latest()); };
healthPublicationApi.campaignState = async (_id, body) => { state.status = body.closed ? 'CLOSED' : 'OPEN'; state.version++; return { ...campaign, status: state.status, rowVersion: state.version }; };
