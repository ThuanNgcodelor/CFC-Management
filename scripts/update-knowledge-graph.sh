#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
graphify_bin="$HOME/.local/share/graphify/venv/bin/graphify"

if [[ ! -x "$graphify_bin" ]]; then
    echo "Chưa cài Graphify tại $graphify_bin" >&2
    exit 1
fi

cd -- "$repo_root"
if [[ ! -f graphify-out/graph.json ]]; then
    echo 'Chưa có bản đồ. Hãy chạy quy trình Graphify để tạo bản đồ lần đầu.' >&2
    exit 1
fi

"$graphify_bin" update .
"$graphify_bin" cluster-only . --no-label
