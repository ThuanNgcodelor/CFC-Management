# Sổ sức khỏe — bàn giao Phase 5 ở dev

Ngày **11/11/2026**. Đã triển khai báo cáo thống kê và xuất Excel/PDF nội bộ trong module health. Chỉ chạy dev; không deploy, không ghi server/production, không công bố kết quả hoặc mở cổng tra cứu.

## Cách kiểm tra

Mở **http://127.0.0.1:5195/manager/hr/health?tab=records** khi preview dev đang chạy.

1. **Tập thể:** tìm mã/tên, chọn nhóm hiện tại, tình trạng có/chưa có kết quả và sort. Chọn khoảng năm xuất rồi bấm **Thống kê và xuất theo bộ lọc**. Đọc số người/lượt/kết quả và mẫu số trước khi tải Excel. File gồm toàn bộ người trong phạm vi, không chỉ trang đang xem. Muốn toàn bộ nhân sự trong khoảng, xóa từ khóa/nhóm và chọn “Tất cả hồ sơ trong phạm vi”.
2. **Cá nhân:** mở một sổ, áp dụng khoảng năm → **Xuất báo cáo cá nhân** → tải Excel hoặc PDF. Tất cả lượt trong khoảng được giữ riêng, kể cả nhiều lượt cùng năm.
3. **Chọn lượt:** tích các lượt cần đối chiếu → **Báo cáo các lượt đã chọn**. Có thể xuất một lượt; so sánh dùng 2–6 lượt của cùng người. Nút chọn lượt luôn giữ chỗ trên trang để không đẩy bảng khi tích checkbox.
4. ESC đóng báo cáo, giữ tìm/lọc/sort/lượt chọn. Lỗi tải hiện thông báo tiếng Việt; có thể bấm tải lại mà không import lại hoặc mất bộ lọc. Đóng báo cáo hủy request phía trình duyệt.

Khoảng xuất tập thể là phạm vi riêng được ghi ngay trên màn/file: nếu bộ lọc danh sách có năm thì khởi tạo từ năm đó; nếu không có thì mặc định năm hiện tại và ba năm trước. Người dùng có thể sửa khoảng xuất (tối đa 10 năm). Bộ lọc dữ liệu có/chưa có được tính lại trong khoảng xuất, không dựa vào trang danh sách. Thống kê và file được tạo bằng cùng một luồng tính, nhưng tại hai thời điểm riêng: nếu có người sửa kết quả giữa hai lần, tải lại thống kê để đối chiếu.

## Nội dung và ý nghĩa báo cáo

Excel có sáu sheet:

| Sheet | Nội dung |
| --- | --- |
| Pham vi | Năm, bộ lọc, lựa chọn lượt và giới hạn diễn giải |
| Thong ke | Người/lượt/kết quả, theo năm, nhóm hiện tại, phân loại theo lượt, thiếu ngày/metadata; mỗi chỉ số có mẫu số và cơ sở |
| Nhan su | Nhận diện, ngày sinh ISO, nhóm hiện tại và số lượt trong phạm vi; giữ người chưa có lượt nếu chọn ALL/WITHOUT_DATA |
| Luot kham | Từng lượt, năm, ngày khám khi có, SID/nguồn, phân loại raw và số kết quả |
| Ket qua | Từng giá trị raw, đơn vị/phương pháp/tham chiếu, sheet/dòng/cột/tiêu đề nguồn, gắn đúng người/lượt |
| So sanh | Dạng bảng dài, lọc được theo người/chỉ tiêu/lượt. Người có 2–6 lượt được so sánh riêng, không so chéo người; giữ ô thiếu và nguyên tắc số học Phase 4 |

Người có hơn 6 lượt vẫn xuất đủ dữ liệu chi tiết; muốn bảng so sánh hãy chọn cụ thể tối đa 6 lượt trong hồ sơ cá nhân. Chênh lệch lưu chuỗi thập phân chính xác, không làm tròn thành số Excel gây mất độ chính xác. Giá trị lâm sàng luôn là ô STRING, kể cả `=...`, `+...`, dấu giới hạn hoặc huyết áp; không sinh công thức từ nguồn. Ngày hiển thị ISO, không suy ngày từ năm/SID.

PDF là **báo cáo cá nhân được tạo mới**, không phải phiếu bệnh viện hay file gốc nhiều người. Font Be Vietnam Pro được nhúng từ tài nguyên đã có, nội dung dài xuống dòng/chuyển trang, có số trang; không cắt kết quả khi dài. Ký tự chưa được font hỗ trợ được báo lỗi và hướng sang Excel để giữ nguyên nguồn.

Không có dữ liệu không đồng nghĩa chưa khám hoặc sức khỏe bình thường. Phân loại thống kê theo từng lượt: I–V rõ ràng; thiếu và giá trị cần rà soát tách riêng, raw vẫn ở danh sách lượt. Chưa có snapshot đơn vị tại ngày khám nên báo rõ chưa thống kê được theo đơn vị. Độ đầy đủ chỉ đo metadata đang có, không suy thiếu gói khám khi chưa có danh mục xét nghiệm bắt buộc. Không chẩn đoán hoặc đánh giá tốt/xấu từ chênh lệch.

