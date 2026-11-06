#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
JAR_NAME="booking-system-0.0.1-SNAPSHOT.jar"
JAR_PATH="$BACKEND_DIR/target/$JAR_NAME"
PREVIOUS_JAR_PATH="$JAR_PATH.previous"
RELEASE_VERSION="$BACKEND_DIR/target/release-version.txt"
PREVIOUS_RELEASE_VERSION="$RELEASE_VERSION.previous"
TUNNEL_ID="745ab8be-c55c-4e72-b985-d918206ca82f"
CREDENTIAL_FILE="$HOME/.cloudflared/$TUNNEL_ID.json"
RUNTIME_DIR="${XDG_RUNTIME_DIR:-/tmp}/bookingbase"
BACKEND_LOG="$RUNTIME_DIR/backend.log"
TUNNEL_LOG="$RUNTIME_DIR/cloudflared.log"
TUNNEL_CONFIG="$RUNTIME_DIR/cloudflared-config.yml"
BACKEND_LAUNCHER="$RUNTIME_DIR/start-backend.sh"
TUNNEL_LAUNCHER="$RUNTIME_DIR/start-tunnel.sh"
BACKEND_UNIT="bookingbase-backend.service"
TUNNEL_UNIT="bookingbase-tunnel.service"
BACKUP_SCRIPT="$SCRIPT_DIR/backup-database.sh"
LEGACY_SNAPSHOT_SCRIPT="$SCRIPT_DIR/capture-legacy-table-counts.sh"
HR_VERIFY_SCRIPT="$SCRIPT_DIR/verify-hr-phase1.sh"
PRODUCTION_ATTENDANCE_VERIFY_SCRIPT="$SCRIPT_DIR/verify-production-attendance.sh"
WORD_EDITOR_COMPOSE="$ROOT_DIR/docker-compose.word-editor.yml"
JAVA_OPTS="${JAVA_OPTS:--Xms256m -Xmx768m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Dfile.encoding=UTF-8}"
HR_LEGACY_SNAPSHOT=""
INITIALIZE_HR_SCHEMA=false

usage() {
  cat <<'EOF'
Cach dung:
  ./deployserver/linux/run.sh
  ./deployserver/linux/run.sh --initialize-hr-schema

--initialize-hr-schema  Chi dung mot lan cho database BookingBase cu chua co
                        flyway_schema_history. Script se backup, baseline version
                        0, chay HR V1 va verify truoc khi mo Cloudflare Tunnel.
EOF
}

cleanup_hr_snapshot() {
  [[ -z "$HR_LEGACY_SNAPSHOT" ]] || rm -f -- "$HR_LEGACY_SNAPSHOT"
}
trap cleanup_hr_snapshot EXIT

log() {
  printf '[BookingBase] %s\n' "$*"
}

fail() {
  printf '[BookingBase] ERROR: %s\n' "$*" >&2
  exit 1
}

case "${1:-}" in
  "")
    ;;
  --initialize-hr-schema)
    INITIALIZE_HR_SCHEMA=true
    ;;
  --help|-h)
    usage
    exit 0
    ;;
  *)
    usage >&2
    fail "Tham so khong hop le: ${1:-}."
    ;;
