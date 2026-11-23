# CFCBase / CFC Management

Hệ thống quản lý nhân sự nội bộ: hồ sơ, thử việc, biến động, chấm công, thưởng ca đêm, hợp đồng, phiếu lương và sổ sức khỏe. Module hạ tầng mạng dành ADMIN đã có nền tảng; kết nối thiết bị thật/camera còn cần nghiệm thu. Booking phòng/xe là legacy/frozen.

- **Bắt đầu đọc:** [Mục lục và trạng thái toàn dự án](documents/README.md).
- **Kiến trúc/nghiệp vụ:** [Master Context](documents/PROJECT_MASTER_CONTEXT.md).
- **Vận hành Fedora:** [CI/CD](documents/DEPLOYMENT_CICD_PLAN.md), [backup/restore](documents/DATABASE_BACKUP.md).
- **Sổ sức khỏe:** [Tổng hợp triển khai](documents/EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md), [tự nhập lịch sử và chọn lô rà soát](documents/EMPLOYEE_HEALTH_AUTO_IMPORT.md).
- **Trước khi sửa code:** [AGENTS.md](AGENTS.md).

Ngày23/11/2026, log/ảnh do người dùng gửi xác nhận production release04a663be, auto-deploy success và lịch sử sức khỏe83dòng nhập/936dòng nguồn chờ. Đây là mốc xác nhận theo thời điểm; kiểm tra trạng thái mới tại Fedora bằng runbook. Không đưa Excel y tế thật, khóa riêng, dump hoặc.env vào repository public.
