// Entirely fictional; never imported by the application entrypoint.
export const state = { calls: [], revoked: false, expired: false };
window.healthSelfFixture = state;
const paged = (content) => ({ content, page: 0, size: 20, totalPages: 1, totalElements: content.length });
const releases = [2024, 2025].map((year) => ({ id: `fictional-${year}`, revision: 1, year, campaign: `Khám định kỳ giả lập ${year}`, provider: 'Cơ sở khám giả lập', examinedOn: null, classification: 'II', incomplete: true, publishedAt: `${year}-11-01` }));
const measurement = { key: 'fake-ast', code: 'AST_FAKE', label: 'AST giả lập', raw: '<34', unit: 'U/L', method: null, reference: '0–34' };
const check = () => { if (state.expired) throw Object.assign(new Error('Phiên tra cứu đã hết hạn.'), { status: 401 }); };
export const api = {
  profile: async (params) => { check(); state.calls.push({ type: 'profile', params }); return { name: 'Người tra cứu giả lập', code: 'DEMO-SELF', birthDate: null, releases: paged(state.revoked ? [] : releases.filter((r) => r.year >= params.from && r.year <= params.to)) }; },
  detail: async (id, params) => { check(); state.calls.push({ type: 'detail', id, params }); if (state.revoked) throw new Error('Phiên bản đã thu hồi.'); return { release: releases.find((r) => r.id === id), results: paged(!params.keyword || measurement.label.toLowerCase().includes(params.keyword.toLowerCase()) ? [measurement] : []) }; },
  compare: async (params) => { check(); state.calls.push({ type: 'compare', params }); return { visits: releases.map((r) => ({ id: r.id, year: r.year, provider: r.provider })), rows: paged([{ indicator: { key: measurement.key, label: measurement.label, unit: measurement.unit }, cells: releases.map((r) => ({ visitId: r.id, missing: false, raw: '<34', reference: '0–34' })), numericComparable: false, note: 'Chưa xác minh phương pháp; giữ kết quả gốc.' }]) }; },
  pdf: async () => { throw new Error('Phiếu giả lập đang tạm lỗi, vui lòng thử lại.'); },
};
