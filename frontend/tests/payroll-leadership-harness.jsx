import React from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { HelmetProvider } from 'react-helmet-async';
import HrPayroll from '../src/pages/hr/HrPayroll';
import { hrPayrollApi } from '../src/api/hrPayrollApi';
import '../src/index.css';
if (location.hostname !== '127.0.0.1') throw new Error('Chỉ chạy fixture local.');
const page = content => ({ content, number: 0, totalElements: content.length, totalPages: 1, size: 20 });
const imports = [];
window.payrollFixture = { uploads: [], sends: 0 };
hrPayrollApi.listImports = async () => page(imports);
hrPayrollApi.testRecipient = async () => null;
hrPayrollApi.upload = async (file, payrollType) => {
  window.payrollFixture.uploads.push({ file: file.name, payrollType });
  const batch = { id: 'fake-batch', payrollType, fileName: 'Lương giả lập.xlsx', payrollMonth: '10/2026', status: 'PREVIEWED', totalRows: 2, readyRows: 1, skippedRows: 0, invalidRows: 1 };
  imports.push(batch); return batch;
};
hrPayrollApi.preview = async () => ({ batch: imports[0], rows: page([
  { id: 'r1', employeeCode: 'DEMO1', employeeName: 'Người giả lập', status: 'READY', values: { payrollType: 'LEADERSHIP', luongCoBan: 10000000, buDauNam: 3000000, tienLuong: 13000000, nganHangChuyen: 12000000 } },
  { id: 'r2', employeeCode: 'DEMO2', employeeName: 'Dòng lỗi giả lập', status: 'INVALID', errorMessage: 'D4: Ô Excel bị lỗi.', values: { payrollType: 'LEADERSHIP', luongCoBan: 'Chưa có số hợp lệ' } },
]) });
hrPayrollApi.campaignForImport = async () => null;
hrPayrollApi.sendTest = async () => { window.payrollFixture.sends++; };
createRoot(document.getElementById('root')).render(<HelmetProvider><MemoryRouter><HrPayroll /></MemoryRouter></HelmetProvider>);
