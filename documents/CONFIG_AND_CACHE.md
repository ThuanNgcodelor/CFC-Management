# Config And Cache Registry (BookingBase / HR)

Update this file in the same change whenever a cache, config key, or environment variable is added or changed. Never write secret values here, only names and purpose. State is from the 2026-10-16 audit; verify against code before relying on it.

## Caches

Current state: **no application cache exists** (`@EnableCaching`, `@Cacheable`, `@CacheEvict` are all absent). Redis is declared in `pom.xml` but unused.

When you add a cache, add one row:

| Cache name | Data | Provider | TTL | Max size | Evicted by (every write path) | Added in |
| --- | --- | --- | --- | --- | --- | --- |
| (none yet) | | | | | | |

Allowed candidates (see AGENTS.md section 6): department/position/working-condition catalogs (`HrManagementService.getCatalogs()`, evict on catalog create/update/delete), shift policies and work credit rules (`HrProductionAttendanceService.shiftPolicies()`, evict in `upsertShiftPolicy` and `upsertWorkCreditRule`), night reward program (`HrNightRewardProgramRepository.findEffective()`).

Never cache: DRAFT imports/shifts, unconfirmed qualifications, notifications, booking data. Service worker: do not cache dynamic booking API responses.

## Backend configuration (application.yml / application.properties)

| Key / area | Current value | Note |
| --- | --- | --- |
| JDBC `serverTimezone` | `UTC` (`application.yml:16`) | Business runs at +07:00. Do not change without a decision (see DECISIONS.md, Open) |
| `show-sql` | `true` (`application.yml:24`) | Should be `${SHOW_SQL:false}` (TECH_DEBT) |
| `spring.jpa.open-in-view` | not set (Spring default `true`) | |
| `hibernate.jdbc.batch_size`, `order_inserts`, `order_updates` | not set | Needed if batch inserts are introduced |
| Connection pool (HikariCP) | defaults (10 connections) | |
| `spring.servlet.multipart.max-file-size` | 50MB | Upload limit for Excel imports |
| Hibernate dialect | `MySQLDialect` | Tests use H2 |
| Migrations | Flyway V1 to V33; V1–V28 SQL resources, V29 Java migration, V30–V33 SQL | Schema changes only via new migration; V31–V32 cover infrastructure inventory, IP observations, topology links, assets, and assignment history; V33 stores the singleton encrypted RouterOS connection |
| Secrets and credentials (JWT, DB, SMTP, VAPID) | set through config/env; names only in `application.properties.example` | Defaults currently committed (P0 in TECH_DEBT). Never write values here |
| `cfc.word-editor.enabled` / `WORD_EDITOR_ENABLED` | `false` mặc định; `run.sh` ép `false` sau khi đọc `.env` và trong backend launcher | Không đăng ký API editor/kho mẫu, không chạy ONLYOFFICE; `run.sh` dừng container cũ và không tạo ingress docs. Muốn bật lại cần quyết định và sửa code triển khai. |
| `CFC_INFRA_MONITORING_ENABLED` | `false` | Bật polling read-only RouterOS từ backend sau khi kiểm tra route/ACL và tài khoản API riêng; không tự đổi cấu hình router |
| `CFC_INFRA_ROUTER_BASE_URL` | `https://192.168.1.1` | URL HTTPS RouterOS REST; JVM kiểm tra chứng thư mặc định, không tắt xác thực TLS |
| `CFC_INFRA_ROUTER_USERNAME` / `CFC_INFRA_ROUTER_PASSWORD` | trống | Fallback tương thích cho server cũ khi chưa có cấu hình WEB. ADMIN nhập tài khoản tại Kết nối MikroTik; mật khẩu lưu AES-256-GCM trong DB, không trả lại browser. Cấu hình WEB ưu tiên toàn bộ, kể cả khi disabled; không fallback sang env khi tắt WEB |
| `CFC_INFRA_CREDENTIAL_KEY_FILE` / `cfc.infrastructure.credential-key-file` | `${user.home}/.local/share/cfcbase/infrastructure/credential.key` | Khóa AES 32 byte sinh lần lưu đầu, quyền Linux 0600, thư mục mới 0700; nằm ngoài checkout. Backup riêng cùng DB; mất khóa phải khôi phục hoặc nhập lại mật khẩu. Không đưa khóa vào Git/log/UI. Decrypt không tự sinh khóa thay thế |
| RouterOS WEB connection | một cấu hình, không cache | Đọc trực tiếp DB; optimistic `rowVersion`, audit metadata không chứa mật khẩu; đổi IP/tài khoản cần nhập lại mật khẩu. HTTPS IP RFC1918, không redirect và không tắt TLS verification. Lưu tạo/kích hoạt catalog ROUTER tương ứng, xóa trạng thái cũ; không ghi cấu hình lên thiết bị |
| RouterOS HTTP timeout | connect 3s, read 8s / request | Chỉ GET resource/interface/DHCP/ARP, không retry nền; kiểm tra explicit có thể đọc hai request liên tiếp. Lỗi auth/TLS/timeout dùng thông báo an toàn, không phản chiếu body RouterOS |
| `CFC_INFRA_POLL_INTERVAL_MS` | `60000` (giới hạn 15–300 giây) | Chu kỳ lấy system resource/interfaces; chỉ truy vấn một router được cấu hình trong catalog |
| `CFC_INFRA_LOKI_BASE_URL` | trống | URL nội bộ tới Loki; endpoint tra log giới hạn tối đa 7 ngày và 200 dòng/lần |
| `NETWORK_SYSLOG_BIND_IP` / `NETWORK_SYSLOG_PORT` | `127.0.0.1` / `5514` | Bind UDP syslog Alloy; phải đổi sang IP LAN khi sẵn sàng nhận log và giới hạn bằng firewall |
| `NETWORK_LOKI_PORT` | `3100` | Port quản trị/query Loki chỉ bind loopback trên host trong overlay compose |

