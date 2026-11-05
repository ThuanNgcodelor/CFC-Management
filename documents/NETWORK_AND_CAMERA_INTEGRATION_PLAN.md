# Kế hoạch đề xuất: Quản lý hạ tầng mạng và camera

> Trạng thái: Phase 1–3 đã có nền tảng local trong CFCBase; chưa deploy, chưa bật polling, chưa khởi chạy Loki/Alloy và chưa kết nối/đổi cấu hình router thật. Xem ghi chú thực hiện ở cuối tài liệu.
> Ngày lập: 04/11/2026 (Asia/Ho_Chi_Minh)
> Mục tiêu: giúp người phụ trách không chuyên sâu về mạng có một nơi để biết hạ tầng nào đang chạy, thiết bị nào kết nối ở đâu, lỗi nào cần xử lý và xem camera được phép xem mà không phải mở từng công cụ riêng.

## 1. Tóm tắt hướng đi

Đề xuất tạo phân hệ **Hạ tầng IT** gồm hai khu vực có liên kết nhưng phân quyền riêng:

1. **Mạng & thiết bị:** MikroTik, router/firewall khác, switch, access point, đường truyền, VLAN, subnet, IP, máy trạm và nhật ký sự cố.
2. **Camera (để sau):** danh mục camera/NVR, vị trí, tình trạng, quyền xem live, và về sau tra cứu bản ghi nếu hệ thống ghi hình hiện tại có API phù hợp.

Không nên cố gắng biến CFCBase thành WinBox, NMS chuyên dụng và VMS/NVR đầy đủ ngay từ đầu. Nên làm một bảng điều khiển vận hành và danh mục tài sản trước; các công cụ hiện tại vẫn là nơi cấu hình thiết bị. Tích hợp đầu tiên chỉ đọc, giới hạn trong mạng nội bộ và có nhật ký truy cập.

Kết quả mong muốn: khi có báo “mất mạng”, người dùng có thể lần theo đường **ISP → router → switch tổng → switch tầng/phòng → AP/máy/camera**, thấy điểm đầu tiên mất liên lạc, các log gần thời điểm đó, thiết bị/IP liên quan và hướng xử lý. Độ chính xác của đường đi phụ thuộc thiết bị có bật LLDP/SNMP/API và mức độ được kiểm kê.

### Cập nhật theo thông tin người dùng — 04/11/2026

- Người dùng cho biết có MikroTik tại `192.168.1.1`, identity `CTYPHANBON`, board `L009UiGS`, uptime lúc chụp `51d 09:20:43`, RouterOS đang cài `7.16.2 (stable)`. WinBox hiển thị `7.25.1 available`; đây là bản mới có thể nâng cấp, không phải bản đang chạy. Chưa xác nhận thiết bị thứ hai có phải router MikroTik thứ hai không.
- Switch tổng là Ruijie; Wi‑Fi/AP dùng MikroTik. Cần model chính xác của switch và AP, có thể có khoảng 4–5 vị trí/điểm mạng (ý này cần xác nhận là site hay số thiết bị).
- Có server Linux riêng chạy trong mạng LAN. Đây là vị trí ứng viên cho collector nội bộ; Phase 0 cần kiểm tra server có route tới IP quản trị router/switch và có thể nhận syslog.
- Module nằm trong CFCBase, dùng phiên đăng nhập hiện tại; ban đầu chỉ ADMIN thấy nút/menu “Hạ tầng”, bấm sẽ mở route/trang riêng trong ứng dụng. Camera vẫn nằm trong kế hoạch tổng thể nhưng hoãn thực hiện tới khi phần mạng ổn.
- Người dùng đề cập dùng mật khẩu để đăng nhập RouterOS. Không dùng lại mật khẩu đăng nhập web CFCBase, không nhập mật khẩu router ở browser và không dùng account quản trị cá nhân cho collector. Pilot cần tạo account riêng read-only, giữ credential ở server/collector; người dùng đăng nhập CFCBase bằng account hiện tại.
- Log cần tra cứu khoảng 7 ngày. Với quy mô pilot hiện biết, khuyến nghị chưa dùng Kafka hoặc RabbitMQ. Dùng receiver syslog + searchable log store có retention 7 ngày; thêm disk buffer có giới hạn ở collector nếu store đích tạm thời không sẵn sàng. Kafka/RabbitMQ chỉ được xét lại khi đã đo throughput, cần fan-out/replay nhiều consumer, hoặc có yêu cầu durability/scale rõ.
- Mục tiêu pilot đầu tiên được đề xuất là **mạng chậm/mất và khoanh vùng điểm lỗi**: uptime/router health + probe Internet + log WAN/interface + tuyến RouterOS↔Ruijie core↔một AP. Tìm IP conflict là use case kế tiếp.

## 2. Phạm vi hiện tại và giả định

- CFCBase hiện là ứng dụng HR; tài liệu master ghi phạm vi phát triển chủ động chỉ gồm HR. Người dùng đã chọn hướng tích hợp vào cùng CFCBase, nhưng đây vẫn là kế hoạch, chưa có code hay kết nối thiết bị production.
- Đã biết một thiết bị MikroTik pilot, switch tổng Ruijie, AP MikroTik và server Linux trong LAN. Model Ruijie/AP, subnet/VLAN, đường đi Docker/backend và chính sách truy cập vẫn cần khảo sát.
- Tài liệu ảnh cho biết RouterOS `7.16.2 stable` đang chạy; `7.25.1 available` là thông báo phiên bản mới. Không tự nâng cấp router trong pilot; cần xem release notes, backup và lịch bảo trì nếu sau này muốn nâng cấp.
- Chưa xác nhận số site/điểm mạng: 4–5 có thể là số tòa nhà/điểm hoặc thiết bị.
- Backend Docker có thể ở LAN, nhưng cần xác minh chính container backend có route/ACL tới management IP không. Không suy ra rằng server LAN đồng nghĩa mọi container/VLAN đều kết nối được.
- Không đưa dữ liệu nhân sự HR sang hệ thống mạng ngoài mức cần thiết. Phòng ban/đơn vị, người phụ trách tài sản chỉ liên kết khi người quản trị xác nhận danh mục thiết bị.
- Giai đoạn đầu không thay cấu hình router/switch/camera, không chặn thiết bị tự động, không ghi hình mới, không công khai camera ra Internet.

