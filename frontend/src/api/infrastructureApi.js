import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

const root = '/infrastructure';
export const infrastructureApi = {
  devices: async (params = {}, signal) => unwrapApiData(await baseApi.get(`${root}/devices`, { params, signal })),
  addDevice: async (body) => unwrapApiData(await baseApi.post(`${root}/devices`, body)),
  updateDevice: async (id, body) => unwrapApiData(await baseApi.put(`${root}/devices/${id}`, body)),
  archiveDevice: async (id) => unwrapApiData(await baseApi.delete(`${root}/devices/${id}`)),
  logs: async (params = {}, signal) => unwrapApiData(await baseApi.get(`${root}/logs`, { params, signal })),
  routerStatus: async (signal) => unwrapApiData(await baseApi.get(`${root}/router/status`, { signal })),
};