## Frontend configuration

| Area | Current state |
| --- | --- |
| Bundler | Vite 8; `vite-plugin-pwa` generates the service worker (99 precache entries, about 5.1 MB) |
| Largest chunk | `HrNightRewards` 914 kB (goal: under 500 kB) |
| HTTP client | Axios in `baseApi.js`: adds Bearer token, refreshes on 401 and retries; no central 403 handling |
| Lint | `oxlint`; baseline 0 errors, 10 warnings |
| HR list navigation (04/11/2026) | Applied filters/sort/page/tab/period use URL query parameters; `returnTo` accepts internal `/manager/hr` paths only, maximum 8,192 characters. Invalid/external paths fall back to the module list. No uploaded files or form payloads are stored in the URL. |
| HR scroll positions | `sessionStorage` key `hr-list-scroll-v1`, memory fallback; maximum 100 keys shared by history-entry and path positions, oldest discarded; restored after data loads, discarded with the browser session. Stores scroll offsets only, no server responses. |
| Movement query page size | Default 20, maximum 50 enforced by backend; UI offers 20/50. Sort fields/directions and record type are whitelisted; stable `id` tie-break. No application cache added. |
| Movement number suggestion | Tăng suggests `/HĐLĐ-PBHC/<current browser year>` when opening a new form; user enters the prefix and can edit the complete number, suffix/unit/year. Giảm starts blank; no automatic numbering, DB setting or environment variable. Saved historical numbers stay unchanged. |

## Local knowledge graph tooling

| Setting | Value | Purpose / refresh |
| --- | --- | --- |
| Graphify installation | `~/.local/share/graphify/venv`, `graphifyy[mcp,sql]==0.9.100`, MCP SDK `1.30.0` | Local development tool, separate from application dependencies; stdio initialize, tool listing, statistics and bounded query verified |
| Codex MCP `graphify` | User-level `~/.codex/config.toml`; Python module `graphify.serve`, stdio; absolute path to this repository's `graphify-out/graph.json` | Reads the local snapshot; no HTTP listener or external LLM API is needed for queries |
| Query budget | Start at 1,500 tokens, depth 1–2 | Narrow queries to reduce context; increase only if relevant results are truncated |
| Codex Graphify tool allow list | `query_graph`, `get_node`, `get_neighbors`, `get_community`, `god_nodes`, `graph_stats`, `shortest_path` | Expose graph navigation tools only; repository working directory is pinned locally |
| Graph / AST cache | `graphify-out/`, ignored by Git; no TTL or size cap configured | Refresh with `bash scripts/update-knowledge-graph.sh` after code changes; semantic docs/images require the Graphify skill. No background watcher or git hook |

## Application environment variables

List variable names (not values) that deploy relies on. Add a row when a new one is introduced.

| Variable | Purpose | Default | Where used |
| --- | --- | --- | --- |
| `SHOW_SQL` (proposed) | Toggle SQL logging | `false` | `application.yml` |

