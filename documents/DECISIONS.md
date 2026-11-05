# Decisions Log (BookingBase / HR)

Append-only log of technical decisions. Newest at the bottom of each section. Before reversing a decision here, ask the user. Add an entry whenever you choose or remove a library/dependency, change a rule in AGENTS.md, or pick an approach others must follow.

Entry format: `date | decision | reason | status`

## Decided

- 2026-11-02 | Bỏ qua toàn bộ `/graphify-out/` trong Git theo yêu cầu. | Bản đồ và cache là artifact sinh lại được, dùng local; tránh đưa cache lớn vào repository.

- 2026-11-02 | Tạo snapshot bản đồ tri thức bằng Graphify theo yêu cầu; giữ CodeGraph cho tra cứu source theo AGENTS.md. Graphify chạy trong môi trường Python tạm, không thêm dependency runtime, hook hay dịch vụ vào ứng dụng. | Phục vụ khám phá quan hệ giữa mã nguồn, schema và tài liệu; snapshot không thay thế source hoặc kiểm thử nghiệp vụ.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-07-27 | Only the HR domain is under active development; Booking room/car and approval are frozen | Product focus; shared Auth/Notification/PWA stay in place |
| 2026-10-20 | Remove `clean_database_master.sql` from the docs | File does not exist; schema source of truth is entities + Flyway migrations (V1 to V28) |
| 2026-10-20 | New screens use TanStack Query for server state; old screens migrate only when a task touches them | Replaces hand-written fetch + sequence guards (184 `useEffect` fetches today) without a risky mass refactor |
| 2026-10-20 | Existing sequence guards (`shiftsSeq`, `summariesSeq`) stay until their screen moves to Query | Avoid regressions on the shift screens |
| 2026-10-20 | Caching: default no cache; if needed Spring Cache + Caffeine, only for config-like data, every cache registered in `CONFIG_AND_CACHE.md` | Matches the "no Redis just because" rule; wrong cache on DRAFT data would show stale attendance |
| 2026-10-20 | Heavy client libraries (`xlsx`, `xlsx-js-style`) must be loaded with dynamic `import()`; new chunk limit 500 kB | `HrNightRewards` chunk is 914 kB because of a static import |
| 2026-10-20 | Rules in AGENTS.md are in English; business descriptions stay in Vietnamese | AI follows English rules more closely |
| 2026-10-20 | Vitest is not added yet | Adding a test runner is a new dependency; revisit when frontend tests are needed |
| 2026-10-20 | Technical debt is tracked in `documents/TECH_DEBT.md`, not in AGENTS.md | AGENTS.md stays short; AI fixes debt only when assigned |

- 2026-11-02 | Theo yêu cầu tiếp theo của người dùng, cài Graphify bền vững và đăng ký MCP stdio cho Codex, đọc snapshot local của CFCBase. | Tra cứu đồ thị theo câu hỏi với budget nhỏ để giảm context; giữ CodeGraph cho source hiện tại, cập nhật AST bằng script chủ động, không thêm watcher/hook hoặc dependency ứng dụng.

- 2026-11-02 | Thêm role HR_STAFF (Nhân viên nhân sự): Tổng quan/Nhân sự chỉ đọc; Chấm công/Thưởng ca đêm toàn quyền theo xác nhận người dùng. | Giới hạn cả API và UI, vẫn cho đọc danh mục lọc và tài liệu đã có; không cấp quyền sửa hồ sơ, quản trị, payroll hay các module HR khác. ADMIN/MANAGER giữ quyền cũ. V29 mở rộng enum users.role có sẵn trên MySQL để không phụ thuộc Hibernate tự sửa danh sách enum; không thay đổi bảng HR hoặc tiền/ngưỡng thưởng.

- 2026-11-02 | Tắt tính năng Mẫu Word/ONLYOFFICE theo yêu cầu: gỡ menu, route, nút sửa; bỏ màn hình editor khỏi đồ thị import frontend; API editor/kho mẫu chỉ đăng ký khi bật cờ; triển khai ép cờ tắt và dừng Document Server cũ. | Giảm RAM, tránh màn hình soạn thảo chậm; vẫn giữ dữ liệu mẫu và chức năng xuất/xem/tải hợp đồng DOCX thông thường. Việc bật lại cần thay đổi có chủ đích.

