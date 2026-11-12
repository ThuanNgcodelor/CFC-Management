# Sổ sức khỏe — bàn giao Phase 7 ở dev

Ngày 12/11/2026. Cổng cá nhân và quản lý mã tra cứu đã triển khai dev theo lựa chọn người dùng: **hai lựa chọn: CCCD + mã nhân viên, hoặc chỉ nhập mã riêng do ADMIN/MANAGER cấp/thu hồi**. Không cần tài khoản CFCBase, Telegram hoặc SMS. Không deploy/server/Fedora; không lấy thêm CCCD thật, không cấp mã/công bố kết quả thật. Baseline dev riêng không bị nạp fixture.

## Dùng và review

- **Trang cá nhân:** `/health-lookup`, nằm ngoài sidebar/login quản trị. Mặc định backend trả “chưa mở”; cần cả `HEALTH_ENABLED=true` và `HEALTH_SELF_SERVICE_ENABLED=true` trên môi trường dev riêng có V37. Không tự bật các flag trên server.
- **Quản trị:** Sổ sức khỏe → Hồ sơ sức khỏe → mở người → **Mã tra cứu sức khỏe cá nhân**. Kiểm tra CCCD HR hợp lệ, nhập lý do, cấp mã. Mã 32 ký tự chỉ hiện một lần, không thể đọc lại; nút Ẩn mã, đổi tab/pagehide xóa bản đang hiện. Giao riêng sau khi xác minh người nhận; mất mã thì cấp lại. Cấp lại/thu hồi kết thúc các phiên cũ. Không tự gửi tin nhắn hoặc tải danh sách mã hàng loạt.
- **Nhân viên:** chọn cách tra cứu, nhập hai trường (khi quản trị cho phép) hoặc chỉ nhập mã riêng, mở phiên tối đa15 phút; xem lịch sử theo khoảng tối đa10 năm, giữ riêng nhiều lượt/năm; chi tiết kết quả/đơn vị/phương pháp/tham chiếu, tìm chỉ tiêu có debounce; so sánh2–6 lượt đã công bố và biểu đồ số học khi đủ điều kiện; tải PDF một lượt. Không tự đánh giá bệnh/tốt/xấu hoặc thay ô thiếu bằng0.
- **Thoát/hết phiên:** dữ liệu và form được xóa khỏi RAM/query/DOM. Reload hoặc quay lại sau pagehide cần xác minh lại. File đã tải vẫn nằm trên thiết bị; không thể thu hồi bản đã tải. Bấm ESC chỉ đóng bảng kết quả, giữ lịch sử/lựa chọn để mở lại.

## Demo an toàn không cần dữ liệu thật

Vite loopback hiện dùng cổng5195. Trang test chỉ phục vụ trong dev, không được đóng gói thành trang production:

- `http://127.0.0.1:5195/tests/health-phase7.html` — toàn luồng cá nhân dùng fixture giả; CCCD `000000000001`, mã nhân viên `DEMO-SELF`: chọn hai trường để thử không cần mã. Khi chọn dùng mã riêng, mã giả là32 ký tự `X`. Đây không phải tài khoản hoặc CCCD thật. Có2024/2025 giả để so sánh; thao tác PDF ở fixture này cố ý báo lỗi để test retry.
- `http://127.0.0.1:5195/tests/health-phase7-access.html` — giao diện quản trị cấp/ẩn/cấp lại/thu hồi mã hoàn toàn giả.
- PDF thành công, quyền A/B, thu hồi và session được kiểm bằng API MockMvc/H2 + PDFBox. Không coi fixture browser là bằng chứng đã kết nối production hoặc nhập xong lịch sử2023–2024.

## Cơ chế và invariant

