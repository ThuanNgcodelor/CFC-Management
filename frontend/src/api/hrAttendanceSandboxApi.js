import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

export async function previewAttendanceFile(file) {
  const body = new FormData();
  body.append('file', file);
  return unwrapApiData(await baseApi.post('/hr/attendance/production/sandbox/preview', body));
}

export async function rematchAttendanceFile(file, decisions) {
  const body = new FormData();
  body.append('file', file);
  body.append('request', new Blob([JSON.stringify({ decisions })], { type: 'application/json' }));
  return unwrapApiData(await baseApi.post('/hr/attendance/production/sandbox/rematch', body));
}
