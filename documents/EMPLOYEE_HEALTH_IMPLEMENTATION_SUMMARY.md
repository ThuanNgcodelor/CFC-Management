# Tổng hợp triển khai Sổ sức khỏe nhân viên

Cập nhật 18/11/2026. **Phase1–7 có bản triển khai dev**, bao gồm hai cách tra cứu vừa được người dùng duyệt. Phân biệt hoàn tất chức năng với nghiệm thu dữ liệu: baseline Phase3 còn dòng chờ; mẫu xuất Phase5 còn cần mở Excel/LibreOffice duyệt; Phase8 đã nghiệm thu kỹ thuật dev/MySQL/HTTP; Phase9 có phần chuẩn bị tiếp nhận2026 bằng dữ liệu giả. Rollout/pilot thật và file2026 chưa nghiệm thu. Không commit/push/deploy server hoặc công bố kết quả thật trong lần này.

## Toàn bộ phần đã làm

| Phase | Đã triển khai | Còn cần review |
|---|---|---|
| 0 — Phân tích | Đọc6nguồn Excel/Markdown 2023–2025; phân biệt trực tiếp/công nhân và gián tiếp/văn phòng; chọn Tổng hợp làm nguồn đầu, giữ chênh lệch; chốt ADMIN/MANAGER và hai cách tra cứu | Retention/backup/policy vận hành và nghiệm thu lịch sử |
| 9 — Sẵn sàng 2026 | Review ánh xạ trước nhập; bộ5 file giả, demo một lệnh trong khung CFCBase;6 bài kiểm tra luồng2026 trên H2/MySQL; so sánh và xuất cá nhân/tập thể | Người dùng xác nhận chưa có file2026; chờ cấu trúc/dữ liệu bệnh viện thật |
| 8 — Nghiệm thu kỹ thuật dev | MySQL 8.0.46: 32 bài API/migration, nâng V33→V39, restore 79 bảng; HTTP thật 14 nhóm, bộ test gate OFF, CI job MySQL | HTTPS/Cloudflare/Fedora, chính sách lưu trữ, file desktop và pilot thật chưa nghiệm thu |
| 1 — Nền tảng | Module backend health riêng, danh mục đợt/chỉ tiêu/alias, schema, API, menu trong khung CFCBase, phân quyền/audit riêng, feature flags defaultOFF | MySQL dev đã kiểm tra ở Phase8; production chưa nghiệm thu |
| 2 — Import | Sheet/tiêu đề nhiều hàng, ánh xạ cột và profile theo cấu trúc, staging/preview phân trang, ghép nhân viên, hash/SID chống trùng, xác nhận từng phần/tiếp tục lô, KEEP/REPLACE có lý do và lịch sử, hủy pending | Không hứa đọc được mọi định dạng bệnh viện; mẫu2026 cần dry-run |
| 3 — Lịch sử | Công cụ dry-run/phân tích và H2 riêng, clone nhân sự tối thiểu đã được phép, xác nhận năm2023/2024 theoSID không suy ngày, nhập83lượt2025 khớp chắc chắn | 936dòng nguồn pending không phải936người; tên/mã/xung đột còn chờ quản trị duyệt |
| 4 — Hồ sơ nội bộ | Tìm mã/tên, lọc/năm/nhóm/phân trang, Back/Forward giữ trạng thái; timeline/nhiều lượt cùng năm, raw/nguồn/tham chiếu; so sánh2–6lượt và biểu đồ số học đủ điều kiện | Dữ liệu thật2023–2024 chưa đủ hồ sơ đã ghép; không có kho ảnh/PDF bệnh viện nếu nguồn chưa cung cấp |
| 5 — Báo cáo/xuất | Thống kê số người/lượt/phân loại/độ đầy đủ với mẫu số, Excel cá nhân/tập thể theo bộ lọc nhiều năm, PDF cá nhân; giữ dữ liệu gốc, bảo vệ formula injection, giới hạn RAM/tệp/private spool | Duyệt mẫu desktop; chưa có snapshot đơn vị lịch sử thì báo rõ không suy dữ liệu |
| 6 — Công bố | Hàng đợi/preview cố định phạm vi, rà soát rõ ràng, công bố một/nghiều người, thành công từng phần, snapshot revision bất biến, sửa cần công bố lại; lịch sử/reason/audit, thu hồi không fallback; khóa/mở đợt riêng | 0kết quả thật đã công bố; cần pilot có người dùng duyệt |
| 7 — Cá nhân | Trang riêng không cần tài khoản/Telegram/SMS; hai lựa chọn tra cứu; phiên riêng15phút; lịch sử/chi tiết/tìm/so sánh/PDF của chính mình; ADMIN/MANAGER cấp/cấp lại/thu hồi mã và bật/tắt cách hai trường | Chưa mở Internet/dữ liệu thật, cần kiểm tra HTTPS/proxy/rate-limit/MySQL/retention ởPhase8 |

