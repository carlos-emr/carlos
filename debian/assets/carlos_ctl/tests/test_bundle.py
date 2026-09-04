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
from unittest import mock

from carlos_ctl import o19bundle


class TestClassifyMembers(unittest.TestCase):

    """The bundle's member list, resolved to roles.

    A bundle is three files with fixed roles; anything else in it is a
    refusal rather than a guess, because guessing which of two dumps is
    the clinic's is how the wrong database gets imported."""
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

    """Which bundle forms exist, and the openssl argv each implies.

    The derivation defaults are the ones a modern openssl uses; a clinic
    on an older one passes its own, and the flags must then REPLACE the
    defaults rather than stack with them."""
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
        argv = o19bundle.openssl_decrypt_argv("aes-256-cbc", [], "file:p",
                                              "/srv/b.tar.enc")
        self.assertEqual(argv, ["openssl", "enc", "-d", "-aes-256-cbc",
                                "-pbkdf2", "-iter", "200000",
                                "-pass", "file:p", "-in", "/srv/b.tar.enc"])

    def test_openssl_opts_replace_the_derivation_defaults(self):
        argv = o19bundle.openssl_decrypt_argv(
            "aes-256-cbc", ["-md", "md5"], "env:P", "b.enc")
        self.assertEqual(argv, ["openssl", "enc", "-d", "-aes-256-cbc",
                                "-md", "md5", "-pass", "env:P", "-in",
                                "b.enc"])

    def test_bundle_is_read_via_in_so_pass_stdin_stays_usable(self):
        # `-pass stdin` reads the password from stdin: the ciphertext must
        # therefore never be piped through stdin
        argv = o19bundle.openssl_decrypt_argv("aes-256-cbc", [], "stdin",
                                              "b.enc")
        self.assertIn("-in", argv)
        self.assertEqual(o19bundle.pass_spec_fd("fd:7"), 7)
        self.assertIsNone(o19bundle.pass_spec_fd("file:x"))
        self.assertIsNone(o19bundle.pass_spec_fd("fd:x"))


class TestTarListingAndMemberTypes(unittest.TestCase):

    """The tar listing parser, and the member types it refuses.

    Every refusal here is an extraction that never happens: a symlink, a
    device node, an absolute or traversing name, or a member whose name
    could be read by tar as an option."""
    LISTING = [
        "-rw-r--r-- root/root      1234 2020-03-09 00:00 o19.sql.gz",
        "-rw-r--r-- root/root        12 2020-03-09 00:00 oscar.properties",
        "lrwxrwxrwx root/root         0 2020-03-09 00:00 o19-docs.tar -> "
        "/etc/shadow",
        "drwxr-xr-x root/root         0 2020-03-09 00:00 sub/",
        "hrw-r--r-- root/root         0 2020-03-09 00:00 x.sql link to "
        "o19.sql.gz",
        "crw-rw-rw- root/root       1,3 2020-03-09 00:00 null",
    ]

    def test_parses_type_letter_and_name_with_spaces(self):
        entries = o19bundle.parse_tar_listing(
            ["-rw-r--r-- u/g 5 2020-03-09 00:00 name with spaces.sql"])
        self.assertEqual(entries, [("-", "name with spaces.sql")])

    def test_symlink_hardlink_device_are_refused(self):
        entries = o19bundle.parse_tar_listing(self.LISTING)
        with self.assertRaises(ValueError) as cm:
            o19bundle.validate_tar_members(entries, allow_dirs=True)
        msg = str(cm.exception)
        for name in ("o19-docs.tar", "x.sql", "null"):
            self.assertIn(name, msg)
        self.assertNotIn("sub/", msg.split("is not a plain file")[0])

    def test_directories_only_when_allowed(self):
        entries = o19bundle.parse_tar_listing(self.LISTING[:2] +
                                              [self.LISTING[3]])
        names = o19bundle.validate_tar_members(entries, allow_dirs=True)
        self.assertEqual(names, ["o19.sql.gz", "oscar.properties", "sub/"])
        with self.assertRaises(ValueError):
            o19bundle.validate_tar_members(entries, allow_dirs=False)

    def test_absolute_and_traversal_names_are_refused(self):
        for bad in ("/etc/passwd", "a/../../x", ".."):
            with self.assertRaises(ValueError):
                o19bundle.validate_tar_members([("-", bad)],
                                               allow_dirs=True)
        # a leading ./ is fine
        self.assertEqual(
            o19bundle.validate_tar_members([("-", "./ok.sql")], True),
            ["./ok.sql"])

    def test_option_like_member_names_are_refused(self):
        # GNU tar permutes argv, so a member named like an option would be
        # parsed as one at extraction (root RCE via --to-command=...)
        for bad in ("--to-command=sh x.properties",
                    "--checkpoint-action=exec=touch /tmp/p .sql", "-x.sql"):
            with self.assertRaises(ValueError):
                o19bundle.validate_tar_members([("-", bad)], allow_dirs=False)
            with self.assertRaises(ValueError):
                o19bundle.classify_members([bad, "o19.sql", "a.properties"])

    def test_listed_size_sums_plain_file_sizes(self):
        listing = ["-rw-r--r-- u/g 1000 2020-03-09 00:00 a.sql",
                   "drwxr-xr-x u/g 0 2020-03-09 00:00 d/",
                   "-rw-r--r-- u/g 24 2020-03-09 00:00 d/b.pdf"]
        self.assertEqual(o19bundle.listed_size(listing), 1024)

    def test_lone_directory_member_is_refused_by_classifier(self):
        # regression: a directory named like the dump used to classify as
        # the dump and blow up later in sha256_file
        with self.assertRaises(ValueError) as cm:
            o19bundle.classify_members(["o19.sql/", "oscar.properties"])
        self.assertIn("directory", str(cm.exception))


