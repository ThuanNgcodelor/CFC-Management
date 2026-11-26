# Mục lục và trạng thái tài liệu CFCBase

Cập nhật 23/11/2026. Đây là điểm bắt đầu tra cứu; kiến trúc và quy tắc vẫn thuộc Master Context/AGENTS, cấu hình thuộc registry và quyết định thuộc decision log. Không gộp mọi phase thành một file khiến mất lịch sử hoặc biến test dev thành xác nhận production.

## Đọc theo nhu cầu

| Bạn cần | Mở tài liệu |
| --- | --- |
| Nắm toàn bộ hệ thống, phạm vi và quyền | [Master Context](PROJECT_MASTER_CONTEXT.md) |
| Xem sức khỏe đã làm tới đâu | [Tổng hợp triển khai sức khỏe](EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md) |
| Không thấy kết quả / không biết chọn lô nào | [Tự nhập lịch sử](EMPLOYEE_HEALTH_AUTO_IMPORT.md) |
| Push nhánh dev → PR/main → Fedora tự chạy | [CI/CD Fedora](DEPLOYMENT_CICD_PLAN.md) |
| DB dừng, backup lỗi hoặc cần phục hồi | [Backup/restore](DATABASE_BACKUP.md), mục vận hành đầu tài liệu CI/CD |
| Vận hành chấm công | [Cẩm nang ca sản xuất](WORKER_ATTENDANCE_USAGE_GUIDE.md) |
| Tìm lỗi search/filter bị giật | [Audit tìm kiếm chấm công](ATTENDANCE_SEARCH_AUDIT.md) |
| Xem việc còn thiếu và quyết định chưa chốt | [Nợ kỹ thuật](TECH_DEBT.md), [Quyết định](DECISIONS.md) |

## Trạng thái mới nhất có bằng chứng

Bằng chứng là log Fedora/ảnh người dùng gửi ngày 23/11; lần cập nhật tài liệu này không truy cập server, không sửa CSDL và không chạy lại kiểm thử ứng dụng.

- **Release:**04a663be4bd8e4a670a2a3da0fb7653c9fb5e52a; backend/tunnel active, MySQL/Redis chạy; deploy success, timer kiểm tra cùng SHA và không restart lại. Trùng main tại lần kiểm tra, không tự coi mọi HEAD tương lai đã deploy.
- **Backup:** trước kích hoạt JAR đã xuất gzip 18 MB; log ghi giữ 24 bản. Chưa thử restore production trong phiên này.
- **Sức khỏe:** giao diện quản trị đã lên server, gói lịch sử đã kích hoạt;83 dòng nhập và936 dòng nguồn chờ. 2025 Gián tiếp còn 2 dòng;2024 Tổng hợp 256 dòng;2023 Tổng hợp 208 dòng. Tổng dòng bao gồm các sheet đối chiếu lặp, không phải tổng người.
- **Cá nhân:** chức năng tra cứu đã có code; cổng mặc định OFF, không tự cấp mã/công bố. Chưa có bằng chứng mở cổng hoặc công bố kết quả thật. Chưa có file khám 2026.
- **Hạ tầng:** nguồn code/màn hình không đồng nghĩa đã kết nối router/switch/Wi-Fi thật. Camera là roadmap.
- **Kiểm thử gần nhất đã ghi nhận20/11:** backend460 test,0failure/error,55 skipped;50 MySQL opt-in chạy riêng đạt, restore QA 79 bảng khớp checksum;52 deploy Python / 30 Node đạt; frontend build đạt, lint 0 lỗi / 10 cảnh báo cũ; MCP 9 nhóm luồng lịch sử và 24 nhóm hồi quy. Không gọi đây là lần test23/11.

## Tài liệu nền và bảo trì

| Tài liệu | Vai trò |
| --- | --- |
| [Master Context](PROJECT_MASTER_CONTEXT.md) | Kiến trúc, luồng nghiệp vụ, quyền, trạng thái theo mốc; ưu tiên bảng hiện hành ở đầu |
| [Quy tắc repository](../AGENTS.md) | Workflow sửa code, an toàn dữ liệu, test và cập nhật docs |
| [Cấu hình/cache](CONFIG_AND_CACHE.md) | Flags, key-file, timeout, giới hạn, vòng đời; không chứa secret |
| [Decision log](DECISIONS.md) | Lý do lựa chọn, ngoại lệ đã được duyệt và các điểm còn mở |
| [Technical debt](TECH_DEBT.md) | Hạng mục chưa xử lý; các ghi chú audit cũ không tự phản ánh source hiện tại |
| [Knowledge graph](KNOWLEDGE_GRAPH.md) | Graphify để tìm quan hệ, CodeGraph để tra source; graph không phản ánh DB live |

