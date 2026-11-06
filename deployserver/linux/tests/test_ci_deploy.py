import importlib.util
from pathlib import Path
import unittest
import sys
from tempfile import TemporaryDirectory
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("ci_deploy", Path(__file__).resolve().parents[1] / "ci-deploy.py")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
deploy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy)
import ci_deploy_state as state_store


def run(**updates):
    return {"id": 10, "run_number": 10, "run_attempt": 1, "head_sha": "abc",
            "head_branch": "main", "event": "push", "status": "completed",
            "conclusion": "success", **updates}


class CiGateTest(unittest.TestCase):
    def test_concurrent_wrapper_cannot_enter_deployment(self):
        with TemporaryDirectory() as directory:
            with (Path(directory) / "cfcbase-deploy.lock").open("w") as lock:
                deploy.fcntl.flock(lock, deploy.fcntl.LOCK_EX | deploy.fcntl.LOCK_NB)
                with patch.object(deploy, "git", return_value=directory), patch.object(deploy, "prepare") as prepare, \
                        patch.object(deploy.sys, "argv", ["ci-deploy.py", "--auto"]), self.assertRaises(RuntimeError):
                    deploy.main()
                prepare.assert_not_called()

    def test_check_mode_never_merges_or_builds(self):
        with TemporaryDirectory() as directory, patch.object(deploy, "git", return_value=directory), \
                patch.object(deploy, "prepare", return_value=("old", "new")), \
                patch.object(deploy.subprocess, "run") as command, \
                patch.object(deploy.sys, "argv", ["ci-deploy.py", "--check"]), patch("builtins.print"):
            deploy.main()
            command.assert_not_called()

    def test_active_runner_blocks_deploy_before_merge_or_build(self):
        with TemporaryDirectory() as directory, patch.object(deploy, "git", return_value=directory), \
                patch.object(deploy, "prepare", return_value=("old", "new")), \
                patch.object(deploy.subprocess, "run", return_value=deploy.subprocess.CompletedProcess([], 0)) as command, \
                patch.object(deploy.sys, "argv", ["ci-deploy.py", "--deploy"]), self.assertRaises(RuntimeError):
            deploy.main()
        self.assertEqual(1, command.call_count)
        self.assertEqual("systemctl", command.call_args.args[0][0])

    def test_old_success_does_not_override_latest_failure_or_pending(self):
        for updates in ({"conclusion": "failure"}, {"status": "in_progress", "conclusion": None}):
            with self.subTest(updates=updates), self.assertRaises(RuntimeError):
                deploy.latest_run([run(run_number=9), run(**updates)], "abc")

    def test_different_commit_branch_and_pr_cannot_authorize_deployment(self):
        for updates in ({"head_sha": "other"}, {"head_branch": "feature"}, {"event": "pull_request"}):
            with self.subTest(updates=updates), self.assertRaises(RuntimeError):
                deploy.latest_run([run(**updates)], "abc")

    def test_required_jobs_must_both_succeed(self):
        jobs = [{"name": name, "status": "completed", "conclusion": "success"}
                for name in deploy.REQUIRED_JOBS]
        deploy.verify_jobs(jobs)
        for bad in (jobs[:1], [dict(job, conclusion="skipped") for job in jobs], jobs + jobs):
            with self.subTest(jobs=bad), self.assertRaises(RuntimeError):
                deploy.verify_jobs(bad)

    def test_checks_exact_run_attempt_and_rejects_unread_jobs(self):
        with patch.object(deploy, "api_get", side_effect=[
            {"workflow_runs": [run(run_attempt=2)]}, {"jobs": [], "total_count": 101}
        ]) as api, self.assertRaises(RuntimeError):
            deploy.verify_ci("abc")
        self.assertIn("/attempts/2/jobs", api.call_args_list[1].args[0])