- Mã cấp192bit ngẫu nhiên; token phiên256bit; SHA-256 lưu DB, không lưu bản rõ. Hạn mã mặc định90ngày (config1–365), phiên15phút (config1–30), không vượt hạn mã. Không đưa mã/token/CCCD vào URL, audit, cookie, storage hoặc shared Auth interceptor.
- Phiên riêng qua `X-Health-Session`, browser fetch `credentials:omit`, token chỉ RAM. Không dùng JWT ADMIN giả hoặc tin employeeId do client gửi. Token không được tự động gửi từ cookie nên không mở ambient-cookie CSRF; CORS vẫn dùng allowlist chung. API chỉ HTTPS khi rollout; HTTP loopback chỉ dev.
- Ownership/current/hash kiểm ở mỗi nội dung và tải PDF; current là revision cao nhất và PUBLISHED, thu hồi không fallback. Sau render PDF kiểm lại current và phiên. Query error không hiển thị kết quả cũ. Dữ liệu đang sửa không đổi snapshot đã công bố.
- Lịch sử chỉ truy vấn publication hiện hành theo owner, phân trang tối đa20; năm dựa trên snapshot `report_year`, không dùng năm đợt đang biên tập. So sánh snapshot cùng chủ, không query biên tập. Không trả actor/review reason/nguồn ô nội bộ ra cổng cá nhân.
- V37 là Java migration + SQL resource, thêm grant/session, expiry index và frozen year; backfill từng20snapshot hash-present, không sửa payload/hash. Bản legacy thiếuhash không mở. Chưa auto-approve migration hay chạy MySQL thật.
- Khóa ghi employee/grant, generation chống ghi đè; cấp lại/thu hồi/xác minh đồng thời có khóa. Một phiên đang hoạt động/người; đăng nhập mới thay phiên cũ. Phiên hết hạn được dọn khi tra cứu tiếp theo, truy vấn luôn loại phiên hết hạn dù chưa dọn.
- Limiter RAM15phút:20lần/IP,10lần/mã, tối đa10.000khóa băm; đầy từ chối, không đẩy khóa đang giới hạn. Dùng remoteAddr, không tin X-Forwarded-For do khách gửi. Cần nghiệm thu IP qua reverse proxy trước rollout; nhiều instance/restart cần giải pháp được duyệt riêng, không thêm Redis/Kafka.
- Response cổng `no-store, private`, kể cả lỗi; no-referrer/noindex/nosniff/frame DENY. SW hiện không cache API/file; asset shell có thể precache mà không có kết quả. Query provider riêng gc0/retryfalse/cancel stale, không persistence/polling.

## Kiểm chứng và phần còn lại

Kết quả kiểm thử cuối được cập nhật sau khi chạy full suite bên dưới. Fixture không chứa dữ liệu thật. MySQL, HTTPS qua Cloudflare, rate-limit sau proxy, retention/backup, desktop file nghiệm thu và pilot dữ liệu thật thuộc Phase8. Mã chỉ được giao khi quản trị xác minh người nhận; CCCD/mã nhân viên không được quảng bá là hai yếu tố xác thực độc lập.

### Kết quả cuối

- Full backend offline/H2: **381 test, 0 failure/error, 5 skip có sẵn** (376 thực chạy). Sau đó test migration bổ sung **1/1 đạt**, dùng43 publication cũ để kiểm tra backfill qua nhiều trang vẫn giữ nguyên payload và năm snapshot. Không sửa các migration đã có; Java V37/resource riêng tránh có hai migration cùngversion. Lỗi duplicate V37 trong target tạm cũ được xử lý bằng build directory sạch, không sửa DB history/checksum.
- API/service coverage gồm CCCD/mã/sai bí mật, unknown/expired chung thông báo, hash-at-rest, cấp lại/thu hồi/logout/hết phiên, generation stale, HR_STAFF/guest không quản lý mã, ADMIN JWT không thay phiên cá nhân, IDOR ngườiA/B ở detail/PDF/compare, no-store lỗi/thành công, review reason không có trongPDF, published year không đổi theo đợt biên tập, limiter và expiry EXPLAIN.
- MCP browser: **12 nhóm cá nhân** (StrictMode, sai thông tin, lịch sử/chi tiết, tìm debounce, so sánh, PDF error retry, ESC, responsive390px, logout/401/pagehide/no storage) và **7 nhóm cấp mã** (lý do, cấp/ẩn/cấp lại/generation, pagehide, revoke, mobile) đạt, không có pageerror. Regression công bố Phase6 **11 nhóm** đạt, có tảiPDF thành công bằng fixture đã có.
- Frontend build đạt; lint **0error/10warning cũ**, không thêm warning; Node health permission **1/1 đạt**. Chunk cổng **14,98kB**, quản trị **83,82kB**, phần dùng chung được Vite tách; không thêm dependency hoặc static Excel import. Không so riêng HrHealth83,82kB với115,07kB Phase6 như tối ưu tuyệt đối vì lần này thay cách chia shared chunk.
- Refresh graph AST đạt **7.852nodes/31.214edges/277communities**; semantic docs chưa tái trích xuất. `git diff --check` sạch. Không commit/push/deploy; không đổi server/Fedora hoặc lịch sử sức khỏe thật.

