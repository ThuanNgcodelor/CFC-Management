# Sổ sức khỏe — bàn giao Phase 2 ở dev

Ngày: **11/11/2026**. Đã triển khai và kiểm thử local luồng nhập Excel/rà soát; **không truy cập Fedora, không deploy, không nhập dữ liệu sức khỏe thật vào database**. Phase 1 xem [bàn giao nền](EMPLOYEE_HEALTH_PHASE1_DEV.md).

## Sử dụng

Vào **Nhân sự → Sổ sức khỏe → Nhập dữ liệu**, bằng ADMIN hoặc MANAGER, ở môi trường dev riêng đã bật hai cờ health theo hướng dẫn Phase 1.

1. Chọn/tạo đợt khám đúng năm, cơ sở và khối. Với workbook nhiều năm, xử lý từng đợt; các dòng khác năm được giữ để kiểm tra, không tự đưa sang năm đang chọn.
2. Chọn `.xlsx`. Màn hình liệt kê cả sheet ẩn, gợi ý hàng tiêu đề; có thể sửa hàng bắt đầu 1–100 và ghép 1–3 hàng tiêu đề. Không tự nhập tất cả sheet.
3. Nhập mã nguồn bệnh viện ổn định, dùng lại cho các file bổ sung/các năm. Mã nguồn + SID là danh tính lượt từ cơ sở; đổi mã nguồn tùy tiện sẽ làm mất khả năng phát hiện trùng.
4. Ánh xạ toàn bộ cột: định danh, kết quả, khoảng tham chiếu hoặc **Bỏ qua**. Cột chưa nhận diện không được bỏ âm thầm. Chọn/tạo chỉ tiêu trong danh mục, ghi đơn vị/phương pháp theo nguồn. Cột khoảng tham chiếu phải cùng chỉ tiêu/đơn vị/phương pháp với cột kết quả.
5. Ngày khám chỉ lấy từ cột ngày hoặc ngày chung do quản trị nhập. Chỉ suy từ tiền tố SID khi quản trị đánh dấu đã xác minh quy ước. Thiếu SID: xác nhận rõ một người chỉ có một lượt/ngày ở nguồn này để dùng khóa người/ngày; nếu nhiều lượt cùng ngày thì bổ sung mã lượt riêng vào file và ánh xạ vào SID. Không suy ngày sinh hoặc mặc định ngày khám hiện tại.
6. Có thể đặt tên và lưu mẫu ánh xạ; chỉ hiện các mẫu có đúng fingerprint cấu trúc tiêu đề/cột. Mẫu chỉ áp dụng ánh xạ và mã nguồn, không tự phê duyệt suy ngày/khóa thiếu SID ở file mới.
7. Chọn **Tạo lô và xem trước**: mới lưu vùng chờ, chưa tạo kết quả khám. Xem 20 dòng/trang, mở **Xem đối chiếu** để thấy giá trị cũ/mới và tham chiếu. Cột/chỉ tiêu dài có phân trang riêng.
8. Mã nhân viên chính xác kết hợp tên đã chuẩn hóa và ngày/năm sinh nếu có mới được ghép tự động. Thiếu mã/khác tên/mâu thuẫn thì **Ghép nhân sự**, tìm mã/tên và ghi lý do xác nhận. Không tự tạo/sửa nhân viên HR.
9. Với ô chênh lệch, chọn **Giữ đang lưu** hoặc **Thay bằng file mới**; thay phải có lý do. Ô kết quả trống không xóa kết quả cũ; khoảng tham chiếu trống cũng giữ tham chiếu cũ. Không ép chữ, dấu `<`/`>`, huyết áp hay phân loại thành số/0.
10. Chọn các dòng hợp lệ đã xem và xác nhận theo trang. Mỗi lần là một transaction: nếu một dòng chọn bị lỗi/thay đổi thì không lưu dòng nào trong lần đó. Không tự công bố cho nhân viên.
11. Đóng cửa sổ đối chiếu bằng ESC không mất lô đã tạo. Mở lại từ **Các lô đã tạo trong đợt** để tiếp tục, không cần tải file lại. Sau khi rời tab/refresh, chọn lại đợt rồi mở lịch sử lô; lựa chọn chưa xác nhận trong trình duyệt không lưu bền vững.
12. **Hủy phần chưa nhập** cần xác nhận trên màn hình; xóa payload của dòng còn chờ, giữ kết quả đã nhập và lịch sử. Lô hủy toàn bộ có thể tải lại cùng file để ánh xạ lại. Lô đã nhập một phần không cho remap/ghi đè lịch sử bằng cùng file; sửa nguồn thành file đính chính hoặc nhập file bổ sung và đối chiếu.

