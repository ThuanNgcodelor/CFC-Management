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
