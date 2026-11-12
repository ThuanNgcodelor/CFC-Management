# Sổ sức khỏe — bàn giao Phase 3 ở dev

Ngày: **11/11/2026**. Đã triển khai công cụ baseline, nhập thử các file thật vào **H2 dev riêng**, có backup/restore và giao diện rà soát. **Chưa nghiệm thu toàn bộ baseline; không ghi nghiệp vụ production, không deploy và chưa công bố.**

## Xem ngay trên máy dev

Bản preview đang chạy tại:

**http://127.0.0.1:5195/manager/hr/health?tab=imports**

- Dùng khung DashboardLayout hiện có: sidebar, topbar và menu Nhân sự → Sổ sức khỏe. Không mở thành một app giao diện riêng.
- Chọn **HISTORY_2025 — Lịch sử khám sức khỏe 2025**, mở lô **Gián tiếp · 85 dòng**. Hiện đã nhập 83, còn chờ 2; tìm theo dòng trong file và dùng **Ghép nhân sự** nếu cần.
- Chọn **HISTORY_2023** hoặc **HISTORY_2024** để xem lô Tổng hợp/sheet con/file bổ sung. Người dùng đã xác nhận năm 2023/2024 theo SID. Lỗi chờ xác minh năm được gỡ bằng thao tác có lịch sử; chưa suy ngày khám và vẫn cần ghép người/giải quyết lỗi khác.
- Mở **Xem đối chiếu** để xem ô nguồn và lỗi. Phân loại ngoài I–V có form đính chính với lý do; giữ giá trị gốc và lịch sử. Thiếu phân loại không tự gán I hoặc “Bình thường”.
- Link CSV trên banner tải danh sách rà soát riêng tại máy dev. File có định danh/SID/ID ứng viên nên không chia sẻ công khai hoặc commit.
- ESC chỉ đóng drawer, không mất lô staging. Reload rồi chọn lại đợt/lô; dữ liệu H2 file còn giữ khi restart tiến trình. Không lưu hồ sơ/CCCD/kết quả trong localStorage.

Preview dùng API health thật trên `127.0.0.1:18085` và H2 file riêng; danh tính quản trị giả chỉ ở launcher test tạm ngoài repository. Không thay đăng nhập CFCBase. Các menu ngoài sức khỏe trong preview này không gọi nghiệp vụ production. Preview không phải cách chạy production hoặc mở cổng cho mạng LAN/Internet.

## Nguồn và quy tắc đã dùng

Người dùng cho phép đọc API nhân sự cfcbooking, sao chép trường cần thiết vào dev và tự nhập thử ba Excel. Snapshot HR có **398 hồ sơ** gồm ID/mã/họ tên; bổ sung ngày sinh cho **249 hồ sơ liên quan**. Không sao chép CCCD, điện thoại, địa chỉ, hợp đồng hoặc lương. API ngoài chỉ được đọc nghiệp vụ; đăng nhập phục vụ phiên đọc, không gọi thao tác sửa/xóa/xuất/công bố nhân sự.

Người dùng chọn **Tổng hợp làm nguồn ban đầu** của 2023–2024. Sheet con và file bổ sung là nguồn đối chiếu/bổ sung ô thiếu, không tự thắng dữ liệu chính. Các trường hợp tên/kết quả khác nhau giữ chờ xác nhận. Người dùng đã xác nhận bổ sung năm 2023/2024 theo SID; chưa xác nhận ngày khám và không cho phép ghép tự động chỉ theo tên.

