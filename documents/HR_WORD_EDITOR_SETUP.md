# Trạng thái Mẫu Word và ONLYOFFICE

Từ 2026-11-02, màn hình **Mẫu Word** và chức năng sửa Word trực tiếp bằng ONLYOFFICE đã tắt theo yêu cầu vận hành để giảm RAM. Menu và nút sửa đã ẩn; URL cũ chuyển về Tổng quan. Frontend không import màn hình editor. Backend không đăng ký API editor và kho mẫu khi `cfc.word-editor.enabled=false`.

`deployserver/linux/run.sh` luôn ép `WORD_EDITOR_ENABLED=false` sau khi đọc `.env`, kể cả trong launcher backend. Script không mở hostname `docs.cfcbooking.io.vn`, không khởi động Document Server, và dừng container `documentserver` cũ nếu có. Compose ONLYOFFICE nằm trong profile `word-editor`, tách khỏi khởi động mặc định. `deployserver/linux/.env.example` và cấu hình backend mặc định cũng đặt cờ là `false`.

Các mẫu và lịch sử đã lưu được giữ trong database; volume ONLYOFFICE không bị xóa. Chức năng xuất hợp đồng DOCX bằng Apache POI, xem trước và tải các bản hợp đồng đã tạo vẫn hoạt động.

## Kiểm tra Docker trên máy chạy ứng dụng

```bash
docker ps -a --filter ancestor=onlyoffice/documentserver --format '{{.Names}} {{.Status}}'
```

Nếu đã nhận bản code này mà container cũ vẫn còn chạy, có thể dừng ngay mà không build ứng dụng:

```bash
WORD_EDITOR_JWT_SECRET=stop-command-placeholder-secret docker compose -f docker-compose.word-editor.yml --project-directory . stop documentserver
```

Lệnh `stop` giữ nguyên volume và database. Sau khi triển khai code mới, `run.sh` cũng dừng container cũ. Thay đổi giao diện và API chỉ có hiệu lực ở bản ứng dụng mới khi được phát hành; yêu cầu hiện tại không chạy build hoặc phát hành.

Muốn dùng lại ONLYOFFICE sau này cần quyết định mới để khôi phục route, API, logic khởi động và cấu hình bảo mật; chỉ đổi `.env` thành `true` sẽ không bật được tính năng.
