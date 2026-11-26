# Phase 9 — Sẵn sàng tiếp nhận đợt khám 2026

> Bàn giao theo mốc của phase, không phải trạng thái production hiện tại. Cập nhật23/11/2026: quản trị đã deploy, gói lịch sử đã kích hoạt83dòng nhập/936dòng nguồn chờ theo ảnh người dùng. Gate nội bộOFF hoặc “chưa deploy” bên dưới là lịch sử; cổng cá nhân/công bố không tự bật. Xem [trạng thái hiện hành](EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md), [hướng dẫn nhập và chọn lô](EMPLOYEE_HEALTH_AUTO_IMPORT.md), [mục lục](README.md).

Cập nhật 18/11/2026. Người dùng xác nhận **chưa có file khám 2026, sẽ bổ sung sau**. Phần chuẩn bị phần mềm đã được kiểm thử bằng dữ liệu giả. Không tạo đợt/kết quả 2026 trong DB baseline lịch sử hoặc production.

## Phần đã hoàn thiện

- Khi bệnh viện đổi mẫu, đối chiếu fingerprint cấu trúc trước khi dùng profile. Cột mới phải được xử lý rõ, không tự dùng ánh xạ cũ sai vị trí.
- Màn import có bảng kiểm tra toàn bộ cột: cột chưa xử lý, kết quả/tham chiếu thiếu chỉ tiêu, số cột kết quả và danh sách cột bỏ qua. Nút kiểm tra đưa đến trang chứa cột, kể cả cột sau trang đầu. Thay ánh xạ phải xác nhận lại trước khi tạo lô. Backend vẫn kiểm tra độc lập.
- Luồng 2024–2025–2026 đã kiểm tra qua parser/import/service thật: một người có lịch sử ba năm; một người chỉ có2026; chỉ tiêu bổ sung không có ở năm cũ giữ ô thiếu; phương pháp khác giữ dòng riêng và không tính xu hướng tùy tiện.
- Bổ sung muộn có xung đột phải chọn KEEP/REPLACE và lý do. Bản đã công bố giữ nguyên đến khi rà soát/công bố revision mới. Người chưa ghép hoặc ngày sai tiếp tục chờ, không được tạo lượt giả để lấp timeline.
- Excel cá nhân/tập thể và PDF cá nhân nhiều năm được đọc kiểm chứng; cổng cá nhân hiển thị đúng lịch sử công bố và tải được PDF.
- MCP phát hiện và đã sửa lỗi servlet error dispatch làm phản hồi403 của API sức khỏe thành401. Phản hồi401/403 sức khỏe giờ trả JSON trực tiếp, no-store/private; fallback của các API khác giữ hành vi hiện có. Có test HTTP thật cho tình huống này.

## Mở demo bằng một lệnh

Từ repo root, dùng Java21, Maven, npm và dependencies đã có:

```bash
python3 scripts/health/run_dev_preview.py \
  --maven /home/kali/.local/share/maven/bin/mvn \
  --java-home /home/kali/.local/share/jdk-21
```

Đổi đường dẫn nếu chạy máy khác. Runner chạy6 test luồng2026 trước, lấy classpath từ báo cáo test, mở backend127.0.0.1:18086/H2 memory và Vite127.0.0.1:5196. Không cần plugin Maven `dependency:build-classpath`. Không tự tải dependencies, không đọc `.env` server, không tắt process đang chiếm cổng.

- Quản trị trong khung CFCBase: `http://127.0.0.1:5196/tests/health-phase9.html?tab=records`.
- Cá nhân: `http://127.0.0.1:5196/health-lookup`.
- File và thông tin tra cứu giả nằm trong thư mục riêng `/tmp/cfc-health-2026-*` mà runner in ra; `ready.json` private chứa mã demo. Không đưa mã thật vào tài liệu. Các thao tác thử chỉ tác động DB memory.
- Ctrl+C dừng process backend và nhóm process Vite của demo; DB memory mất. Log/file giả vẫn giữ private để review. Chạy lại là một bộ dữ liệu mới.

Test launcher/mocked identity nằm **test-source**; HTML demo nằm ngoài entry build production. Các API sức khỏe trong demo gọi backend thật, chỉ thông tin người dùng của khung trang là fixture. Không dùng launcher làm service production. Health flags/defaults/allowlist migration production không thay đổi.

## Bộ file giả để thử mẫu bệnh viện

