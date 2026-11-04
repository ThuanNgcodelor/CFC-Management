import { authApi } from '../../api/authApi';
import { canAccessHrPath } from '../../utils/hrPermissions';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight,
  ArrowUpDown,
  Clock3,
  CircleDashed,
  ContactRound,
  TableProperties,
  UserCheck,
  UserMinus,
  UserPlus,
  Send,
} from 'lucide-react';
import SEOHead from '../../components/SEOHead';
import { HrError, HrLoading, HrPageHeader, HrPageShell } from '../../components/hr/HrUi';
import { hrEmployeeApi } from '../../api/hrEmployeeApi';

function overviewValue(data, ...paths) {
  for (const path of paths) {
    const value = path.split('.').reduce((current, key) => current?.[key], data);
    if (value !== undefined && value !== null) return value;
  }
  return 0;
}

function StatCard({ label, value, icon: Icon, color, to }) {
  const canOpen = to && canAccessHrPath(authApi.getRole(), to.split('?')[0]);
  const Container = canOpen ? Link : 'div';
  const colors = {
    emerald: 'bg-emerald-50 text-emerald-700',
    blue: 'bg-blue-50 text-blue-700',
    amber: 'bg-amber-50 text-amber-700',
    gray: 'bg-gray-100 text-gray-600',
  };
  return (
    <Container {...(canOpen ? { to } : {})} className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-emerald-200 focus-visible:outline-emerald-600">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-gray-500">{label}</p>
          <p className="mt-2 text-3xl font-semibold tracking-tight text-gray-900">{value}</p>
        </div>
        <div className={`rounded-lg p-3 ${colors[color]}`}><Icon className="h-5 w-5" /></div>
      </div>
    </Container>
  );
}

function QuickLink({ to, icon: Icon, title, description }) {
  if (!canAccessHrPath(authApi.getRole(), to)) return null;
  return (
    <Link to={to} className="group rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition hover:border-emerald-200 hover:shadow-md">
      <div className="flex items-start justify-between gap-4">
        <div className="rounded-lg bg-emerald-50 p-2.5 text-emerald-700"><Icon className="h-5 w-5" /></div>
        <ArrowRight className="h-4 w-4 text-gray-300 transition group-hover:translate-x-0.5 group-hover:text-emerald-600" />
      </div>
      <h2 className="mt-4 text-base font-semibold text-gray-900">{title}</h2>
      <p className="mt-1 text-sm leading-6 text-gray-500">{description}</p>
    </Link>
  );
}

export default function HrOverview() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    hrEmployeeApi.getOverview({ signal: controller.signal })
      .then(setData)
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setError(requestError.response?.data?.message || 'Không thể tải tổng quan nhân sự.');
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [reloadKey]);

  return (
    <HrPageShell>
      <SEOHead title="CFC Base | Tổng quan nhân sự" url="https://cfcbooking.io.vn/manager/hr" />
      <HrPageHeader
        title="Tổng quan nhân sự"
        description="Theo dõi hồ sơ nhân sự, người đang làm việc, đang thử việc và đã nghỉ việc."
      />

      {loading ? <HrLoading /> : error ? (
        <HrError message={error} onRetry={() => setReloadKey((value) => value + 1)} />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard label="Tổng hồ sơ" value={overviewValue(data, 'totalEmployees', 'employeeCounts.total')} icon={ContactRound} color="blue" to="/manager/hr/employees" />
            <StatCard label="Đang làm việc" value={overviewValue(data, 'activeEmployees', 'employeeCounts.active')} icon={UserCheck} color="emerald" to="/manager/hr/employees?status=ACTIVE" />
            <StatCard label="Đang thử việc" value={overviewValue(data, 'probationEmployees')} icon={CircleDashed} color="amber" to="/manager/hr/probation?status=IN_PROBATION" />
            <StatCard label="Đã nghỉ việc" value={overviewValue(data, 'inactiveEmployees', 'employeeCounts.inactive')} icon={UserMinus} color="gray" to="/manager/hr/employees?status=INACTIVE" />
          </div>

          <h2 className="mb-3 mt-8 text-lg font-semibold text-gray-900">Truy cập nhanh</h2>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <QuickLink to="/manager/hr/employees" icon={ContactRound} title="Danh sách nhân sự" description="Tìm kiếm, lọc và xem hồ sơ theo từng trang." />
            <QuickLink to="/manager/hr/movements" icon={ArrowUpDown} title="Tăng / Giảm" description="Tạo, kiểm tra và xác nhận biến động nhân sự." />
            <QuickLink to="/manager/hr/rosters" icon={TableProperties} title="Danh sách theo tháng" description="Kế thừa, mở và chốt danh sách nhân sự từng tháng." />
            <QuickLink to="/manager/hr/probation" icon={UserPlus} title="Thử việc" description="Quản lý ứng viên thử việc, tạo hợp đồng và chuyển hồ sơ khi đạt." />
            <QuickLink to="/manager/hr/payroll" icon={Send} title="Gửi phiếu lương" description="Import file lương, kiểm tra người nhận và gửi qua Telegram." />
            <QuickLink to="/manager/hr/attendance" icon={Clock3} title="Chấm công" description="Cấu hình mẫu file, import tháng và kiểm tra dữ liệu chấm công." />
          </div>
        </>
      )}
    </HrPageShell>
  );
}
