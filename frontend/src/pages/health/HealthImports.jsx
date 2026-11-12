import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { healthImportApi } from '../../api/healthImportApi';
import { HrError, HrLoading, HrPagination } from '../../components/hr/HrUi';
import { Button } from '../../components/ui/Button';
import HealthCatalogPicker from './HealthCatalogPicker';
import { healthError } from './healthImportUi';
import HealthImportMapping from './HealthImportMapping';
import HealthImportReview from './HealthImportReview';

export default function HealthImports() {
  const [campaign, setCampaign] = useState(null);
  const [picker, setPicker] = useState(false);
  const [batch, setBatch] = useState(null);
  const [page, setPage] = useState(0);
  const client = useQueryClient();
  const history = useQuery({ queryKey: ['health', 'import-batches', campaign?.id, page], enabled: Boolean(campaign),
    queryFn: ({ signal }) => healthImportApi.batches(campaign.id, page, signal) });
  const changed = async (value) => { setBatch(value); await client.invalidateQueries({ queryKey: ['health', 'import-batches', campaign.id] }); };
  return <div className="space-y-4">
    <div className="flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <p className="flex-1 text-sm">{campaign ? <><strong>{campaign.code} — {campaign.name}</strong><span className="block text-slate-500">Năm {campaign.reportYear} · {campaign.provider}</span></> : 'Chọn hoặc tạo đợt khám trước khi nhập file.'}</p>
      <Button variant="secondary" onClick={() => setPicker(true)}>Chọn đợt khám</Button>
      {batch && <Button variant="secondary" onClick={() => setBatch(null)}>Nhập file khác</Button>}
    </div>
    {campaign && <>
      {batch ? <HealthImportReview key={batch.id} batch={batch} onChanged={changed} /> : <HealthImportMapping key={campaign.id} campaign={campaign} onStaged={changed} />}
      <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
        <h2 className="font-semibold">Các lô đã tạo trong đợt</h2><p className="text-sm text-slate-500">Có thể mở lại lô để tiếp tục rà soát mà không cần tải file lên lần nữa.</p>
        {history.isPending ? <HrLoading /> : history.isError ? <HrError message={healthError(history.error)} onRetry={history.refetch} /> : <>
          {history.data.content.map((item) => <button key={item.id} type="button" className="block w-full rounded-lg border border-slate-200 p-3 text-left text-sm hover:bg-emerald-50" onClick={() => setBatch(item)}>
            <strong>{item.sheet}</strong> · {item.totalRows} dòng · Đã nhập {item.importedRows} · Còn chờ {item.pendingRows} · Đã hủy {item.totalRows - item.importedRows - item.pendingRows}{item.status === 'CANCELLED' ? ' · Đã hủy' : ''}
          </button>)}
          <HrPagination page={page} totalPages={history.data.totalPages} totalElements={history.data.totalElements} loading={history.isFetching} onPageChange={setPage} />
        </>}
      </section>
    </>}
    {picker && <HealthCatalogPicker type="campaigns" onClose={() => setPicker(false)} onSelect={(value) => { setCampaign(value); setBatch(null); setPage(0); }} />}
  </div>;
}
