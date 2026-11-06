# Backup Và Restore Database CFCBase

Baseline lịch sử: **2026-09-01** (Production Baseline: 35 Bảng Master CSDL, 338 Nhân sự T8-2026, Cấu hình AI OCR `hr_system_settings`, Hồ sơ đa định dạng `hr_employee_documents`).

## 1. Chính sách hiện tại

- Backup logical toàn bộ MySQL `booking_db` (35 bảng + 12 Flyway migrations) mỗi giờ vào phút `05`.
- Systemd user linger đã bật cho `david-nguyen` / user vận hành, nên timer tiếp tục hoạt động sau logout và được khởi tạo lại sau reboot.
- Mỗi file backup chứa database/schema, tables, indexes, foreign keys, triggers, routines, events và toàn bộ data dạng INSERT (gồm cả 338 nhân sự chính thức, 3 ứng viên thử việc, cấu hình AI OCR, và binary HĐTV/HĐLĐ).
- Dùng `--single-transaction` để không dừng website và giữ snapshot nhất quán cho InnoDB.
- File được gzip, kiểm tra hợp lệ rồi atomic rename; bản cũ chỉ bị xóa sau khi bản mới thành công.
- Giữ đúng 24 bản gần nhất trong `backups/database/`; thư mục này bị Git ignore.
- Backup chứa dữ liệu thật, quyền file là `600`, quyền thư mục là `700`.

## 2. Lệnh vận hành

### 2.1 Backup thủ công:

```bash
./deployserver/linux/backup-database.sh
```

### 2.2 Xem timer và log backup:

```bash
systemctl --user list-timers bookingbase-backup.timer
journalctl --user -u bookingbase-backup.service
```

### 2.3 Restore từ một file backup:

```bash
./deployserver/linux/restore-database.sh backups/database/booking_db_YYYY-MM-DD_HH-MM-SS.sql.gz
```

> **Lưu ý an toàn:** Restore yêu cầu nhập chính xác chữ `RESTORE` và script sẽ tự động tạo một backup khẩn cấp ngay trước khi import. Sau khi restore, nên restart lại backend để làm mới persistence context/cache.

## 3. Cấu hình tùy chọn

- `BOOKINGBASE_BACKUP_DIR`: đổi thư mục lưu (mặc định: `backups/database/`).
- `BOOKINGBASE_BACKUP_KEEP`: số bản giữ lại (mặc định: `24`).
- `BOOKINGBASE_DB_CONTAINER`: tên container MySQL (mặc định: `booking_db`).
- `BOOKINGBASE_DB_NAME`: tên CSDL (mặc định: `booking_db`).

Không truyền password trong command line. Script đọc `MYSQL_USER`/`MYSQL_PASSWORD` bên trong container và dùng `MYSQL_PWD` chỉ trong process con.

## 4. Danh sách các bảng quan trọng được sao lưu:

1. `hr_employees` (338 nhân sự chính thức).
2. `hr_employee_employment` (Phòng ban, chức vụ, mức lương, phụ cấp, hợp đồng).
3. `hr_employee_identity`, `hr_employee_insurance`, `hr_employee_contacts`, `hr_employee_leave_entitlements`.
4. `hr_system_settings` (Cấu hình động AI OCR Gemini & Groq).
5. `hr_employee_documents` (Hồ sơ, bằng cấp, chứng chỉ Word/PDF/Excel/Ảnh).
6. `hr_probation_candidates` & `hr_probation_contracts` (Ứng viên thử việc và file Word HĐTV).
7. `hr_employment_contracts` (Hợp đồng lao động chính thức).
8. `hr_monthly_rosters` & `hr_monthly_roster_items` (Quân số các tháng T6, T7, T8).
9. `flyway_schema_history` (12 migration versions V1..V12).

## 5. Backup trước auto-deploy migration — 06/11/2026

Các số liệu bảng/nhân sự/version phía trên là baseline lịch sử, không phải kiểm kê production hiện tại. Bản deploy-agent bổ sung local dùng cùng script để backup sau build/smoke và trước khi thay JAR khi có migration. V33 thuộc nhóm được phép auto theo review tương thích ngược; xem [quy trình CI/CD](DEPLOYMENT_CICD_PLAN.md).

Dump bắt buộc gzip hợp lệ, có schema/table và footer mysqldump hoàn tất; pipeline lỗi hoặc dump thiếu footer bị loại, không kích hoạt ứng dụng mới và không xóa backup cũ. File mới có tên dạng `booking_db_YYYY-MM-DD_HH-MM-SS_NANOSECONDS.sql.gz`; script restore vẫn nhận cả tên cũ và mới qua đường dẫn tệp. Quyền 0600 được đặt từ lúc tạo; retention và lock dùng chung với backup định kỳ.

Backup khi ứng dụng cũ còn chạy là snapshot InnoDB; không tự khôi phục snapshot đó khi deploy lỗi vì có thể mất dữ liệu phát sinh sau snapshot. Rollback tự động chỉ áp dụng JAR; schema phải tương thích ngược và restore vẫn là thao tác chủ động theo quy trình. Với credential MikroTik mã hóa, phải backup riêng tệp khóa ngoài checkout cùng DB; dump không chứa khóa giải mã. Chưa xác minh dump/restore thực tế cho luồng mới trên Fedora.