## Hai cách tra cứu hiện tại

1. **CCCD + mã nhân viên:** không yêu cầu mã riêng, chỉ chấp nhận khi quản trị đã cho phép trên quyền tra cứu còn hiệu lực của người đó.
2. **Mã tra cứu riêng:** chỉ nhập mã được cấp, không cần CCCD hoặc mã nhân viên; giữ mức yêu cầu này cho grant cũ/mới theo mặc định.

Quản trị vào Sổ sức khỏe → Hồ sơ sức khỏe → mở người → **Quyền và mã tra cứu sức khỏe cá nhân**. Cấp quyền/mã lần đầu; mã chỉ hiện một lần để giao riêng. Nhập lý do rồi bật **Cho phép CCCD + mã nhân viên** nếu muốn người đó dùng cả hai cách. Rút lại qua **Chỉ cho phép dùng mã riêng**. Người biết hai định danh có thể tra cứu khi cách1 bật; thêm cách2 không làm cách1 an toàn như mã bí mật.

Đổi policy/cấp lại/thu hồi kết thúc phiên cũ. Cấp lại mã quay về yêu cầu mã riêng. Thu hồi hoặc hết hạn chặn cả hai. Không tự cho phép cách1 chỉ vì người dùng chọn radio; backend xác minh policy. Không gửi mode thì giữ hành vi cũ là yêu cầu mã. Sai mã không fallback.

## Dữ liệu và giới hạn

Mốc dữ liệu dev đã đối chiếu ởPhase6:398nhân sự tối thiểu để ghép,10lô/1.019dòng nguồn,549SID,83người/83lượt2025,2.216kết quả;936dòng nguồn chờ;0publication. SID/dòng không bằng số người duy nhất. Không kéo thêm CCCD/contact/lương thật để làm demo.2026 chưa có đợt khám thật; không tạo dữ liệu lấp chỗ trống.

Thiếu kết quả không thành0/bình thường/chưa khám; không suy chẩn đoán. Chỉ so sánh số khi cùng chỉ tiêu/đơn vị/phương pháp và thứ tự thời gian đủ rõ. Cá nhân chỉ thấy snapshot công bố mới nhất của mình, không staging/biên tập/nguồn ô/audit/phiếu tập thể. File tải về không thể xóa khỏi máy người dùng bằng thu hồi trên hệ thống.

## Demo dev

- `http://127.0.0.1:5195/tests/health-phase7.html`: CCCD giả `000000000001`, mãNV `DEMO-SELF`; cách1 không cần32chữX. Cách2 chỉ nhập mã giả32chữX, không nhập CCCD/mã nhân viên. Toàn bộ dữ liệu làfixture, không gọi API production.
- `http://127.0.0.1:5195/tests/health-phase7-access.html`: mô phỏng quản trị cấp/ẩn/cấp lại/thu hồi và cho phép/rút cách1.
- Trang ứng dụng thật là `/health-lookup`. Backend cần cả hai gate health/self-service bật tại dev và migrationV38. Chưa bật hay nâng DB baseline thật trong lần sửa này.