- Mã nhân viên chuẩn + tên đã chuẩn hóa + độ chính xác ngày/năm sinh của nguồn mới đủ điều kiện auto match. Chỉ năm sinh thì không tạo ngày sinh 01/01.
- Tên/ngày sinh khi thiếu mã chỉ là ứng viên. Người quản trị phải xác nhận liên kết và lý do; không tự tạo/sửa HR từ kết quả khám.
- SID phân biệt lượt trong một mã nguồn, không phải mã người xuyên năm. Các sheet trùng SID không được cộng thành các lượt độc lập.
- Tên chỉ tiêu giữ đầy đủ, gồm hậu tố máy/phương pháp. Danh mục chưa xác minh dùng TEXT để giữ raw, không tự đoán đơn vị hoặc gộp hai phương pháp thành cùng chỉ số số học.
- Cột có giá trị nhưng không có tên được giữ và báo lỗi; không bỏ âm thầm. Trường Code của đơn vị không coi là mã nhân viên.
- 23 dòng công việc bổ sung được lưu ở báo cáo nguồn riêng, không biến thành lượt khám hoặc kết quả.
- Năm 2026 chưa khám: không tạo campaign/kết quả 2026 trong DB baseline.

## Kết quả nạp thử

| Chỉ số | Kết quả dev |
| --- | --- |
| Dòng kết quả trên mọi sheet nguồn | 1.019 |
| SID khác nhau | 549 — không phải số người |
| Dòng công việc riêng | 23 |
| Lô staging sau chia sheet/năm | 10 |
| Dòng khớp chính xác mã/tên/thông tin sinh | 83 dòng / 83 người |
| Dòng chỉ có gợi ý cần xác nhận | 789 |
| Dòng chưa khớp đủ điều kiện | 147 |
| Dòng đã xác nhận vào kết quả nội bộ | 83/85 dòng file 2025 |
| Dòng nguồn còn chờ | 936, bao gồm dòng lặp ở sheet con |
| Lượt khám nội bộ / ô kết quả | 83 / 2.216 |
| Công bố / campaign 2026 | 0 / 0 |
| Chạy lại cùng snapshot/workbook | Xác nhận thêm 0 dòng; không tăng số lô/lượt/kết quả |

Khác cách ghi tên giữa nguồn cùng SID: 33 trường hợp trên các dòng nguồn, gồm 32 biến thể chuẩn hóa tương đương và 1 khác tên thực sự. Có 14 ô có giá trị ở cột chưa có tiêu đề, 1 phân loại không thuộc I–V, 43 dòng thiếu phân loại và 2 lỗi nguồn. Số này có thể giao nhau hoặc lặp qua sheet, không dùng để đếm số người bệnh/lỗi duy nhất.

Không thấy chênh kết quả khi so cùng SID/khóa tiêu đề trong khảo sát này; không suy ra hai xét nghiệm khác máy/đơn vị tương đương. Các file/đợt sau vẫn đi qua KEEP/REPLACE và lịch sử đính chính của Phase 2.

## Công cụ và dữ liệu riêng

`HealthBaselineDev` ở **test-classes**, không đóng gói vào JAR production. Công cụ đọc/parse/hash và xuất báo cáo trước transaction; catalog và staging dùng bean transaction riêng, batch writes. CLI mặc định chỉ dry-run, ghi DB phải thêm `--stage --policy TOTAL_FIRST` và snapshot được phép.

Thư mục riêng hiện tại: `/tmp/cfc-health-baseline-review` (0700; file 0600):

- Bản sao workbook nguồn, snapshot định danh và metadata lần đọc.
- `baseline-private.json`: nguồn đầy đủ; `baseline-report-private.json`: kết quả đối chiếu.
- `baseline-review-private.csv`: danh sách cần xử lý, có chống công thức CSV.
- `baseline-receipt-private.json`: các lô tạo lại/idempotency, không thay cho số đếm sau thao tác trên DB.
- `health-dev-db.mv.db`: DB thử, tách khỏi MySQL/dev database hiện hữu.
- SQL backup trước ghi và `after-baseline-private.sql`; `backup-restore-proof.json` chứa bằng chứng tổng số sau restore.

Đã restore backup sau baseline sang một H2 khác: 398 HR, 10 lô, 1.019 dòng, 83 dòng nhập, 83 lượt, 2.216 kết quả, 0 công bố, 0 campaign 2026 đều khớp. Không restore đè DB đang review hoặc production.

