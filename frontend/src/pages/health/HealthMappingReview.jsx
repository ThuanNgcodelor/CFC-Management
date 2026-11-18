export default function HealthMappingReview({ review, onColumn }) {
  return <aside className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm" aria-label="Kiểm tra ánh xạ trước khi nhập">
    <p className="font-medium">Kiểm tra trước khi tạo lô</p>
    <p className="mt-1 text-slate-600">{review.results} cột kết quả · {review.ignored.length} cột bỏ qua. Mẫu bệnh viện có thể đổi cột hoặc bổ sung chỉ tiêu giữa các năm; hãy kiểm tra tên gốc, đơn vị và phương pháp.</p>
    {review.issues.length > 0 && <ul className="mt-2 list-inside list-disc text-amber-800">{review.issues.map((issue) => <li key={issue}>{issue}</li>)}</ul>}
    {[...review.unresolved, ...review.missingIndicators].length > 0 && <div className="mt-2 flex flex-wrap gap-2">
      {[...review.unresolved, ...review.missingIndicators].map((column) => <button key={column.index} type="button" className="max-w-full break-words rounded border border-amber-200 bg-white px-2 py-1 text-left text-amber-900" onClick={() => onColumn(column.index)}>
        Kiểm tra cột {column.letter} · {column.name || 'Không có tiêu đề'}
      </button>)}
    </div>}
    {review.ignored.length > 0 && <details className="mt-2"><summary className="cursor-pointer">Xem {review.ignored.length} cột sẽ không nhập</summary>
      <ul className="mt-1 list-inside list-disc break-words">{review.ignored.map((column) => <li key={column.index}>{column.letter} · {column.name || 'Không có tiêu đề'}</li>)}</ul>
    </details>}
  </aside>;
}
