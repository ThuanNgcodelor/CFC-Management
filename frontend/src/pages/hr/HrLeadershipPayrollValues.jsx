const FIELDS = [
  ['luongCoBan', 'Lương cơ bản'], ['buDauNam', 'Bù đầu năm (cột 30%)'], ['tienLuong', 'Tổng tiền lương'],
  ['thueTncn', 'Thuế TNCN'], ['bhxh', 'BHXH 10,5%'], ['baoGiat', 'Bgiặt'], ['htkk', 'HTKK'],
  ['thuDangPhi', 'Đảng'], ['asxh', 'Quỹ ASXH'], ['xhhc', 'Quỹ HC'], ['doanPhi', 'Thu ĐP'],
  ['tongThu', 'Tổng khoản thu'], ['nganHangChuyen', 'NH chuyển'],
];
export default function HrLeadershipPayrollValues({ values }) {
  if (values?.payrollType !== 'LEADERSHIP') return null;
  return <details className="mt-2 text-xs"><summary className="cursor-pointer font-medium text-blue-700">Xem số liệu lương lãnh đạo</summary>
    <dl className="my-2 grid min-w-[220px] grid-cols-2 gap-x-4 gap-y-1">{FIELDS.map(([key, label]) => <div className="contents" key={key}><dt>{label}</dt><dd className="text-right font-medium">{typeof values[key] === 'number' ? values[key].toLocaleString('vi-VN') : values[key] ?? '—'}</dd></div>)}</dl>
  </details>;
}
