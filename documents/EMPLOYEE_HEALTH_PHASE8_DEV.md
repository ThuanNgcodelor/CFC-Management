# Phase 8 — Nghiệm thu kỹ thuật và chuẩn bị rollout

Ngày 12/11/2026. Hoàn tất phần kiểm chứng kỹ thuật **dev**, chưa hoàn tất pilot/production. Không kết nối Fedora, không ghi DB baseline lịch sử, không cấp mã hoặc công bố kết quả thật. Phase9 chưa bắt đầu.

## Bằng chứng kiểm thử

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Backend offline toàn bộ | 420 test, 0 failure/error, 37 skip | 32 MySQL opt-in mặc định skip; 5 skip cũ. Không tính skip là đã chạy |
| MySQL 8.0.46 riêng | 32 test chạy, 0 lỗi/skip | Container tmpfs riêng, không giống hoàn toàn cấu hình máy chủ thật |
| Upgrade V33→V39 | Giữ nhân sự trước nâng cấp; backfill report_year2024 không đổi payload snapshot; validate và chạy lại không thêm migration | Fixture tối thiểu, không phải bản sao DB production |
| Backup/restore | 79 bảng khớp số dòng và CHECKSUM TABLE EXTENDED | Bản QA có 2 chủ hồ sơ, 3 lượt/kết quả/publication, 1 grant; chưa diễn tập tệp/khóa vận hành |
| UI thật → API HTTP | 14 nhóm đạt | Loopback, dữ liệu giả, không giả lập phản hồi API |
| Gate đóng | Cả hai flag OFF: nội bộ/public chặn truy cập; chưa công bố dữ liệu | Schema vẫn chạy Flyway dù feature flag OFF |
| Frontend | build đạt; lint 0 lỗi/10 cảnh báo cũ | Chunk xlsx dùng chung vẫn có cảnh báo kích thước; không phát sinh dependency mới |
| Runner Python | 3 test an toàn đạt | Từ chối URL ngoài, restore khác không được PASS, từ chối tên bảng bất thường |

E2E HTTP gồm: code-only xác định đúng người; lịch sử 2 lượt của chủ; chi tiết bản công bố; tải PDF thật; so sánh; viewport390px; IDOR người khác404; no-store/private; CORS origin lạ403; thu hồi chặn PDF404; thoát chặn401; giả X-Forwarded-For không vượt limiter429; không API sức khỏe trong CacheStorage. Bản chụp dùng fixture, không dùng kết quả thật.

EXPLAIN MySQL: tìm hash dùng `uk_health_access_secret_hash` (const, 1 dòng); dọn phiên hết hạn dùng `idx_health_access_session_expiry` (range). Truy vấn publication hiện hành dùng index revision ở nhánh anti-join; bảng ngoài ALL trên fixture3 dòng. Đây chưa phải chứng cứ hiệu năng với dữ liệu lớn hoặc lý do thêm index.

Benchmark H2 toàn suite: 500 người, 2.000 lượt, 20.000 kết quả, xuất Excel khoảng1,93giây/2,37MB trong lần đo này. Heap peak của JVM chạy toàn suite không cô lập, không dùng để kết luận RAM export hoặc năng lực production. Benchmark JVM giới hạn riêng và các test đảo response/BackForward/demo đúng-xung đột-thiếu của phase trước vẫn được giữ; cần người dùng nghiệm thu trọn bộ với cấu hình triển khai thực.

## Chạy lại MySQL QA ở máy dev

Cần Docker, Java21, Maven và image/dependencies đã có. Runner không tải image/dependency tự động, không nhận URL DB ngoài. Từ repo root:

```bash
python3 -m unittest discover -s scripts/health -p 'test_*.py'
python3 scripts/health/mysql_acceptance.py --maven /duong-dan/toi/mvn --java-home /duong-dan/toi/jdk-21
```

Thay hai đường dẫn cho máy của bạn. Runner tạo container tên ngẫu nhiên, bind127.0.0.1, tmpfs tối đa768MiB, giới hạn1GiB RAM/2CPU; không dùng volume hoặc database hiện có. Cần đủ RAM trống. Cuối cùng xóa đúng container của nó và file credentials; dump/log/proof giữ trong `/tmp/cfc-health-qa-*` riêng0700, tệp0600. Không commit/chia sẻ raw log/dump. Dọn bằng chứng riêng sau khi review; nếu process bị kill, xác minh tên/label `cfc.health.qa=phase8` trước khi dọn container còn sót.

CI mới `Health MySQL acceptance` chạy gate-OFF test để chuẩn bị cả dependency/Surefire, kiểm tra an toàn runner, rồi chuẩn bị mysql:8.0 và chạy cùng runner. Chưa quan sát run GitHub của thay đổi này. Không upload dump/log thành public artifact. CI fail phải giữ release cũ; không bỏ gate để chạy thử bản lỗi.

## Chạy HTTP pilot giả

