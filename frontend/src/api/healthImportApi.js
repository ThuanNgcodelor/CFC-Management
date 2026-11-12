import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';
const root = '/hr/health';
const upload = (file, request) => {
  const form = new FormData(); form.append('file', file);
  if (request) form.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
  return form;
};
export const healthImportApi = {
  inspect: async (file, params, signal) => unwrapApiData(await baseApi.post(`${root}/imports/inspect`, upload(file), { params, signal, headers: { 'Content-Type': undefined } })),
  stage: async ({ file, request }) => unwrapApiData(await baseApi.post(`${root}/imports`, upload(file, request), { headers: { 'Content-Type': undefined } })),
  batches: async (campaignId, page, signal) => unwrapApiData(await baseApi.get(`${root}/imports`, { params: { campaignId, page, size: 20 }, signal })),
  rows: async (id, page, signal, state = 'ALL') => unwrapApiData(await baseApi.get(`${root}/imports/${id}/rows`, { params: { page, size: 20, state }, signal })),
  profiles: async (fingerprint, signal, page = 0) => unwrapApiData(await baseApi.get(`${root}/mapping-profiles`, { params: { fingerprint, page, size: 20 }, signal })),
  employees: async (keyword, signal) => unwrapApiData(await baseApi.get(`${root}/imports/employees`, { params: { keyword }, signal })),
  confirm: async ({ id, rows }) => unwrapApiData(await baseApi.post(`${root}/imports/${id}/confirm`, { rows })),
  resolve: async ({ id, rowId, ...body }) => unwrapApiData(await baseApi.post(`${root}/imports/${id}/rows/${rowId}/resolve`, body)),
  correctClassification: async ({ id, rowId, ...body }) => unwrapApiData(await baseApi.post(`${root}/imports/${id}/rows/${rowId}/correct-classification`, body)),
  cancel: async ({ id, rowVersion }) => unwrapApiData(await baseApi.post(`${root}/imports/${id}/cancel`, { rowVersion })),
};
