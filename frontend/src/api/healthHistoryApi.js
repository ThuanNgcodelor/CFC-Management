import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';
const root = '/hr/health/history-import';
export const healthHistoryApi = {
  status: async (signal) => unwrapApiData(await baseApi.get(root, { signal })),
  unlock: async (file) => { const body = new FormData(); body.append('file', file); return unwrapApiData(await baseApi.post(`${root}/key`, body)); },
  run: async () => unwrapApiData(await baseApi.post(`${root}/run`, { reason: 'Quản trị yêu cầu kiểm tra lại gói lịch sử; giữ nguyên dòng chưa xác minh.' })),
};