## 3. Người dùng và kết quả họ cần

| Người dùng | Cần biết/làm gì | Quyền gợi ý |
|---|---|---|
| Người phụ trách IT | Theo dõi thiết bị, lỗi, IP, sơ đồ kết nối, camera; xác nhận sự cố và gán tài sản | Quản trị hạ tầng |
| Nhân viên IT trực | Xem trạng thái/log, tiếp nhận cảnh báo, ghi chú xử lý | Vận hành, không sửa cấu hình thiết bị |
| Trưởng bộ phận được duyệt | Xem trạng thái tổng quan hoặc camera tại phạm vi cho phép | Chỉ xem theo khu vực |
| ADMIN hệ thống | Quản lý tài khoản và quyền tích hợp | Quản trị ứng dụng; quyền camera không tự động cấp |

**Quyết định cho MVP:** chỉ `ADMIN` thấy nút/menu **Hạ tầng**; bấm mở route/page riêng bên trong CFCBase và dùng phiên đăng nhập CFCBase hiện tại. Không cần màn đăng nhập ứng dụng thứ hai. ADMIN là allowlist ban đầu; sau này nếu cần IT vận hành độc lập mới tách permission. Không cấp quyền camera trong MVP. Mật khẩu RouterOS là credential dịch vụ ở server, không phải mật khẩu để đăng nhập CFCBase và không gửi tới browser.

Về lâu dài, khi mở rộng khỏi một người quản trị, nên dùng permission riêng như `NETWORK_VIEW`/`NETWORK_ADMIN` thay vì cấp toàn bộ quyền HR ADMIN cho IT.

## 4. Những gì có thể lấy từ MikroTik và giới hạn

| Dữ liệu/câu hỏi | Nguồn khả thi | Điều kiện và giới hạn |
|---|---|---|
| Router đã chạy bao lâu | `/system/resource` có uptime, phiên bản, CPU, RAM, dung lượng | Đây là thời gian từ lần khởi động gần nhất; không thay thế uptime/availability được hệ thống giám sát đo qua thời gian |
| Router có đang truy cập được không | Polling API/SNMP/ICMP từ collector | Mất poll có thể do đường quản trị/ACL/collector, chưa đủ kết luận toàn công ty mất Internet |
| Log lỗi và thay đổi | RouterOS logging tới syslog tập trung; có topic/severity/time/message | Log trong RAM có giới hạn; chỉ thu thập được từ thời điểm cấu hình gửi đi, không tự khôi phục lịch sử cũ |
| DHCP cấp IP cho thiết bị nào | DHCP lease: IP, MAC, lease state, hostname nếu client gửi | Lease động đổi theo thời gian; hostname có thể trống/sai; không xác định chắc người đang dùng máy |
| Xung đột IP | DHCP conflict detection/log, ARP observations, cùng IP xuất hiện đồng thời với nhiều MAC | Cần gom bằng chứng và giữ trạng thái “nghi ngờ/cần xác minh”; IP đổi hoặc lease cũ không tự động là xung đột |
| MAC đang học ở cổng bridge nào | Bridge host table của thiết bị có chức năng bridge/switch và API tương ứng | Không phải mọi switch đều hiển thị qua router; hardware offload và mô hình VLAN có thể ảnh hưởng dữ liệu |
| Router/switch láng giềng nằm ở cổng nào | MNDP/CDP/LLDP neighbor discovery | Chủ yếu thấy thiết bị cùng broadcast domain hỗ trợ discovery; không dựng được đầy đủ topology qua thiết bị không hỗ trợ/không bật |
| Wi‑Fi client/AP | CAPsMAN, wireless registration, SNMP/API tùy model và cấu hình | Phụ thuộc thiết bị MikroTik, phiên bản RouterOS và cách triển khai Wi‑Fi; cần lab theo model thật |
| Lưu lượng theo cổng/link | SNMP counters/API polling và dữ liệu interface | SNMP polling thường cho mẫu theo chu kỳ, không phải packet capture; độ chi tiết cao làm tăng lưu trữ và tải |
| Internet/đường truyền đang lỗi | Probe nhiều điểm: gateway, DNS, IP ngoài, HTTP endpoint; trạng thái WAN và log | Phân biệt “router online nhưng ISP lỗi”, “DNS lỗi”, “dịch vụ đích lỗi” bằng nhiều probe độc lập |

