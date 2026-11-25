import { useState } from 'react';
import { QueryClient, QueryClientProvider, useMutation } from '@tanstack/react-query';
import { FileSpreadsheet, UploadCloud } from 'lucide-react';
import toast from 'react-hot-toast';
import { Button } from '../../components/ui/Button';
import { hrPayrollApi } from '../../api/hrPayrollApi';
import { apiErrorMessage } from '../../utils/hr';

export default function HrPayrollUpload(props) {
  const [client] = useState(() => new QueryClient());
  return <QueryClientProvider client={client}><PayrollUploadForm {...props} /></QueryClientProvider>;
}

function PayrollUploadForm({ busy, onImported }) {
  const [type, setType] = useState('STANDARD');
  const [file, setFile] = useState(null);
  const mutation = useMutation({ mutationFn: ({ source, payrollType }) => hrPayrollApi.upload(source, payrollType),
    onSuccess: async result => { setFile(null); toast.success('Đã đọc file lương, hãy kiểm tra dữ liệu.'); await onImported(result); },
    onError: error => toast.error(apiErrorMessage(error, 'Không thể import file lương.')) });
  const working = busy || mutation.isPending;
  const upload = event => {
    event.preventDefault();
    if (!file || working) return;
    if (!file.name.toLowerCase().endsWith('.xlsx')) { toast.error('Chỉ nhận file Excel .xlsx.'); return; }
    if (file.size > 15 * 1024 * 1024) { toast.error('File không được vượt quá 15 MB.'); return; }
    mutation.mutate({ source: file, payrollType: type });
  };
  return <section className="space-y-4 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
    <div className="flex flex-wrap gap-2" role="group" aria-label="Loại file lương">
      <Button variant={type === 'STANDARD' ? 'primary' : 'secondary'} disabled={working} aria-pressed={type === 'STANDARD'} onClick={() => { setType('STANDARD'); setFile(null); }}>Nhập file lương Excel</Button>
      <Button variant={type === 'LEADERSHIP' ? 'primary' : 'secondary'} disabled={working} aria-pressed={type === 'LEADERSHIP'} onClick={() => { setType('LEADERSHIP'); setFile(null); }}>Nhập file lương lãnh đạo</Button>
    </div>
    <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
      <div className="flex min-w-0 items-start gap-3"><UploadCloud className="h-5 w-5 shrink-0 text-blue-700" /><div><h2 className="font-semibold text-gray-900">{type === 'LEADERSHIP' ? 'Lương Ban điều hành' : 'Nhập file lương Excel'}</h2><p className="mt-1 text-sm text-gray-500">Chỉ tạo bản xem trước, chưa gửi tin nhắn. {type === 'LEADERSHIP' && 'Đọc số tiền hoặc kết quả công thức đã lưu; ô lỗi sẽ bị chặn gửi. Không yêu cầu cột Công/Số tài khoản.'}</p></div></div>
      <form onSubmit={upload} className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-center">
        <label className="flex min-h-11 min-w-0 cursor-pointer items-center gap-3 rounded-lg border border-dashed border-gray-300 bg-gray-50 px-4 py-2 text-sm sm:min-w-[260px]">
          <FileSpreadsheet className="h-5 w-5 shrink-0 text-gray-500" /><span className="min-w-0"><span className="block truncate font-medium">{file?.name || 'Chọn file .xlsx'}</span><span className="block text-xs text-gray-500">Tối đa 15 MB</span></span>
          <input key={`${type}-${file ? 'selected' : 'empty'}`} type="file" aria-label="File lương Excel" accept=".xlsx" disabled={working} onChange={event => setFile(event.target.files?.[0] || null)} className="sr-only" />
        </label>
        <Button type="submit" disabled={!file || working}>{mutation.isPending ? 'Đang đọc file...' : 'Import & xem trước'}</Button>
      </form>
    </div>
  </section>;
}