## Manual Fedora deployment

| Setting | Value / purpose | Where |
| --- | --- | --- |
| CI repository / workflow gate | Public `ThuanNgcodelor/CFC-Management`, workflow `ci.yml`; push/main, exact SHA and both `Frontend lint and build` / `Backend tests` successful | `deployserver/linux/ci-deploy.py`; no PAT or new env variable |
| GitHub API timeout / job bound | 30 seconds per API call; at most 100 jobs, rejects larger runs instead of accepting a partial list; API failures stop deploy | `deployserver/linux/ci-deploy.py` |
| Deploy concurrency lock | `.git/cfcbase-deploy.lock`, non-blocking shared lock for manual/auto wrapper invocations | `deployserver/linux/ci-deploy.py` |
| Auto-deploy state | `.git/cfcbase-deploy-state.json`, atomic write, successful SHA and deploying/failed/success phases; interrupted/failed attempts pause all auto-deploys until acknowledged manual retry succeeds | `ci_deploy_state.py`; no credentials, no TTL |
| Migration gate | New SQL versions above deployed baseline may auto-deploy only with matching SHA256, backwardCompatible=true and review reason in candidate `auto-migrations.json`; changed/deleted/Java/undeclared migrations stay manual | `ci_deploy_migrations.py`; V33 declared, no runtime cache |
| Auto-deploy Git transport | Fixed public repository HTTPS URL, noninteractive; no SSH agent/PAT, does not rewrite origin | `ci-deploy.py --auto` |
| User timer cadence / timeout | OnBootSec 2min; OnUnitInactiveSec 5min; RandomizedDelaySec 30s; AccuracySec 30s; service timeout 30min; no Restart | `bookingbase-deploy.service` / `.timer`, opt-in via installer |
| User service build environment | Captures only PATH/JAVA_HOME in user config `cfcbase/deploy.env`, mode 600; rerun installer if tool paths change. GIT_TERMINAL_PROMPT=0, PYTHONUNBUFFERED=1 and noninteractive SSH policy in unit. Production `.env` unchanged | `install-deploy-timer.py`; no secrets copied |

Cấu hình mặc định hạ tầng được đóng gói trong `infrastructure-defaults.properties` qua `@PropertySource`, nên alias `CFC_INFRA_*` không còn phụ thuộc file application YAML ignored. Các property nguồn có ưu tiên cao hơn vẫn được giữ. Khi đã lưu WEB, bật/tắt giám sát bằng web; env chỉ là fallback trước lần lưu đầu. Không thêm cache hoặc broker.

| Deploy setting | Behavior | Location / lifecycle |
| --- | --- | --- |
| Deploy migration backup | `build-prod.sh --backup-before-activate`: after build/smoke, before JAR/marker swap; failure stops activation | Auto approved migrations and manual schema changes/unknown baseline require it. Existing BOOKINGBASE_BACKUP_DIR/KEEP/DB_CONTAINER/DB_NAME apply; no new env or secret |
| Backup publishing | umask 077; gzip + schema/table/footer validation, nanosecond filenames; retention 24 by existing BOOKINGBASE_BACKUP_KEEP | `backup-database.sh`; failed dump never deletes older backups. Consumes full stream, no early grep pipe close |
| CI deploy jobs | Frontend lint and build, Backend tests, Deployment script checks; exact SHA/push-main/latest attempt + overall success | `ci-deploy.py`; CI pending/failure leaves release unchanged |

| Search/navigation setting | Behavior | Location / lifecycle |
| --- | --- | --- |
| Production attendance search debounce | 350 ms, unchanged; latest callback, pending acknowledgements preserve newer drafts, POP/external value cancels timer; cleanup on unmount | `useDebouncedHrSearchValue`; no cache or background polling |
| HR list URL writer | Stable setter + getValues, same-tick patches merge against pending latest search, identical params do not navigate; cleanup blocks unmounted writers | `useHrListState`; no persisted extra state, URL remains source of applied filters |
| Production attendance stale reads | Imports/base guarded by request sequence and current month; shifts/summaries retain sequence guard and invalidate on empty selection/cleanup | `HrProductionAttendance`; no AbortController mixed into this existing screen, no cache |

