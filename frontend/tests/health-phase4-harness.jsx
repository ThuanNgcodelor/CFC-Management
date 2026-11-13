import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import HrHealth from '../src/pages/health/HrHealth';
import { healthApi } from '../src/api/healthApi';
import { healthRecordApi } from '../src/api/healthRecordApi';
import { baseApi } from '../src/api/baseApi';
const originalGet = baseApi.get.bind(baseApi);
baseApi.get = (path, config) => path.startsWith('/hr/health/personal-access/') ? Promise.resolve({ data: { data: { eligible: false, issued: false, revoked: false, generation: null } } }) : originalGet(path, config);
import '../src/index.css';

// Isolated UI fixture: no employee or clinical result below belongs to a real person.
const state = { calls: [], cancelled: 0 };
window.healthRecordTest = state;
healthApi.capabilities = async () => ({ enabled: true });
const people = Array.from({ length: 43 }, (_, n) => ({ employeeId: `record-${n}`, code: `RECORDDEV${String(n + 1).padStart(3, '0')}`, name: `Nhân viên hồ sơ giả lập ${n + 1}`, birthDate: '1990-01-02', currentGroup: n % 2 ? 'GENERAL_LABOR' : 'OFFICE', visitCount: n === 42 ? 0 : 4, lastYear: n === 42 ? null : 2025 }));
const visits = [2023, 2024, 2024, 2025].map((year, n) => ({ id: `visit-${n}`, year, campaign: `Đợt giả lập ${n}`, provider: 'Cơ sở giả lập', examinedOn: `${year}-${n === 2 ? '10' : '09'}-01`, namespace: 'RECORD_DEV', sid: `FAKE-${n}`, resultCount: 22, status: 'REVIEW' }));
const paged = (rows, page = 0, size = 20) => ({ content: structuredClone(rows.slice(page * size, page * size + size)), page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) });
healthRecordApi.people = async (params, signal) => {
  state.calls.push(params); await new Promise((resolve) => setTimeout(resolve, params.keyword === 'OLD' ? 600 : 30));
  if (signal?.aborted) { state.cancelled++; throw new DOMException('Aborted', 'AbortError'); }
  let filtered = people.filter((p) => !params.keyword || `${p.code} ${p.name}`.toLowerCase().includes(params.keyword.toLowerCase()));
  if (params.group) filtered = filtered.filter((p) => p.currentGroup === params.group);
  if (params.year && Number(params.year) === 2026) filtered = filtered.map((p) => ({ ...p, visitCount: 0, lastYear: null }));
  if (params.data === 'WITH_DATA') filtered = filtered.filter((p) => p.visitCount > 0);
  if (params.data === 'WITHOUT_DATA') filtered = filtered.filter((p) => p.visitCount === 0);
  filtered = filtered.toSorted((a, b) => (params.sort.startsWith('name') ? a.name.localeCompare(b.name) : a.code.localeCompare(b.code)) * (params.sort.endsWith('Desc') ? -1 : 1));
  return paged(filtered, params.page, params.size);
};
healthRecordApi.timeline = async (id, params) => ({ person: people.find((p) => p.employeeId === id), visits: paged(visits.filter((v) => v.year >= Number(params.fromYear) && v.year <= Number(params.toYear)).toReversed(), params.page, params.size), years: Array.from({ length: Number(params.toYear) - Number(params.fromYear) + 1 }, (_, n) => ({ year: Number(params.fromYear) + n, visits: visits.filter((v) => v.year === Number(params.fromYear) + n).length })) });
healthRecordApi.results = async (_id, visitId, params) => {
  const values = Array.from({ length: 22 }, (_, n) => ({ key: `result-${n}`, label: n === 0 ? 'AST giả lập' : `Chỉ tiêu giả lập ${n}`, category: 'Nhóm giả lập', raw: n === 0 ? '<0.5' : 'Kết luận giả lập', reference: '<34', unit: 'U/L', method: 'Máy A', sourceSheet: 'Sheet giả lập', sourceRow: 2, sourceColumn: 'D', sourceLabel: 'Tên nguồn giả lập' })).filter((r) => !params.keyword || r.label.toLowerCase().includes(params.keyword.toLowerCase()));
  return { visit: visits.find((v) => v.id === visitId), results: paged(values, params.page, params.size) };
};
healthRecordApi.compare = async (_id, body) => {
  const selected = visits.filter((v) => body.visitIds.includes(v.id));
  const rows = [
    { indicator: { key: 'ast', label: 'AST giả lập', unit: 'U/L', method: 'Máy A' }, cells: selected.map((v, n) => ({ visitId: v.id, missing: false, raw: String(10 + n), numeric: 10 + n, reference: '<34', sourceSheet: 'Sheet giả lập', sourceRow: 2, sourceColumn: 'D' })), numericComparable: true, difference: selected.length - 1, note: 'Chênh lệch số học, không đánh giá tốt/xấu.' },
    { indicator: { key: 'extra', label: 'Chỉ tiêu khám thêm giả lập', unit: '', method: '' }, cells: selected.map((v, n) => ({ visitId: v.id, missing: n === 0, raw: n === 0 ? null : '<0.5', reference: '', sourceSheet: 'Sheet bổ sung giả lập', sourceRow: 3, sourceColumn: 'F' })), numericComparable: false, difference: null, note: 'Cần đối chiếu đơn vị/phương pháp.' },
  ].filter((r) => !body.keyword || r.indicator.label.toLowerCase().includes(body.keyword.toLowerCase()));
  return { visits: selected, rows: paged(rows, body.page, body.size) };
};
createRoot(document.getElementById('root')).render(<BrowserRouter><HrHealth /></BrowserRouter>);
