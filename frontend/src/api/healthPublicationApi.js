import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

const root = '/hr/health/publications';
export const healthPublicationApi = {
  candidates: async (params, signal) => unwrapApiData(await baseApi.get(`${root}/candidates`, { params, signal })),
  preview: async (body, signal) => unwrapApiData(await baseApi.post(`${root}/preview`, body, { signal })),
  apply: async (action, body) => unwrapApiData(await baseApi.post(`${root}/${action}`, body)),
  revoke: async (id, body) => unwrapApiData(await baseApi.post(`${root}/${encodeURIComponent(id)}/revoke`, body)),
  campaignState: async (id, body) => unwrapApiData(await baseApi.post(`${root}/campaigns/${encodeURIComponent(id)}/state`, body)),
  history: async (params, signal) => unwrapApiData(await baseApi.get(`${root}/history`, { params, signal })),
  results: async (id, params, signal) => unwrapApiData(await baseApi.get(`${root}/${encodeURIComponent(id)}/results`, { params, signal })),
  pdf: async (id, employeeId, signal) => {
    try {
      const response = await baseApi.get(`${root}/${encodeURIComponent(id)}/pdf`, { params: { employeeId }, signal, responseType: 'blob' });
      if (signal.aborted) return;
      const url = URL.createObjectURL(response.data); const link = document.createElement('a');
      link.href = url; link.download = 'So_suc_khoe_cong_bo.pdf'; document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      if (error.response?.data instanceof Blob) {
        try { error.response.data = JSON.parse(await error.response.data.text()); } catch { error.response.data = { message: 'Không tải được phiên bản công bố.' }; }
      }
      throw error;
    }
  },
};