## Sổ sức khỏe Phase 1 — dev, 10/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| `cfc.health.enabled` / `HEALTH_ENABLED` | `false`, shipped `health-defaults.properties` loaded by module configuration | Opt-in backend nghiệp vụ; capabilities chỉ trả boolean. Tắt trả 404 cho nghiệp vụ; không mở tra cứu công khai. Không sửa env/server trong lần triển khai này |
| `VITE_HEALTH_ENABLED` | `false` nếu không có giá trị `true` | Chỉ hiện menu/route khi bật; backend vẫn kiểm tra role/flag riêng. Dev cần restart Vite, production cần build mới nếu sau này rollout |
| Health Query client | Provider riêng màn, `staleTime=0`, `gcTime=0`, retry=false; không refetch focus/reconnect/polling | Invalidate đúng campaign/indicator sau save; abort request cũ; clear khi rời màn; không persist, không thêm cache backend |
| Health list | 20 dòng/page, backend chấp nhận 1–100, page 0–10000; keyword tối đa 100; debounce dùng hook chung 350 ms | Lọc tại DB trước pagination, sort ổn định có ID; không trả unbounded list |
| Indicator aliases | Tối đa 30 tên, 255 ký tự/tên | NFC + lowercase + collapse whitespace; trùng giữa chỉ tiêu trả conflict. Alias-only edit cũng tăng aggregate rowVersion |
| Campaign/indicator identifiers | Code 1–40 ký tự chữ/số/gạch ngang/gạch dưới, chuẩn hóa uppercase | Mã và kiểu chỉ tiêu ổn định sau tạo; ngừng sử dụng qua status, không xóa cứng |

V34 additive mới tạo bảng, không seed hoặc đổi dữ liệu HR; chưa thêm vào auto-migration manifest và chưa deploy. EXPLAIN dev H2, các lệnh test an toàn và giới hạn kiểm chứng: [EMPLOYEE_HEALTH_PHASE1_DEV.md](EMPLOYEE_HEALTH_PHASE1_DEV.md).


## Sổ sức khỏe Phase 2 — dev, 11/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| Health Excel input | `.xlsx`, 10 MB compressed/64 MB expanded, 2.000 ZIP entries/30 sheets; per-sheet 2.500 rows/256 nonempty columns/200.000 nonempty cells | Parser constants, không thêm env/global upload config; chọn một sheet, không evaluate công thức hoặc gọi dịch vụ ngoài |
| Header/mapping | Start 1–100, span 1–3; mỗi cột phải map hoặc chọn ignore; max 16.000 ký tự/value, 60.000 byte UTF-8/payload hoặc mapping | Profile chỉ theo fingerprint cấu trúc; đổi cấu trúc phải review. Metadata định danh không đưa vào browser storage |
| Import confirmation | API 1–100 rows/call, max 10.000 result cells; UI page 20 | Transaction theo lần xác nhận; rowVersion + preview token chống stale, retry imported rows không nhân đôi |
| Source identity | Mã nguồn ổn định + SID; optional ngày SID cần checkbox xác minh; thiếu SID dùng khóa người/ngày chỉ khi checkbox xác nhận riêng | Không tự lấy ngày hiện tại; nhiều lượt/ngày cần mã lượt riêng; không dùng profile tự phê duyệt các checkbox |
| Health import Query keys | Private health provider; inspect theo file/sheet/header, rows theo batch/page, batches theo campaign/page, profiles theo fingerprint/page, employee lookup theo keyword | Abort stale reads; invalidate rows/history sau confirm/resolve/cancel; search 350 ms, không polling/persist/cache backend |
| Import retention | Không lưu file gốc; payload đã map ở staging; hủy pending xóa payload pending, giữ imported/revisions/audit | Chưa có tự purge/TTL, cần chốt trước dữ liệu thật. Không xóa kết quả đã xác nhận |
| Dev source inspection test | Tắt mặc định; `HEALTH_SOURCE_DIRECTORY` chỉ opt-in test đọc ba workbook local | Không phải runtime setting; không ghi DB/PII log, không truyền file ra mạng |

V35 chưa vào auto-migration manifest hoặc server. Kiểm chứng và EXPLAIN H2: [EMPLOYEE_HEALTH_PHASE2_DEV.md](EMPLOYEE_HEALTH_PHASE2_DEV.md).


