import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

function download(blob, fileName) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  anchor.click();
  URL.revokeObjectURL(url);
}

export const hrReportApi = {
  headcount: async (params, options = {}) => unwrapApiData(await baseApi.get('/hr/reports/headcount', { params, signal: options.signal })),
  leavers: async (params, options = {}) => unwrapApiData(await baseApi.get('/hr/reports/leavers', { params, signal: options.signal })),
  movementSources: async (params, options = {}) => unwrapApiData(await baseApi.get('/hr/reports/movement-sources', { params, signal: options.signal })),
  exportLeavers: async (params) => {
    const response = await baseApi.get('/hr/reports/leavers/export', { params, responseType: 'blob' });
    download(response.data, `bao-cao-nghi-viec-${params.year}.xlsx`);
  },
};
