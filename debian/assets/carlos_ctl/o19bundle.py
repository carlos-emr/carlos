# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""--bundle handling for the OSCAR 19 importer (experimental).

A bundle is ONE archive holding the three migration inputs at its ROOT,
paths trimmed: the database dump (*.sql / *.sql.gz), optionally the
OscarDocument tar (*.tar / *.tar.gz), and the clinic's *.properties file.
Accepted bundle names: *.tar, *.tar.gz, *.tar.enc, *.tar.gz.enc — .enc means
openssl password-based symmetric encryption (canonical creation command in
docs/o19-import-deb.md; default derivation -pbkdf2 -iter 200000).

Design rules (migration plan §"--bundle"):
 - member identification is by extension and AMBIGUITY IS A HARD ERROR —
   never guess which of two .sql files is the dump;
 - tar members with path separators or '..' are refused (no traversal from
   a clinic-supplied archive);
 - a wrong password/derivation NEVER fails silently: openssl's exit code or
   a failed tar magic check aborts with the two usual causes and their
   --bundle-openssl-opt remedies spelled out.

Pure classification/command-construction functions live up top (unit
tested); the thin execution helpers at the bottom go through util.run.
"""

import hashlib
import os
import subprocess
from typing import Dict, List, Optional, Tuple

from .util import die, log, run

GZIP_MAGIC = b"\x1f\x8b"
# ustar magic at offset 257 covers every tar the tools here produce; a
# v7 tar without it would also fail `tar -t`, which is checked as well.
TAR_MAGIC_OFFSET = 257
TAR_MAGIC = b"ustar"

DEFAULT_CIPHER = "aes-256-cbc"
DEFAULT_DERIVATION = ["-pbkdf2", "-iter", "200000"]

WRONG_KEY_GUIDANCE = (
    "decryption failed. The two usual causes:\n"
    "  1. wrong password;\n"
    "  2. key-derivation mismatch with the openssl that CREATED the bundle\n"
    "     (older openssl defaulted to -md md5 and no -pbkdf2).\n"
    "Remedies: re-check the password, or match the creator's derivation,\n"
    "e.g.  --bundle-openssl-opt -md --bundle-openssl-opt md5\n"
    "(and drop the default -pbkdf2 by passing the creator's exact options)."
)


# --------------------------------------------------------------------------
# pure functions
# --------------------------------------------------------------------------

def bundle_kind(name: str) -> Tuple[bool, bool]:
    """(encrypted, gzipped) from the bundle file name; error otherwise."""
    base = os.path.basename(name)
    if base.endswith(".tar.gz.enc"):
        return True, True
    if base.endswith(".tar.enc"):
        return True, False
    if base.endswith(".tar.gz"):
        return False, True
    if base.endswith(".tar"):
        return False, False
    raise ValueError(
        "unsupported bundle name '{0}' — expected .tar, .tar.gz, .tar.enc "
        "or .tar.gz.enc".format(base))


def classify_members(names: List[str]) -> Dict[str, Optional[str]]:
    """Map tar member names to the three inputs; hard error on anything odd.

    Returns {"dump": name, "documents": name-or-None, "properties": name}.
    """
    problems: List[str] = []
    dumps: List[str] = []
    docs: List[str] = []
    props: List[str] = []
    unknown: List[str] = []
    for raw in names:
        name = raw.rstrip("/")
        if not name or name != raw and raw.endswith("/") and "/" not in name:
            # a lone top-level directory entry — not expected, refuse below
            pass
        if "/" in name or name.startswith(".") or ".." in name.split("/"):
            problems.append("member '{0}' carries a path — bundle members "
                            "must sit at the archive root with paths "
                            "trimmed".format(raw))
            continue
        if name.endswith(".sql") or name.endswith(".sql.gz"):
            dumps.append(name)
        elif name.endswith(".tar") or name.endswith(".tar.gz"):
            docs.append(name)
        elif name.endswith(".properties"):
            props.append(name)
        else:
            unknown.append(name)
    if len(dumps) != 1:
        problems.append("expected exactly one *.sql/*.sql.gz dump, found "
                        "{0}: {1}".format(len(dumps), dumps or "none"))
    if len(docs) > 1:
        problems.append("expected at most one documents *.tar/*.tar.gz, "
                        "found {0}: {1}".format(len(docs), docs))
    if len(props) != 1:
        problems.append("expected exactly one *.properties file, found "
                        "{0}: {1}".format(len(props), props or "none"))
    if unknown:
        problems.append("unrecognized member(s): {0} — a bundle carries "
                        "only the dump, the documents tar and the "
                        "properties file".format(unknown))
    if problems:
        raise ValueError("bundle rejected:\n  " + "\n  ".join(problems))
    return {"dump": dumps[0], "documents": docs[0] if docs else None,
            "properties": props[0]}


def openssl_decrypt_argv(cipher: str, openssl_opts: List[str],
                         pass_spec: str) -> List[str]:
    """openssl argv reading the bundle on stdin, plaintext to stdout."""
    opts = list(openssl_opts) if openssl_opts else list(DEFAULT_DERIVATION)
    return (["openssl", "enc", "-d", "-" + cipher] + opts
            + ["-pass", pass_spec])


def validate_bundle_args(bundle: str, pass_spec: Optional[str]) -> None:
    encrypted, _ = bundle_kind(bundle)
    if encrypted and not pass_spec:
        raise ValueError(
            "bundle '{0}' is encrypted (.enc) — --bundle-pass is required "
            "(openssl -pass syntax: file:PATH, env:VAR, fd:N, stdin)"
            .format(os.path.basename(bundle)))
    if not encrypted and pass_spec:
        raise ValueError(
            "--bundle-pass given but '{0}' is not .enc — refusing the "
            "ambiguity".format(os.path.basename(bundle)))


def check_magic(path: str, gzipped: bool) -> None:
    """Cross-check the file's magic bytes against what its name claims."""
    with open(path, "rb") as fh:
        head = fh.read(TAR_MAGIC_OFFSET + len(TAR_MAGIC))
    if gzipped:
        if not head.startswith(GZIP_MAGIC):
            raise ValueError(
                "'{0}' is named .gz but does not start with the gzip magic "
                "bytes — the file is not what its name claims"
                .format(os.path.basename(path)))
    else:
        if head[TAR_MAGIC_OFFSET:TAR_MAGIC_OFFSET + len(TAR_MAGIC)] \
                != TAR_MAGIC:
            raise ValueError(
                "'{0}' is not a tar archive (magic check failed)"
                .format(os.path.basename(path)))


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


