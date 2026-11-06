"""Real local Git repositories; only CI responses, build and services are simulated."""

from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from test_ci_deploy import deploy, state_store


class PullDeployIntegrationTest(unittest.TestCase):
    def command(self, *args):
        return subprocess.check_output(["git", *map(str, args)], text=True, stderr=subprocess.DEVNULL).strip()

    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        base = Path(temporary.name)
        self.origin, self.root, self.publisher = base / "origin", base / "server", base / "publisher"
        self.command("init", "--bare", "--initial-branch=main", self.origin)
        self.command("clone", self.origin, self.root)
        (self.root / ".gitignore").write_text("backend/target/\n")
        self.old = self.commit(self.root)
        self.command("clone", self.origin, self.publisher)
        self.new = self.publish("app.txt", "new code")
        self.release = self.root / "backend/target/release-version.txt"
        self.release.parent.mkdir(parents=True)
        self.release.write_text(self.old + "\n")
        self.state_path = self.root / ".git" / state_store.STATE_FILE
        self.state = state_store.save_state(self.state_path, {}, "success", self.old)
        self.builds = 0
        self.real_run = subprocess.run

    def commit(self, root):
        self.command("-C", root, "add", ".")
        self.command("-C", root, "-c", "user.name=Deployment Test", "-c", "user.email=deploy@example.test", "commit", "-m", "fixture")
        self.command("-C", root, "push", "origin", "main")
        return self.command("-C", root, "rev-parse", "HEAD")

    def publish(self, name, content):
        path = self.publisher / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)
        return self.commit(self.publisher)

    def build_command(self, arguments, **kwargs):
        if arguments[0] == "bash":
            self.builds += 1
            self.release.write_text(self.new + "\n")
            return subprocess.CompletedProcess(arguments, 0)
        return self.real_run(arguments, **kwargs)

    def test_fetch_merge_success_then_next_poll_does_not_redeploy(self):
        with patch.object(deploy, "PUBLIC_GIT_URL", str(self.origin)), patch.object(deploy, "require_services"), \
                patch.object(deploy, "verify_ci", return_value={"html_url": "https://github.com/test"}) as api, \
                patch.object(subprocess, "run", side_effect=self.build_command):
            deploy.auto_deploy(self.root, self.state_path, self.state)
            state = state_store.load_state(self.state_path)
            deploy.auto_deploy(self.root, self.state_path, state)
        self.assertEqual(self.new, self.command("-C", self.root, "rev-parse", "HEAD"))
        self.assertEqual(self.new, state["last_success_sha"])
        self.assertEqual(1, self.builds)
        self.assertEqual(1, api.call_count)

    def test_actual_java_migration_diff_blocks_before_merge(self):
        self.publish("backend/src/main/java/db/migration/V100__test.java", "migration fixture")
        with patch.object(deploy, "PUBLIC_GIT_URL", str(self.origin)), patch.object(deploy, "require_services"), \
                patch.object(deploy, "verify_ci") as api, self.assertRaises(RuntimeError):
            deploy.auto_deploy(self.root, self.state_path, self.state)
        self.assertEqual(self.old, self.command("-C", self.root, "rev-parse", "HEAD"))
        api.assert_not_called()

    def test_dirty_checkout_stops_before_api_and_preserves_user_file(self):
        (self.root / "local.txt").write_text("preserve me")
        with patch.object(deploy, "PUBLIC_GIT_URL", str(self.origin)), patch.object(deploy, "require_services"), \
                patch.object(deploy, "verify_ci") as api, self.assertRaises(RuntimeError):
            deploy.auto_deploy(self.root, self.state_path, self.state)
        api.assert_not_called()
        self.assertEqual("preserve me", (self.root / "local.txt").read_text())
        self.assertEqual(self.old, self.command("-C", self.root, "rev-parse", "HEAD"))


if __name__ == "__main__":
    unittest.main()