esac
(( $# <= 1 )) || fail "Chi duoc truyen mot tham so."

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Khong tim thay lenh '$1'."
}

for command_name in java docker cloudflared curl systemctl systemd-run; do
  require_command "$command_name"
done

[[ -f "$JAR_PATH" ]] || fail "Chua co production JAR. Hay chay deployserver/linux/build-prod.sh truoc."
[[ -r "$CREDENTIAL_FILE" ]] || fail "Thieu credential: $CREDENTIAL_FILE"

mkdir -p -- "$RUNTIME_DIR"
chmod 700 "$RUNTIME_DIR"

# Optional local secrets/config. Never commit this file.
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  log "Nap bien moi truong tu deployserver/linux/.env..."
  set -a
  # shellcheck disable=SC1091
  source "$SCRIPT_DIR/.env"
  set +a
fi

export WORD_EDITOR_ENABLED=false
HR_PRODUCTION_ATTENDANCE_ENABLED="${HR_PRODUCTION_ATTENDANCE_ENABLED:-true}"
HR_PRODUCTION_ATTENDANCE_SHADOW_MODE="${HR_PRODUCTION_ATTENDANCE_SHADOW_MODE:-false}"

for flag_value in "$HR_PRODUCTION_ATTENDANCE_ENABLED" "$HR_PRODUCTION_ATTENDANCE_SHADOW_MODE"; do
  [[ "$flag_value" == true || "$flag_value" == false ]] \
    || fail "HR_PRODUCTION_ATTENDANCE_ENABLED/SHADOW_MODE chi duoc la true hoac false."
done

if [[ "$INITIALIZE_HR_SCHEMA" == true ]]; then
  # CLI opt-in is deliberately process-local and overrides .env for this run.
  export FLYWAY_BASELINE_ON_MIGRATE=true
fi

case "${FLYWAY_BASELINE_ON_MIGRATE:-false}" in
  true|false)
    ;;
  *)
    fail "FLYWAY_BASELINE_ON_MIGRATE chi duoc la true hoac false."
    ;;
esac
FLYWAY_BASELINE_MODE="${FLYWAY_BASELINE_ON_MIGRATE:-false}"
DB_CONTAINER="${BOOKINGBASE_DB_CONTAINER:-booking_db}"
DB_NAME="${BOOKINGBASE_DB_NAME:-booking_db}"

wait_for_database() {
  for _ in {1..60}; do
    if docker exec "$DB_CONTAINER" sh -c '
      : "${MYSQL_USER:?MYSQL_USER is missing}"
      : "${MYSQL_PASSWORD:?MYSQL_PASSWORD is missing}"
      exec env MYSQL_PWD="$MYSQL_PASSWORD" mysqladmin \
        --user="$MYSQL_USER" --silent ping
    ' >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "MySQL container $DB_CONTAINER khong san sang sau 60 giay."
}

start_backend() {
  systemctl --user reset-failed "$BACKEND_UNIT" 2>/dev/null || true
  systemd-run --user --unit "${BACKEND_UNIT%.service}" --collect \
    --property=Restart=on-failure --property=RestartSec=5s \
    "$BACKEND_LAUNCHER" >/dev/null
}

wait_for_backend() {
  local timeout="$1"
  for ((i=1; i<=timeout; i++)); do
    if ! systemctl --user is-active --quiet "$BACKEND_UNIT"; then
      tail -n 80 "$BACKEND_LOG" >&2 || true
      return 1
    fi
    if curl --fail --silent --max-time 2 http://127.0.0.1:8080/ >/dev/null; then
      return 0
    fi
    sleep 1
  done
  tail -n 80 "$BACKEND_LOG" >&2 || true
  return 1
}

rollback_backend_release() {
  if [[ ! -f "$PREVIOUS_JAR_PATH" ]]; then
    log "Khong co JAR truoc de rollback; can can thiep thu cong."
    return 1
  fi

  log "Khoi phuc JAR truoc: $(head -n 1 "$PREVIOUS_RELEASE_VERSION" 2>/dev/null || printf 'unknown')"
  systemctl --user stop "$BACKEND_UNIT" 2>/dev/null || true
  cp -- "$PREVIOUS_JAR_PATH" "$JAR_PATH.rollback"
  mv -f -- "$JAR_PATH.rollback" "$JAR_PATH"
  if [[ -f "$PREVIOUS_RELEASE_VERSION" ]]; then
    cp -- "$PREVIOUS_RELEASE_VERSION" "$RELEASE_VERSION.rollback"
    mv -f -- "$RELEASE_VERSION.rollback" "$RELEASE_VERSION"
  else
    printf 'unknown\n' > "$RELEASE_VERSION"
  fi
  : > "$BACKEND_LOG"
  start_backend || return 1
  if ! wait_for_backend "$BACKEND_STARTUP_TIMEOUT"; then
    systemctl --user stop "$BACKEND_UNIT" 2>/dev/null || true
    log "Rollback JAR da chay nhung backend cu khong health-check duoc."
    return 1
  fi
  log "Rollback hoan tat; ban truoc dang phuc vu."
}

