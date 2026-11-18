#!/usr/bin/env python3
"""Run the synthetic health preview on loopback, with an isolated in-memory database."""
import argparse
import os
from pathlib import Path
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--maven', default='mvn')
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    args = parser.parse_args()
    java = str(Path(args.java_home) / 'bin/java') if args.java_home else shutil.which('java')
    if not java or not Path(java).is_file() or not shutil.which(args.maven) or not shutil.which('npm'):
        raise SystemExit('Cần Java 21, Maven và npm đã cài. Script không tự tải dependency.')
    for port in (18086, 5196):
        with socket.socket() as connection:
            try:
                connection.bind(('127.0.0.1', port))
            except OSError:
                raise SystemExit(f'Cổng QA {port} đang được dùng. Dừng đúng demo cũ trước; script không tự tắt process khác.') from None
    folder = Path(tempfile.mkdtemp(prefix='cfc-health-2026-')); folder.chmod(0o700)
    env = os.environ.copy()
    if args.java_home:
        env['JAVA_HOME'] = args.java_home
    processes = []
    try:
        with (folder / 'compile.log').open('w') as log:
            (folder / 'compile.log').chmod(0o600)
            subprocess.run([args.maven, '--offline', '-B', '-ntp', '-f', str(ROOT / 'backend/pom.xml'),
                            f'-Dbooking.build.directory={folder / "target"}', '-Dtest=Health2026ReadinessTest', 'test'],
                           cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
        report = folder / 'target/surefire-reports/TEST-com.booking.system.health.Health2026ReadinessTest.xml'
        classpath = ET.parse(report).find(".//property[@name='java.class.path']").get('value')
        with (folder / 'backend.log').open('w') as log:
            (folder / 'backend.log').chmod(0o600)
            backend = subprocess.Popen([java, '-cp', classpath, 'com.booking.system.health.Health2026Preview', str(folder)],
                                       cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            processes.append(backend)
        deadline = time.monotonic() + 120
        while not (folder / 'ready.json').exists():
            if backend.poll() is not None or time.monotonic() > deadline:
                raise RuntimeError('Backend QA chưa sẵn sàng. Xem backend.log private trong thư mục demo.')
            time.sleep(0.5)
        env.update(VITE_API_URL='http://127.0.0.1:18086/api/v1', VITE_HEALTH_ENABLED='true')
        with (folder / 'frontend.log').open('w') as log:
            (folder / 'frontend.log').chmod(0o600)
            frontend = subprocess.Popen(['npm', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', '5196', '--strictPort'],
                                        cwd=ROOT / 'frontend', env=env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            processes.append(frontend)
        print('Demo quản trị: http://127.0.0.1:5196/tests/health-phase9.html', flush=True)
        print('Tra cứu cá nhân: http://127.0.0.1:5196/health-lookup', flush=True)
        print(f'5 file Excel giả và mã demo private: {folder}', flush=True)
        print('Ctrl+C để dừng hai process QA; dữ liệu H2 memory tự mất.', flush=True)
        while backend.poll() is None and frontend.poll() is None:
            time.sleep(1)
        raise RuntimeError('Một process QA đã dừng; xem log trong thư mục demo.')
    except KeyboardInterrupt:
        print('\nĐã dừng demo QA.')
    except (subprocess.CalledProcessError, RuntimeError) as error:
        raise SystemExit(f'{error}\nBằng chứng private: {folder}') from None
    finally:
        for process in reversed(processes):
            if process.poll() is None:
                os.killpg(process.pid, signal.SIGTERM)
                try:
                    process.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    os.killpg(process.pid, signal.SIGKILL); process.wait()


if __name__ == '__main__':
    main()
