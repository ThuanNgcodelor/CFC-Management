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

## Open (not decided; AI must ask before acting)



| Topic | Question | Where |
| --- | --- | --- |
| Timezone | JDBC uses `serverTimezone=UTC` while the business runs at +07:00; change or keep? Risk: shifting already finalized shift dates | `application.yml:16` |
| Night reward amounts and cycles | Move 12/24-month cycles and amounts (500k, 1M, 6M, 24M) into `HrNightRewardProgram`? This changes how money rules are configured | `HrNightRewardService.java:32-37` |
| Redis | Remove the unused `spring-boot-starter-data-redis` and use Caffeine, or keep it for a future multi-instance setup? | `pom.xml:48` |
| Secrets and identity | Committed secret defaults and client-trusted identity (P0) | Needs a dedicated security task |
