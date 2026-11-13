import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';
const root = '/hr/health/records';
export const healthRecordApi = {
  people: async (params, signal) => unwrapApiData(await baseApi.get(root, { params, signal })),
  timeline: async (id, params, signal) => unwrapApiData(await baseApi.get(`${root}/${encodeURIComponent(id)}/timeline`, { params, signal })),
  results: async (id, visitId, params, signal) => unwrapApiData(await baseApi.get(`${root}/${encodeURIComponent(id)}/visits/${encodeURIComponent(visitId)}/results`, { params, signal })),
  compare: async (id, body, signal) => unwrapApiData(await baseApi.post(`${root}/${encodeURIComponent(id)}/compare`, body, { signal })),
};
