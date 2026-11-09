from pathlib import Path
from io import BytesIO
import importlib.util
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('console_patch', Path(__file__).with_name('patch-console.py'))
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


def archive(entries):
    b = BytesIO()
    with zipfile.ZipFile(b, 'w', compression=zipfile.ZIP_STORED) as z:
        for name, data in entries.items():
            z.writestr(name, data)
    return b.getvalue()


class PatchTests(unittest.TestCase):
    def test_patches_only_the_protection_condition_preserving_nested_jars(self):
        js = b'before ' + mod.OLD + b'badge:dash after'
        nested = archive({mod.ASSET: js, 'other.js': b'unchanged'})
        outer = archive({mod.LIB: nested, 'server.class': b'original server logic'})
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)/'app.jar'
            p.write_bytes(outer)
            mod.patch(p)
            with zipfile.ZipFile(p) as z:
                self.assertEqual(z.read('server.class'), b'original server logic')
                self.assertEqual(z.getinfo(mod.LIB).compress_type, zipfile.ZIP_STORED)
                with zipfile.ZipFile(BytesIO(z.read(mod.LIB))) as inner:
                    self.assertEqual(inner.read('other.js'), b'unchanged')
                    self.assertEqual(inner.read(mod.ASSET), b'before '+mod.NEW+b'badge:dash after')
            first = p.read_bytes()
            mod.patch(p)
            self.assertEqual(p.read_bytes(), first)

    def test_unknown_bundle_fails_without_overwriting_original(self):
        original = archive({mod.LIB: archive({mod.ASSET: b'new version'})})
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)/'app.jar';p.write_bytes(original)
            with self.assertRaises(RuntimeError):mod.patch(p)
            self.assertEqual(p.read_bytes(), original)

    def test_ambiguous_condition_is_rejected(self):
        with self.assertRaises(RuntimeError):mod.fix_js(mod.OLD+mod.OLD)


if __name__ == '__main__':
    unittest.main()