## Nhân sự, chấm công và giao diện

| Tài liệu | Nội dung / giới hạn |
| --- | --- |
| [Kế hoạch HR reporting/UX](HR_REPORTING_UX_PLAN.md) | Dashboard, Chờ tiếp nhận/Dừng tiếp nhận, Tăng/Giảm, giữ filter/Back, quân số và nghỉ việc; không mở rộng tính lương |
| [Các phase HR reporting/UX](HR_REPORTING_UX_PHASES.md) | Checklist và bằng chứng theo từng phase; nghiệm thu báo cáo còn đối chiếu riêng |
| [Cẩm nang ca sản xuất](WORKER_ATTENDANCE_USAGE_GUIDE.md) | Import → ghép ca → rà soát → chốt → xuất Excel; giữ bất biến giờ gốc |
| [Thử nghiệm chấm công](HR_ATTENDANCE_SANDBOX.md) | Dùng chung logic/UI chính, kết quả thử không ghi bảng công; có xuất đối chiếu |
| [Audit search/filter](ATTENDANCE_SEARCH_AUDIT.md) | Race URL/debounce/response; bỏ polling 30 giây; bằng chứng hồi quy |
| [Nhập lương lãnh đạo](HR_LEADERSHIP_PAYROLL_IMPORT.md) | Mẫu Ban điều hành, số/cached formula, preview/PDF riêng, chặn dòng lỗi; dev25/11 |
| [OCR điện thoại](HR_GENERAL_LABOR_OCR_CAPTURE.md) | Chụp CCCD từ điện thoại cho LĐPT, phiên/khôi phục/bảo vệ dữ liệu |
| [Mẫu Word/ONLYOFFICE](HR_WORD_EDITOR_SETUP.md) | Editor tắt/ẩn, DocumentServer không khởi động; xuất hợp đồng có sẵn vẫn dùng |

## Sổ sức khỏe

Đọc tổng hợp và tự nhập trước; các file `PHASE*_DEV` giữ bằng chứng theo ngày, không phải checklist phải làm lại từ đầu trên server. Các mô tả gate nội bộOFF/chưa deploy ở mốc cũ đã được thay bởi cập nhật20–23/11; cổng cá nhân vẫn là gate riêng.

| Tài liệu | Nội dung |
| --- | --- |
| [Kế hoạch tổng thể](EMPLOYEE_HEALTH_PLAN.md) | Phạm vi, nhóm trực tiếp/gián tiếp, công bố, tra cứu, báo cáo và phase 0–9 |
| [Phân tích dữ liệu](EMPLOYEE_HEALTH_DATA_ANALYSIS.md) | Cấu trúc workbook 2023–2025, rủi ro định danh/SID/sheet, nguồn ban đầu |
| [Tổng hợp triển khai](EMPLOYEE_HEALTH_IMPLEMENTATION_SUMMARY.md) | Trạng thái hiện tại, phần đã làm, giới hạn và nghiệm thu còn lại |
| [Tự nhập lịch sử](EMPLOYEE_HEALTH_AUTO_IMPORT.md) | Khóa một lần, dữ liệu mã hóa theo release, chống trùng, chọn lô và quản lý khóa |
| [Phase 1](EMPLOYEE_HEALTH_PHASE1_DEV.md) | Nền schema/danh mục/API/quyền/menu |
| [Phase 2](EMPLOYEE_HEALTH_PHASE2_DEV.md) | Import linh hoạt, ánh xạ, staging, xác minh người, KEEP/REPLACE |
| [Phase 3](EMPLOYEE_HEALTH_PHASE3_DEV.md) | Baseline lịch sử, năm theoSID, ghép nhân sự tối thiểu và xử lý dòng chờ |
| [Phase 4](EMPLOYEE_HEALTH_PHASE4_DEV.md) | Hồ sơ, timeline, so sánh cùng người nhiều năm/lượt |
| [Phase 5](EMPLOYEE_HEALTH_PHASE5_DEV.md) | Báo cáo, Excel cá nhân/tập thể và PDF; duyệt mẫu desktop còn riêng |
| [Phase 6](EMPLOYEE_HEALTH_PHASE6_DEV.md) | Rà soát/công bố/thu hồi theo snapshot và phiên bản |
| [Phase 7](EMPLOYEE_HEALTH_PHASE7_DEV.md) | Hai cách tra cứu độc lập, cấp/thu hồi mã, phiên riêng và giới hạn quyền |
| [Phase 8](EMPLOYEE_HEALTH_PHASE8_DEV.md) | MySQL/upgrade/restore/HTTP dev; checklist rollout và pilot |
| [Phase 9](EMPLOYEE_HEALTH_PHASE9_DEV.md) | Chuẩn bị mẫu bệnh viện2026 thay đổi bằng fixture; chờ file thật |