- 2026-11-02 | Thêm khu Thử chấm công riêng cho HR_STAFF: backend chỉ đọc file/config và gọi parser/matcher hiện hữu; thao tác sửa/xác nhận và đối soát xí nghiệp chỉ nằm trong phiên trình duyệt. | Kiểm tra các file công và người có/không có trong danh sách xí nghiệp mà không tạo import, ghi DB, chốt công hoặc ảnh hưởng thưởng ca đêm thật. Số đối soát là ca đêm dự kiến sau chỉnh sửa thử, có báo ngày chưa xử lý; không phải kết quả chốt chính thức.
- 2026-11-02 | Thay khu thử độc lập bằng tab Thử nghiệm trong Chấm công, dùng chung bảng tổng quan, form quyết định ca và modal đối soát với màn chính. Quyết định thử được ghép lại trên backend từ file gốc nhưng không lưu. | Màn thử cũ khác giao diện và tính sai số ngày cần xử lý của nhân viên chưa có hồ sơ (A001); cần so sánh trực tiếp theo luồng chính mà vẫn giữ dữ liệu thật bất biến.
- 2026-11-02 | Bộ đọc Excel đối soát ca đêm nhận `MS` và `MV`; khi có cả hai bảng cùng dòng tiêu đề, lấy bảng `MS` có đủ họ tên và công ca đêm. Quét 30 dòng đầu và chỉ tải thư viện Excel khi người dùng đọc/xuất file. | File đối chiếu T9 chỉ có `MS` nên trước đây bị từ chối; file Gửi thuận có `MV` rỗng nên qua bước tiêu đề nhưng không đọc đúng 2 nhân viên. Giữ thao tác đối soát ở trình duyệt, không ghi DB.
- 2026-11-02 | Giữ file đối soát ca đêm trong state của modal khi đóng/mở cùng tháng; đối chiếu lại từ dữ liệu file đã đọc khi danh sách nhân viên đổi. Xem trước thưởng ca đêm đọc qualification và entitlement theo lô, đồng thời memo bảng nhân viên. | Tránh nhập lại file do ESC và giảm truy vấn theo từng người cùng render lại bảng lớn khi nhập form ngoại lệ; không thêm cache cho dữ liệu chấm công/thưởng động.
- 2026-11-02 | Modal đối soát ca đêm hiển thị thẻ theo nhân viên ở màn nhỏ và bảng nhiều cột từ cỡ desktop; chỉ render một cách trình bày theo kích thước hiện tại. | Bảng 13 cột và thanh lọc cố định không dùng được trên điện thoại; tránh render cả bảng ẩn lẫn thẻ cùng lúc khi danh sách dài.
- 2026-11-02 | Xuất đối soát ca đêm từ modal dùng `Blob` `.xlsx` và liên kết tải lại sau khi tự tải; hủy liên kết khi nguồn đối soát thay đổi. | `writeFile()` chỉ kích hoạt tải xuống rồi trả về tên file, nên sau đó người dùng không có file trong giao diện để bấm tải/mở lại khi demo. Dùng chung cho tab chính và Thử nghiệm, vẫn chỉ xử lý trên trình duyệt.
- 2026-11-02 | Bổ sung cách xem Theo nhân viên ngay trong Thưởng ca đêm: tìm nhân viên theo mã/tên bằng API phân trang, tra 12/24 tháng công ca sản xuất đã xác nhận và ghép với lịch sử chốt thưởng hiện có; giữ lọc/phân trang bảng Theo tháng ở UI. | Cho HR truy vết từng người kể cả khi người đó không có trong tháng đang chọn, phân biệt thiếu file với không có công, không tính lại hay ghi dữ liệu thưởng. Chỉ hiển thị số công thực tế và nhãn tích lũy 12/24 tháng đã chốt đạt thưởng, theo yêu cầu; không suy diễn “chuyên cần/đủ công”.

