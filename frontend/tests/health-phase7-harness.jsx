import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { healthSelfApi } from '../src/api/healthSelfApi';
import HealthSelfPortal from '../src/pages/health/HealthSelfPortal';
import { api, state } from './health-self-fixture';
import '../src/index.css';

state.allowIdentityOnly = true;
healthSelfApi.capabilities = async () => ({ enabled: true });
healthSelfApi.lookup = async (body) => {
  state.calls.push({ type: 'lookup', fields: Object.keys(body).sort() });
  if (body.mode === 'IDENTITY' ? (!state.allowIdentityOnly || body.citizenNumber !== '000000000001' || body.employeeCode !== 'DEMO-SELF') : body.accessCode !== 'X'.repeat(32)) {
    throw { status: 401, response: { data: { message: 'Không thể xác minh thông tin hoặc phiên đã hết hạn.' } } };
  }
  return { token: 'X'.repeat(43), expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString().replace('Z', '') };
};
healthSelfApi.forSession = () => api;
healthSelfApi.logout = async () => { state.calls.push({ type: 'logout' }); };
createRoot(document.getElementById('root')).render(<React.StrictMode><BrowserRouter><HealthSelfPortal /></BrowserRouter></React.StrictMode>);
