# Sổ sức khỏe — bàn giao Phase 1 ở dev

Ngày: 10/11/2026. Trạng thái: hoàn tất source và kiểm thử local Phase 1; chưa deploy, chưa nhập kết quả thật, chưa mở tra cứu cá nhân.

## Phạm vi đã có

- Backend nằm trong `com.booking.system.health`, với controller/DTO, service, repository, entity và cấu hình riêng. Dùng lại base entity/audit timestamp/actor HR; không đặt code module mới vào service HR cũ.
- Migration V34 tạo chín bảng sức khỏe: đợt khám, chỉ tiêu, alias, chủ hồ sơ, import, lượt khám, kết quả, phiên bản công bố và audit riêng. Không seed người hoặc kết quả; các bảng chủ hồ sơ/import/kết quả/công bố mới là nền, chưa có luồng nghiệp vụ/API nhập hoặc phát hành.
- ADMIN/MANAGER tạo/sửa đợt khám, chọn năm, cơ sở khám, ngày nếu biết và khối gián tiếp/trực tiếp/cả hai. Khóa đợt chưa triển khai ở Phase 1.
- ADMIN/MANAGER tạo/sửa chỉ tiêu, nhóm, kiểu, trạng thái và tối đa 30 tên cột tương đương. Alias chuẩn hóa NFC/case/khoảng trắng; một alias chỉ thuộc một chỉ tiêu. Phương pháp và đơn vị của kết quả thực tế thuộc phase nhập liệu.
- Mã đợt/mã chỉ tiêu giữ ổn định sau tạo; kiểu chỉ tiêu cũng giữ ổn định để tránh diễn giải lại dữ liệu cũ. Có thể sửa tên/cơ sở/ngày/nhóm/alias/trạng thái phù hợp. Không cung cấp xóa cứng.
- Ghi/sửa trong transaction, audit cùng transaction; `rowVersion` bắt buộc khi sửa. Sửa alias cũng tăng version của chỉ tiêu để chống hai quản trị viên ghi đè.
- UI `/manager/hr/health` có tab Đợt khám/Danh mục chỉ tiêu, tạo/sửa, lọc năm/khối, tìm mã/tên có debounce, phân trang và trạng thái lỗi/đang tải. Dùng TanStack Query 5 trong provider riêng màn, hủy request cũ, không polling hoặc lưu cache xuống ổ đĩa.
- Health được kiểm tra quyền backend trước các matcher HR rộng; HR_STAFF không có menu hoặc quyền truy cập trực tiếp. Không đưa dữ liệu sức khỏe vào DTO hay tài liệu nhân sự chung. Audit chỉ lưu hành động, ID thực thể và role/actor trong bảng health riêng, không đưa nội dung vào feed audit HR.

## Bật ở môi trường dev đã được cấu hình riêng

Hai cờ độc lập, mặc định tắt:

| Cờ | Mục đích |
|---|---|
| `HEALTH_ENABLED=true` / `cfc.health.enabled=true` | Bật API nghiệp vụ sức khỏe ở backend dev |
| `VITE_HEALTH_ENABLED=true` | Hiện menu/route ở frontend dev; cần restart Vite khi đổi |

Không sửa `.env` Fedora hoặc chạy build/deploy script server để test. Bản này không thêm V34 vào danh sách auto-migration được phép. Việc phát hành production cần review riêng ở phase rollout.

## Kiểm thử backend hoàn toàn bằng H2 trong bộ nhớ

Chạy tại `backend`, với Java 21 và Maven đã có ở máy dev:

```bash
JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -Dbooking.build.directory=/tmp/cfc-health-phase1-target test
```

Hoặc kiểm thử riêng module:

```bash
JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -Dbooking.build.directory=/tmp/cfc-health-phase1-target \
  -Dtest=HealthPhase1Test,HealthDefaultsTest test
```

