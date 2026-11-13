import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

const root = '/hr/health';
export const healthApi = {
  capabilities: async (signal) => unwrapApiData(await baseApi.get(`${root}/capabilities`, { signal })),
  list: async (type, params, signal) => unwrapApiData(await baseApi.get(`${root}/${type}`, { params, signal })),
  save: async ({ type, item }) => unwrapApiData(await (item.id
    ? baseApi.put(`${root}/${type}/${item.id}`, item)
    : baseApi.post(`${root}/${type}`, item))),
};