RouterOS có API và REST API để ứng dụng giao tiếp; API có quyền điều chỉnh cấu hình nên kết nối phải dùng tài khoản riêng chỉ có quyền đọc ở MVP. SNMP phù hợp giám sát và vẽ số liệu, không nên bật quyền ghi cho collector. MikroTik nêu resource có uptime/CPU/RAM, logging gửi được remote syslog, DHCP server có conflict detection, bridge host cho biết MAC và interface, còn neighbor discovery tìm thiết bị cùng lớp 2 hỗ trợ MNDP/CDP/LLDP. Các chức năng này chỉ là nguồn dữ liệu; chúng không tự tạo bản đồ đầy đủ toàn doanh nghiệp. Tài liệu: [API](https://help.mikrotik.com/docs/spaces/ROS/pages/47579160/API), [SNMP](https://help.mikrotik.com/docs/spaces/ROS/pages/8978519/SNMP), [Resource](https://help.mikrotik.com/docs/spaces/ROS/pages/40992875/Resource), [Logging](https://help.mikrotik.com/docs/spaces/ROS/pages/328094/Log), [DHCP](https://help.mikrotik.com/docs/spaces/ROS/pages/24805500/DHCP), [Bridge host](https://help.mikrotik.com/docs/spaces/ROS/pages/328068/Bridging%2Band%2BSwitching), [Neighbor discovery](https://help.mikrotik.com/docs/spaces/ROS/pages/24805517/Neighbor%2Bdiscovery).

## 5. Khả năng quan sát mạng cần có

### 5.1 Trang Tổng quan hạ tầng

- Số thiết bị theo trạng thái: bình thường, cảnh báo, mất liên lạc, chưa kiểm kê.
- Internet/WAN theo từng đường truyền; gateway, DNS và một endpoint ngoài công ty.
- Thời gian hoạt động từ lần reboot gần nhất và biểu đồ availability theo ngày/tuần/tháng.
- CPU, RAM, nhiệt độ/điện áp nếu thiết bị có expose, dung lượng lưu trữ và tải từng interface.
- Cảnh báo mới nhất theo mức độ, site, thiết bị và thời điểm bắt đầu.
- Sơ đồ topology đơn giản có trạng thái từng node/link, có nhãn **tự phát hiện**, **đã xác minh IT** hoặc **ước lượng**.
- Không dùng một số “uptime công ty” duy nhất nếu nhiều site/WAN có trạng thái khác nhau.

### 5.2 Lịch sử uptime và availability

- Collector lấy heartbeat định kỳ; lưu trạng thái up/down, thời gian phản hồi, lỗi giao tiếp và phiên bản thiết bị.
- Lưu uptime RouterOS mà thiết bị báo cùng với lần reboot suy ra từ uptime giảm về thấp hoặc log reboot.
- Tách ba khái niệm: **uptime thiết bị từ lần boot**, **availability quan sát được bởi collector**, **availability Internet qua probe**.
- Thời gian mất liên lạc cần ghi rõ khoảng bắt đầu/kết thúc, độ trễ nhận diện theo chu kỳ poll, mất gói và mức tin cậy.
- Cho phép bảo trì có lịch để không báo outage giả trong thời gian IT chủ động tắt thiết bị.

### 5.3 Thu thập và phân loại log

- Nhận log từ RouterOS và sau đó từ switch, AP, firewall, server, UPS, NVR/camera nếu hỗ trợ syslog.
- Chuẩn hóa timestamp theo UTC trong lưu trữ, hiển thị theo Asia/Ho_Chi_Minh; lưu thiết bị, facility/topic, severity, message gốc, nguồn nhận và parser version.
- Bộ lọc theo thời gian, site, thiết bị, severity, topic, từ khóa/IP/MAC; có trang chi tiết liên kết tới thiết bị/sự cố liên quan.
- Quy tắc ban đầu dựa trên mẫu rõ ràng: reboot, interface link down/up, DHCP conflict, PPPoE/WAN down/up, authentication failure, disk/resource warning, mất SNMP/API, camera offline.
- Gom log lặp thành nhóm sự kiện (count, first seen, last seen), nhưng vẫn giữ log gốc và đường dẫn truy vết.
- Tạo timeline sự cố: trạng thái poll + logs + link/port events + DHCP/ARP + cảnh báo WAN quanh cùng một khoảng thời gian.
- Không gọi một dòng log đơn lẻ là “nguyên nhân gốc”; giao diện phân biệt **triệu chứng**, **bằng chứng**, **giả thuyết**, **nguyên nhân đã xác nhận**.
- MVP không cần AI sinh kết luận. Có thể thêm tóm tắt AI sau khi dữ liệu, phân quyền, lọc bí mật và nguồn trích dẫn đã ổn.

RouterOS hỗ trợ remote logging; tài liệu hiện hành ghi log có thể gửi remote và RouterOS 7.18 thêm CEF/timestamp mili giây. Tài liệu cũng lưu ý TCP/TLS chỉ hoạt động với CEF; định dạng syslog vẫn dùng UDP. Vì vậy phải kiểm tra chính xác RouterOS đang dùng và thử nhận log trong VLAN quản trị trước khi chốt giao thức/bộ thu nhận. Tài liệu: [RouterOS Log](https://help.mikrotik.com/docs/spaces/ROS/pages/328094/Log).

### 5.4 Tìm lỗi trùng IP và vấn đề địa chỉ

- Tạo IPAM: site → VLAN/subnet → IP → trạng thái (available/reserved/leased/static/unknown), gateway, DHCP scope, DNS, mục đích sử dụng.
- Nối quan sát DHCP lease, ARP, MAC table, switch port và danh mục tài sản vào một thực thể thiết bị có lịch sử.
- Phát hiện candidate conflict khi cùng IP được gắn với hai MAC trong thời gian chồng lấp, DHCP báo conflict, hoặc nhiều ARP observation bất thường.
- Hiển thị bằng chứng: MAC A/B, interface/VLAN, lần đầu/cuối thấy, lease và log liên quan; có nút xác minh false positive/true incident.
- Lịch sử IP theo thời gian để tra “ngày hôm qua IP này là của ai/thiết bị nào”.
- Hỗ trợ import subnet/IP/asset từ CSV/Excel trước khi có tích hợp tự động.
- Không tự đổi IP, xóa lease hoặc chặn MAC trong các phase đầu.

### 5.5 Xác định switch tổng ở đâu và kết nối qua cổng nào

- Tạo danh mục thiết bị mạng: role (core/distribution/access/router/AP), hostname, model, serial, management IP, firmware, site/tủ/rack/phòng, nguồn điện/UPS, người phụ trách, uplink và ghi chú.
- Lưu liên kết thiết bị-cổng: local device/port ↔ remote device/port, VLAN/trunk, tốc độ, LACP/PoE, nguồn dữ liệu, lần phát hiện và người xác nhận.
- Tự phát hiện qua LLDP/MNDP/CDP nếu bật trên các thiết bị; qua SNMP lấy interface, link state, FDB/MAC table và counters khi thiết bị expose được.
- RouterOS bridge host có thể cho thấy MAC trên interface của MikroTik; nó không luôn xuyên qua được các switch phía sau để biết cổng access cuối cùng.
- Nếu switch tổng là unmanaged hoặc không expose LLDP/SNMP, cần kiểm kê thủ công và xác nhận bằng sơ đồ/cáp thực tế. Giao diện phải đánh dấu đường đi chưa kiểm chứng.
- Bản đồ topology khởi đầu dạng cây/đồ thị 2D có zoom, search, legend và click vào thiết bị/port; không cần sơ đồ mặt bằng GIS ngay.
- Quy trình khám phá: import thiết bị → poll an toàn → xem candidate links → IT xác nhận/chỉnh nhãn → đưa vào sơ đồ chính thức.

### 5.6 Danh mục máy tính và phòng ban

- Tách **network identity** (MAC/IP/hostname quan sát được) khỏi **asset record** (mã tài sản, loại máy, serial, phòng ban, vị trí, người quản lý).
- Mapping có nguồn và thời hạn hiệu lực; một thiết bị có thể có nhiều MAC/network interfaces.
- Đưa thiết bị mới/không khớp vào “Chưa nhận diện”; tránh gán nhầm nhân viên chỉ từ DHCP hostname.
- Gán phòng ban trước, người giữ máy là tùy chọn và chỉ hiển thị cho vai trò phù hợp.
- Có import Excel, hợp nhất duplicate có audit, phân biệt máy cá nhân/BYOD, server, printer, camera, IoT.
- Không quét sâu nội dung máy người dùng hoặc thu thập lịch sử web trong mục tiêu này.

## 6. Phân hệ camera

### 6.1 Quản lý danh mục trước

- Camera/NVR: mã, hãng, model, serial, IP quản trị, MAC, firmware, vị trí vật lý, khu vực giám sát, NVR/channel, VLAN, trạng thái, profile ONVIF, codec/resolution nếu xác định được.
- Bản đồ vị trí có tên khu vực và hướng nhìn mô tả; không hiển thị công khai cho mọi role.
- Trạng thái online/last seen, lỗi xác thực, video stream health, thời gian NVR, dung lượng/retention nếu API cho phép.
- Camera/NVR được gắn với thiết bị mạng và switch port khi có dữ liệu; từ camera có thể lần ra port/VLAN/uplink.

### 6.2 Xem live video từ IP

- Chỉ biết IP chưa đủ. Cần biết model/firmware, credentials, ONVIF, stream URI/protocol, codec và camera có đi qua NVR hay không.
- Nếu camera/NVR hỗ trợ ONVIF, dùng discovery trong VLAN phù hợp và kiểm tra profile thực tế. Profile T hướng tới stream nâng cao H.264/H.265 và events; Profile G liên quan ghi/tra cứu recording; hỗ trợ phụ thuộc thiết bị và client.
- Nhiều camera cung cấp RTSP URI, nhưng trình duyệt web không nên được coi là RTSP player mặc định. Cần kiểm thử thiết bị thực; nếu cần xem trong CFCBase, dùng NVR/VMS hiện hữu hoặc một gateway stream nội bộ có chuyển RTSP sang WebRTC/HLS.
- Không đưa RTSP URL/username/password vào URL trang, frontend bundle, logs hoặc browser local storage. Backend/gateway giữ credential và cấp session/token ngắn hạn.
- Mặc định stream qua LAN/VPN nội bộ; không mở port forwarding từ camera/NVR tới Internet và không proxy video ra Cloudflare công khai nếu chưa có security review/thiết kế băng thông.
- Mặc định substream cho grid nhiều camera; main stream chỉ khi mở một camera và theo quyền. Giới hạn số luồng đồng thời, timeout và idle auto-close để không làm nghẽn mạng/NVR.
- Có nút “Mở trên phần mềm hiện tại” nếu hệ thống vendor đã có client ổn định; đây có thể là MVP nếu browser streaming tốn công.

ONVIF định nghĩa các profile để tăng tương thích nhưng không đảm bảo mọi hãng/model hỗ trợ cùng tính năng. Profile G dành cho quản lý recording/storage; Profile T hỗ trợ advanced streaming; ONVIF đang khuyến nghị chuyển khỏi cơ chế username-token cũ của Profile S và cho biết Profile S sẽ ngừng nhận sản phẩm/firmware mới chứng nhận từ 31/03/2027. Cần kiểm tra model trong danh sách conformant và thử lab trước khi mua/thiết kế tích hợp. Tài liệu: [Profile G](https://www.onvif.org/profiles/profile-g/), [Profile T overview](https://www.onvif.org/wp-content/uploads/2026/04/onvif-overview-202604.pdf), [Profile S deprecation Q&A](https://www.onvif.org/profiles/profile-s/profile-s-deprecation-qna/), [ONVIF product conformance](https://www.onvif.org/).

### 6.3 Bản ghi, quyền riêng tư và audit

- Đợt đầu chỉ mở live view, không tự ghi bản sao video trong CFCBase.
- Nếu cần playback, tích hợp NVR/VMS qua API/ONVIF Profile G hoặc mở ứng dụng chính thức; NVR tiếp tục là kho bản ghi nguồn.
- Ghi audit: ai xem camera nào, lúc nào, trong bao lâu, live/playback, nguồn IP/người dùng, kết quả truy cập. Không ghi video stream hoặc thông tin xác thực vào audit.
- Quyền theo khu vực/camera và mục đích công việc; camera khu vực nhạy cảm cần nhóm được duyệt riêng. Nên yêu cầu lý do khi xem playback/xuất clip.
- Retention video và log/audit phải do công ty/pháp chế/IT chốt; không tự đặt thời hạn chính thức trong code.
- Không nhận diện khuôn mặt, theo dõi nhân viên hay phân tích hành vi trong phạm vi kế hoạch này.

## 7. Kiến trúc kỹ thuật đề xuất

```text
RouterOS + Ruijie core + MikroTik APs (camera/NVR deferred)
        │  API/SNMP read-only, syslog (LAN/management VLAN only)
        ▼
Collector trên Linux LAN (separate container/service)
        ├── telemetry/status ──HTTPS──▶ CFCBase backend
        └── syslog ────────────────▶ log store nội bộ (7-day retention)
        ▼
CFCBase: ADMIN-only “Hạ tầng” route → backend-authenticated status/log queries
         Camera/NVR path reserved for later phase
```

- **Collector nội bộ** chạy trên server Linux hiện có dưới dạng service/container tách biệt. Trước hết kiểm tra route/firewall từ host và container tới `192.168.1.1` và switch Ruijie. Collector chỉ gửi dữ liệu cần thiết tới backend qua TLS outbound; không mở API/SNMP inbound từ Internet.
- CFCBase dùng đăng nhập hiện có; ADMIN-only menu/route riêng gọi backend có kiểm tra role. Backend truy vấn thiết bị/log store; không expose RouterOS credential hoặc Loki/API key tới trình duyệt.
- Poller nhỏ đọc một MikroTik và một Ruijie switch là đủ cho pilot. Nếu hãng Ruijie có controller/API hiện hữu, ưu tiên API/controller đó trước khi tự viết SNMP parser.
- Không chọn thêm broker hoặc nền tảng chỉ vì “sau này có thể cần”. Ước lượng số thiết bị × tần suất poll × metric/log/day rồi mới mở rộng.
- Chuẩn hóa sự kiện theo nguồn: `observed_at`, `received_at`, `device_id`, `source`, `severity`, `event_type`, `raw_payload_ref`, `correlation_id`. Tách event logs khỏi asset catalog.
- Đồng bộ giờ NTP là điều kiện bắt buộc để ghép log/poll/video events chính xác.

### 7.1 Lưu log 7 ngày: Kafka, RabbitMQ hay log store?

**Khuyến nghị hiện tại: chưa triển khai Kafka hoặc RabbitMQ.** Với một pilot gồm khoảng một MikroTik, một switch tổng và vài AP, nhu cầu là nhận log, tìm kiếm, lọc theo thiết bị/thời gian và tự xóa sau 7 ngày. Broker không thay thế công cụ tìm kiếm/lưu log; Kafka vẫn cần consumer và log store/query layer, còn RabbitMQ chủ yếu dùng để giao message/job. Kafka có thể cấu hình retention theo thời gian (mặc định topic thường là 7 ngày trong tài liệu hiện hành), nhưng đó không phải lý do đủ để thêm cả cluster/vận hành Kafka. RabbitMQ cũng có TTL/stream retention nhưng sẽ vẫn cần giao diện truy vấn phù hợp.

**Phương án pilot để đánh giá:** RouterOS gửi syslog tới Grafana Alloy trên Linux LAN; Alloy nhận syslog và gửi vào một Loki nội bộ; retention cấu hình 168 giờ; CFCBase gọi log API qua backend với phạm vi ADMIN. Loki được dùng làm searchable log store, không phải message broker. Thêm disk-backed buffer/queue giới hạn tại collector để giảm rủi ro khi Loki tạm ngừng, theo dõi queue full/drop và dung lượng ổ đĩa. Trước khi chốt, đo lượng log/ngày và kiểm thử xóa retention thực sự.

**Giới hạn do phiên bản hiện tại:** RouterOS `7.16.2` trong ảnh không thể mặc định dùng CEF/TLS logging. Tài liệu MikroTik ghi remote `syslog` dùng UDP; TCP/TLS chỉ hoạt động với CEF, và CEF được thêm từ RouterOS 7.18. Vì vậy pilot có thể phải dùng UDP trong VLAN quản trị với ACL chỉ nhận từ IP router, NTP đồng bộ, log volume vừa phải và cảnh báo mất luồng. Không nâng cấp router chỉ để lấy TLS trước khi xem release notes, tạo backup và lên maintenance window. Tài liệu: [MikroTik Log](https://help.mikrotik.com/docs/spaces/ROS/pages/328094/Log), [Kafka retention](https://kafka.apache.org/26/generated/topic_config.html), [RabbitMQ queues/TTL](https://www.rabbitmq.com/docs/queues), [Grafana Alloy syslog receiver](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.syslog/), [Loki retention](https://grafana.com/docs/loki/latest/operations/storage/retention/).

Kafka chỉ quay lại phương án nếu phép đo chứng minh cần replay dài hạn, nhiều consumer độc lập hoặc tải lớn/cao điểm; RabbitMQ phù hợp hơn nếu về sau cần xử lý job/cảnh báo bất đồng bộ có retry. Cả hai sẽ cần quyết định kiến trúc, giám sát broker, disk sizing, backup và vận hành; không nằm trong MVP.

## 8. Bảo mật và vận hành

- Tài khoản API/SNMP riêng cho collector, quyền read-only, bind allowlist IP collector, tắt service không cần, không dùng tài khoản admin chung.
- SNMPv3 auth/privacy ưu tiên khi thiết bị hỗ trợ; nếu chỉ có SNMPv2c thì community riêng, chỉ qua management VLAN, firewall ACL chặn mọi nguồn khác.
- Không bật API/SNMP/WinBox trên WAN. Quản trị từ xa qua VPN/management network. MikroTik khuyến nghị giới hạn dịch vụ/quyền truy cập IP; áp dụng từng bước và có backup/rollback để tránh tự khóa quản trị.
- Secrets lưu phía server bằng cơ chế phù hợp triển khai hiện tại; mã hóa at-rest nếu bảng DB dùng để lưu credentials, che secret khỏi log/API export. Rotation, người thay đổi và ngày thay đổi được audit.
- Camera credentials tách riêng khỏi router credentials; user viewer/read-only nếu hãng/NVR có; không chia sẻ admin account.
- Mọi thay đổi cấu hình tích hợp (host, port, community, API account reference, polling interval) qua ADMIN_HẠ_TẦNG; mọi lần xem camera và xuất log có audit.
- Bảo vệ chống SSRF: địa chỉ thiết bị chỉ chọn từ dải private/allowlist được IT khai báo; validate route/IP/port ở backend; không cho nhập URL tùy ý để backend truy cập.
- Rate limit poll và stream; circuit breaker/backoff khi thiết bị lỗi; không để một router treo làm nghẽn worker/API HR.
- Tách module/permission và network workers khỏi HR; không làm giảm availability của login, chấm công hoặc payroll khi monitoring bị lỗi.
- Có backup cấu hình thiết bị do công cụ hiện có thực hiện; kế hoạch này không thay thế backup/restore RouterOS/NVR.

## 9. Kế hoạch theo phase và cổng nghiệm thu

### Phase 0 — Khảo sát thực tế và thiết kế an toàn

**Việc làm:** xác minh RouterOS `7.16.2` trên `192.168.1.1` (L009UiGS, identity CTYPHANBON); xem có một hay hai router; lấy model/version Ruijie và MikroTik AP; xác định 4–5 là site, tủ hay thiết bị; kiểm tra VLAN/subnet/DHCP, WAN, topology hiện hữu, Docker network mode và route từ Linux server/container. Xác nhận account read-only, ACL, retention 7 ngày và mức độ được phép dùng syslog UDP. Camera/NVR chưa khảo sát ở phase này.

**Bàn giao:** sơ đồ as-is cho tuyến MikroTik → Ruijie core → một AP, bảng thiết bị, port/protocol matrix, data classification, kiểm tra connectivity từ collector, ước lượng log/metric và retention.

**Cổng:** xác nhận quyền truy cập, backup hiện tại, allowlist, log protocol/version, rollback và test account read-only; nếu chưa xác minh được thì chỉ làm catalog/manual demo.

### Phase 1 — Nền tảng phân hệ và danh mục hạ tầng

**Việc làm:** chỉ ADMIN thấy nút/menu **Hạ tầng** trong CFCBase; bấm mở route riêng, dùng phiên đăng nhập hiện tại. Tạo catalog cho router/switch/AP/đường truyền; topology thủ công có trạng thái verified; camera/NVR để sau.

**Nghiệm thu:** ADMIN nhập/sửa/ẩn catalog; backend bảo vệ API riêng; tìm kiếm/phân trang; audit tạo/sửa/ẩn; không đọc HR salary/PII; không cần kết nối MikroTik để demo.

### Phase 2 — MikroTik read-only và sức khỏe thiết bị

**Việc làm:** kết nối một router pilot; đọc system resource, version/uptime và interface state; poll trạng thái. Credential read-only.

**Nghiệm thu:** poll tắt mặc định, chu kỳ cấu hình giới hạn 15–300 giây; lỗi mạng/auth/TLS hiện trạng tường minh; RouterOS HTTPS certificate validation giữ bật; không có thao tác ghi config. DHCP/ARP/neighbor, WAN probe, reboot-vs-Internet correlation và đo tải thật vẫn cần hoàn tất trong pilot tiếp.

### Phase 3 — Syslog tập trung, tìm log và timeline sự cố

**Việc làm:** có overlay Docker Compose tùy chọn Alloy UDP syslog → Loki; Loki dùng volume persistent và retention 168 giờ; API ADMIN truy vấn tối đa 7 ngày, giới hạn 200 dòng, lọc nội dung/IP.

**Nghiệm thu:** receiver mặc định chỉ bind loopback; cấu hình IP LAN/firewall và gửi syslog trên router là bước vận hành thủ công chưa thực hiện. Syslog UDP có thể mất gói; phân loại severity/facets, cảnh báo dung lượng, correlation và event thật chưa được nghiệm thu.

### Phase 4 — IPAM và phát hiện xung đột IP

**Đã triển khai bước đầu:** ADMIN có thể yêu cầu đọc DHCP lease và ARP qua RouterOS REST HTTPS, lưu lịch sử quan sát IP/MAC/hostname/interface, tìm kiếm, và xem ứng viên khi cùng IP có nhiều MAC trong 15 phút. Có thể ghi nhận `OPEN`, `CONFIRMED`, `NOT_CONFLICT`, `RESOLVED` kèm người xử lý; đây là luồng xác minh, không chặn IP. Bản ghi giữ `first_seen`/`last_seen`, chưa có chính sách purge.

**Còn lại:** subnet/VLAN scopes, nhận diện IP tĩnh không khai báo, giữ dấu vết tất cả lần cấp lại thay vì first/last seen, purge/retention, và kiểm thử cùng IP/NAT/VRRP trên dữ liệu thật. Chưa chạy trên router thật. Candidate chỉ dựa trên các quan sát mới trong 15 phút, nên không phải kết luận xung đột.

### Phase 5 — Bản đồ topology và switch/AP đa hãng

**Đã triển khai bước đầu:** ADMIN có thể khai báo đường nối thiết bị, cổng hai đầu, loại liên kết và VLAN tùy chọn; đánh dấu liên kết đã kiểm tra thủ công. Giao diện phân biệt liên kết chưa xác minh và xác minh thủ công.

**Còn lại:** SNMPv3, LLDP/CDP/MNDP, interface counters, FDB/MAC-port, CAPsMAN/client discovery, tự khám phá links và cảnh báo port down. Chưa biết model/firmware switch Ruijie nên chưa thêm MIB/OID hoặc tuyên bố đã kết nối thiết bị.

### Phase 6 — Danh mục máy trạm và phòng ban

**Đã triển khai bước đầu:** danh mục tài sản thủ công với mã tài sản, loại, tên, MAC/IP, phòng ban, vị trí, người phụ trách, trạng thái và ghi nhận lịch sử phân công khi cập nhật. Không tự gán nhân viên theo hostname.

**Còn lại:** mẫu CSV/Excel import cần chốt theo file thực tế; hàng đợi đối chiếu thiết bị chưa nhận diện; lịch sử liên kết IP/MAC/interface; audit phân công chi tiết; chính sách BYOD/MAC random/shared và quyền xem dữ liệu theo phòng ban. Chưa kiểm chứng với danh mục tài sản thật.

### Phase 7 — Camera inventory và trạng thái NVR/camera (để sau)

**Trạng thái:** chưa nằm trong pilot mạng đầu tiên; chỉ bắt đầu khi người dùng cung cấp hãng/model camera và NVR, chính sách xem và yêu cầu live/playback.

**Việc làm:** catalog camera/NVR; poll online/last seen và health metadata; ONVIF discovery trong VLAN; map camera→NVR channel→switch port→vị trí; mở client vendor hiện hữu.

**Nghiệm thu:** thử ít nhất một model camera và NVR; kiểm chứng ONVIF profile, codec, stream; viewer không nhìn thấy secret URI; audit mỗi lần truy cập; test user không có quyền bị 403.

### Phase 8 — Live view trong web và playback (chỉ khi cần)

**Việc làm:** thử nghiệm kỹ thuật trình duyệt trên model thực; quyết định NVR/VMS API hay gateway RTSP→WebRTC/HLS; token phiên; substream/grid; giới hạn stream đồng thời; playback theo retention NVR nếu hỗ trợ.

**Nghiệm thu:** đo latency/bandwidth, CPU/RAM gateway, số stream đồng thời; kiểm thử hết hạn token, revoke permission, mất kết nối, audit; xem được trên trình duyệt mục tiêu; không mở camera ra Internet.

### Phase 9 — Cảnh báo, sự cố và hardening

**Việc làm:** ngưỡng/rule do IT cấu hình; bảo trì; assign incident; ghi chú, nguyên nhân xác nhận, thời gian khắc phục; thông báo qua kênh hiện có sau khi chốt; backup, restore, disaster recovery, audit review.

**Nghiệm thu:** diễn tập WAN outage, switch down, IP conflict, camera offline, collector down; cảnh báo không spam; có hướng dẫn trực ca và rollback; tải của monitoring không ảnh hưởng HR.

## 10. Dashboard và quy trình sử dụng hàng ngày

### Màn hình đề xuất

1. **Tổng quan:** sites, Internet, thiết bị down, lỗi mức critical, availability, sự cố đang mở.
2. **Sơ đồ mạng:** tuyến từ ISP/router đến switch/AP/endpoint; port/VLAN và độ tin cậy của liên kết.
3. **Thiết bị:** inventory/filter theo loại, site, phòng ban, trạng thái, firmware, last seen.
4. **IP & VLAN:** subnet utilization, DHCP/static/unknown, conflict candidates, lịch sử cấp phát.
5. **Nhật ký:** tìm kiếm và lọc log; timeline; drilldown từ log đến thiết bị/sự cố.
6. **Sự cố:** danh sách, mức độ, người phụ trách, timeline, bằng chứng, resolution/runbook.
7. **Camera:** danh sách theo khu vực, health, mở live/client, audit truy cập; quyền riêng theo vùng.
8. **Cấu hình tích hợp:** endpoint/credential reference/polling status, test connection, last sync, không hiển thị secret.

### Khi người dùng báo “mạng phòng X chậm/mất”

1. Chọn site/phòng; xem endpoint/gateway/ISP probes và các thiết bị upstream.
2. Xác định node đầu tiên down/latency tăng và so sánh với switch/interface/PoE/AP.
3. Mở timeline cùng thời điểm để thấy link down/up, router reboot, DHCP, WAN, DNS events.
4. Nếu có client mapping, tìm máy/port/IP; nếu mapping thiếu, hiện “chưa xác định”.
5. Tạo incident, giao IT, ghi chẩn đoán/khắc phục; chỉ người có quyền mới thực hiện thao tác trên WinBox/VMS.

### Khi báo “đụng IP”

1. Mở conflict candidate, xác nhận IP/VLAN và khoảng thời gian.
2. Xem DHCP/ARP/MAC/port observations và log liên quan.
3. Liên hệ người phụ trách tài sản hoặc kiểm tra cổng; đánh dấu đúng/sai.
4. IT sửa DHCP/static IP trong công cụ quản trị hiện hành; CFCBase cập nhật trạng thái sau lần poll tiếp theo.
5. Ghi lại resolution và tránh lặp cảnh báo trong cùng một incident.

## 11. Dữ liệu, retention và chi phí

- Tách catalog (chậm đổi), telemetry (chu kỳ), logs (tần suất biến động), audit/security logs, và video (không lưu trong CFCBase ở MVP).
- Trước khi chọn retention phải đo: số thiết bị, metric per device, poll interval, log events/day, số người xem camera đồng thời, bitrate mỗi stream và hiện trạng NVR.
- Bắt đầu với poll interval vừa phải cho health (ví dụ 30–60 giây cho pilot, điều chỉnh sau đo), và ít thường xuyên hơn cho config/inventory. Đây là giả định thử nghiệm, không phải cam kết SLA.
- Giữ log ngắn hạn để tìm sự cố nhanh và archive theo yêu cầu pháp lý/vận hành nếu được duyệt; chưa đặt con số retention chính thức.
- Không lưu raw packet capture hay stream video mặc định. Packet capture chỉ thực hiện có phê duyệt, thời gian ngắn và khu vực giới hạn.
- Theo dõi dung lượng, drop rate, ingest lag, poll failure và collector health như các dịch vụ hạng nhất.

## 12. Rủi ro chính và giảm thiểu

| Rủi ro | Tác động | Giảm thiểu |
|---|---|---|
| Mở API/SNMP/camera port ra Internet | Chiếm quyền router/camera, lộ dữ liệu | Collector nội bộ, management VLAN, ACL/VPN, không port-forward |
| Tài khoản quá quyền | Sai cấu hình, mất mạng | User read-only, tách credentials, cấm config write trong MVP |
| Dữ liệu discovery không đầy đủ | Bản đồ gây hiểu nhầm | Hiển thị nguồn, last seen, verified/manual/unknown |
| DHCP/ARP thay đổi nhanh | Báo nhầm xung đột hoặc người dùng | Correlate theo time/VLAN/MAC và yêu cầu xác nhận |
| Logs thiếu/mất/reordered | Kết luận nguyên nhân sai | Đồng bộ NTP, received_at + event time, cảnh báo ingest gap; ghi rõ UDP loss |
| Camera stream làm nghẽn LAN/NVR | Ảnh hưởng ghi hình/ứng dụng | Substream, giới hạn đồng thời, pilot bitrate, auto-close |
| Lộ thông tin xem camera | Ảnh hưởng riêng tư/an ninh | Permission theo khu vực, audit, token ngắn hạn, không lưu URI có credential |
| Tự xây toàn bộ NMS/VMS | Tăng độ phức tạp cho dev một người | MVP nhỏ, đánh giá công cụ chuyên dụng trước khi viết poller/parser rộng |
| Tích hợp ảnh hưởng HR | Hệ thống chính gián đoạn | Worker/queueing tách biệt khi có nhu cầu, resource caps, circuit breakers, feature flag |

## 13. Những thứ không làm trong MVP

- Không thay WinBox, RouterOS WebFig, NVR/VMS hoặc hệ thống cấu hình hiện có.
- Không đổi firewall/VLAN/route/DHCP, không reboot, không khóa MAC/IP tự động.
- Không quét subnet tùy ý hay brute force credential.
- Không public camera/RTSP ra ngoài; không copy video vào hệ thống HR.
- Không chấm công bằng nhận diện khuôn mặt, không theo dõi hành vi cá nhân, không đọc lịch sử web.
- Không tự đưa “AI phân tích nguyên nhân” vào trước khi có dữ liệu chất lượng và evidence link.
- Không mua thêm thiết bị/phần mềm cho tới khi Phase 0 xác nhận khoảng trống và được duyệt ngân sách.

## 14. Câu hỏi cần chốt trước khi chuyển từ plan sang triển khai

### Hạ tầng mạng

1. Ngoài MikroTik tại `192.168.1.1`, có router MikroTik thứ hai không, hay ý “1 và 2” là một thiết bị/router và switch? Router này có phải gateway/DHCP chính?
2. Model Ruijie switch tổng là gì? Có model switch access khác, controller, SNMP/LLDP/port map không?
3. Model AP MikroTik nào, và có CAPsMAN hay mỗi AP cấu hình riêng?
4. “4–5 cái” là số site/tòa nhà, switch/AP hay router? Tên/địa chỉ các vị trí và sơ đồ cáp/VLAN/subnet/DHCP đã có chưa?
5. Linux server và Docker network mode gì; container backend có route tới `192.168.1.1` không? Có thể cấp IP/ACL riêng cho collector không?
6. Dashboard/log cần truy cập từ ngoài công ty không, hay chỉ LAN/VPN? MVP hiện mặc định chỉ ADMIN đăng nhập trên CFCBase; collector không mở ra Internet.
7. Bảy ngày là retention cho log thô hay cả audit và metric? Có cần export incident trước khi log hết hạn không?
8. Khi bạn nói “dùng pass đăng nhập 192.168.1.1”, ý là muốn lưu RouterOS credential để collector đọc thiết bị phải không? Khuyến nghị tạo tài khoản đọc riêng thay vì dùng lại mật khẩu quản trị hiện tại.

### Camera

9. (Khi bắt đầu phase camera) Hãng/model camera và NVR/VMS là gì; ghi hình ở NVR, thẻ nhớ hay cloud?
10. (Phase camera) Muốn live view trong browser hay mở phần mềm/NVR hiện tại? Bao nhiêu camera xem đồng thời?
11. (Phase camera) Ai được xem live/playback ở khu vực nào; có yêu cầu nhập lý do/audit không; video vẫn lưu tại NVR chứ?

### Phạm vi triển khai

12. Module đã chốt hiển thị trong CFCBase và ADMIN-only ở đợt đầu. Sau này có cần cấp quyền riêng cho nhân viên IT mà không cho họ quyền ADMIN HR không?
13. Đề xuất pilot ưu tiên “mất mạng/chậm mạng → xem uptime, WAN probe, log và khoanh vùng router/switch/AP”; nếu việc cấp bách nhất của bạn là khác, hãy đổi thứ tự trước khi bắt đầu.

## 15. Đề xuất quyết định

Đề nghị bắt đầu với **Phase 0 khảo sát và thiết kế pilot, sau đó Phase 1 trang ADMIN-only trong CFCBase**. Pilot đầu tiên kết nối read-only từ Linux server tới MikroTik L009UiGS `192.168.1.1`, Ruijie switch tổng và một AP MikroTik. Chỉ đọc device catalog, uptime/health, WAN probes, DHCP/ARP/neighbor và syslog chọn lọc; log tra cứu 7 ngày; chưa có camera viewer, chưa có config write và chưa thêm Kafka/RabbitMQ.

Thứ tự đề xuất: 1) biết mạng/Internet đang hoạt động không; 2) thấy log và điểm đầu tiên bị lỗi theo tuyến router→Ruijie→AP; 3) tra IP/MAC và phát hiện candidate xung đột IP; 4) mở rộng các site/thiết bị còn lại; 5) camera ở phase riêng sau này.