HealthPhase1Test cấu hình H2 rõ ràng, Flyway và Hibernate validate; không quét seeder/collector/mail/Telegram của ứng dụng chính. Mock JWT/user chỉ phục vụ xác thực kiểm thử; sử dụng SecurityConfig/JwtAuthFilter/controller/service/repository thật. Kiểm tra đường vòng dùng cả controller/service tài liệu nhân sự thật với dữ liệu H2. H2 và toàn bộ bản ghi test mất khi tiến trình kết thúc; không kết nối MySQL production.

## Tự xem giao diện bằng dữ liệu giả, không cần backend

Tại `frontend`:

```bash
npm run dev -- --host 127.0.0.1 --port 5195 --strictPort
```

Mở `http://127.0.0.1:5195/tests/health-phase1.html`. Đây là harness dùng component production với API giả trong RAM; không đăng nhập, không gọi API thật, reload là mất dữ liệu giả. File test không nằm trong entry của production build.

MCP Playwright có thể chạy `frontend/tests/health-phase1.playwright.js` qua `browser_run_code_unsafe` với tham số `filename`; file chứa function entry cho MCP, không chạy bằng Node trực tiếp. Test kiểm tra tạo/sửa, xung đột giữ nội dung đang nhập, alias nhiều dòng, debounce, response cũ và responsive 390 px. UI test này không thay cho integration test backend bên trên.

Kiểm tra frontend:

```bash
npm run lint
npm run build
node --test src/utils/*.test.js src/api/*.test.js
```

## Kết quả đã xác minh

| Kiểm tra | Kết quả |
|---|---|
| Full backend offline, output riêng `/tmp` | 288 test, 0 failure/error, 5 skipped; 283 thực thi đạt |
| Health integration/defaults | 13 test đạt, không skip |
| Migration | Flyway V1–V34 trên H2; Hibernate validate cho entity HR + health đạt |
| API quyền | Khách 401; HR_STAFF 403 cho GET/POST/PUT/PATCH/DELETE trong namespace health; ADMIN/MANAGER dùng được API; nhân viên/inactive bị chặn |
| Nghiệp vụ nền | Tạo/sửa, stale version, ngày sai, body/filter sai, mã/alias trùng, filter trước pagination, literal wildcard và audit riêng đạt |
| MCP browser | Bảy nhóm tương tác đạt trên API giả trong RAM; selected tab có chữ đọc được, không tràn trang ở 390 px |
| Node | 8 test đạt |
| Frontend lint | 0 error, 10 warning baseline cũ, không có warning mới |
| Frontend build | Đạt; chunk HrHealth khoảng 43,8 kB, dưới 500 kB; bundle lớn cũ ở module khác vẫn còn |

Năm test backend skipped là fixture ngoài repo/điều kiện tùy chọn của các module cũ và một test matcher đã disable; không có health test bị bỏ qua. Lượt chạy full đầu ghi report vào target cũ có cảnh báo quyền tệp; đã chạy lại full vào `/tmp/cfc-health-phase1-target` với kết quả ở bảng trên.

EXPLAIN đã chạy trên H2 dev:

```sql
EXPLAIN SELECT * FROM hr_health_campaigns
WHERE report_year = 2026 ORDER BY code, id LIMIT 20;
-- FROM public.hr_health_campaigns
-- /* public.idx_health_campaign_year_code: report_year = 2026 */
-- WHERE report_year = 2026 ORDER BY 2, 1 FETCH FIRST 20 ROWS ONLY
```

Đây là bằng chứng H2 chọn index, chưa phải EXPLAIN hoặc nghiệm thu MySQL 8. Chưa chạy migration trên database dev MySQL hoặc server. Cần đo lại query thực tế khi có dữ liệu và môi trường MySQL pilot.

## Những phần chưa thuộc Phase 1

Import Excel/đối chiếu dữ liệu thật, lịch sử khám/so sánh theo người, xuất Excel/PDF, công bố/thu hồi và tra cứu CCCD/mã nhân viên chưa có luồng sử dụng. Các quyết định Phase 0 về nguồn ưu tiên và xác minh cá nhân vẫn giữ trạng thái cần review; không bị tự coi là đã duyệt vì Phase 1 hoàn tất.