class TestTarEntriesFromHeaders(unittest.TestCase):
    """The listing comes from the archive's own headers, not from a
    formatted `tar -tv` rendering: GNU tar escapes non-ASCII names under
    LC_ALL=C, and bsdtar prints nine columns rather than six."""

    def _archive(self, gz=True):
        import tarfile
        root = tempfile.mkdtemp(prefix="o19tar-")
        self.addCleanup(shutil.rmtree, root)
        src = os.path.join(root, "src")
        os.makedirs(os.path.join(src, "sub"))
        for name in ("documents-sant\u00e9.tar.gz", "sub/d\u00e9j\u00e0.pdf"):
            with open(os.path.join(src, name), "wb") as fh:
                fh.write(b"x" * 10)
        os.symlink("/etc/shadow", os.path.join(src, "link"))
        path = os.path.join(root, "a.tar.gz" if gz else "a.tar")
        with tarfile.open(path, "w:gz" if gz else "w") as tf:
            tf.add(src, arcname=".")
        return path

    def test_non_ascii_names_survive_verbatim(self):
        entries = o19bundle.read_tar_entries(self._archive(), True)
        names = [n for _k, n, _s in entries]
        self.assertIn("./documents-sant\u00e9.tar.gz", names)
        self.assertIn("./sub/d\u00e9j\u00e0.pdf", names)

    def test_sizes_come_from_the_headers(self):
        entries = o19bundle.read_tar_entries(self._archive(), True)
        self.assertEqual(o19bundle.entries_size(entries), 20)

    def test_a_symlink_is_typed_and_refused(self):
        entries = o19bundle.read_tar_entries(self._archive(), True)
        with self.assertRaises(ValueError) as cm:
            o19bundle.validate_tar_members(
                [(k, n) for k, n, _ in entries], allow_dirs=True)
        self.assertIn("not a plain file", str(cm.exception))

    def test_a_dot_built_bundle_extracts(self):
        # `tar -C dir -czf bundle .` is the ordinary way to build one: it
        # writes the archive root as a `.` entry and every member as
        # ./name, and used to be refused with three confusing messages
        import tarfile
        root = tempfile.mkdtemp(prefix="o19dot-")
        self.addCleanup(shutil.rmtree, root)
        src = os.path.join(root, "src")
        os.makedirs(src)
        for name in ("o19.sql.gz", "oscar.properties"):
            with open(os.path.join(src, name), "w") as fh:
                fh.write("x")
        path = os.path.join(root, "b.tar.gz")
        with tarfile.open(path, "w:gz") as tf:
            tf.add(src, arcname=".")
        entries = o19bundle.read_tar_entries(path, True)
        names = o19bundle.validate_tar_members(
            [(k, n) for k, n, _ in entries], allow_dirs=False)
        self.assertEqual(sorted(names),
                         ["./o19.sql.gz", "./oscar.properties"])
        members = o19bundle.classify_members(names)
        out = os.path.join(root, "out")
        os.makedirs(out)
        rc = subprocess.call(
            ["tar", "-xzf", path, "-C", out, "--"]
            + [m for m in members.values() if m])
        self.assertEqual(rc, 0)
        self.assertEqual(sorted(os.listdir(out)),
                         ["o19.sql.gz", "oscar.properties"])

    def test_a_real_subdirectory_is_still_refused_in_a_bundle(self):
        with self.assertRaises(ValueError):
            o19bundle.validate_tar_members(
                [("d", "./sub"), ("-", "./sub/x.sql")], allow_dirs=False)

    def test_dot_slash_members_are_the_ordinary_bundle_shape(self):
        # `tar -C dir -czf bundle .` writes every member as ./name
        members = o19bundle.classify_members(
            ["./o19.sql.gz", "./oscar.properties", "./o19-documents.tar.gz"])
        # the ARCHIVE's own name goes back to tar: `-- o19.sql.gz` does
        # not match a member stored as `./o19.sql.gz`
        self.assertEqual(members["dump"], "./o19.sql.gz")
        self.assertEqual(members["properties"], "./oscar.properties")
        self.assertEqual(members["documents"], "./o19-documents.tar.gz")