class AutoDeployTest(unittest.TestCase):
    old = "a" * 40
    new = "b" * 40

    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.state_path = self.root / state_store.STATE_FILE
        self.release = self.root / "backend/target/release-version.txt"
        self.release.parent.mkdir(parents=True)
        self.release.write_text(self.old + "\nBuilt at: test\n")
        self.state = state_store.save_state(self.state_path, {}, "success", self.old)

    def test_unchanged_release_never_calls_api_or_restarts(self):
        with patch.object(deploy, "require_services"), patch.object(deploy, "prepare", return_value=(self.old, self.old)), \
                patch.object(deploy, "verify_ci") as api, patch.object(deploy, "deploy_release") as build:
            deploy.auto_deploy(self.root, self.state_path, self.state)
        api.assert_not_called()
        build.assert_not_called()

    def test_auto_uses_deployed_baseline_even_when_checkout_was_pulled_ahead(self):
        with patch.object(deploy, "require_services"), patch.object(deploy, "prepare", return_value=(self.new, self.new)), \
                patch.object(deploy, "git", return_value="") as git, \
                patch.object(deploy, "verify_ci", return_value={"html_url": "https://github.com/test"}), \
                patch.object(deploy, "deploy_release") as build:
            deploy.auto_deploy(self.root, self.state_path, self.state)
        self.assertEqual((self.old, self.new), git.call_args.args[3:5])
        build.assert_called_once()

    def test_migration_change_requires_manual_deploy_before_ci_or_build(self):
        with patch.object(deploy, "require_services"), patch.object(deploy, "prepare", return_value=(self.old, self.new)), \
                patch.object(deploy, "git", return_value="backend/src/main/java/db/migration/V100.java"), \
                patch.object(deploy, "verify_ci") as api, patch.object(deploy, "deploy_release") as build, self.assertRaises(RuntimeError):
            deploy.auto_deploy(self.root, self.state_path, self.state)
        api.assert_not_called()
        build.assert_not_called()

    def test_failed_interrupted_or_uninitialized_state_blocks_new_commits(self):
        for state in ({}, {**self.state, "phase": "failed"}, {**self.state, "phase": "deploying"}):
            with self.subTest(state=state), patch.object(deploy, "prepare") as prepare, self.assertRaises(RuntimeError):
                deploy.auto_deploy(self.root, self.state_path, state)
            prepare.assert_not_called()

    def test_pending_ci_does_not_mark_attempt_or_deploy(self):
        with patch.object(deploy, "require_services"), patch.object(deploy, "prepare", return_value=(self.old, self.new)), \
                patch.object(deploy, "git", return_value=""), \
                patch.object(deploy, "verify_ci", side_effect=deploy.CiNotReady("CI đang chạy")), \
                patch.object(deploy, "deploy_release") as build:
            deploy.auto_deploy(self.root, self.state_path, self.state)
        build.assert_not_called()
        self.assertEqual("success", state_store.load_state(self.state_path)["phase"])

    def test_build_failure_keeps_successful_sha_and_pauses_future_runs(self):
        def command(arguments, **kwargs):
            # A power cut would leave 'deploying'; a normal build failure must leave 'failed'.
            self.assertEqual("deploying", state_store.load_state(self.state_path)["phase"])
            if arguments[0] == "bash":
                raise deploy.subprocess.CalledProcessError(1, arguments)
        with patch.object(deploy, "git", side_effect=[self.old, ""]), \
                patch.object(deploy.subprocess, "run", side_effect=command), self.assertRaises(deploy.subprocess.CalledProcessError):
            deploy.deploy_release(self.root, self.old, self.new, self.state_path, self.state)
        state = state_store.load_state(self.state_path)
        self.assertEqual(("failed", self.old, self.new), (state["phase"], state["last_success_sha"], state["attempt_sha"]))
        with self.assertRaises(RuntimeError):
            state_store.require_retry_acknowledgement(state, False)
        state_store.require_retry_acknowledgement(state, True)

    def test_success_written_only_after_build_and_release_verification(self):
        def command(arguments, **kwargs):
            if arguments[0] == "bash":
                self.release.write_text(self.new + "\n")
        with patch.object(deploy, "git", side_effect=[self.old, ""]), \
                patch.object(deploy.subprocess, "run", side_effect=command), patch.object(deploy, "require_services"):
            deploy.deploy_release(self.root, self.old, self.new, self.state_path, self.state)
        state = state_store.load_state(self.state_path)
        self.assertEqual(("success", self.new), (state["phase"], state["last_success_sha"]))

    def test_exit_zero_with_wrong_release_is_failure(self):
        with patch.object(deploy, "git", side_effect=[self.old, ""]), \
                patch.object(deploy.subprocess, "run"), patch.object(deploy, "require_services"), self.assertRaises(RuntimeError):
            deploy.deploy_release(self.root, self.old, self.new, self.state_path, self.state)
        self.assertEqual("failed", state_store.load_state(self.state_path)["phase"])

    def test_external_release_change_blocks_auto(self):
        self.release.write_text(self.new + "-dirty\n")
        with patch.object(deploy, "prepare") as prepare, self.assertRaises(RuntimeError):
            deploy.auto_deploy(self.root, self.state_path, self.state)
        prepare.assert_not_called()

    def test_corrupt_state_is_not_replaced(self):
        self.state_path.write_text('{"phase": "unknown"}')
        with self.assertRaises(RuntimeError):
            state_store.load_state(self.state_path)
        self.assertEqual('{"phase": "unknown"}', self.state_path.read_text())

    def test_retry_flag_cannot_be_used_by_timer(self):
        with patch.object(deploy.sys, "argv", ["ci-deploy.py", "--auto", "--retry-failed"]), self.assertRaises(SystemExit):
            deploy.main()


if __name__ == "__main__":
    unittest.main()