| File | Nội dung | Kết quả mong đợi |
|---|---|---|
| 01-lich-su-gia-2024.xlsx | HEALTHDEV01, AST20 | Một lượt năm2024 |
| 02-lich-su-gia-2025.xlsx | HEALTHDEV01, AST22 | Một lượt năm2025 |
| 03-mau-moi-gia-2026.xlsx | Đảo thứ tự cột, hàng3–4 làm tiêu đề, thêm chỉ tiêu; hai người | Hai lượt2026; người02 không có lịch sử cũ |
| 04-bo-sung-gia-2026.xlsx | Cùng SID của người01, AST26 thay24 | Xung đột, chưa ghi đè; cần lý do và công bố lại |
| 05-du-lieu-loi-gia-2026.xlsx | Mã không tồn tại; thiếu tên/ngày31/02 | Hai dòng chờ xử lý, không xác nhận thành kết quả |

Demo đã nhập các lượt hợp lệ và công bố snapshot giả. Lô bổ sung và lô lỗi đã được tạo để mở lại từ lịch sử nhập, không cần upload lại. Nếu upload lại file đã nạp, hệ thống có thể chặn trùng đúng thiết kế. Muốn thử toàn luồng từ đầu, dùng6 test tự động hoặc khởi tạo đợt giả mới và đối chiếu SID/namespace; không chuyển file giả vào đợt thật.

## Kiểm thử ngày18/11/2026

| Bộ kiểm tra | Kết quả |
|---|---|
| Full backend, bật khảo sát3 workbook có sẵn theo chế độ chỉ đọc | **433 test,0 failure/error,43 skip**:38 MySQL opt-in và5 skip cũ.390 test thực thi |
| MySQL8.0.46 riêng | **38/38 đạt**, gồm32 test Phase8 và6 luồng2026; upgradeV33→V39 và restore79 bảng khớp count/checksum |
| HTTP thực bảo vệ quyền | Test mới giữ403 JSON cho HR_STAFF và401 JSON cho khách, no-store |
| Node frontend | **29/29 đạt**, gồm4 kiểm tra review ánh xạ |
| Python |45 test deploy và3 test an toàn MySQL runner đạt |
| Frontend |Build đạt; lint0lỗi/10cảnh báo cũ. Chunk HrHealth84,78→87,03kB (gzip21,53kB); không thêm thư viện/cache/schema |
| MCP |12 bộ kiểm tra, **114 nhóm tương tác đạt**, gồm92 nhóm các phase trước/ánh xạ,8 nhóm HTTP2026 và14 nhóm HTTP cá nhân/thu hồi |

Bộ MCP đã chạy: Phase1(7), Phase2(13), Phase3(5), Phase4(12), Phase5(9), Phase6(11), Phase7cá nhân(12), Phase7quản trị mã(7), hai phương thức(10), review ánh xạ(6), HTTP2026(8), HTTPPhase8(14). Fixture UI kiểm tra thao tác/race; hai bộ HTTP dùng backend thật và dữ liệu giả. Không coi fixture UI là nghiệm thu dữ liệu thật.

Ảnh từ MCP trên cùng khung giao diện và API dev:

- [So sánh ba năm — desktop](screenshots/health/phase9-desktop.png).
- [So sánh — mobile390px](screenshots/health/phase9-mobile.png).
- [Kiểm tra cột import — mobile](screenshots/health/phase9-mapping-mobile.png).

[Bảng kết quả kiểm thử dạng JSON](EMPLOYEE_HEALTH_TEST_RESULTS.json).

## Khi nhận file2026 thật

1. Giữ file gốc tại nơi được phân quyền, tạo đợt đúng năm/nhóm và xác minh ngày khám từ nguồn thực.
2. Inspect/dry-run, chọn sheet/hàng tiêu đề. Profile chỉ dùng khi cấu trúc khớp; chỉ tiêu mới cần chọn/tạo, không suy đơn vị/phương pháp.
3. Xem kết quả ghép mã/tên/thông tin sinh; giải quyết thiếu/trùng/xung đột có lý do. Thiếu lịch sử giữ thiếu, không tự ghép chỉ theo tên.
4. Nhập từng phần, kiểm tra timeline và xuất báo cáo cá nhân/tập thể theo cùng bộ lọc.
5. Rà soát bản công bố, xác nhận phạm vi/người nhận, công bố có kiểm soát rồi kiểm tra cổng cá nhân.
6. Bổ sung từ bệnh viện sau đó dùng cùng namespace/SID đúng người; xử lý xung đột và công bố revision mới.

Không có file2026 nên chưa nghiệm thu cấu trúc bệnh viện thật hoặc nhập/công bố thật. Nhắc tái khám, ZIP nhiềuPDF, adapter bệnh viện mới và tích hợp cổng quốc gia/VNeID/AI là mục mở rộng tùy nhu cầu trong kế hoạch, chưa được triển khai trong đợt này. Baseline936 dòng nguồn còn cần người quản trị xác nhận; không đánh dấu đã duyệt thay họ. [Checklist production còn lại](EMPLOYEE_HEALTH_PHASE8_DEV.md).