Vì bạn là một dev phụ trách và mạng hiện có quy mô pilot nhỏ, ưu tiên tích hợp công cụ thu thập/lưu log có sẵn rồi làm màn dashboard đúng nhu cầu trong CFCBase. Không tự triển khai Kafka, RabbitMQ hay một hệ NMS/VMS đầy đủ trước khi đo thấy cần.

## 16. Nguồn kỹ thuật đã đọc

- MikroTik RouterOS: [API](https://help.mikrotik.com/docs/spaces/ROS/pages/47579160/API), [REST API](https://help.mikrotik.com/docs/spaces/ROS/pages/47579162/REST%2BAPI), [SNMP](https://help.mikrotik.com/docs/spaces/ROS/pages/8978519/SNMP), [Resource](https://help.mikrotik.com/docs/spaces/ROS/pages/40992875/Resource), [Log](https://help.mikrotik.com/docs/spaces/ROS/pages/328094/Log), [DHCP](https://help.mikrotik.com/docs/spaces/ROS/pages/24805500/DHCP), [Bridging and Switching](https://help.mikrotik.com/docs/spaces/ROS/pages/328068/Bridging%2Band%2BSwitching), [Neighbor discovery](https://help.mikrotik.com/docs/spaces/ROS/pages/24805517/Neighbor%2Bdiscovery), [ARP](https://help.mikrotik.com/docs/spaces/ROS/pages/100892687/ARP), [Packages/The Dude](https://help.mikrotik.com/docs/spaces/ROS/pages/40992872/Packages).
- ONVIF: [Profile G](https://www.onvif.org/profiles/profile-g/), [Profile T overview](https://www.onvif.org/wp-content/uploads/2026/04/onvif-overview-202604.pdf), [Profile S deprecation Q&A](https://www.onvif.org/profiles/profile-s/profile-s-deprecation-qna/), [ONVIF Conformant Products](https://www.onvif.org/).

### Ghi chú thực hiện Phase 1–3 — 05/11/2026

- **Phase 1:** route `/admin/infrastructure`, menu ADMIN, inventory tìm kiếm/phân trang, tạo/sửa/ẩn và audit actor. Flyway V31 tạo bảng inventory/status/audit và ghi nhận MikroTik L009UiGS `192.168.1.1` theo thông tin đã cung cấp. Security contract test bao phủ ADMIN/MANAGER/anonymous. Chưa có topology đồ họa, export hoặc audit UI.
- **Phase 2:** polling HTTPS read-only cho `/rest/system/resource` và `/rest/interface`, lưu trạng thái mới nhất; mặc định tắt. Cần cấu hình secret env, CA/truststore hợp lệ và đường mạng từ backend tới router. Chưa có DHCP/ARP/neighbor, WAN probes hoặc so sánh với WinBox.
- **Phase 3:** overlay tùy chọn `docker-compose.network-monitoring.yml`, Alloy UDP/5514 → Loki 3.7.9 retention 168h, API/UI tìm kiếm log. Compose chưa được chạy; router chưa gửi log. Port mặc định loopback; cần chủ động bind IP LAN và cấu hình firewall allowlist.
- Credential RouterOS chỉ nằm trong env server, không ở catalog/trình duyệt; TLS validation không bị tắt. Không thêm RabbitMQ/Kafka.
- Nền tảng này hỗ trợ demo UI và bước pilot; chưa nghiệm thu trên mạng thật, chưa xác nhận availability/topology và không gồm camera.

Đây là trạng thái code local; chưa xác minh model/cấu hình đầy đủ hay quyền truy cập mạng CFC. Cổng nghiệm thu thật là pilot an toàn trên thiết bị đại diện và đối chiếu với WinBox.