Sáu file nguồn Excel/Markdown ở root đã được ignore để tránh đưa nhầm hồ sơ thật vào repo; các file vẫn giữ nguyên trên máy.

`/tmp` có thể bị dọn hoặc mất sau reboot; đây không phải chính sách lưu trữ dài hạn. Khi cần giữ lâu, người vận hành phải chọn vùng dev riêng được bảo vệ và quy trình backup/retention trước; không chuyển vào repo public. Không thêm auto purge khi chưa có quyết định.

## Chạy lại kiểm thử

Từ root repository:

```bash
HEALTH_SOURCE_DIRECTORY="$PWD" JAVA_HOME="$HOME/.local/share/jdk-21" \
  "$HOME/.local/share/maven/bin/mvn" -o --batch-mode --no-transfer-progress \
  -f backend/pom.xml -Dbooking.build.directory=/tmp/cfc-health-phase3-target test

cd frontend
npm run lint
npm run build
node --test src/utils/*.test.js src/api/hrApiUtils.test.js
```

`HEALTH_SOURCE_DIRECTORY` chỉ bật bài khảo sát workbook đọc RAM trong suite, không tự nhập dữ liệu thật hoặc gọi cfcbooking. Các fixture tích hợp/Playwright dùng tên và kết quả giả. Browser QA chạy `frontend/tests/health-phase3.playwright.js` cùng regression Phase 1–2 trên Vite local; không sử dụng dữ liệu thật làm snapshot/test public.

Để vận hành CLI, dừng API preview đang giữ H2 trước, dùng classpath test từ Surefire sau khi compile. Ví dụ với classpath đã chuẩn bị trong phiên dev này:

```bash
umask 077
java -javaagent:/path/to/mockito-core.jar -cp "$(cat /tmp/health-phase3-classpath.txt)" \
  com.booking.system.health.HealthBaselineDev \
  --input "$PWD" --output /tmp/cfc-health-baseline-review \
  --identities /tmp/cfc-health-baseline-review/identity-snapshot.json \
  --stage --policy TOTAL_FIRST --confirm-code-matches
```

Thay đường dẫn Mockito bằng JAR hiện có trong classpath test; không tải dependency mới để chạy production. Bỏ `--stage` là dry-run. `--confirm-code-matches` chỉ xác nhận dòng nguồn chính hợp lệ, không tự xác nhận ứng viên thiếu mã/sheet con/xung đột. `--confirm-sid-years` dùng sau xác nhận rõ ràng về năm; người dùng đã đồng ý cho bộ lịch sử hiện tại. Với lô có sẵn, thao tác này giữ ID/raw/ngày/liên kết và lỗi khác, chỉ bỏ lỗi năm chưa xác minh khi SID khớp đợt, lưu history/lý do/người/thời điểm và làm mới preview version. Retry không tạo thêm history. Không cần hủy/tạo lại lô; không tự xác nhận các dòng kết quả.

Bootstrap lần đầu của công cụ chỉ thay seed HR trong H2 thử riêng chưa có sức khỏe, sau backup. Snapshot đã cài được đánh dấu fingerprint; cùng snapshot chạy lại bỏ qua, snapshot đổi hoặc DB đã có sức khỏe nhưng thiếu marker thì từ chối. Guard từ chối MySQL/TCP và H2 file ngoài `/tmp`; tuyệt đối không dùng công cụ để đồng bộ production.

## Kiểm chứng và giới hạn nghiệm thu

| Kiểm tra | Kết quả |
| --- | --- |
| Full backend offline, có khảo sát nguồn opt-in | 317 test, 0 failure/error, 5 skipped baseline; 312 test thực thi đạt |
| Backend Phase 3 | Chuẩn hóa I–V; bảo toàn source; thiếu mã/năm chặn; idempotent staging; confirm không publish; lý do/rowVersion đính chính; guard H2 |
| UI Phase 3 | 4 nhóm đạt: chặn phân loại sai, bắt lý do, giữ raw/history, xác nhận sau đính chính |
| UI regression Phase 1/2 | 7 + 13 nhóm đạt |
| API preview với baseline thật | HISTORY_2025 hiển thị 83 đã nhập/2 chờ; 390 px không tràn trang; không có pageerror |
| Frontend Node | 8 test đạt |
| Frontend lint/build | 0 error, 10 warning cũ; chunk HrHealth 71,86 kB (Phase 2: 69,31 kB) |
| Backup/restore và retry | Tổng số khớp; retry không nhân đôi hoặc nhập thêm |