## Sổ sức khỏe Phase 3 — baseline dev, 11/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| Baseline operator | `HealthBaselineDev` chỉ ở test-classes; mặc định dry-run; `--stage` yêu cầu `--policy` và snapshot định danh được phép | DB cố định H2 file trong thư mục `/tmp` riêng; guard từ chối MySQL/TCP. Không thêm runtime env/auto-deploy manifest |
| Baseline source policy | Người dùng chọn `TOTAL_FIRST`: Tổng hợp là nguồn ban đầu; sheet con/file bổ sung chờ đối chiếu; auto confirm chỉ lô nguồn chính hợp lệ khi opt-in `--confirm-code-matches` | Khác kết quả cần KEEP/REPLACE có lý do theo luồng Phase 2; tên-only không tự ghép. `STAGE_ALL` không được auto confirm |
| Historical year | `--confirm-sid-years` opt-in; người dùng đã xác nhận năm 2023/2024 theo SID của bộ nguồn hiện tại, chưa xác nhận ngày | Có transaction riêng xác nhận năm trên dòng PENDING của nguồn CFC_HISTORY/đợt HISTORY_2023–2024. Chỉ gỡ đúng lỗi chờ năm khi SID khớp; giữ ID/dữ liệu/ngày/liên kết/lỗi khác, lưu lý do/người/thời điểm và tăng version. Retry không thêm lịch sử; không hủy/tạo lại lô hoặc auto import. Đã áp dụng cho 921 dòng lịch sử pending, ngày khám vẫn trống |
| Identity clone | Max 20.000 người; ID/mã/tên/ngày sinh, không CCCD/lương/liên hệ. Snapshot fingerprint cố định cho DB dev | Lần đầu reset seed HR chỉ ở H2 thử riêng chưa có health sau backup; retry cùng snapshot bỏ qua. Snapshot đổi hoặc DB đã có health nhưng thiếu marker thì từ chối |
| Private baseline artifacts | Thư mục riêng mode 0700, file 0600; workbook gốc, JSON, CSV review và H2/SQL backup ở `/tmp/cfc-health-baseline-review` | Có dữ liệu thật, không commit/log/gửi ra dịch vụ ngoài. `/tmp` có thể bị dọn/reboot, không thay backup production hoặc chính sách retention dài hạn |
| Source classification correction | I–V; pending only; reason 1–500 ký tự, max 20 lần/dòng, payload max 60.000 byte UTF-8 | Giữ raw trước/sau/người/thời điểm riêng trong health; khóa campaign/batch + rowVersion, invalidate rows sau sửa; không tự sửa kết quả đã nhập/công bố |

`.gitignore` loại trừ chính xác sáu file Excel/Markdown nguồn sức khỏe tại root và `frontend/dev-dist/`; không xóa file hoặc bỏ qua toàn bộ Excel/migration.

Không thêm backend cache, broker, polling hoặc schema/index trong Phase 3. UI preview dùng chính DashboardLayout và API health thật ở loopback với DB dev riêng; quyền đăng nhập giả lập chỉ nằm ở launcher tạm ngoài checkout, không đưa vào app production.


## Sổ sức khỏe Phase 4 — đọc hồ sơ nội bộ dev, 11/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| Private record reads | ADMIN/MANAGER, `/api/v1/hr/health/records`; GET people/timeline/results, POST compare chỉ đọc; response no-store | Không thêm vào HR DTO/tài liệu/feed, HR_STAFF bị chặn tại security và service, chưa có cổng cá nhân |
| Pagination/range | UI 20; API 1–100/page, page 0–10.000; timeline 1900–2200, khoảng tối đa 10 năm | Queries projections/paging ở DB, không fetch-all/filter/sort trong JSX; chưa thêm index/cache |
| Comparison | 2–6 ID lượt khác nhau và thuộc đúng người; phân trang chỉ tiêu theo valueKey (indicator/unit/method) | Mỗi ô giữ raw/reference/source riêng; bỏ ô không có thành null/missing, không thành 0; cùng năm thiếu ngày không tự chọn thứ tự |
| Numeric chart | Chỉ NUMBER, unit/method không trống, đủ mọi ô và thứ tự năm/ngày; chuỗi số thập phân dấu chấm, tối đa 15 chữ số có nghĩa | Giữ dấu giới hạn/locale/huyết áp ở raw; không chuyển `<0.5`, `1,000`, `120/80` thành số chính xác. Không gắn tăng/giảm tốt/xấu |
| Query keys | `['health','records',params]`, timeline theo employee/range/page, visit-results theo employee/visit/search/page, comparison theo employee/IDs/search/page | Provider health RAM, gcTime 0, staleTime 0; signal cancel, không placeholder dữ liệu người cũ, polling/persist/backend cache vẫn tắt. Refresh khi vào lại; import không công bố hoặc sửa dữ liệu đọc ngầm |
| URL context | ID người/lượt, danh sách chọn và lọc/sort/page; không chứa kết quả/CCCD/token | Mở người khác xóa selection/results scope cũ; Back/Forward và nút quay lại giữ list context; Query cache xóa khi unmount provider |
| Import row state | `state=ALL` mặc định tương thích cũ; PENDING/IMPORTED/CANCELLED, lọc trước phân trang tại DB | Đổi lọc clear selection/page; mutation invalidate mọi page/filter của batch, bộ lọc khác ALL về trang đầu. Không cache staging |
| Shared drawer | Thêm opt-in `size=full` max 1600 px cho bảng kết quả/so sánh mới; standard/wide giữ nguyên | Màn nhỏ rộng theo viewport, cuộn bảng bên trong; ESC không xóa dữ liệu hoặc lựa chọn lượt |

