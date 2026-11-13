# Sổ sức khỏe — bàn giao Phase 4 ở dev

Ngày: **11/11/2026**. Đã có hồ sơ, timeline và so sánh nội bộ trên dev, dùng khung giao diện CFCBase. **Không deploy, không ghi production, không tự xác nhận liên kết của Phase 3 và chưa mở tra cứu cá nhân.**

## Mở và kiểm tra

**http://127.0.0.1:5195/manager/hr/health?tab=records**

1. Chọn tab **Hồ sơ sức khỏe**. Mặc định hiển thị người có kết quả đã nhập; có thể tìm mã/tên, chọn năm, nhóm HR hiện tại, có/chưa có dữ liệu và sắp xếp mã/tên.
2. Mở một người: xem năm có/chưa có dữ liệu và toàn bộ lượt trong khoảng tối đa 10 năm. Nhiều lượt cùng năm giữ riêng; không tự chọn một lượt đại diện.
3. **Xem kết quả lần khám**: tìm chỉ tiêu, đọc giá trị gốc, đơn vị/phương pháp, tham chiếu và sheet/dòng/cột/tên trường nguồn. Phân trang 20 kết quả; drawer rộng theo viewport, bảng cuộn bên trong khi cần.
4. Chọn 2–6 lượt của cùng người → **So sánh**. Mỗi cột là một lượt, mỗi hàng là một chỉ tiêu/đơn vị/phương pháp; ô thiếu ghi **Không có dữ liệu**.
5. Chỉ hàng NUMBER đủ điều kiện mới có **Xem xu hướng số học** và chênh lệch cuối − đầu. Không gắn tăng/giảm là tốt/xấu, không chẩn đoán từ biểu đồ.
6. ESC đóng drawer, giữ hồ sơ/lựa chọn lượt; nút quay lại hoặc Back/Forward trình duyệt giữ tìm kiếm, sort và trang danh sách. Mở người khác xóa lựa chọn lượt/kết quả của người trước.

Chọn **Nhập dữ liệu → HISTORY_2025 → Gián tiếp → Chỉ dòng còn chờ rà soát** để xem ngay 2 dòng năm 2025 chưa ghép. Nguồn ngày/năm sinh được hiển thị ở đối chiếu/ghép người; không biến năm sinh thành ngày 01/01. Việc chọn người và lý do vẫn cần quản trị xác nhận.

## Trạng thái dữ liệu thật

- DB dev riêng hiện có **83 hồ sơ/83 lượt năm 2025**, 2.216 kết quả. Phase 4 chỉ đọc chúng, không tạo thêm người/lượt/kết quả/công bố.
- **936 dòng nguồn staging còn chờ** ở Phase 3, gồm dòng lặp giữa sheet. Năm 2023/2024 theo SID đã được người dùng xác nhận cho 921 dòng lịch sử; chưa suy ngày khám và chưa tự xác nhận người thiếu mã.
- Vì lịch sử thật 2023–2024 còn ở staging, hồ sơ hiện có thể chỉ thấy năm 2025. Đây không phải mất dữ liệu. Chỉ sau khi duyệt liên kết và xác nhận import thì timeline mới có các lượt lịch sử tương ứng.
- Dữ liệu nhân sự sao chép tối thiểu không có snapshot nhóm/đơn vị lịch sử. Bộ lọc nhóm dùng `workforceGroup` HR hiện tại; nhiều hồ sơ dev vẫn “Chưa xác định”. Không kéo thêm trường từ production hoặc gán văn phòng/công nhân theo phỏng đoán.
- Các chỉ tiêu baseline chưa xác minh đang là TEXT và thiếu đơn vị/phương pháp chuẩn: giữ raw để đối chiếu, không ép thành NUMBER để vẽ biểu đồ. QA biểu đồ nhiều năm dùng fixture giả riêng, không thêm số liệu demo vào DB thật.
- Bộ Excel tổng hợp không có PDF/ảnh phiếu khám riêng. Màn kết quả báo rõ chưa có tệp cá nhân; chưa xây upload/download kho tệp hoặc dùng tài liệu HR chung. Xuất Excel/PDF chính thức thuộc Phase 5; công bố và cổng cá nhân thuộc phase sau.

## Quy tắc so sánh

| Trường hợp | Hành vi |
| --- | --- |
| Cùng chỉ tiêu nhưng khác đơn vị/phương pháp | Giữ thành hàng riêng theo valueKey, không tự đổi đơn vị hoặc nối chuỗi |
| Thiếu đơn vị hoặc phương pháp | Đặt raw cạnh nhau, ghi cần đối chiếu, không tính chênh lệch/vẽ |
| Ô thiếu / không thuộc gói / chưa nhận file | Chỉ biết thiếu dữ liệu; không gán 0/bình thường hoặc suy lý do chưa khám |
| `<0.5`, `>=12`, `120/80`, `1,000` | Giữ chuỗi nguyên gốc; không ép thành một số chính xác |
| NUMBER, số dấu chấm <=15 chữ số có nghĩa, metadata đầy đủ | Cho chênh lệch số học và biểu đồ; giới hạn chữ số để không làm sai qua JavaScript Number |
| Nhiều lượt cùng năm thiếu ngày hoặc cùng ngày | Không tự sắp theo thời điểm import; chưa có thứ tự đủ rõ thì không vẽ xu hướng |
| Hai năm khác nhau, chưa có ngày khám | Chỉ dùng thứ tự năm đã xác nhận, không tạo ngày; nhãn vẫn báo chưa có ngày |
| Phân loại sức khỏe | Hiển thị raw từ nguồn; nếu nhiều giá trị khác nhau thì ghi cần rà soát, không chọn giá trị “cao nhất” |