## Quyền, hiệu năng và lưu trữ tạm

- POST `/api/v1/hr/health/reports/preview`, `/excel`, `/pdf`: ADMIN/MANAGER, `@Valid`, service guard và no-store; HR_STAFF/khách bị chặn. PDF bắt buộc một người; lượt chọn phải đúng người và khoảng năm, không trùng ID.
- Một snapshot projection trong transaction read-only REPEATABLE_READ; truy vấn theo tập, không N+1. Sau khi transaction kết thúc mới tạo tệp. Không tạo công bố, sửa kết quả/staging hoặc nối tài liệu/feed HR chung.
- Giới hạn: **500 người, 2.000 lượt, 20.000 kết quả, 60.000 ô so sánh, 10 năm; 16 MiB text nguồn; 20 MiB tệp**. Ô quá 32.767 ký tự bị từ chối rõ, không cắt dữ liệu. PDF **1.000 kết quả / 200 trang**. Vượt giới hạn yêu cầu thu hẹp phạm vi.
- Mỗi instance chỉ render một tệp báo cáo Phase 5 đồng thời; yêu cầu khác nhận 409 với hướng dẫn thử lại, không xếp hàng giữ nhiều snapshot trong RAM. Đây là giới hạn tài nguyên, không phải cache.
- Excel ghi tuần tự SXSSF (cửa sổ 50 dòng), inline strings; không thay TempFile strategy toàn tiến trình. Spool riêng dưới thư mục tạm của JVM: `cfc-health-export-*`, mode 0700, file gzip 0600, dọn cả khi xuất lỗi. Tệp cuối trả về RAM, không tạo kho download dài hạn. Nếu tiến trình bị kill/mất điện, thư mục tạm có thể còn; cần kiểm tra/dọn khi dừng app trước rollout. Cần filesystem POSIX hỗ trợ quyền riêng.
- UI dùng Query health riêng, signal hủy request, không polling/persist/backend cache. Lỗi Blob JSON được giải mã để hiển thị thông báo nghiệp vụ.

## Kiểm chứng và giới hạn nghiệm thu

- Full backend offline H2: **339 test, 0 failure/error, 5 skip cũ**; 334 test thực chạy. Các test mới gồm scope, cùng năm/nhiều lượt/thiếu năm, dữ liệu raw/nguồn, formula injection, font PDF, quyền/ownership, giới hạn, chỉ một export đồng thời và cleanup spool khi lỗi.
- Snapshot tập thể dùng **3 câu SQL** trong fixture, không tăng theo số người/lượt. Chạy API dev thật đọc được 398 định danh, 83 người/83 lượt, 2.216 kết quả; xuất Excel tập thể và PDF cá nhân qua nút tải trình duyệt thành công. Không tự duyệt thêm 936 dòng nguồn đang chờ.
- **9 nhóm UI Phase 5** đạt: lọc tập thể/toàn trang, thông báo lỗi và retry, Excel/PDF, 390 px, lượt chọn, ESC giữ ngữ cảnh. **12 nhóm regression Phase 4** đạt. Thử lỗi HTTP 500 có chủ đích trong fixture, không phải lỗi còn tồn tại của API dev.
- Build/lint đạt: **0 lỗi, 10 warning cũ**; chunk HrHealth **96,47 kB** so với Phase 4 **90,37 kB**, không thêm dependency. Hai test Node quyền/URL đạt.
- Benchmark giả lập 500 người/2.000 lượt/20.000 kết quả/20.000 ô so sánh: XLSX khoảng **2,36 MB**. Sau chuyển streaming, khoảng **1,7 giây** trong lượt test ấm. Chạy riêng JVM **`-Xmx256m`** vẫn đạt, khoảng **2,54 giây**, tổng đỉnh pool heap đọc được khoảng **187 MiB**. Đây là phép đo cục bộ, không là cam kết tốc độ production; tổng đỉnh các pool không phải RSS hay số đo chính xác bộ nhớ tăng thêm. Cách XSSF ban đầu tốn RAM cao đã được thay, không giữ lại trong sản phẩm.
- POI mở/kiểm tra workbook nhỏ, SAX đọc workbook lớn đủ dòng; PDFBox đọc nội dung tiếng Việt/nhiều trang và Poppler render ảnh kiểm tra được. **Chưa có Excel/LibreOffice cài ở dev để nghiệm thu bằng ứng dụng desktop đó**; cần người dùng mở và duyệt mẫu trước bổ sung ZIP hoặc định dạng khác.
- Không có migration/config env/dependency mới. Chưa nghiệm thu MySQL thực hoặc filesystem khác POSIX; Phase 6 công bố và Phase 7 tra cứu cá nhân chưa triển khai.

Ảnh và tệp demo trong `/tmp/health-phase5-fixtures`, `/tmp/health-phase5-*-fixture.png`, `/tmp/health-phase5-pdf-preview.png` chỉ chứa dữ liệu giả. File tải với dữ liệu thật giữ riêng trong thư mục dev private, không commit hoặc gửi công khai.