Nút xác nhận hàng loạt chỉ chọn dòng hợp lệ **của trang hiện tại**, không ngầm chọn mọi trang. Tổng dòng/đã nhập/còn chờ/đã hủy là toàn lô; lỗi và chênh lệch được hiển thị theo trang đang xem. Lô nhiều năm không được coi tất cả dòng là cùng năm chỉ vì chọn một campaign.

## Nguyên tắc kỹ thuật

- Backend mới nằm trong `com.booking.system.health`; V35 additive bổ sung staging/profile/lịch sử đính chính và provenance. Không thay schema nghiệp vụ HR hoặc auto-migration manifest.
- Kiểm tra ZIP, đọc Excel và SHA256 hoàn tất trước transaction; bean persistence riêng mở transaction ghi. Không gọi repository trong vòng lặp từng nhân viên/kết quả; dữ liệu liên quan lấy theo tập và ghi `saveAll`.
- Hash file/sheet trong cùng đợt chặn tải lại trùng. Import lại bản bổ sung dùng cùng nguồn/SID thì bổ sung chỉ tiêu mới hoặc đưa chênh lệch vào đối chiếu. Retry xác nhận dòng đã lưu chỉ bỏ qua, không tạo bản sao.
- Khóa campaign/batch và nhân viên khi ghi, kết hợp `rowVersion` và token của preview chứa phiên bản dòng/người/lượt/chỉ tiêu/kết quả. Preview cũ bị trả conflict, không âm thầm ghi đè. Giao diện giữ lựa chọn khi lỗi nhưng phải tải lại/chọn lại sau conflict dữ liệu thật.
- Lịch sử thay kết quả lưu giá trị/provenance trước–sau và lý do trong bảng health riêng. Audit chung HR không nhận nội dung khám; không ghi giá trị/người vào log nghiệp vụ. Excel gốc không lưu trên disk/server; payload nguồn đã ánh xạ nằm trong bảng staging riêng để mở lại.
- API vẫn `/api/v1/hr/health/**`, chỉ ADMIN/MANAGER; HR_STAFF bị chặn. Response `Cache-Control: no-store`. Query chỉ trong RAM, có hủy request cũ, không polling, không localStorage/IndexedDB hoặc cache backend.
- Kết quả mới ở trạng thái rà soát; Phase 2 không có API công bố, tra cứu công khai, xuất phiếu, chẩn đoán hoặc so sánh nhiều năm.

## Giới hạn hiện tại

- Chỉ `.xlsx` không mã hóa/không macro, một người trên một dòng; chưa hỗ trợ `.xls`, PDF/OCR hoặc mỗi xét nghiệm một dòng. Chưa hứa tự nhận mọi file bệnh viện 2026.
- Tối đa 10 MB/file, 64 MB giải nén, 2.000 ZIP entry, 30 sheet; mỗi sheet tối đa 2.500 dòng, 256 cột có dữ liệu, 200.000 ô có dữ liệu. Cột chỉ có định dạng trống không tính vào vùng dữ liệu.
- Giữ raw value tối đa 16.000 ký tự/ô; payload/mapping tối đa 60.000 byte UTF-8. Mỗi lần xác nhận tối đa 100 dòng và 10.000 kết quả; UI chọn tối đa 20 dòng/trang.
- Chỉ gợi ý tối đa 10 người/dòng; tìm thủ công trả tối đa 20 người. Nếu dữ liệu mơ hồ/quá nhiều ứng viên thì dùng tìm kiếm cụ thể, không ghép tên gần đúng tự động.
- Chưa chốt TTL/xóa tự động cho staging/lịch sử; hủy chỉ xóa payload đang chờ. Chưa mở dữ liệu thật trước review retention/backup ở Phase 0/8. Chưa có chức năng hoàn tác kết quả đã xác nhận; không cung cấp xóa cứng.
- Không tự chuẩn hóa/chẩn đoán phân loại sức khỏe trong phase này; giá trị chữ gốc vẫn hiện để người quản trị rà soát. Baseline lịch sử và phân loại bất thường thuộc Phase 3.
- Chưa chạy migration/EXPLAIN trên MySQL dev hoặc production; không coi H2 là bằng chứng MySQL. Không triển khai lên server.

