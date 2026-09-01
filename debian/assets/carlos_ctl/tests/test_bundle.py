# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""--bundle contract tests: member classification, magic checks, openssl
command construction, refusal paths, and real end-to-end extraction of all
four suffix variants (tar/openssl are available on any dev machine).

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import os
import shutil
import subprocess
import tempfile
import unittest

from carlos_ctl import o19bundle


class TestClassifyMembers(unittest.TestCase):

    GOOD = ["o19.sql.gz", "o19-documents.tar.gz", "oscar.properties"]

    def test_happy_path(self):
        m = o19bundle.classify_members(self.GOOD)
        self.assertEqual(m, {"dump": "o19.sql.gz",
                             "documents": "o19-documents.tar.gz",
                             "properties": "oscar.properties"})

    def test_documents_member_is_optional(self):
        m = o19bundle.classify_members(["o19.sql", "oscar.properties"])
        self.assertIsNone(m["documents"])

    def test_two_dumps_are_ambiguous(self):
        with self.assertRaises(ValueError) as cm:
            o19bundle.classify_members(self.GOOD + ["extra.sql"])
        self.assertIn("exactly one", str(cm.exception))

    def test_missing_properties_is_refused(self):
        with self.assertRaises(ValueError):
            o19bundle.classify_members(["o19.sql.gz",
                                        "o19-documents.tar.gz"])

    def test_unknown_member_is_refused(self):
        with self.assertRaises(ValueError) as cm:
            o19bundle.classify_members(self.GOOD + ["notes.txt"])
        self.assertIn("unrecognized", str(cm.exception))

    def test_pathed_member_is_refused(self):
        with self.assertRaises(ValueError) as cm:
            o19bundle.classify_members(["sub/o19.sql.gz",
                                        "oscar.properties"])
        self.assertIn("path", str(cm.exception))

    def test_traversal_member_is_refused(self):
        with self.assertRaises(ValueError):
            o19bundle.classify_members(["../o19.sql.gz",
                                        "oscar.properties"])


class TestBundleKindAndArgs(unittest.TestCase):

    def test_all_four_suffixes(self):
        self.assertEqual(o19bundle.bundle_kind("b.tar"), (False, False))
        self.assertEqual(o19bundle.bundle_kind("b.tar.gz"), (False, True))
        self.assertEqual(o19bundle.bundle_kind("b.tar.enc"), (True, False))
        self.assertEqual(o19bundle.bundle_kind("b.tar.gz.enc"), (True, True))

    def test_unknown_suffix_is_refused(self):
        with self.assertRaises(ValueError):
            o19bundle.bundle_kind("b.zip")

    def test_enc_without_pass_is_refused(self):
        with self.assertRaises(ValueError) as cm:
            o19bundle.validate_bundle_args("b.tar.gz.enc", None)
        self.assertIn("--bundle-pass", str(cm.exception))

    def test_pass_without_enc_is_refused(self):
        with self.assertRaises(ValueError):
            o19bundle.validate_bundle_args("b.tar.gz", "file:x")

    def test_openssl_argv_defaults_to_canonical_derivation(self):
        argv = o19bundle.openssl_decrypt_argv("aes-256-cbc", [], "file:p")
        self.assertEqual(argv, ["openssl", "enc", "-d", "-aes-256-cbc",
                                "-pbkdf2", "-iter", "200000",
                                "-pass", "file:p"])

    def test_openssl_opts_replace_the_derivation_defaults(self):
        argv = o19bundle.openssl_decrypt_argv(
            "aes-256-cbc", ["-md", "md5"], "env:P")
        self.assertEqual(argv, ["openssl", "enc", "-d", "-aes-256-cbc",
                                "-md", "md5", "-pass", "env:P"])


def _have(cmd):
    return shutil.which(cmd) is not None


@unittest.skipUnless(_have("tar") and _have("openssl"),
                     "tar/openssl unavailable")
class TestOpenBundleEndToEnd(unittest.TestCase):

    def setUp(self):
        self.work = tempfile.mkdtemp(prefix="o19bundle-test-")
        self.addCleanup(shutil.rmtree, self.work)
        src = os.path.join(self.work, "src")
        os.makedirs(src)
        with open(os.path.join(src, "o19.sql"), "w") as fh:
            fh.write("-- fixture dump\nSELECT 1;\n-- Dump completed\n")
        with open(os.path.join(src, "oscar.properties"), "w") as fh:
            fh.write("billregion=ON\n")
        self.passfile = os.path.join(self.work, "pass")
        with open(self.passfile, "w") as fh:
            fh.write("test-password")
        self.src = src

    def _tar(self, out, gz):
        argv = (["tar", "-C", self.src]
                + (["-czf"] if gz else ["-cf"])
                + [out, "o19.sql", "oscar.properties"])
        subprocess.check_call(argv)

    def _enc(self, plain, out, opts=None):
        argv = (["openssl", "enc", "-aes-256-cbc"]
                + (opts if opts is not None
                   else ["-pbkdf2", "-iter", "200000"])
                + ["-salt", "-pass", "file:" + self.passfile,
                   "-in", plain, "-out", out])
        subprocess.check_call(argv)

    def test_plain_and_gz_and_both_enc_variants_extract(self):
        for gz in (False, True):
            plain = os.path.join(
                self.work, "b.tar.gz" if gz else "b.tar")
            self._tar(plain, gz)
            for enc in (False, True):
                path = plain + (".enc" if enc else "")
                if enc:
                    self._enc(plain, path)
                dest = tempfile.mkdtemp(dir=self.work)
                res = o19bundle.open_bundle(
                    path, dest,
                    pass_spec=("file:" + self.passfile) if enc else None)
                self.assertTrue(os.path.isfile(res["dump"]))
                self.assertTrue(os.path.isfile(res["properties"]))
                self.assertIsNone(res["documents"])
                self.assertEqual(len(res["bundle_sha256"]), 64)
                with open(res["dump"]) as fh:
                    self.assertIn("Dump completed", fh.read())

    def test_wrong_password_dies_with_guidance(self):
        plain = os.path.join(self.work, "b.tar")
        self._tar(plain, gz=False)
        enc = plain + ".enc"
        self._enc(plain, enc)
        wrong = os.path.join(self.work, "wrong")
        with open(wrong, "w") as fh:
            fh.write("not-the-password")
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(enc, tempfile.mkdtemp(dir=self.work),
                                  pass_spec="file:" + wrong)

    def test_derivation_mismatch_dies_not_garbage(self):
        # encrypted WITHOUT -pbkdf2 (old-openssl style), decrypted with the
        # canonical defaults: must die with guidance, never hand back bytes.
        plain = os.path.join(self.work, "b.tar")
        self._tar(plain, gz=False)
        enc = plain + ".enc"
        self._enc(plain, enc, opts=["-md", "md5"])
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(enc, tempfile.mkdtemp(dir=self.work),
                                  pass_spec="file:" + self.passfile)

    def test_gz_named_but_not_gz_is_refused(self):
        bogus = os.path.join(self.work, "b.tar.gz")
        with open(bogus, "wb") as fh:
            fh.write(b"not gzip at all")
        with self.assertRaises(ValueError):
            o19bundle.check_magic(bogus, gzipped=True)


if __name__ == "__main__":
    unittest.main()