# --------------------------------------------------------------------------
# execution
# --------------------------------------------------------------------------

def _decrypt_to(bundle: str, dest_tar: str, cipher: str,
                openssl_opts: List[str], pass_spec: str) -> None:
    argv = openssl_decrypt_argv(cipher, openssl_opts, pass_spec)
    with open(bundle, "rb") as src, open(dest_tar, "wb") as out:
        cp = subprocess.run(argv, stdin=src, stdout=out)  # nosec B603
    if cp.returncode != 0:
        try:
            os.unlink(dest_tar)
        except OSError:
            pass
        die(WRONG_KEY_GUIDANCE)


def open_bundle(bundle: str, workdir: str, pass_spec: Optional[str] = None,
                cipher: str = DEFAULT_CIPHER,
                openssl_opts: Optional[List[str]] = None) -> Dict[str, str]:
    """Decrypt (if needed), verify, classify and extract a bundle.

    Members land directly in workdir (0700, created if needed). Returns
    {"dump": path, "documents": path-or-None, "properties": path,
     "bundle_sha256": hex, "members": {name: sha256}}.
    """
    validate_bundle_args(bundle, pass_spec)
    encrypted, gzipped = bundle_kind(bundle)
    os.makedirs(workdir, mode=0o700, exist_ok=True)

    tar_path = bundle
    if encrypted:
        tar_path = os.path.join(workdir,
                                ".bundle.tar.gz" if gzipped else ".bundle.tar")
        log("decrypting bundle ...")
        _decrypt_to(bundle, tar_path, cipher, openssl_opts or [], pass_spec)
        # a wrong key that openssl does not catch (no -pbkdf2 header)
        # produces garbage — the magic check turns that into a clear error
        try:
            check_magic(tar_path, gzipped)
        except ValueError:
            die(WRONG_KEY_GUIDANCE)
    else:
        check_magic(tar_path, gzipped)

    tar_flags = "-tzf" if gzipped else "-tf"
    cp = run(["tar", tar_flags, tar_path], capture_output=True)
    if cp.returncode != 0:
        die("cannot list bundle contents: {0}".format(cp.stderr.strip()))
    names = [line for line in cp.stdout.splitlines() if line]
    try:
        members = classify_members(names)
    except ValueError as exc:
        die(str(exc))

    extract_flags = "-xzf" if gzipped else "-xf"
    cp = run(["tar", extract_flags, tar_path, "-C", workdir]
             + [m for m in members.values() if m])
    if cp.returncode != 0:
        die("bundle extraction failed")

    result: Dict[str, object] = {
        "bundle_sha256": sha256_file(bundle),
        "members": {},
    }
    for role in ("dump", "documents", "properties"):
        name = members[role]
        if name is None:
            result[role] = None
            continue
        path = os.path.join(workdir, name)
        os.chmod(path, 0o600)
        result[role] = path
        result["members"][name] = sha256_file(path)
    if encrypted:
        os.unlink(tar_path)
    return result