class TestTarHeaderChecksum(unittest.TestCase):

    """The magic/checksum probe that decides a file really is a tar.

    v7 tars carry no magic string, so the checksum is the only evidence;
    accepting them matters because that is what old OSCAR hosts produce."""
    @staticmethod
    def v7_header(name=b"o19.sql", size=0):
        # a v7 (pre-POSIX) header: no ustar magic at 257, checksum only
        h = bytearray(512)
        h[0:len(name)] = name
        h[100:108] = b"0000644\0"
        h[108:116] = b"0000000\0"
        h[116:124] = b"0000000\0"
        h[124:136] = ("%011o" % size).encode() + b"\0"
        h[136:148] = b"00000000000\0"
        h[156] = ord("0")
        h[148:156] = b"        "
        chk = sum(h)
        h[148:156] = ("%06o" % chk).encode() + b"\0 "
        return bytes(h)

    def test_v7_header_without_magic_passes_checksum(self):
        head = self.v7_header()
        self.assertNotEqual(head[257:262], b"ustar")
        self.assertTrue(o19bundle.tar_header_checksum_ok(head))

    def test_corrupt_header_fails(self):
        head = bytearray(self.v7_header())
        head[0] ^= 0xFF
        self.assertFalse(o19bundle.tar_header_checksum_ok(bytes(head)))
        self.assertFalse(o19bundle.tar_header_checksum_ok(b"\0" * 512))
        self.assertFalse(o19bundle.tar_header_checksum_ok(b"not a tar"))

    def test_check_magic_accepts_v7_tar_file(self):
        work = tempfile.mkdtemp(prefix="o19bundle-v7-")
        self.addCleanup(shutil.rmtree, work)
        path = os.path.join(work, "b.tar")
        with open(path, "wb") as fh:
            fh.write(self.v7_header() + b"\0" * 1024)
        o19bundle.check_magic(path, gzipped=False)  # must not raise


def _have(cmd):
    return shutil.which(cmd) is not None


@unittest.skipUnless(_have("tar") and _have("openssl"),
                     "tar/openssl unavailable")
