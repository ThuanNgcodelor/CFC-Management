#!/usr/bin/env python3
"""Compare only the encrypted history resource in staging/current JARs before activation."""
import hashlib
from pathlib import Path
import sys
import zipfile

ENTRY = 'BOOT-INF/classes/health/history/company-history-v1.bundle'
MAX_BYTES = 32 * 1024 * 1024


def bundle_digest(path, required=False):
    if not Path(path).is_file():
        if required:
            raise ValueError('Không tìm thấy staging JAR.')
        return None
    with zipfile.ZipFile(path) as jar:
        if ENTRY not in jar.namelist():
            if required:
                raise ValueError('Staging JAR thiếu gói lịch sử đã khai báo trong source.')
            return None
        with jar.open(ENTRY) as source:
            data = source.read(MAX_BYTES + 1)
        if len(data) > MAX_BYTES:
            raise ValueError('Gói lịch sử vượt giới hạn 32MiB.')
        return hashlib.sha256(data).hexdigest()


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit('Cần staging JAR và JAR hiện tại.')
    print('same' if bundle_digest(sys.argv[1], required=True) == bundle_digest(sys.argv[2]) else 'changed')