Snapshot dev chỉ có nhóm HR chưa xác định, không kéo thêm trường từ production để điền nhóm/đơn vị. Chưa có API lưu/tải tệp ảnh/PDF gốc bệnh viện; báo cáo PDF tạo mới có ở Phase 5; không dùng kho tài liệu HR có quyền HR_STAFF.

## Sổ sức khỏe Phase 5 — báo cáo dev, 11/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| Report scope | POST health/reports preview/excel/pdf, ADMIN/MANAGER; 1900–2200, tối đa 10 năm; keyword max100, sort whitelist, nhóm HR hiện tại, ALL/WITH_DATA/WITHOUT_DATA theo khoảng xuất | Không phân trang file; lượt tùy chọn tối đa6, duy nhất và cùng một người trong khoảng; không lấy staging |
| Bounded snapshot | 500 người / 2.000 lượt / 20.000 kết quả / 60.000 ô so sánh; 16 MiB text, ô max32.767 ký tự; tệp max20 MiB | Vượt trả lỗi yêu cầu thu hẹp, không cắt nguồn. Read-only REPEATABLE_READ; kết thúc trước render |
| PDF cap/font | Một người, tối đa1.000 kết quả / 200 trang; nhúng BeVietnamPro hiện có | Giữ raw; font thiếu ký tự trả lỗi gợi ý Excel, không thay nguồn bằng dấu hỏi |
| Render concurrency | Một tệp báo cáo Phase 5/instance, tryAcquire không đợi | Busy 409, giải phóng slot cả khi lỗi. Preview không cache kết quả/không polling |
| Excel spool | SXSSF window50 inline strings, gzip trong java.io.tmpdir/cfc-health-export-*; directory0700/files0600, POSIX | Per-export, không đổi POI global strategy; dọn trong close cả khi output lỗi, không giữ file để tải sau. Kill/mất điện có thể để lại spool, xem bàn giao |
| Report UI query | health/report-summary theo scope, provider gc0; signal cancellation; tải Blob có AbortController theo vòng đời drawer | Không persist/polling/cache backend; ESC giữ bộ lọc/lượt chọn. Tải lại lấy snapshot mới, không khóa kết quả giữa preview và download |

Không thêm env, dependency, schema/index hoặc chính sách TTL cho hồ sơ. Chưa có snapshot đơn vị/nhóm lịch sử; độ đầy đủ là metadata hiện có, không là kết luận đủ gói khám.

## Sổ sức khỏe Phase 6 — công bố dev, 12/11/2026