`HealthPhase8Preview` là **test-source**, không đưa vào production JAR. Nó tái sử dụng fixture/mocked JWT của test, chỉ mở127.0.0.1:18086 và H2 memory, không nhận URL DB. Không dùng test launcher làm server thật.

Biên dịch test classes ra thư mục riêng, tạo runtime classpath bằng Maven `dependency:build-classpath` gồm test dependencies, rồi chạy class `com.booking.system.health.HealthPhase8Preview` với một thư mục riêng `/tmp/cfc-health-phase8-*`. Classpath cần cả test-classes và classes; log/ready.json phải giữ private, không in token. Mất process là mất dữ liệu giả.

Chạy Vite riêng với `VITE_API_URL=http://127.0.0.1:18086/api/v1`, host127.0.0.1, port5196. MCP Playwright chạy `frontend/tests/health-phase8-http.playwright.js`. Test gọi API quản trị fixture để cấp mã, truy cập `/health-lookup`, thu hồi bản công bố và thử limiter. Mỗi lần chạy lại cần khởi động fixture mới vì test cố ý thay trạng thái/đạt rate limit. Đóng hai process sau khi nghiệm thu; không đổi môi trường dev baseline.

## Hướng dẫn nghiệp vụ để nghiệm thu

1. ADMIN/MANAGER tạo đợt đúng năm/nhóm, chọn nguồn và dry-run. Bệnh viện đổi cấu trúc thì ánh xạ/profile mới; không tự áp mẫu cũ khi chưa kiểm tra.
2. Rà soát ghép mã/tên/ngày sinh. Thiếu, trùng, khác kết quả đưa chờ xác nhận; bổ sung muộn cần KEEP/REPLACE có lý do. Dòng nguồn không tương đương số người.
3. Mở hồ sơ kiểm tra lịch sử, thiếu chỉ tiêu/đơn vị, so sánh nhiều lượt; xuất Excel cá nhân/tập thể đúng filter và PDF cá nhân, mở bằng phần mềm desktop để duyệt.
4. Chọn phạm vi công bố → preview cố định → rà soát → công bố; kiểm tra từng người thành công/bị chặn. Sửa bản biên tập không đổi snapshot cũ, cần rà soát/công bố revision mới.
5. Cấp mã riêng, giao riêng cho đúng người; chỉ bật CCCD+mã nhân viên theo chính sách đã duyệt. Thử cả hai cách, đúng người, hết hạn, cấp lại và thu hồi; không gửi mã/CCCD trong URL hoặc ảnh demo.
6. Nếu kết quả sai: thu hồi đúng lượt hoặc quyền tra cứu, ghi lý do. Bản đã tải xuống không thể bị xóa từ xa. Giữ audit để xử lý sau.

## Checklist còn lại trước production

- [ ] Người dùng duyệt kết quả lịch sử còn pending và mẫu Excel/PDF desktop; không công bố936 dòng pending chỉ vì code đạt test.
- [ ] Chốt retention, người phụ trách hỗ trợ, giao mã riêng/nhân viên đã nghỉ và quyền bật cách hai định danh.
- [ ] Xác nhận phiên bản/collation MySQL thật, dung lượng backup và restore trên môi trường riêng tương ứng; backup DB, nguồn tệp cần giữ, cấu hình/khóa liên quan qua kênh riêng.
- [ ] Kiểm chứng HTTPS/Cloudflare, CORS đúng domain, remoteAddr/trusted proxy và rate limit với nhiều người thật. Không tin mù quáng header X-Forwarded-For; limiter hiện dựa remoteAddr có thể gom nhiều người sau proxy.
- [ ] Review V34–V39 và khả năng tương thích release cũ; **không auto-approve**. Java V37 vẫn bị gate migration tự động chặn. Feature flag OFF không ngăn schema upgrade.
- [ ] Pilot nhỏ ADMIN/MANAGER → kiểm tra người nhận thật → nghiệm thu kết quả/tải file → mới mở rộng. Hiện chưa có bước nào được thực thi trên server.

## Dừng và phục hồi

Khi nghi lộ quyền/sai dữ liệu, tắt self-service ở môi trường triển khai và restart theo quy trình hiện có; thu hồi grant/publication tương ứng khi cần. Giữ HR cũ hoạt động và audit. Nếu cần tắt cả module, đóng health gate và frontend gate. Tránh mở lại vô tình chỉ bằng đổi frontend.

Rollback JAR chỉ khôi phục code, không hoàn tác Flyway/DB hoặc tệp đã tải. Trước khi restore thật phải dừng ghi theo phạm vi, xác minh bản backup và môi trường đích, restore thử riêng, review quyền/phiên còn hiệu lực, đối chiếu số liệu rồi mới mở lại. Không chạy lệnh restore QA vào DB thật. Nếu revoke xảy ra sau backup, restore có thể khôi phục grant cũ: cần rà soát/thu hồi phiên và mã trước khi mở cổng.

Các bước production là checklist bàn giao, **không phải xác nhận đã triển khai**. Chi tiết toàn module: [tổng hợp](EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md), [plan](EMPLOYEE_HEALTH_PLAN.md).
