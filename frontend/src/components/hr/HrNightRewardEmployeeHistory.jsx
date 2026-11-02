import { useEffect, useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, Search } from 'lucide-react';
import { hrNightRewardApi as api } from '../../api/hrNightRewardApi';
import { apiErrorMessage, formatHrDate } from '../../utils/hr';
import { HrEmpty, HrError, HrLoading, HrPagination } from './HrUi';

const rewardStatus = {
  QUALIFIED: 'Đạt điều kiện', QUALIFIED_EXCEPTION: 'Đạt ngoại lệ', NOT_QUALIFIED: 'Chưa đạt',
  EXCLUDED: 'Không áp dụng', STALE: 'Cần chốt lại',
};
const workValue = (value) => value == null ? '—' : Number(value).toLocaleString('vi-VN', { maximumFractionDigits: 2 });

export function HrNightRewardEmployeeHistory({ employeeCode, initialMonth, onSelectEmployee, onOpenRewardTimeline }) {
  const [keyword, setKeyword] = useState(employeeCode || '');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchPage, setSearchPage] = useState(0);
  const [searchResult, setSearchResult] = useState(null);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState('');
  const [through, setThrough] = useState(initialMonth);
  const [months, setMonths] = useState(12);
  const [overview, setOverview] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [expandedMonth, setExpandedMonth] = useState('');

  useEffect(() => {
    setKeyword(employeeCode || '');
    setSearchOpen(false);
  }, [employeeCode]);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedKeyword(keyword.trim()), 250);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    if (!searchOpen || debouncedKeyword.length < 2) return undefined;
    const controller = new AbortController();
    setSearchLoading(true);
    setSearchError('');
    api.searchEmployees(debouncedKeyword, searchPage, controller.signal)
      .then(setSearchResult)
      .catch((requestError) => {
        if (!controller.signal.aborted) setSearchError(apiErrorMessage(requestError, 'Không tìm được nhân viên.'));
      })
      .finally(() => { if (!controller.signal.aborted) setSearchLoading(false); });
    return () => controller.abort();
  }, [debouncedKeyword, searchOpen, searchPage]);

  useEffect(() => {
    if (!employeeCode || !through) {
      setOverview(null);
      return undefined;
    }
    const controller = new AbortController();
    setOverview(null);
    setLoading(true);
    setError('');
    Promise.all([
      api.timeline(employeeCode, controller.signal),
      api.attendanceHistory(employeeCode, through, months, controller.signal),
    ]).then(([timeline, attendance]) => setOverview({ timeline, attendance }))
      .catch((requestError) => {
        if (!controller.signal.aborted) setError(apiErrorMessage(requestError, 'Không tải được lịch sử nhân viên.'));
      })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [employeeCode, through, months]);

  const latestQualifications = useMemo(() => {
    const latest = new Map();
    for (const item of overview?.timeline?.qualifications || []) {
      const current = latest.get(item.attendanceMonth);
      if (!current || item.revision > current.revision) latest.set(item.attendanceMonth, item);
    }
    return latest;
  }, [overview]);

  const cumulativeByMonth = useMemo(() => {
    if (!overview) return new Map();
    const orderedQualifications = [...latestQualifications.values()]
      .sort((left, right) => left.attendanceMonth.localeCompare(right.attendanceMonth));
    const orderedMonths = [...overview.attendance.months]
      .sort((left, right) => left.attendanceMonth.localeCompare(right.attendanceMonth));
    const result = new Map();
    let qualified = 0;
    let index = 0;
    for (const item of orderedMonths) {
      while (index < orderedQualifications.length && orderedQualifications[index].attendanceMonth <= item.attendanceMonth) {
        if (['QUALIFIED', 'QUALIFIED_EXCEPTION'].includes(orderedQualifications[index].status)) qualified++;
        index++;
      }
      result.set(item.attendanceMonth, {
        periodic: qualified % overview.timeline.progress.periodicRequiredMonths,
        loyalty: qualified % overview.timeline.progress.loyaltyRequiredMonths,
      });
    }
    return result;
  }, [latestQualifications, overview]);
  const searchPending = keyword.trim() !== debouncedKeyword;

  const selectEmployee = (code) => {
    setSearchOpen(false);
    setSearchResult(null);
    setExpandedMonth('');
    onSelectEmployee(code);
  };

  return (
    <section className="mt-5 space-y-5">
      <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:p-5">
        <h2 className="font-semibold text-gray-900">Tra cứu theo nhân viên</h2>
        <p className="mt-1 text-sm text-gray-500">Tìm được cả người không có trong bảng thưởng của tháng đang chọn.</p>
        <div className="mt-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto_auto]">
          <div className="relative">
            <label htmlFor="night-reward-employee-search" className="sr-only">Tìm mã hoặc tên nhân viên</label>
            <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
            <input id="night-reward-employee-search" value={keyword}
              onChange={(event) => { setKeyword(event.target.value); setSearchPage(0); setSearchResult(null); setSearchLoading(true); setSearchOpen(true); }}
              onFocus={() => { if (keyword.trim().length >= 2) setSearchOpen(true); }}
              placeholder="Nhập mã hoặc tên nhân viên" autoComplete="off"
              className="h-10 w-full rounded-lg border border-gray-300 pl-9 pr-3 text-sm" />
          </div>
          <label className="flex items-center gap-2 text-sm text-gray-600">Đến tháng
            <input type="month" value={through} onChange={(event) => setThrough(event.target.value)}
              className="h-10 rounded-lg border border-gray-300 px-2 text-sm" />
          </label>
          <select value={months} onChange={(event) => setMonths(Number(event.target.value))}
            aria-label="Khoảng thời gian" className="h-10 rounded-lg border border-gray-300 bg-white px-3 text-sm">
            <option value={12}>12 tháng</option><option value={24}>24 tháng</option>
          </select>
        </div>
        {searchOpen && keyword.trim().length >= 2 && (
          <div className="mt-3 rounded-lg border border-gray-200 bg-white p-2 shadow-sm">
            {(searchLoading || searchPending) && <p className="p-2 text-sm text-gray-500">Đang tìm nhân viên...</p>}
            {!searchPending && searchError && <HrError message={searchError} />}
            {!searchLoading && !searchPending && !searchError && !searchResult?.content?.length && (
              <p className="p-2 text-sm text-gray-500">Không tìm thấy nhân viên phù hợp.</p>
            )}
            {!searchLoading && !searchPending && searchResult?.content?.map((employee) => (
              <button key={employee.employeeCode} type="button" onClick={() => selectEmployee(employee.employeeCode)}
                className="flex w-full items-center justify-between gap-3 rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-50">
                <span><b>{employee.employeeCode}</b> · {employee.employeeName}</span><ChevronRight className="h-4 w-4 shrink-0" />
              </button>
            ))}
            {!searchPending && searchResult?.totalPages > 1 && <HrPagination page={searchResult.page} totalPages={searchResult.totalPages}
              totalElements={searchResult.totalElements} onPageChange={(nextPage) => { setSearchPage(nextPage); setSearchResult(null); setSearchLoading(true); }} />}
          </div>
        )}
      </div>

      {!employeeCode && <HrEmpty title="Chọn nhân viên để xem lịch sử" description="Nhập ít nhất 2 ký tự của mã hoặc họ tên." />}
      {employeeCode && !through && <HrEmpty title="Chọn tháng kết thúc" description="Chọn tháng để xem công và tích lũy." />}
      {employeeCode && loading && <HrLoading label="Đang tải công và lịch sử xét thưởng..." />}
      {employeeCode && error && <HrError message={error} />}
      {employeeCode && !loading && !error && overview && (
        <div className="space-y-4">
          <div className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:p-5">
            <h2 className="text-lg font-semibold text-gray-900">{overview.attendance.employeeCode} · {overview.attendance.employeeName}</h2>
            <button type="button" onClick={() => onOpenRewardTimeline(employeeCode)}
              className="mt-2 text-sm font-medium text-blue-700 hover:underline">Xem chi tiết các lần chốt thưởng</button>
            <div className="mt-3 grid gap-3 text-sm sm:grid-cols-2">
              <p className="rounded-lg bg-emerald-50 p-3">Tích lũy định kỳ: <b>{overview.timeline.progress.periodicQualifiedMonths}/{overview.timeline.progress.periodicRequiredMonths}</b> tháng</p>
              <p className="rounded-lg bg-blue-50 p-3">Tích lũy gắn bó: <b>{overview.timeline.progress.loyaltyQualifiedMonths}/{overview.timeline.progress.loyaltyRequiredMonths}</b> tháng</p>
            </div>
            <p className="mt-3 text-xs text-gray-500">Tổng công lấy từ ca sản xuất đã xác nhận. Nhãn tích lũy chỉ đếm tháng đã chốt đạt điều kiện thưởng.</p>
          </div>
          <div className="space-y-3">
            {overview.attendance.months.map((item) => {
              const qualification = latestQualifications.get(item.attendanceMonth);
              const cumulative = cumulativeByMonth.get(item.attendanceMonth);
              const open = expandedMonth === item.attendanceMonth;
              return (
                <article key={item.attendanceMonth} className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
                  <button type="button" aria-expanded={open}
                    onClick={() => setExpandedMonth(open ? '' : item.attendanceMonth)}
                    className="grid w-full grid-cols-[1fr_auto] items-center gap-3 p-4 text-left sm:grid-cols-[110px_1fr_1fr_1fr_auto] sm:p-5">
                    <b className="text-gray-900">{item.attendanceMonth}</b>
                    <span className="text-sm text-gray-700">Công: <b>{item.hasEmployeeData ? workValue(item.totalWorkValue) : '—'}</b></span>
                    <span className="text-sm text-gray-700">Ca đêm trong công: <b>{item.hasEmployeeData ? item.nightShifts : '—'}</b></span>
                    <span className="text-sm font-medium text-gray-800">{qualification ? rewardStatus[qualification.status] || qualification.status : 'Chưa chốt thưởng'}</span>
                    <ChevronDown className={`h-4 w-4 text-gray-400 transition ${open ? 'rotate-180' : ''}`} />
                  </button>
                  <p className="border-t border-gray-100 px-4 py-2 text-xs text-gray-500 sm:px-5">
                    {!item.hasConfirmedImport ? 'Chưa có file chấm công đã xác nhận.' : !item.hasEmployeeData ? 'File đã xác nhận nhưng không có ngày công của nhân viên này.' :
                      `${item.days.length} ngày công có dữ liệu.`}
                    {qualification && ` · Xét thưởng: ${qualification.actualNightShiftCount}/${qualification.requiredNightShiftCount} ca đêm hợp lệ.`}
                    {cumulative && ` · Tích lũy sau tháng: định kỳ ${cumulative.periodic}/${overview.timeline.progress.periodicRequiredMonths}, gắn bó ${cumulative.loyalty}/${overview.timeline.progress.loyaltyRequiredMonths}.`}
                  </p>
                  {open && (
                    <div className="space-y-2 border-t border-gray-100 p-4 sm:p-5">
                      {qualification?.qualificationReason && <p className="rounded-lg bg-slate-50 p-3 text-sm text-gray-700">{qualification.qualificationReason}</p>}
                      {item.days.length ? <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                        {item.days.map((day) => <p key={day.workDate} className="rounded-lg border border-gray-200 p-3 text-xs">
                          <b>{formatHrDate(day.workDate)}</b> · {workValue(day.workValue)} công · {day.shiftCode || 'Chưa rõ ca'}
                          <span className="mt-1 block text-gray-500">{day.status === 'NO_PUNCH' ? 'Không có dấu chấm' : day.status === 'NEEDS_REVIEW' ? 'Cần kiểm tra' : 'Đã xác nhận'}</span>
                        </p>)}
                      </div> : <p className="text-sm text-gray-500">Không có ngày công để xem.</p>}
                    </div>
                  )}
                </article>
              );
            })}
          </div>
        </div>
      )}
    </section>
  );
}