| Setting | Default / scope | Invalidation / note |
| --- | --- | --- |
| Publication preview | ADMIN/MANAGER/no-store; một đợt; keyword max100, nhóm HR hiện tại, optional IDs max200; tối đa500 lượt/10.000 kết quả | Chỉ kết quả đã nhập; cố định tập ID sau preview, token gắn hash nội dung + rowVersion lượt/đợt/latest publication. Không cache/polling |
| Publication batch | POST review/publish/revoke-batch: max20 người, mỗi người max20 lượt; lý do1–500 ký tự | UI chia20 người tuần tự, bean transaction riêng từng người; rollback toàn lượt đã chọn của người đó khi lỗi. Dữ liệu đổi phải preview lại, retry không nhân đôi revision/audit |
| Publication snapshot | Format1, max1.000 kết quả / 2 MiB UTF-8; LONGTEXT V36 | Snapshot immutable; giữ raw/nguồn/định danh tối thiểu, không workbook nhiều người/CCCD. Bản cũ thiếu hash không đủ điều kiện làm nội dung phát hành |
| Current release rule | Revision cao nhất của lượt và status PUBLISHED | REVOKED ở bản cao nhất chặn nội dung/PDF, không fallback. Mọi lần read/download kiểm tra owner và trạng thái, sau render kiểm tra lần nữa; admin vẫn có quyền đọc bản biên tập riêng |
| Campaign state | OPEN/CLOSED, rowVersion + lý do | Khóa/mở không đổi publication; CLOSED chặn review/import/edit. Picker Phase 6 allowClosed=true để mở lại đợt, default picker import vẫn false |
| Private UI keys | publication-candidates theo đợt/filter/page; publication-preview theo scope cố định; publication-history theo employee/visit/page; published-results theo owner/release/search/page | Query gc0/signal, invalidate health sau ghi; không persist/placeholder kết quả cũ sau lỗi. Đọc/tải có AbortController khi drawer đóng |
| Published PDF rendering | Một slot riêng/instance, PDFBox/font/cap1.000 kết quả/200 trang của Phase 5 | Render ngoài transaction; không giữ tệp server sau tải. Slot độc lập với báo cáo biên tập Phase 5; không queue/background cache |
| Locks | Ghi READ_COMMITTED: campaign → employee → visit → indicator, ID có thứ tự; indicator PESSIMISTIC_WRITE ngắn | Bảo vệ snapshot trước import/catalog edit đồng thời; dùng được với fixture H2/MySQL dialect hiện hữu. Chưa chứng minh MySQL thực |

Không thêm env/dependency/index. V36 chưa có trong auto-migrations manifest; chỉ chạy trên DB H2 dev đã backup. Audit health có reason riêng; không đưa ra feed HR chung.

## Sổ sức khỏe Phase 7 — tra cứu cá nhân dev, 12/11/2026

| Cấu hình/cơ chế | Giá trị/phạm vi | Thu hồi/vòng đời/giới hạn |
|---|---|---|
| `cfc.health.self-service.enabled` / `HEALTH_SELF_SERVICE_ENABLED` | `false`; cần cả `cfc.health.enabled=true` | Gate riêng cho mọi API `/api/v1/health-self/**`; tắt trả 404. Trang `/health-lookup` vẫn mở được nhưng chỉ hiện chưa bật |
| `cfc.health.self-service.grant-days` / `HEALTH_SELF_GRANT_DAYS` | 90 ngày; hợp lệ 1–365 | Chỉ áp dụng mã cấp mới; mã cũ giữ hạn đã lưu UTC. Không lưu bản rõ, không trả mã ở status/audit |
| `cfc.health.self-service.session-minutes` / `HEALTH_SELF_SESSION_MINUTES` | 15 phút tuyệt đối; hợp lệ 1–30, không vượt hạn mã | Một phiên/người; đăng nhập mới/cấp lại/thu hồi/thoát xóa phiên. Token băm lưu DB, bản rõ chỉ RAM/header; không cookie/URL/storage |
| Lookup limiter | RAM, cửa sổ 15 phút; 20 lần/IP và 10 lần/mã nhân viên, tính cả lần đúng; tối đa 10.000 khóa băm | Dọn khóa hết hạn mỗi lookup; đầy thì từ chối, không evict khóa đang giới hạn. Không tin X-Forwarded-For trực tiếp; cần nghiệm thu proxy/IP thật trước Internet, restart reset bộ đếm |
| Access/grant writes | ADMIN/MANAGER, lý do max500, generation chống ghi đè; khóa employee/grant | Cấp lại ngẫu nhiên/thu hồi xóa phiên, audit chỉ metadata. Không sửa CCCD từ health; yêu cầu CCCD HR 12 số; người đã nghỉ không tự mất lịch sử |
| Personal query | Provider cục bộ RAM, retry false/stale0/gc0, cancel qua signal; danh sách max20/page, so sánh2–6 lượt, lịch sử max10 năm | Không polling/persist/placeholder; thoát/401/hết hạn/pagehide xóa query và màn riêng. Reload cần xác minh lại. PDF Blob URL được revoke sau tải |
| Personal content | Current = revision cao nhất PUBLISHED, kiểm owner/current/hash mỗi lần; không dùng biên tập | Thu hồi chặn tải tiếp theo, không fallback bản cũ. Không trả audit/review reason/nguồn ô nội bộ. PDF cá nhân max1.000 kết quả/200 trang, 1 render/instance |
| HTTP/PWA | no-store/private kể cả lỗi, no-referrer, noindex, nosniff, DENY frame | Không thay Auth cookie/shared login. Client dùng fetch riêng credentials omit; API/tệp không có runtime cache trong SW. Asset shell có thể precache, không chứa kết quả |
| V37 | Java migration + SQL resource riêng, thêm2 bảng; report_year trên publication backfill từ snapshot | Batch20, payload tối đa2MiB, fail khi snapshot hash-present không hợp lệ; chưa auto-migrations, chưa chạy baseline thật hoặc production |

