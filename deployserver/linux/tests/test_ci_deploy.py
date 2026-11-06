import importlib.util
from pathlib import Path
import unittest
from tempfile import TemporaryDirectory
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("ci_deploy", Path(__file__).resolve().parents[1] / "ci-deploy.py")
deploy = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(deploy)


def run(**updates):
    return {"id": 10, "run_number": 10, "run_attempt": 1, "head_sha": "abc",
            "head_branch": "main", "event": "push", "status": "completed",
            "conclusion": "success", **updates}


class CiGateTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