Trạng thái rà soát:

1. Năm 2023/2024 theo SID đã được người dùng xác nhận. Ngày khám vẫn chưa xác minh, không coi SID là ngày khám chắc chắn.
2. Liên kết người ở các dòng thiếu mã, biến thể tên/thông tin sinh; giải quyết 2 dòng năm 2025 còn chờ theo nguồn thực tế.
3. Ý nghĩa cột không tên và phân loại sai; không đoán nội dung lâm sàng.
4. Đối chiếu sheet con/bổ sung sau nguồn ban đầu; chỉ xác nhận giá trị khác sau khi review.

**Phase 3 có bản triển khai và dữ liệu dev để review, chưa đạt cổng nghiệm thu toàn bộ baseline.** Timeline cá nhân/so sánh thuộc Phase 4; xuất báo cáo sức khỏe chính thức thuộc Phase 5; công bố/thu hồi và cổng tự tra cứu thuộc phase sau. Chưa chạy migration/EXPLAIN MySQL, chưa deploy V34/V35 hoặc thay manifest tự deploy. API production chỉ được đọc danh tính đã cho phép, không gửi Excel/kết quả sức khỏe lên cfcbooking.


### Xác nhận năm sau review của người dùng

Người dùng xác nhận **năm 2023/2024 theo SID, chưa suy ngày khám**. Đã áp dụng cho 921 dòng lịch sử pending trên các lô hiện có; giữ nguyên 1.019 ID dòng, ID lô, liên kết, giá trị nguồn và các lỗi khác. 921 là số dòng qua sheet, có trùng SID, không phải số người. Mọi ngày khám lịch sử vẫn trống. Các lô năm 2025 và bổ sung 2024 không có lỗi năm chờ xác minh được giữ nguyên.

Đã đối chiếu từng payload/ID/trạng thái với DB restore trước thay đổi; chứng cứ riêng ở `year-confirmation-proof.json`. Số lượt/kết quả đã nhập vẫn 83/2.216, 936 dòng nguồn còn chờ, công bố 0. Không tự xác nhận người hoặc kết quả. Test bổ sung kiểm tra loại bỏ đúng lỗi năm, giữ lỗi I–V, date null, token đổi, retry không đổi version và rollback khi SID không khớp campaign; full suite mới 317 test đạt với 5 skipped baseline.


### Bổ sung công cụ review khi triển khai Phase 4

Bộ lọc **Chỉ dòng còn chờ rà soát** chạy tại DB trước phân trang, giúp tìm ngay 2 dòng năm 2025 còn chờ. Có thể chuyển All/Đã nhập/Đã hủy; đổi bộ lọc xóa lựa chọn chưa lưu, không xóa payload hoặc lô. Cửa sổ ghép/đối chiếu hiện thêm raw ngày/năm sinh trong nguồn, giữ độ chính xác chỉ có năm. Nền Phase 3 đã có công cụ; bước nghiệm thu 936 dòng nguồn vẫn do quản trị thực hiện, không tự ghép chỉ theo tên. Hồ sơ/so sánh Phase 4 chỉ đọc kết quả đã nhập, không kéo staging thành kết quả tự động.

Bản ghép Phase 4 đã chạy full backend 327 bài (5 skipped baseline), riêng bộ UI Phase 3 5 nhóm đạt sau khi thêm pending filter. Tổng dữ liệu baseline vẫn giữ nguyên; kết quả đã nhập 83 lượt, không có công bố. [Bàn giao hồ sơ nội bộ Phase 4](EMPLOYEE_HEALTH_PHASE4_DEV.md).