H2 EXPLAIN kiểm tra dọn phiên hết hạn: `DELETE FROM hr_health_access_sessions /* public.idx_health_access_session_expiry: expires_at < CURRENT_TIMESTAMP */ WHERE expires_at < CURRENT_TIMESTAMP`. Đây không thay bằng chứng EXPLAIN MySQL trong Phase 8.

### Bổ sung hai cách tra cứu — dev 12/11/2026

| Cấu hình/cơ chế | Giá trị/phạm vi | Thu hồi/vòng đời |
|---|---|---|
| `hr_health_access_grants.allow_identity_only` | V38, FALSE cho cả grant cũ/mới. ADMIN/MANAGER đổi qua POST personal-access/{employee}/policy, `@Valid`, lý do/generation bắt buộc | Bật cho phép IDENTITY lẫn ACCESS_CODE; tắt chỉ ACCESS_CODE. Mỗi thay đổi thay generation/xóa phiên; cấp lại mã reset FALSE; thu hồi/expired chặn cả hai |
| Lookup `mode` | IDENTITY: CCCD+mã nhân viên và grant cho phép; ACCESS_CODE: thêm mã riêng đúng. Không gửi mode giữ ACCESS_CODE để tương thích request cũ | Không tự fallback sang IDENTITY nếu ACCESS_CODE sai/thiếu. Không công khai chính sách của người chưa xác minh, lỗi vẫn chung, limiter dùng chung cả hai |
| Mode picker | Mặc định hiển thị IDENTITY, hai radio, ô mã chỉ ở ACCESS_CODE | Đổi mode xóa mã đã nhập/lỗi; không lưu mode/CCCD/mã trên URL/storage. Backend vẫn là nguồn quyền, chọn radio không tự bật grant |

Không thêm cache/env/dependency/index. V38 không đưa vào auto-migrations manifest. Mặc định gate cổng vẫn OFF; không cấp/đổi quyền trên dữ liệu thật.

### Làm rõ cách dùng mã riêng — dev 12/11/2026

- `ACCESS_CODE` chỉ nhận/bắt buộc `accessCode` (mode có thể bỏ trống để giữ tương thích). Không cần CCCD/mã nhân viên; backend tìm grant bằng SHA-256, không dùng mã nhân viên gửi thêm để chọn owner. `IDENTITY` giữ CCCD+mã nhân viên và `allow_identity_only`.
- V39 thêm unique index `uk_health_access_secret_hash`; không sửa V37/V38. Query tìm mã riêng không join/bắt buộc bản CCCD, kiểm tra grant/expiry/revoke và khóa employee rồi kiểm generation hiện tại; lookup transaction READ_COMMITTED để nhìn thấy cấp lại/thu hồi vừa commit sau khi chờ khóa.
- Limiter key băm phân biệt `identity:<mã nhân viên>` và `private:<mã riêng>`, cùng giới hạn IP hiện có; không lưu bản rõ trong counter/log. UI chỉ gửi mode+hai trường hoặc mode+mã, đổi mode xóa mọi trường đang nhập.
- H2 test EXPLAIN truy vấn `SELECT employee_id FROM hr_health_access_grants WHERE secret_hash=?` phải dùng `uk_health_access_secret_hash`. Chưa EXPLAIN MySQL hoặc chạy migration trên server/baseline thật. Mọi giới hạn phiên, cổng mặc địnhOFF, no-store và không persistence vẫn giữ nguyên.

H2 EXPLAIN đã chạy cho V39: `SELECT employee_id FROM hr_health_access_grants /* public.uk_health_access_secret_hash: secret_hash = ?1 */ WHERE secret_hash = ?1`. Chưa có bằng chứng EXPLAIN MySQL; không thay gate triển khai/pilot Phase8.