## Chưa triển khai/nghiệm thu

- Phase8 còn: HTTPS+Cloudflare/IP thật cho limiter, tải thực nhiều người, retention/backup vận hành, duyệt file desktop, pilot và rollout. MySQL/API/EXPLAIN/restore giả đã kiểm tra; xem [bàn giao Phase8](EMPLOYEE_HEALTH_PHASE8_DEV.md).
- Phase9 còn nghiệm thu: nhận mẫu thật2026 và nhập/công bố theo dữ liệu được duyệt. Phần chuẩn bị đã triển khai; [hướng dẫn chạy demo và kiểm thử](EMPLOYEE_HEALTH_PHASE9_DEV.md).
- Không có tích hợp Bộ Công an/VNeID/cổng sức khỏe quốc gia, bệnh viện, chẩn đoán AI, kê đơn hoặc dịch vụSMS.

Chi tiết nghiệp vụ và bằng chứng từng phase: [kế hoạch](EMPLOYEE_HEALTH_PLAN.md), [Phase7dev](EMPLOYEE_HEALTH_PHASE7_DEV.md), [phân tích nguồn](EMPLOYEE_HEALTH_DATA_ANALYSIS.md).

## Kiểm thử cuối sau hai lựa chọn

Full backend offline/H2 **385test,0lỗi,5skipcũ**; frontend build đạt, lint0lỗi/10warningcũ; Node quyền1/1. MCP kiểm tra10nhóm hai cách tra cứu và12nhóm regressioncá nhân đạt. Đây là bằng chứng dev, không thay nghiệm thu MySQL hoặc production. Chi tiết hạn mức/proxy/lưu trữ trong [CONFIG_AND_CACHE.md](CONFIG_AND_CACHE.md).

Hiệu chỉnh cuối: cách2 chỉ một mã riêng (không còn ba trường); full backend387test/0lỗi/5skipcũ, build/lint và browser đạt. V39 unique hash index đã có H2 EXPLAIN; MySQL/server chưa nghiệm thu. Tổng hợp chức năng Phase1–7 ở trên giữ nguyên phạm vi dev.


## Nghiệm thu kỹ thuật Phase 8 — 12/11/2026

Backend toàn bộ: **420 test, 0 failure/error, 37 skip** (32 MySQL opt-in chạy riêng, 5 skip cũ). MySQL riêng: **32/32 đạt**, không skip. Frontend build đạt; lint 0 lỗi/10 cảnh báo cũ. E2E trang thật với API HTTP loopback: **14 nhóm đạt**; backup/restore QA có 3 publication, 3 kết quả và 1 grant, so count/checksum cả 79 bảng khớp. Không ghi baseline lịch sử hay production, chưa chạy CI mới trên GitHub. [Hướng dẫn và giới hạn Phase8](EMPLOYEE_HEALTH_PHASE8_DEV.md).


## Hoàn thiện dev và kiểm tra MCP18/11/2026

Đã hoàn thiện bảng kiểm tra ánh xạ (cột mới/chỉ tiêu thiếu/cột bỏ qua), demo một lệnh và bộ nguồn giả2024–2026. Sửa lỗi403 của API health bị đổi401 qua servlet error dispatch, có regression HTTP thật. Full backend433test/0lỗi/43skip (38MySQL chạy riêng và5skipcũ); MySQL38/38 +restore79bảng;29Node;45deploy+3Python;build/lint đạt(10warningcũ). MCP12bộ/114nhóm đạt, có ảnh desktop/mobile. Xem [bàn giaoPhase9](EMPLOYEE_HEALTH_PHASE9_DEV.md). Không sửa/duyệt936dòng pending thay quản trị; chưa deploy/server hoặc công bố thật.
