# Kế hoạch CI/CD cho server Fedora

## Mục tiêu

Giữ nguyên server Fedora, Docker/MySQL và Cloudflare Tunnel. Thay thao tác lặp `git pull` + chạy build bằng kiểm tra tự động và sau đó là deploy có kiểm soát, có health-check và đường quay lại phiên bản ứng dụng trước.

## CI và CD khác nhau thế nào?

- **CI (Continuous Integration):** GitHub tự kiểm tra code. CI không đăng nhập server và không thay đổi website.
- **CD (Continuous Delivery/Deployment):** sau khi CI đạt, một quy trình chuyển phiên bản mới lên server. Chặng này có quyền tác động production nên cần thiết kế và thử riêng.
- **Self-hosted runner:** chương trình đặt trên Fedora, kết nối outbound tới GitHub để nhận việc. Không cần mở SSH hay cổng dịch vụ của ứng dụng ra Internet.

## Các chặng

### Chặng 1 — CI, đã thêm workflow local

`.github/workflows/ci.yml` chạy khi push và khi mở pull request vào `main`:

1. Frontend: `npm ci`, `npm run lint`, `npm run build`.
2. Backend: Java 21, `./mvnw test`.

Chặng này chỉ chạy trên GitHub-hosted runners. Không có secret production, không gọi server, không deploy. Sau khi push file workflow lên GitHub, mở tab **Actions** để xem kết quả.

### Chặng 2 — Làm deploy hiện tại an toàn

**Trạng thái 05/11/2026:** Đã cập nhật script local để giữ JAR trước, ghi commit/thời điểm release, giữ Cloudflare Tunnel chạy trong lúc restart backend và tự rollback JAR khi backend không health-check được hoặc bước xác minh schema thất bại. Đã kiểm tra cú pháp Bash và `--help`; chưa chạy build/deploy trên Fedora, chưa kiểm chứng rollback với database production.

Trước khi tự động hóa, sửa và thử script Linux để:

1. Build trong thư mục staging, không thay JAR đang chạy khi build chưa đạt.
2. Giữ nguyên một release trước đó và ghi commit/version được kích hoạt.
3. Dùng health endpoint phù hợp của ứng dụng để kiểm tra sau khi khởi động.
4. Khi health-check lỗi, khôi phục JAR cũ và khởi động lại phiên bản cũ; báo rõ nếu rollback lỗi.
5. Không rollback database tự động. Flyway thường tiến schema về phía trước; phải xác nhận migration tương thích ngược trước mỗi lần deploy. Backup DB vẫn cần theo chính sách hiện tại.

`run.sh` giữ Cloudflare Tunnel chạy khi backend khởi động lại; nếu health-check hoặc xác minh thất bại, script thử rollback JAR trước. Đã chạy thành công một lần trên Fedora; vẫn cần theo dõi các lần deploy và thử quy trình rollback có kiểm soát. Database không tự rollback.

### Chặng 3 — Chuẩn bị quyền deploy trên Fedora

1. Xác nhận repo được giữ private và bật bảo vệ nhánh `main`/yêu cầu CI xanh.
2. Theo lựa chọn ngày 06/11/2026, giữ nguyên repo `~/Works/BookingBase`, `.env`, Cloudflare credential và user service `david-nguyen`; không chuyển repo hay runtime sang user mới.
3. Cài self-hosted runner như systemd service dưới user `david-nguyen`, gắn label riêng `cfcbase-production` và chỉ kết nối tới repo CFCBase.
4. User này thuộc nhóm `wheel` và `docker`, nên runner có quyền gần tương đương root. Chỉ cho phép workflow deploy thủ công từ `main`, có duyệt production; không chạy pull request hoặc workflow từ nhánh không tin cậy trên runner này.
5. Runner dùng checkout hiện có để gọi `git pull --ff-only` và deploy script. Không truyền `.env`, Cloudflare credential, DB password hoặc private key qua GitHub Actions/artifact/log.

Việc cài runner là thao tác trên Fedora, chưa thực hiện từ workspace này.

### Chặng 4 — Deploy có người duyệt

Sau khi CI xanh và runner đã Online, workflow `workflow_dispatch` được kích hoạt thủ công từ `main`, có GitHub Environment `production` yêu cầu người duyệt. Job chạy dưới `david-nguyen`, cập nhật checkout dùng chung rồi gọi `build-prod.sh`; health-check/rollback do script thực hiện. Không triển khai từ pull request.

### Chặng 5 — Tự deploy sau khi ổn định

Sau vài lần deploy có xác nhận và rollback đã được thử, có thể tự deploy khi code được merge vào `main`. Push lên nhánh tính năng chỉ chạy CI, không đụng production.

### Chặng 6 — Giảm downtime (tùy chọn)

Hiện backend và frontend được phục vụ chung qua ứng dụng Java tại cổng local 8080. Restart có thể tạo gián đoạn ngắn. Blue-green cần thêm reverse proxy local và hai bản ứng dụng chạy luân phiên; chỉ làm nếu thời gian gián đoạn hiện tại không chấp nhận được.

## Cách dùng sau Chặng 1

1. Commit và push workflow cùng code lên GitHub.
2. Mở repo GitHub → tab **Actions**.
3. Chọn workflow **CI** và xem hai job Frontend/Backend.
4. Nếu đỏ, mở job lỗi để xem dòng kiểm tra thất bại. Website production không bị ảnh hưởng bởi workflow này.

## Điều kiện trước khi bật deploy tự động

- CI xanh ổn định trên GitHub.
- Có backup gần nhất và quy trình restore đã kiểm chứng.
- Script health-check/rollback được thử trên môi trường không production hoặc trong cửa sổ bảo trì.
- Runner không chạy workflow từ fork/PR không tin cậy.
- Chốt rõ việc migration database nào được phép chạy tự động và phương án xử lý migration lỗi.
- Thử ít nhất một lần deploy lỗi có chủ ý trên môi trường thử để chứng minh bản cũ vẫn phục hồi được.