Ảnh dữ liệu giả: `/tmp/health-phase7-mobile-login.png`, `/tmp/health-phase7-mobile-comparison.png`, `/tmp/health-phase7-admin-access.png`. Log kiểm thử riêng `/tmp/health-phase7-full-backend.log`, `/tmp/health-phase7-migration-tests.log`, `/tmp/health-phase7-build.log`, `/tmp/health-phase7-lint.log`.


## Bổ sung hai lựa chọn theo xác nhận người dùng

- Có radio **CCCD + mã nhân viên** và **Dùng mã tra cứu riêng**. Lựa chọn thứ hai chỉ hiện/bắt buộc ô mã, không hiện CCCD/mã nhân viên. Đổi lựa chọn xóa mã đang nhập; mã sai không tự chuyển sang cách hai trường.
- Trong hồ sơ quản trị, sau khi cấp quyền còn hiệu lực: nhập lý do → **Cho phép CCCD + mã nhân viên**. Khi đó người tra cứu có thể dùng cả hai cách. Nút **Chỉ cho phép dùng mã riêng** tắt cách hai trường. Người biết đúng hai định danh không cần bí mật riêng khi quyền này bật; UI quản trị nói rõ điều này.
- Policy được bảo vệ cả phía API, không tin mode do client gửi để tự hạ bảo vệ. V38 thêm `allow_identity_only DEFAULT FALSE`, grant cũ không bị đổi. Request cũ thiếu mode vẫn kiểm mã. Bật/tắt thay generation/xóa phiên; cấp lại mã reset về mã riêng; thu hồi/hết hạn chặn cả hai.
- Chỉ fixture dev. Không nhập CCCD thật, không cấp/quyền/công bố thật, không deploy V38/server. Bản tổng hợp Phase1–7: [EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md](EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md).

### Kiểm chứng sau bổ sung hai lựa chọn

- Full backend offline/H2 **385 test, 0 failure/error, 5 skip cũ** (380 thực chạy), bao gồm3test nghiệp vụ/API mới cho hai mode, quyền quản trị, request cũ không mode, không fallback khi mã sai/thiếu, lỗi mode không hợp lệ, stale policy và kết thúc phiên khi thay quyền/cấp lại.
- Browser MCP **10 nhóm mới** cho hai lựa chọn/ô mã có điều kiện/xóa mã khi đổi mode/từ chối basic chưa bật/chấp nhận basic đã bật/390px và quản trị bật/rút quyền. Regression cổng cá nhân **12 nhóm** đạt. Không có pageerror.
- Build đạt, lint **0error/10warning cũ**, Node permission **1/1**. Cổng chunk khoảng15,84kB trước chỉnh lời nhắc cuối; xem build log cuối `/tmp/health-two-modes-build.log`. Không thêm dependency/polling/cache.
- V38 mới chỉ chạy ở H2 test. Không chạy migration hoặc bật cách hai trường trên baseline thật/production. Log full `/tmp/health-two-modes-full-backend.log`, ảnh giả `/tmp/health-two-modes-login.png`, `/tmp/health-two-modes-admin.png`.

### Hiệu chỉnh cuối: mã riêng dùng độc lập

Theo làm rõ của người dùng: cách1 nhập CCCD+mã nhân viên; cách2 **chỉ nhập mã riêng**. Không còn yêu cầu ba trường. UI ẩn hai ô định danh ở ACCESS_CODE và không gửi chúng trong request. Backend validate theo mode, lấy owner từ hash mã; gửi mã nhân viên người khác không chuyển được quyền sang người đó. V39 thêm unique hash index; mã sai/hết hạn/thu hồi vẫn bị chặn. Mã đã cấp không phụ thuộc việc bản CCCD còn được lưu sau đó; điều kiện quản trị cấp mã lần đầu chưa đổi.

Kiểm chứng hiệu chỉnh chỉ mã riêng: full backend **387 test/0 lỗi/5 skip cũ**; bổ sung API code-only không có định danh, owner không đổi khi gửi mã nhân viên người khác, mã còn dùng được sau khi bỏ bản CCCD, validation theo mode và EXPLAIN hash index. Frontend build đạt/lint0lỗi+10warningcũ;12nhóm regression cá nhân và10nhóm lựa chọn/quản trị đạt; browser xác nhận request mã riêng chỉ có `accessCode,mode`. H2 plan: `SELECT employee_id FROM hr_health_access_grants /* public.uk_health_access_secret_hash: secret_hash = ?1 */ WHERE secret_hash = ?1`. Chưa EXPLAIN MySQL. Log `/tmp/health-code-only-backend.log`; cổng chunk16,01kB. Cổng/baseline/server thật vẫn chưa thay đổi.
