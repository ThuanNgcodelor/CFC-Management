import { baseApi } from './baseApi';
import { unwrapApiData } from './hrApiUtils';

export async function previewAttendanceFile(file) {
  const body = new FormData();
  body.append('file', file);
  return unwrapApiData(await baseApi.post('/hr/attendance/production/sandbox/preview', body));
}