class TestOpenBundleEndToEnd(unittest.TestCase):

    """Opening a real bundle, in every form the clinic may send.

    The failure paths are the point: a wrong password, a derivation
    mismatch, a FIFO or directory in place of the file, a digest that
    does not match what was opened, and too little disk -- each refused
    without leaving plaintext or a half-extracted tree behind."""
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

    def test_refusal_after_decryption_leaves_no_plaintext_behind(self):
        # two dumps: classified as ambiguous AFTER the decrypt; the
        # plaintext tar must not survive the refusal in the workdir
        with open(os.path.join(self.src, "other.sql"), "w") as fh:
            fh.write("-- second dump\n")
        plain = os.path.join(self.work, "two.tar")
        subprocess.check_call(["tar", "-C", self.src, "-cf", plain,
                               "o19.sql", "other.sql", "oscar.properties"])
        enc = plain + ".enc"
        self._enc(plain, enc)
        dest = tempfile.mkdtemp(dir=self.work)
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(enc, dest,
                                  pass_spec="file:" + self.passfile)
        self.assertEqual(os.listdir(dest), [])

    def test_a_fifo_is_refused_before_anything_reads_it(self):
        # a FIFO sizes as 0, so the capacity check passes and the copy
        # then blocks forever on a writer that never comes: the import
        # hangs with nothing written and no error to explain it. The
        # refusal has to precede the read, so this test would time out
        # rather than fail if the guard were removed -- which is why it
        # is asserted as a refusal, not as an absence of output.
        # must end in .tar: the name check runs first, so a FIFO
        # called anything else never reaches this guard
        fifo = os.path.join(self.work, "bundle.tar")
        os.mkfifo(fifo)
        dest = tempfile.mkdtemp(dir=self.work)
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(fifo, dest)

    def test_a_directory_is_refused_as_a_bundle(self):
        # same guard, the case an operator actually hits: pointing
        # --bundle at the directory the bundle lives in
        d = os.path.join(self.work, "adir.tar")
        os.mkdir(d)
        dest = tempfile.mkdtemp(dir=self.work)
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(d, dest)

    def test_digest_verified_by_the_caller_must_match_the_file_opened(
            self):
        plain = os.path.join(self.work, "b.tar")
        self._tar(plain, gz=False)
        dest = tempfile.mkdtemp(dir=self.work)
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(plain, dest, expected_sha256="0" * 64)
        res = o19bundle.open_bundle(
            plain, dest, expected_sha256=o19bundle.sha256_file(plain))
        self.assertEqual(res["bundle_sha256"], o19bundle.sha256_file(plain))

    def test_too_little_headroom_refuses_before_any_copy_is_made(self):
        plain = os.path.join(self.work, "b.tar")
        self._tar(plain, gz=False)
        enc = plain + ".enc"
        self._enc(plain, enc)
        dest = tempfile.mkdtemp(dir=self.work)

        class Exactly:
            """statvfs stand-in: free space is exactly one plain bundle."""
            f_frsize = 1
            f_bavail = os.path.getsize(enc)

        with mock.patch.object(o19bundle.os, "statvfs",
                               return_value=Exactly()):
            # an encrypted bundle needs room for its decrypted twin too
            with self.assertRaises(SystemExit):
                o19bundle.open_bundle(enc, dest, "file:" + self.passfile)
            self.assertEqual(os.listdir(dest), [])

    def test_gz_named_but_not_gz_is_refused(self):
        bogus = os.path.join(self.work, "b.tar.gz")
        with open(bogus, "wb") as fh:
            fh.write(b"not gzip at all")
        with self.assertRaises(ValueError):
            o19bundle.check_magic(bogus, gzipped=True)

    def test_symlink_member_is_refused_before_extraction(self):
        import tarfile
        path = os.path.join(self.work, "evil.tar")
        with tarfile.open(path, "w") as tf:
            tf.add(os.path.join(self.src, "oscar.properties"),
                   arcname="oscar.properties")
            link = tarfile.TarInfo("o19.sql")
            link.type = tarfile.SYMTYPE
            link.linkname = "/etc/hostname"
            tf.addfile(link)
        dest = tempfile.mkdtemp(dir=self.work)
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(path, dest)
        self.assertFalse(os.path.lexists(os.path.join(dest, "o19.sql")))

    def test_option_like_member_never_reaches_tar(self):
        # a member whose NAME is a tar option must be refused before any
        # extraction, and must not execute anything
        import tarfile
        sentinel = os.path.join(self.work, "pwned")
        path = os.path.join(self.work, "evil.tar")
        with tarfile.open(path, "w") as tf:
            tf.add(os.path.join(self.src, "oscar.properties"),
                   arcname="oscar.properties")
            info = tarfile.TarInfo(
                "--checkpoint-action=exec=touch {0} .sql".format(sentinel))
            data = b"SELECT 1;\n"
            info.size = len(data)
            import io
            tf.addfile(info, io.BytesIO(data))
        with self.assertRaises(SystemExit):
            o19bundle.open_bundle(path, tempfile.mkdtemp(dir=self.work))
        self.assertFalse(os.path.exists(sentinel))

    def test_extracted_members_are_plain_files_with_0600(self):
        plain = os.path.join(self.work, "b.tar")
        self._tar(plain, gz=False)
        dest = tempfile.mkdtemp(dir=self.work)
        res = o19bundle.open_bundle(plain, dest)
        for role in ("dump", "properties"):
            st = os.stat(res[role])
            self.assertEqual(st.st_mode & 0o777, 0o600)


if __name__ == "__main__":
    unittest.main()