read_db_scalar() {
  local query="$1"
  docker exec "$DB_CONTAINER" sh -c '
    : "${MYSQL_USER:?MYSQL_USER is missing}"
    : "${MYSQL_PASSWORD:?MYSQL_PASSWORD is missing}"
    exec env MYSQL_PWD="$MYSQL_PASSWORD" mysql \
      --user="$MYSQL_USER" \
      --batch \
      --skip-column-names \
      --database="$1" \
      --execute="$2"
  ' sh "$DB_NAME" "$query"
}

cat > "$TUNNEL_CONFIG" <<EOF
tunnel: $TUNNEL_ID
credentials-file: $CREDENTIAL_FILE

ingress:
  - hostname: cfcbooking.io.vn
    service: http://localhost:8080
  - hostname: www.cfcbooking.io.vn
    service: http://localhost:8080
  - hostname: api.cfcbooking.io.vn
    service: http://localhost:8080
EOF
cat >> "$TUNNEL_CONFIG" <<'EOF'
  - service: http_status:404
EOF
chmod 600 "$TUNNEL_CONFIG"

cat > "$BACKEND_LAUNCHER" <<EOF
#!/usr/bin/env bash
set -a
[[ ! -f "$SCRIPT_DIR/.env" ]] || source "$SCRIPT_DIR/.env"
set +a
export WORD_EDITOR_ENABLED=false
export FLYWAY_BASELINE_ON_MIGRATE=$FLYWAY_BASELINE_MODE
export HR_PRODUCTION_ATTENDANCE_ENABLED=$HR_PRODUCTION_ATTENDANCE_ENABLED
export HR_PRODUCTION_ATTENDANCE_SHADOW_MODE=$HR_PRODUCTION_ATTENDANCE_SHADOW_MODE
export SPRING_JPA_SHOW_SQL=false
export SPRING_JPA_PROPERTIES_HIBERNATE_SHOW_SQL=false
cd "$BACKEND_DIR"
exec java $JAVA_OPTS -jar "$JAR_PATH" --spring.profiles.active=prod >> "$BACKEND_LOG" 2>&1
EOF

cat > "$TUNNEL_LAUNCHER" <<EOF
#!/usr/bin/env bash
exec cloudflared tunnel --config "$TUNNEL_CONFIG" run "$TUNNEL_ID" >> "$TUNNEL_LOG" 2>&1
EOF
chmod 700 "$BACKEND_LAUNCHER" "$TUNNEL_LAUNCHER"

log "Khoi dong MySQL va Redis..."
docker compose -f "$ROOT_DIR/docker-compose.yml" --project-directory "$ROOT_DIR" up -d db redis
wait_for_database

# Stop a previously deployed Document Server without removing its data volumes.
ONLYOFFICE_IMAGE="${ONLYOFFICE_IMAGE:-onlyoffice/documentserver:9.4.0}" \
WORD_EDITOR_JWT_SECRET="${WORD_EDITOR_JWT_SECRET:-stop-command-placeholder-secret}" \
  docker compose -f "$WORD_EDITOR_COMPOSE" --project-directory "$ROOT_DIR" stop documentserver >/dev/null 2>&1 \
  || fail "Khong dung duoc ONLYOFFICE Document Server."

flyway_history_count="$(read_db_scalar "
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 'flyway_schema_history';
")"
hr_table_count="$(read_db_scalar "
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name LIKE 'hr\\_%';
")"
legacy_table_count="$(read_db_scalar "
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_type = 'BASE TABLE'
    AND table_name NOT LIKE 'hr\\_%'
    AND table_name <> 'flyway_schema_history';
")"

if [[ "$flyway_history_count" == 0 && "$hr_table_count" != 0 ]]; then
  fail "Phat hien $hr_table_count bang hr_* nhung chua co Flyway history; dung lai de tranh baseline tren schema dang do."
fi
if [[ "$flyway_history_count" == 0 && "$legacy_table_count" != 0 && "$FLYWAY_BASELINE_MODE" != true ]]; then
  fail "Database cu co $legacy_table_count bang nhung chua khoi tao Flyway. Hay chay mot lan: ./deployserver/linux/run.sh --initialize-hr-schema"
