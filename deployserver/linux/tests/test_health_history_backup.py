import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('health_backup', Path(__file__).resolve().parents[1] / 'check-health-history.py')
policy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(policy)


class HistoryJarBackupTest(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def jar(self, name, value):
        path = self.root / name
        with zipfile.ZipFile(path, 'w') as output:
            if value is not None:
                output.writestr(policy.ENTRY, value)
        return path

    def test_first_bundle_requires_backup_even_if_old_wrapper_has_not_checked_data_changes(self):
        old = self.jar('old.jar', None)
        new = self.jar('new.jar', b'encrypted synthetic history')
        self.assertNotEqual(policy.bundle_digest(new, required=True), policy.bundle_digest(old))

    def test_unchanged_ciphertext_does_not_force_an_extra_backup(self):
        a = self.jar('a.jar', b'ciphertext')
        b = self.jar('b.jar', b'ciphertext')
        self.assertEqual(policy.bundle_digest(a), policy.bundle_digest(b))

    def test_changed_bundle_requires_backup(self):
        a = self.jar('a.jar', b'old encrypted source')
        b = self.jar('b.jar', b'new encrypted source')
        self.assertNotEqual(policy.bundle_digest(a), policy.bundle_digest(b))

    def test_missing_bundle_in_staging_cannot_activate(self):
        with self.assertRaisesRegex(ValueError, 'thiếu'):
            policy.bundle_digest(self.jar('missing.jar', None), required=True)


if __name__ == '__main__':
    unittest.main()
