import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

const root = '/hr/health/reports';
export const healthReportApi = {
  preview: async (body, signal) => unwrapApiData(await baseApi.post(`${root}/preview`, body, { signal })),
  download: async (body, format, signal) => {
    try {
      const response = await baseApi.post(`${root}/${format}`, body, { signal, responseType: 'blob' });
      if (signal.aborted) return;
      const url = URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `So_suc_khoe_${body.fromYear}_${body.toYear}.${format === 'pdf' ? 'pdf' : 'xlsx'}`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      if (error.response?.data instanceof Blob) {
        const message = await error.response.data.text();
        try { error.response.data = JSON.parse(message); } catch { error.response.data = { message: 'Không tải được báo cáo. Hãy thử lại.' }; }
      }
      throw error;
    }
  },
};
