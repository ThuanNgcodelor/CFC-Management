import { HrEmpty } from './HrUi';

const amount = (value) => value ? `${value.toLocaleString('vi-VN')} đ` : '—';

function Detail({ title, children, tone = 'slate' }) {
  const color = tone === 'blue' ? 'border-blue-100 bg-blue-50/60' : 'border-slate-200 bg-slate-50';
  return (
    <div className={`min-w-0 rounded-lg border p-3 ${color}`}>
      <p className="mb-2 text-xs font-bold uppercase tracking-wide text-gray-600">{title}</p>
      <dl className="space-y-1.5 text-xs text-gray-700">{children}</dl>
    </div>
  );
}

function Field({ label, value }) {
  return (
    <div className="flex justify-between gap-2">
      <dt>{label}</dt>
      <dd className="min-w-0 break-words text-right font-semibold text-gray-900">{value}</dd>
    </div>
  );
}

function MatchedCard({ row }) {
  return (
    <article className="space-y-3 rounded-xl border border-gray-200 bg-white p-3 shadow-sm">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="font-bold text-gray-900">{row.xnMaNV}</p>
          <p className="break-words text-sm text-gray-700">{row.xnHoTen}</p>
        </div>
        <span className={`shrink-0 rounded-md px-2 py-1 text-xs font-semibold ${row.diffCaDem === 0 ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-100 text-amber-800'}`}>
          {row.diffCaDem === null ? 'Chưa rõ' : row.diffCaDem === 0 ? 'Khớp' : `${row.diffCaDem > 0 ? '+' : ''}${row.diffCaDem} ca`}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <Detail title="Xí nghiệp">
          <Field label="Ca đêm" value={row.xnCaDem ?? '—'} />
          <Field label="Tích 12" value={amount(row.xnTich12Rate)} />
          <Field label="Tích 24" value={amount(row.xnTich24Rate)} />
        </Detail>
        <Detail title="Hệ thống" tone="blue">
          <Field label="Tên" value={row.sysHoTen} />
          <Field label="Ca đêm" value={row.sysCaDem ?? '—'} />
          <Field label="Tích 12" value={row.sysTich12Months == null ? '—' : `${row.sysTich12Months} th`} />
          <Field label="Tích 24" value={row.sysTich24Months == null ? '—' : `${row.sysTich24Months} th`} />
        </Detail>
      </div>
      <p className="break-words text-xs text-gray-600">{row.note}</p>
    </article>
  );
}

function SingleSideCard({ code, name, count, periodic, loyalty, status }) {
  return (
    <article className="rounded-xl border border-gray-200 bg-white p-3 shadow-sm">
      <p className="font-bold text-gray-900">{code}</p>
      <p className="break-words text-sm text-gray-700">{name}</p>
      <dl className="mt-3 grid grid-cols-3 gap-2 border-t border-gray-100 pt-3 text-xs">
        <div><dt className="text-gray-500">Ca đêm</dt><dd className="mt-1 font-semibold">{count ?? '—'}</dd></div>
        <div><dt className="text-gray-500">Tích 12</dt><dd className="mt-1 font-semibold">{periodic}</dd></div>
        <div><dt className="text-gray-500">Tích 24</dt><dd className="mt-1 font-semibold">{loyalty}</dd></div>
      </dl>
      {status && <p className="mt-3 text-xs text-gray-600">Trạng thái: {status}</p>}
    </article>
  );
}

export function HrNightRewardReconcileMobileRows({ tabFilter, filteredMatched, result, month, statusLabel }) {
  if (tabFilter === 'onlyXn') {
    return (
      <section className="space-y-2" aria-label="Chỉ trong file xí nghiệp">
        <p className="text-xs font-semibold text-orange-900">Chỉ có trong file xí nghiệp · {result.onlyInXN.length} người</p>
        {result.onlyInXN.map((row) => (
          <SingleSideCard key={row.maNV} code={row.maNV} name={row.hoTen} count={row.caDem}
            periodic={amount(row.tich12Rate)} loyalty={amount(row.tich24Rate)} />
        ))}
      </section>
    );
  }
  if (tabFilter === 'onlySys') {
    return (
      <section className="space-y-2" aria-label="Chỉ trong hệ thống">
        <p className="text-xs font-semibold text-blue-900">Chỉ có trong hệ thống tháng {month} · {result.onlyInSystem.length} người</p>
        {result.onlyInSystem.map((row) => (
          <SingleSideCard key={row.sysMaNV} code={row.sysMaNV} name={row.sysHoTen} count={row.sysCaDem}
            periodic={row.sysTich12Months == null ? '—' : `${row.sysTich12Months} th`}
            loyalty={row.sysTich24Months == null ? '—' : `${row.sysTich24Months} th`}
            status={statusLabel(row.sysStatus)} />
        ))}
      </section>
    );
  }
  return (
    <section className="space-y-2" aria-label="Kết quả đối soát">
      {filteredMatched.map((row) => <MatchedCard key={row.xnMaNV} row={row} />)}
      {!filteredMatched.length && (
        <HrEmpty title="Không tìm thấy nhân viên phù hợp" description="Thử thay đổi bộ lọc hoặc từ khóa tìm kiếm để xem danh sách." />
      )}
    </section>
  );
}