## Tự kiểm thử an toàn

Chạy từ root repo, dùng Java/Maven đã cài ở máy dev. Test dùng H2 memory, không Docker, không cấu hình production:

```bash
JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -f backend/pom.xml -Dbooking.build.directory=/tmp/cfc-health-phase2-target test
```

Mặc định test khảo sát file thật bị skip nếu chưa opt-in. Khi ba workbook nguồn còn ở root, có thể đọc/parse **chỉ trong RAM**, không ghi database và không in nội dung người:

```bash
HEALTH_SOURCE_DIRECTORY="$PWD" JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -f backend/pom.xml -Dbooking.build.directory=/tmp/cfc-health-phase2-target \
  -Dtest=HealthSourceWorkbookInspectionTest test
```

Xem UI giả lập, không đăng nhập hoặc gọi backend:

```bash
cd frontend
npm run dev -- --host 127.0.0.1 --port 5195 --strictPort
```

Mở `http://127.0.0.1:5195/tests/health-phase2.html`. Dữ liệu hoàn toàn giả; reload mất fixture. File test không là entry production. Chạy MCP Playwright `browser_run_code_unsafe` với `filename: frontend/tests/health-phase2.playwright.js`; regression Phase 1 dùng file tương ứng Phase 1. Hai bộ kiểm thử UI dùng API giả, độc lập với integration HTTP/backend H2.

## Kết quả 11/11/2026

| Kiểm tra | Kết quả |
|---|---|
| Full backend offline, gồm khảo sát workbook opt-in | 307 test, 0 failure/error, 5 skipped cũ; 302 test thực thi đạt |
| Phase 2 backend integration | 18 test đạt: staging, confirm/retry, duplicate, KEEP/REPLACE/history, stale preview, manual resolution, partial cancel, rollback toàn lần xác nhận, source/SID/date, blank/reference, profile, multipart HTTP, 401/403, validation và EXPLAIN |
| Ba workbook chỉ đọc/parse | 10 sheet gồm sheet ẩn; 2025: 85 dòng/47 cột; Tổng hợp: 464 dòng; bổ sung: 13 dòng/136 cột. Không nhập thật, không quyết định nguồn ưu tiên |
| MCP UI Phase 2 | 13 nhóm tương tác đạt, gồm đổi hàng tiêu đề/cột chưa biết, preview, ghép người/debounce, lỗi giữ lựa chọn, ESC giữ lô, phân trang/mở lại, hủy phần còn chờ và responsive 390 px |
| MCP UI Phase 1 regression | 7 nhóm tương tác đạt |
| Frontend Node | 8 test đạt |
| Frontend lint/build | 0 error, 10 warning baseline, không warning mới; build đạt, chunk HrHealth khoảng 69,3 kB (Phase 1 khoảng 43,8 kB) |

EXPLAIN H2 đã chạy, kết quả rút gọn:

```sql
EXPLAIN SELECT * FROM hr_health_import_rows
WHERE batch_id='fixture' AND state='PENDING' ORDER BY source_row LIMIT 20;
-- /* public.idx_health_row_batch_state: batch_id = 'fixture' AND state = 'PENDING' */

EXPLAIN SELECT * FROM hr_health_imports
WHERE campaign_id='fixture' ORDER BY created_at DESC LIMIT 20;
-- /* public.fk_health_import_campaign_INDEX_2: campaign_id = 'fixture' */
```

H2 dùng index trạng thái dòng mới và index FK campaign hiện hữu. Vẫn cần đo và EXPLAIN trên MySQL với dữ liệu pilot; chưa thêm cache/index phụ vì chưa có đo đạc cần thiết. Phase 3 tiếp theo là baseline lịch sử được duyệt; không mặc nhiên chọn Tổng hợp thắng sheet con hoặc công bố dữ liệu từ bước nhập.
