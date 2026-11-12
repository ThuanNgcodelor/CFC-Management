import React from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import HrHealth from '../src/pages/health/HrHealth';
import { healthApi } from '../src/api/healthApi';
import '../src/index.css';

// Browser fixture only: no login, real employee, database or remote API is used.
const rows = { campaigns: [], indicators: [] };
window.healthTest = { calls: [], failSave: false, rows };
healthApi.capabilities = async () => ({ enabled: true });
healthApi.list = async (type, params, signal) => {
  window.healthTest.calls.push({ type, ...params });
  await new Promise((resolve) => setTimeout(resolve, params.keyword === 'OLD' ? 600 : 30));
  if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
  const content = rows[type].filter((item) => `${item.code} ${item.name}`.toLowerCase().includes(params.keyword.toLowerCase()))
    .filter((item) => !params.year || item.reportYear === Number(params.year))
    .filter((item) => !params.group || item.workforceGroup === params.group);
  return { content: content.slice(params.page * 20, (params.page + 1) * 20), page: params.page,
    totalElements: content.length, totalPages: Math.ceil(content.length / 20), size: 20 };
};
healthApi.save = async ({ type, item }) => {
  if (window.healthTest.failSave) throw { response: { data: { message: 'Dữ liệu đã thay đổi. Vui lòng tải lại trước khi lưu.' } } };
  const next = { ...item, id: item.id || crypto.randomUUID(), rowVersion: (item.rowVersion ?? -1) + 1, status: item.status || 'OPEN' };
  const index = rows[type].findIndex((value) => value.id === item.id);
  if (index < 0) rows[type].push(next); else rows[type][index] = next;
  return next;
};
createRoot(document.getElementById('root')).render(<MemoryRouter><HrHealth /></MemoryRouter>);