Khoảng tham chiếu luôn gắn với ô/lượt nguồn; chưa suy cờ bệnh lý hoặc dùng khoảng tham chiếu của lượt khác. Biểu đồ theo lượt được chọn, không tạo điểm cho năm thiếu dữ liệu. Khi có nhiều lượt cùng năm và ngày rõ, nhãn dùng ngày để phân biệt.

## Bảo vệ dữ liệu và hiệu năng

API riêng `/api/v1/hr/health/records` chỉ ADMIN/MANAGER, service kiểm tra quyền lại; HR_STAFF/khách bị chặn. Kết quả/timeline/compare đều kiểm tra lượt thuộc đúng người, không trả một phần dữ liệu khi trộn ID người khác. POST compare chỉ đọc, có `@Valid`, 2–6 ID khác nhau, không có thao tác ghi hay công bố.

Các truy vấn nằm trong repository, dùng projections, phân trang/lọc/sort ở DB và batch theo tập lượt/chỉ tiêu. Không fetch toàn bộ dữ liệu sức khỏe vào frontend để lọc. Service read-only, không N+1 theo nhân viên; test đo danh sách tối đa 2 SQL và timeline tối đa 5 SQL dù số người/lượt tăng.

- API size 1–100, page 0–10.000; UI size 20, comparison tối đa 6 cột; khoảng timeline tối đa 10 năm.
- Query health chỉ trong RAM, gcTime/staleTime 0, không polling/persist/localStorage/IndexedDB/cache backend. Signal hủy response cũ; không hiện dữ liệu người cũ thay dữ liệu đang tải.
- URL giữ ngữ cảnh ID/filter/sort/page/selection; không đặt CCCD/token/kết quả lên URL. Không trả entity HR đầy đủ hoặc đưa dữ liệu vào DTO/feed/tài liệu HR chung.
- Mọi response thành công `Cache-Control: no-store`; lỗi body/bộ lọc qua health exception handler JSON. Không đổi đăng nhập hoặc quyền HR chung.
- Không thêm dependency, migration, index, broker hoặc cache. Biểu đồ SVG nhỏ; HrHealth chunk khoảng **90,37 kB**, Phase 3 khoảng 71,86 kB, vẫn dưới 500 kB. Lấy số chính xác từ log build bàn giao nếu thay tiếp.
- Drawer mới dùng opt-in `size=full`, tối đa 1600 px; standard/wide của màn cũ giữ kích thước cũ.

## Tự kiểm thử

Từ root repository, Java/Maven dev hiện có; test dùng H2 memory, không Docker hoặc API production:

```bash
HEALTH_SOURCE_DIRECTORY="$PWD" JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -f backend/pom.xml -Dbooking.build.directory=/tmp/cfc-health-phase4-target test

cd frontend
npm run lint
npm run build
node --test src/utils/*.test.js src/api/hrApiUtils.test.js
```

Bỏ `HEALTH_SOURCE_DIRECTORY` nếu chỉ muốn fixture giả; hai bài khảo sát workbook thật sẽ skip, không tự import chúng. UI fixture độc lập ở `/tests/health-phase4.html?tab=records`, chạy MCP Playwright file `frontend/tests/health-phase4.playwright.js`; fixture không gọi API thật, tên/kết quả đều giả. Các screenshot `/tmp/health-phase4-*-fixture.png` chỉ chứa fixture, không chụp hồ sơ người thật để làm tài liệu public.

| Kiểm tra | Kết quả dev |
| --- | --- |
| Backend Phase 4 | 9 bài đạt: paging/filter/sort, năm thiếu/nhiều lượt, raw/reference/source, ownership, metadata/missing/giá trị giới hạn, thứ tự ngày, N+1, phân loại xung đột, RBAC/no-store/validation |
| Backend Phase 3 bổ sung | State filter tại DB, mặc định ALL tương thích; raw ngày/năm sinh giữ trong DTO; các bài staging/import cũ vẫn chạy |
| Full backend opt-in đọc nguồn | 327 test, 0 failure/error, 5 skipped baseline; 322 bài thực thi đạt |
| MCP UI Phase 4 | 12 nhóm đạt: debounce, sort/page, nhiều lượt/năm thiếu, raw/reference, thiếu dữ liệu, biểu đồ, 390 px, quay lại/Back/Forward, response đảo thứ tự |
| UI regression | Phase 1: 7 nhóm; Phase 2: 13 nhóm; Phase 3: 5 nhóm đạt, gồm pending filter; không sửa dữ liệu thật |
| API dev thật | 83 hồ sơ, page 20, no-store; mở timeline/kết quả và tìm Phân loại được; 390 px không tràn, không pageerror |
| Frontend | Lint 0 error/10 warning baseline, build đạt, Node 8 bài đạt |

Đã backup DB riêng trước khi nâng preview; không chạy lại bootstrap/confirm để tạo thêm dữ liệu. Cổng nghiệm thu lịch sử thật vẫn thuộc quản trị Phase 3. Chưa chạy MySQL/EXPLAIN/production rollout hoặc mở tra cứu Internet.
