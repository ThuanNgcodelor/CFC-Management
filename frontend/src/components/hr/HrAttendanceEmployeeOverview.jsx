import { Loader2 } from 'lucide-react';
import { HrEmpty, HrLoading, HrPagination } from './HrUi';

const work = (value) => Number(value || 0).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
const policyLabel = (value) => ({ PRODUCTION_WORKER: 'Công nhân', KCS: 'KCS', OFFICE: 'Hành chính' }[value] || value || '—');

export function HrAttendanceEmployeeOverview({ result, keyword, onKeywordChange, selectedCode, onSelect, onPageChange, loading }) {
  const employees = result.content || [];
  if (!employees.length && !keyword && !loading) return null;
  return <section className="mt-5 rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
    <div className="flex flex-wrap items-end justify-between gap-3">
      <div>
        <div className="flex items-center gap-2">
          <h2 className="font-semibold text-gray-900">Tổng quan theo nhân viên</h2>
          {loading && <Loader2 className="h-4 w-4 animate-spin text-blue-600" />}
        </div>
        <p className="mt-1 text-xs text-gray-500">Dữ liệu được phân trang từ máy chủ. Chọn một người để xem riêng từng ngày.</p>
      </div>
      <input value={keyword} onChange={(event) => onKeywordChange(event.target.value)} placeholder="Tìm mã hoặc tên nhân viên" className="h-10 min-w-64 rounded-lg border border-gray-300 px-3 text-sm" />
    </div>
    {loading && !employees.length ? (
      <div className="mt-4 p-8"><HrLoading label="Đang tải tổng quan theo nhân viên..." /></div>
    ) : (
      <>
        <div className="mt-4 overflow-x-auto"><table className="w-full min-w-[940px] text-left text-sm"><thead className="bg-gray-50 text-xs uppercase text-gray-500"><tr><th className="px-3 py-3">Nhân viên</th><th className="px-3 py-3">Nhóm</th><th className="px-3 py-3 text-center">Ngày</th><th className="px-3 py-3 text-center">Công tạm tính</th><th className="px-3 py-3 text-center">Ca đêm</th><th className="px-3 py-3 text-center">Tăng ca (2 công)</th><th className="px-3 py-3 text-center">Đêm + tăng ca</th><th className="px-3 py-3 text-center">Cần xử lý</th><th className="px-3 py-3">Trạng thái</th></tr></thead><tbody className="divide-y divide-gray-100">{employees.map((item) => <tr key={item.employeeCode} onClick={() => onSelect(item)} className={`cursor-pointer hover:bg-emerald-50 ${selectedCode === item.employeeCode ? 'bg-emerald-50' : ''}`}><td className="px-3 py-3"><b>{item.employeeCode}</b><p className="text-xs text-gray-500">{item.employeeName}</p></td><td className="px-3 py-3">{policyLabel(item.policyGroup)}</td><td className="px-3 py-3 text-center">{item.totalDays}</td><td className="px-3 py-3 text-center font-semibold">{work(item.proposedWorkValue)}</td><td className="px-3 py-3 text-center">{item.nightShifts}</td><td className="px-3 py-3 text-center">{item.overtimeShifts}</td><td className="px-3 py-3 text-center font-bold text-blue-700">{item.nightAndOvertimeShifts}</td><td className={`px-3 py-3 text-center font-semibold ${item.reviewShifts ? 'text-amber-700' : 'text-emerald-700'}`}>{item.reviewShifts}</td><td className="px-3 py-3"><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${item.reviewShifts ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'}`}>{item.reviewShifts ? 'Cần kiểm tra' : 'Đã ổn'}</span></td></tr>)}</tbody></table></div>
        {!employees.length && <HrEmpty title="Không tìm thấy nhân viên" description="Thử tìm bằng mã hoặc họ tên khác." />}
        <HrPagination page={result.page || 0} totalPages={result.totalPages || 0} totalElements={result.totalElements || 0} onPageChange={onPageChange} />
      </>
    )}
  </section>;
}