Không đưa kết quả bệnh án, CCCD, workbook gốc hoặc khóa vào docs/Git. Không kết luận bệnh hay coi ô thiếu là bình thường. Những con số83/936 là trạng thái ảnh theo thời điểm, không hard-code vào chương trình.

## Triển khai, dữ liệu và hạ tầng

| Tài liệu | Nội dung / trạng thái |
| --- | --- |
| [CI/CD Fedora](DEPLOYMENT_CICD_PLAN.md) | Pull main sau CI, khóa đồng thời, backup, health-check, rollback JAR và retry sau lỗi |
| [Backup database](DATABASE_BACKUP.md) | Logical backup toàn DB, retention 24 bản, restore có xác nhận; khóa ngoài DB cần backup riêng |
| [Mạng và camera](NETWORK_AND_CAMERA_INTEGRATION_PLAN.md) | MikroTik/Ruijie, uptime/log/IPAM/topology/tài sản; sourcePhase 1–6, pilot thiết bị thật chưa xác nhận; camera để sau |

## Kiểm thử và tài liệu hỗ trợ ngoài documents

| Tài liệu | Phạm vi |
| --- | --- |
| [Frontend tests](../frontend/tests/README.md) | Harness/MCP search, mapping, healthHTTP và dữ liệu giả; không dùng làm nghiệm thu production |
| [HR verification](../scripts/verification/README.md) | Playwright fixtures và checklist UX nhân sự |
| [Backend conventions](../skills/backend-springboot.md) | Tài liệu hỗ trợ; đối chiếu AGENTS/master khi cũ |
| [Frontend conventions](../skills/frontend-react-ui.md) | Tài liệu hỗ trợ React/UI; không thay quyết định stack hiện hành |
| [Design](../skills/desige.md) | Nguyên tắc thiết kế |
| [Booking architecture cũ](../skills/architecture.md) | Lịch sử domain Booking, không phải roadmap HR hiện hành |
| [Booking/mail plan cũ](../skills/mail.md) | Kế hoạch lịch sử, không tự mở lại Booking hoặc đổi cơ chế thông báo |
| [README dự án](../README.md) | Lối vào repository |

## Phần còn cần làm rõ/nghiệm thu

1. Sức khỏe: rà soát936 dòng nguồn, liên kết đúng người và giải quyết chênh lệch, duyệt mẫu xuất; không duyệt thay người quản lý dữ liệu.
2. Trước mở tra cứu cá nhân: công bố có kiểm soát, cấp quyền/mã, xác minh HTTPS/Cloudflare/IP limiter, chính sách lưu trữ và backup khóa. Chưa tích hợp VNeID/Bộ Công an/cổng sức khỏe quốc gia.
3. File khám2026 thật: kiểm tra sheet/tiêu đề/chỉ tiêu mới, dry-run và xem trước; phần mềm sẵn sàng không đồng nghĩa có dữ liệu năm 2026.
4. Mạng: nghiệm thu một MikroTik read-only, rồi DHCP/ARP/syslog; Ruijie/AP/topology/Wi-Fi/camera cần khảo sát riêng.
5. Vận hành: xác định nguyên nhân DB/Redis/backend đã dừng trước23/11, kiểm tra phục hồi sau reboot và restore/rollback có kiểm soát. Không suy nguyên nhân từ `Exited(0)`.
6. Báo cáo quân số/nghỉ việc và performance các module: theo checklist HR và TECH_DEBT; deploy sức khỏe không chứng minh đã nghiệm thu mọi màn.

## Cách duy trì docs

- Cập nhật trạng thái/bằng chứng ở master và trang tổng hợp module; ghi ngày, môi trường và nguồn xác nhận.
- Giữ kiểm thử dev, trạng thái production và quyết định nghiệp vụ tách rõ. Không xóa lịch sử chỉ vì trạng thái đã đổi.
- Thay config/timeout/cache → registry; thay quyết định → decision log; còn vấn đề chưa xử lý → technical debt.
- Nguồn sự thật là code/config/schema hiện tại. Link file thiếu phải sửa hoặc ghi rõ chưa có, không tạo nội dung thay thế bằng suy đoán.
- Tệp Excel/Markdown chứa dữ liệu sức khỏe gốc bị ignore, không phải tài liệu phát hành; artifact/cache graphify cũng không đưa vào mục lục versioned.