- 2026-11-04 | Bàn giao local HR UX Phase 0–3 theo yêu cầu: dashboard thử việc đếm riêng từ ứng viên; nhãn hồ sơ/biến động theo ngữ cảnh; giữ URL bộ lọc và `returnTo` nội bộ cùng vị trí cuộn giới hạn trong phiên; Tăng/Giảm lọc/sort tại DB trước phân trang. | Giữ đúng đường quay lại và thứ tự trên toàn bộ dữ liệu, không cache dữ liệu nghiệp vụ hoặc tự mở thêm quyền. Các màn hiện hữu dùng request cancellation; không thêm thư viện cho tiện ích điều hướng. | Đã triển khai local; checklist và giới hạn kiểm thử tại [phases](HR_REPORTING_UX_PHASES.md). Phase 4 được triển khai sau trong V30; Phase 5 báo cáo vẫn hoãn.
- 2026-11-04 | Thực hiện quy tắc chứng từ đã chốt: Tăng gợi ý đuôi theo năm hiện tại của trình duyệt, mọi phần sửa được; Giảm để trống hoặc nhập đủ; lưu một chuỗi đầy đủ. Excel Tăng ưu tiên số chứng từ biến động, fallback số hợp đồng khi số chứng từ trống ở dữ liệu cũ. | Gợi ý không phải cấp số hoặc ràng buộc năm; không đồng bộ ngược vào hợp đồng. File xuất phản ánh số đã nhập thay vì thay bằng số hợp đồng hiện tại khác với biến động. | Đã triển khai local và có kiểm thử workbook; thay thế trạng thái “chưa triển khai” của yêu cầu chứng từ phía dưới.

## Phạm vi đã được người dùng chốt tại thời điểm lập kế hoạch

- 2026-11-04 | Tiện ích nhập số chứng từ chỉ điền sẵn phần đuôi HĐLĐ-PBHC cho biến động Tăng; người dùng nhập số đầu và được sửa cả phần đuôi/đơn vị/năm. Biến động Giảm mặc định để trống, không bắt buộc số quyết định, cho tự nhập khi cần. Không tự tăng hoặc cấp số. | Rút ngắn việc nhập lại chuỗi chứng từ nhưng vẫn dùng được các mẫu khác và hồ sơ lịch sử. | Đã chốt và triển khai local; trạng thái kết quả ở [các phase](HR_REPORTING_UX_PHASES.md).

- 2026-11-04 | Tổng quan thay thẻ Bản nháp bằng Đang thử việc, đếm ứng viên IN_PROBATION thực tế; không cộng ứng viên thử việc vào Tổng hồ sơ nhân sự. Đổi Ngừng hoạt động thành Đã nghỉ việc; nút Thêm hồ sơ nhân sự; hồ sơ DRAFT gọi Chờ tiếp nhận; biến động DRAFT gọi Chờ xác nhận. | Làm rõ các bước tiếp nhận và thử việc, tránh nhầm số hồ sơ chờ Tăng với người đang thử việc. | Đã triển khai local; xem [các phase](HR_REPORTING_UX_PHASES.md).
- 2026-11-04 | Phase 5 báo cáo quân số/nghỉ việc: quân số theo hiệu lực cuối kỳ; nghỉ/tái tuyển tách lượt với số người; hợp đồng xét tại ngày báo cáo; nhóm lịch sử tạm dùng nhóm hiện tại và phải công bố giới hạn. HR_STAFF được GET đọc/xuất, không cấp quyền ghi. | Dùng timeline movement đã xác nhận và phép chiếu roster; không ước đoán nhóm quá khứ khi schema chưa có snapshot. | Đã có bản đầu local; drill-down đầy đủ, đối chiếu workbook và nghiệm thu quyền còn mở tại [phases](HR_REPORTING_UX_PHASES.md). Chưa deploy.
- 2026-11-04 | Phạm vi báo cáo gồm biến động quân số tháng/năm và danh sách nghỉ việc; tách nhóm nhân sự với tình trạng hợp đồng. HR_STAFF được xem/xuất báo cáo; ADMIN/MANAGER giữ quyền quản lý nghiệp vụ. Kèm tìm/lọc/sort Tăng/Giảm và giữ trạng thái khi quay lại các màn HR; bỏ lương khỏi đề tài. | Giảm công tổng hợp báo cáo của phòng Tổ chức – Hành chính – Nhân sự. Quyền đọc báo cáo là phần bổ sung được người dùng chấp thuận cho quyết định HR_STAFF ngày 02/11; không mở quyền ghi hoặc quản lý phân hệ khác. | Tăng/Giảm và lưu trạng thái đã triển khai local. Hai báo cáo cùng phân quyền mới hoãn theo yêu cầu để chốt quy tắc trong [kế hoạch](HR_REPORTING_UX_PLAN.md).

