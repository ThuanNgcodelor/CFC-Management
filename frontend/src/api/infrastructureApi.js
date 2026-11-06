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
  routerConnection: async (signal) => unwrapApiData(await baseApi.get(`${root}/router/connection`, { signal })),
  saveRouterConnection: async (body, signal) => unwrapApiData(await baseApi.put(`${root}/router/connection`, body, { signal })),
  testRouterConnection: async (body, signal) => unwrapApiData(await baseApi.post(`${root}/router/connection/test`, body, { signal })),
  refreshRouter: async (signal) => unwrapApiData(await baseApi.post(`${root}/router/refresh`, null, { signal })),
  syncIpam: async () => unwrapApiData(await baseApi.post(`${root}/ipam/sync`)),
  observations: async (params = {}, signal) => unwrapApiData(await baseApi.get(`${root}/ipam/observations`, { params, signal })),
  conflicts: async (signal) => unwrapApiData(await baseApi.get(`${root}/ipam/conflicts`, { signal })),
  reviewIp: async (ip, body) => unwrapApiData(await baseApi.put(`${root}/ipam/conflicts/${encodeURIComponent(ip)}`, body)),
  topology: async (signal) => unwrapApiData(await baseApi.get(`${root}/topology/links`, { signal })),
  addTopologyLink: async (body) => unwrapApiData(await baseApi.post(`${root}/topology/links`, body)),
  verifyTopologyLink: async (id, status) => unwrapApiData(await baseApi.put(`${root}/topology/links/${id}/verification`, null, { params: { status } })),
  assets: async (params = {}, signal) => unwrapApiData(await baseApi.get(`${root}/assets`, { params, signal })),
  addAsset: async (body) => unwrapApiData(await baseApi.post(`${root}/assets`, body)),
  updateAsset: async (id, body) => unwrapApiData(await baseApi.put(`${root}/assets/${id}`, body)),
  assetHistory: async (id) => unwrapApiData(await baseApi.get(`${root}/assets/${id}/history`)),
};