fi

# Keep an existing tunnel online during the backend restart. It will reconnect
# to port 8080 as soon as the new or rolled-back application is healthy.
systemctl --user stop "$BACKEND_UNIT" 2>/dev/null || true
systemctl --user reset-failed "$BACKEND_UNIT" 2>/dev/null || true

if [[ "$FLYWAY_BASELINE_MODE" == true ]]; then
  log "Che do one-time Flyway baseline: tao backup va khoa row-count legacy sau khi backend da dung..."
  "$BACKUP_SCRIPT"
  HR_LEGACY_SNAPSHOT="$RUNTIME_DIR/legacy-before-hr-v1.tsv"
  "$LEGACY_SNAPSHOT_SCRIPT" "$HR_LEGACY_SNAPSHOT"
fi

log "Khoi dong Backend + Frontend production JAR..."
: > "$BACKEND_LOG"
BACKEND_STARTUP_TIMEOUT="${BACKEND_STARTUP_TIMEOUT:-120}"
if ! start_backend || ! wait_for_backend "$BACKEND_STARTUP_TIMEOUT"; then
  if ! rollback_backend_release; then
    fail "Backend moi khong san sang va rollback that bai. Tunnel duoc giu nguyen; can kiem tra $BACKEND_LOG"
  fi
  fail "Backend moi khong san sang; da rollback ve release truoc. Xem log: $BACKEND_LOG"
fi

if [[ "$FLYWAY_BASELINE_MODE" == true ]]; then
  log "Xac minh HR V1 va doi chieu row-count legacy truoc khi mo lai tunnel..."
  if ! BOOKINGBASE_HR_LEGACY_SNAPSHOT="$HR_LEGACY_SNAPSHOT" "$HR_VERIFY_SCRIPT"; then
    if ! rollback_backend_release; then
      fail "HR Phase 1 verify that bai va rollback cung that bai; kiem tra $BACKEND_LOG"
    fi
    fail "HR Phase 1 verify that bai; da rollback JAR. Database khong duoc rollback tu dong."
  fi
fi

if [[ "$HR_PRODUCTION_ATTENDANCE_ENABLED" == true ]]; then
  log "Xac minh schema va seed cham cong ca san xuat..."
  if ! "$PRODUCTION_ATTENDANCE_VERIFY_SCRIPT"; then
    if ! rollback_backend_release; then
      fail "Production attendance verify that bai va rollback cung that bai; kiem tra $BACKEND_LOG"
    fi
    fail "Production attendance verify that bai; da rollback JAR. Database khong duoc rollback tu dong."
  fi
fi

if ! systemctl --user is-active --quiet "$TUNNEL_UNIT"; then
  log "Khoi dong Cloudflare Tunnel bookingbase..."
  : > "$TUNNEL_LOG"
  systemctl --user reset-failed "$TUNNEL_UNIT" 2>/dev/null || true
  systemd-run --user --unit "${TUNNEL_UNIT%.service}" --collect \
    --property=Restart=on-failure --property=RestartSec=5s \
    "$TUNNEL_LAUNCHER" >/dev/null
  sleep 3
fi

if ! systemctl --user is-active --quiet "$TUNNEL_UNIT"; then
  tail -n 80 "$TUNNEL_LOG" >&2 || true
  fail "Cloudflare Tunnel da thoat. Log: $TUNNEL_LOG"
fi

log "Production dang chay."
printf '%s\n' \
  "  Local:   http://localhost:8080" \
  "  Public:  https://cfcbooking.io.vn" \
  "  API:     https://api.cfcbooking.io.vn" \
  "  Release: $(head -n 1 "$RELEASE_VERSION" 2>/dev/null || printf 'unknown')" \
  "  Backend log: $BACKEND_LOG" \
  "  Tunnel log:  $TUNNEL_LOG"
if [[ "$HR_PRODUCTION_ATTENDANCE_ENABLED" == true ]]; then
  printf '%s\n' "  Cham cong ca san xuat: enabled (shadow=$HR_PRODUCTION_ATTENDANCE_SHADOW_MODE)"
else
  printf '%s\n' "  Cham cong ca san xuat: disabled"
fi
