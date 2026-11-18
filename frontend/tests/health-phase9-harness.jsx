import React from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import DashboardLayout from '../src/layouts/DashboardLayout';
import HrHealth from '../src/pages/health/HrHealth';
import { authApi } from '../src/api/authApi';
import { userApi } from '../src/api/userApi';
import '../src/index.css';

// Loopback preview only. Health APIs are real; only the shell user is a test identity.
if (window.location.hostname !== '127.0.0.1' || import.meta.env.VITE_API_URL !== 'http://127.0.0.1:18086/api/v1') {
  throw new Error('Demo sức khỏe chỉ dùng backend QA loopback riêng.');
}
const user = { id: 'health-demo-ADMIN', role: 'ADMIN', email: 'admin@example.test', fullName: 'Quản trị giả lập' };
authApi.setAuthData({ accessToken: 'ADMIN', user });
userApi.getMe = async () => user;
userApi.getPendingRegistrationCount = async () => 0;
createRoot(document.getElementById('root')).render(<BrowserRouter><Toaster /><Routes><Route element={<DashboardLayout />}>
  <Route path="*" element={<><div className="mb-4 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">Bản thử nghiệm 2024–2026 · Toàn bộ kết quả là dữ liệu giả, không dùng cho hồ sơ thật.</div><HrHealth /></>} />
</Route></Routes></BrowserRouter>);