## Open (not decided; AI must ask before acting)

- 2026-11-04 | Bổ sung vào kế hoạch HR yêu cầu tìm hồ sơ qua mọi trạng thái, xử lý hồ sơ chờ tiếp nhận không xóa được do có tham chiếu, và nhập phần số đầu với phần đuôi chứng từ Tăng/Giảm điền sẵn. | Kiểm tra MCP hiện chỉ còn hai hồ sơ DRAFT; hồ sơ thứ ba đã ACTIVE. Mỗi hồ sơ đang được hỏi có một Tăng nháp nên bị chặn xóa. | Chưa triển khai. Dừng tiếp nhận/lưu trữ là phương án đề xuất; mẫu HĐLĐ áp dụng cho loại nào và cách gợi ý số còn chờ chốt. Không ghi cáo buộc cá nhân vào tài liệu.
- 2026-11-04 | Hồ sơ `DRAFT` không tiếp tục tuyển chuyển sang `INTAKE_STOPPED`; giữ trong tổng hồ sơ, không cộng vào ACTIVE/INACTIVE. ADMIN/MANAGER dừng/khôi phục; khi dừng tự hủy Tăng `DRAFT`, giữ các liên kết và audit. Lý do thao tác tùy chọn; khôi phục đưa về `DRAFT` nhưng không hồi sinh Tăng đã hủy. | Tránh xóa cứng các hồ sơ có snapshot/import/biến động và tránh nhầm dừng tuyển với nghỉ việc. Cùng thứ tự khóa hồ sơ trước biến động để đồng bộ với xác nhận Tăng. | Đã triển khai local với migration V30, chưa deploy. Kiểm thử mục tiêu và H2 migration tại [phases](HR_REPORTING_UX_PHASES.md). Phase 5 báo cáo hoãn theo yêu cầu người dùng.
- 2026-11-04, cập nhật sau xác nhận người dùng | Phần số chứng từ của mục mở ngay trên đã được chốt: chỉ Tăng điền sẵn đuôi, số đầu nhập tay, mọi phần sửa được; Giảm để trống hoặc tự điền; không tự cấp số. | Người dùng trả lời câu hỏi và yêu cầu mẫu vẫn phải sửa được. | Quy tắc số chứng từ chuyển sang phạm vi đã chốt phía trên; phương án Dừng tiếp nhận/lưu trữ vẫn đang đề xuất.

- 2026-11-05 | Pilot hạ tầng CFCBase dùng inventory MySQL, polling RouterOS REST qua HTTPS với tài khoản chỉ đọc và trạng thái gần nhất; log dùng Alloy syslog → Loki với retention 168 giờ. Kafka/RabbitMQ không thêm vào pilot. | Quy mô ban đầu vài thiết bị và truy vấn log một tuần không cần message broker; Loki là nơi tìm kiếm log. TLS certificate verification bật mặc định, polling tắt cho tới khi cấu hình server. | Phase 1–3 được triển khai local; chưa kết nối MikroTik, chưa nhận syslog và chưa khởi chạy overlay.



| Topic | Question | Where |
| --- | --- | --- |
| Timezone | JDBC uses `serverTimezone=UTC` while the business runs at +07:00; change or keep? Risk: shifting already finalized shift dates | `application.yml:16` |
| Night reward amounts and cycles | Move 12/24-month cycles and amounts (500k, 1M, 6M, 24M) into `HrNightRewardProgram`? This changes how money rules are configured | `HrNightRewardService.java:32-37` |
| Redis | Remove the unused `spring-boot-starter-data-redis` and use Caffeine, or keep it for a future multi-instance setup? | `pom.xml:48` |
| Secrets and identity | Committed secret defaults and client-trusted identity (P0) | Needs a dedicated security task |
