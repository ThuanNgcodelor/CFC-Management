import importlib.util
import os
import shutil
import subprocess
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("install_timer", ROOT / "deployserver/linux/install-deploy-timer.py")
installer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(installer)


class DeployTimerTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("systemd-analyze"), "systemd-analyze unavailable")
    def test_rendered_units_pass_systemd_parser_without_being_installed(self):
        with TemporaryDirectory() as directory:
            folder = Path(directory)
            environment = folder / "deploy.env"
            environment.write_text('PATH="/usr/bin"\n')
            source = ROOT / "deployserver/linux/systemd"
            service = folder / "bookingbase-deploy.service"
            service.write_text(installer.render_service((source / service.name).read_text(), ROOT, environment, sys.executable))
            timer = folder / "bookingbase-deploy.timer"
            timer.write_text((source / timer.name).read_text())
            result = subprocess.run(["systemd-analyze", "verify", "--man=no", str(service), str(timer)],
                                    capture_output=True, text=True, check=True)
            self.assertNotIn("bookingbase-deploy", result.stderr)

    def test_render_quotes_paths_and_escapes_systemd_specifiers(self):
        source = (ROOT / "deployserver/linux/systemd/bookingbase-deploy.service").read_text()
        rendered = installer.render_service(source, Path('/tmp/A B%"C'), Path('/tmp/env file'), '/usr/bin/python3')
        self.assertNotIn("@REPO_ROOT@", rendered)
        self.assertIn('WorkingDirectory=/tmp/A B%%"C', rendered)
        self.assertIn('EnvironmentFile=/tmp/env file', rendered)
        self.assertIn("--auto", rendered)

    def test_installer_only_persists_tool_environment(self):
        def output(arguments, **kwargs):
            return "yes\n" if arguments[0] == "loginctl" else ""
        with TemporaryDirectory() as directory, patch.dict(os.environ, {
            "XDG_CONFIG_HOME": directory, "PATH": "/usr/bin", "JAVA_HOME": "/jdk path", "JWT_SECRET": "test-sentinel"
        }, clear=True), patch.object(os, "getuid", return_value=1000), \
                patch.object(installer.shutil, "which", return_value="/usr/bin/tool"), \
                patch.object(installer.subprocess, "check_output", side_effect=output), \
                patch.object(installer.subprocess, "run") as command:
            installer.install()
            environment = (Path(directory) / "cfcbase/deploy.env").read_text()
            self.assertNotIn("JWT_SECRET", environment)
            self.assertNotIn("test-sentinel", environment)
            self.assertIn('JAVA_HOME="/jdk path"', environment)
            self.assertEqual(0o600, (Path(directory) / "cfcbase/deploy.env").stat().st_mode & 0o777)
            calls = [call.args[0] for call in command.call_args_list]
            self.assertEqual("--status", calls[0][-1])
            self.assertTrue(any("enable" in call and "bookingbase-deploy.timer" in call for call in calls))

    def test_not_ready_release_prevents_enabling_timer(self):
        with patch.object(os, "getuid", return_value=1000), \
                patch.object(installer.shutil, "which", return_value="/usr/bin/tool"), \
                patch.object(installer.subprocess, "check_output", side_effect=["yes", ""]), \
                patch.object(installer.subprocess, "run", side_effect=installer.subprocess.CalledProcessError(1, ["status"])) as command, \
                self.assertRaises(installer.subprocess.CalledProcessError):
            installer.install()
        self.assertEqual(1, command.call_count)


if __name__ == "__main__":
    unittest.main()
