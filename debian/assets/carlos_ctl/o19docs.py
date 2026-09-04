# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Documents phase (P5) of the OSCAR 19 importer (experimental).

Restores the clinic's OscarDocument tree into the deb layout
(/var/lib/carlos-emr/OscarDocument/carlos/…), renames the O19 context
directory, relocates HRM reports flat under document/ (refusing when two
HRMDocument rows would reach the same basename by different paths) and
rewrites their absolute paths, then runs the BLOCKING
reconciliation of docs plan §5: every `document.docfilename` and every
eForm `${oscar_image_path}` asset must resolve to a real file; orphan
files are report-only; derived cache directories (`document_cache`, …)
are skipped with a report line and never migrated. Finally the
`o19_archive` schema is exported as CSV into the root-only import state
directory (`.../o19-import/o19-archive-export/`), NOT into the documents
tree -- that tree is owned by the service account, and this export is the
clinic's readable copy of records that became archive-only.
"""

import csv
import errno
import hashlib
import html as html_module
import os
import re
import shutil
import stat
import tempfile
import urllib.parse
from typing import Callable, Dict, List, Optional, Set, Tuple

from . import o19bundle, o19etl
from .util import STATE, die, log, run, sql_escape, warn

DOCUMENTS_ROOT = os.path.join(STATE, "OscarDocument")
TARGET_CTX = "carlos"
SERVICE_USER = "carlos"

# derived caches are regenerated on demand by the application
# (NioFileManagerImpl) — never migrated, never a reconciliation failure.
CACHE_DIR_NAMES = {"document_cache", ".o19-incoming"}

# eForm image references: the literal ${oscar_image_path} placeholder and
# the URL-encoded spellings CARLOS also honours ($%7B...%7D, %24%7B...%7D)
IMAGE_TOKEN_RE = re.compile(
    r"(?:\$\{|\$%7[Bb]|%24%7[Bb])oscar_image_path(?:\}|%7[Dd])")

# an OscarDocument context directory is a plain directory basename; the
# name is interpolated into SQL (HRM path rewrite) and into filesystem
# paths, so anything else is refused outright
CONTEXT_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.\-]*$")


def _sql_str(value: str) -> str:
    """SQL string-literal escaping; see util.sql_escape for the reasoning."""
    return sql_escape(value)


def contained(root: str, relative: str) -> bool:
    """True if root/relative resolves (symlinks followed) to a path inside
    root — the guard that keeps a document row or an eForm image reference
    from pointing reconciliation at an unrelated host file."""
    if "\0" in relative:
        # a decoded batch value can carry NUL; realpath would raise, and
        # no file is ever named so
        return False
    root_real = os.path.realpath(root)
    full = os.path.realpath(os.path.join(root, relative))
    return full == root_real or full.startswith(root_real + os.sep)


# --------------------------------------------------------------------------
# pure helpers
# --------------------------------------------------------------------------

def detect_context_dir(member_names: List[str]) -> str:
    """The single top-level directory of the documents tar; anything else
    is a hard error (two contexts or loose files means the tar was not
    made per the documented command)."""
    tops = set()
    loose = []
    for name in member_names:
        # only a literal "./" prefix is cosmetic; anything else (".." or
        # an absolute name) must surface as a bad context name below
        if name.startswith("./"):
            name = name[2:]
        if not name:
            continue
        head = name.split("/", 1)[0]
        if "/" in name or name.endswith("/"):
            tops.add(head)
        else:
            loose.append(name)
    if loose:
        raise ValueError(
            "documents tar carries loose top-level file(s) {0} — expected "
            "a single context directory (tar -C /var/lib/OscarDocument "
            "-czf … <context-dir>)".format(loose[:5]))
    if len(tops) != 1:
        raise ValueError(
            "documents tar must hold exactly ONE top-level context "
            "directory, found {0}: {1}".format(len(tops), sorted(tops)))
    ctx = tops.pop()
    if not CONTEXT_NAME_RE.match(ctx):
        raise ValueError(
            "context directory name {0!r} is not a plain directory name "
            "(letters, digits, '_', '.', '-' only)".format(ctx))
    return ctx


HRM_INCOMING_DIR = "hrm"


def hrm_basename_twins_sql(dst_schema: str) -> str:
    """Basenames that two HRMDocument rows reach through DIFFERENT paths
    (re-sent reports land in dated directories under hrm/sftp_downloads
    and may repeat a name): the basename rewrite would point both rows
    at one file, so the import refuses before rewriting anything."""
    return ("SELECT SUBSTRING_INDEX(REPLACE(reportFile, '\\\\', '/'), '/', "
            "-1) AS b, COUNT(DISTINCT REPLACE(reportFile, '\\\\', '/')) AS "
            "paths FROM `{0}`.HRMDocument WHERE reportFile IS NOT NULL AND "
            "reportFile <> '' GROUP BY b HAVING paths > 1 ORDER BY b"
            .format(dst_schema))


def hrm_rewrite_sql(dst_schema: str,
                    new_root: str = DOCUMENTS_ROOT) -> Tuple[str, str]:
    """(update_sql, select_sql): point every HRMDocument.reportFile at
    the report's basename inside the CARLOS DOCUMENT_DIR.

    CARLOS's HRMReportParser only trusts an absolute reportFile that
    exists INSIDE DOCUMENT_DIR (anything else is re-resolved relative to
    it), so the O19 absolute path — whatever context or OMD_hrm directory
    it named — must become <documents>/carlos/document/<basename>, and
    run_docs moves the report files there. Idempotent: rewriting an
    already-rewritten path yields the same path."""
    doc_dir = os.path.join(new_root, TARGET_CTX, "document") + "/"
    where = "reportFile IS NOT NULL AND reportFile <> ''"
    # a Windows-era path (backslashes) yields its basename too
    update = ("UPDATE `{0}`.HRMDocument SET reportFile = CONCAT('{1}', "
              "SUBSTRING_INDEX(REPLACE(reportFile, '\\\\', '/'), '/', -1)) "
              "WHERE {2}".format(dst_schema, _sql_str(doc_dir), where))
    select = ("SELECT id, reportFile FROM `{0}`.HRMDocument WHERE {1}"
              .format(dst_schema, where))
    return update, select


def classify_hrm_files(rows: List[Tuple[str, str]],
                       doc_dir: str) -> List[str]:
    """rows = (hrm id, reportFile after the rewrite). Every report must be
    a real, non-empty, non-symlink file inside DOCUMENT_DIR."""
    problems = []
    doc_real = os.path.realpath(doc_dir)
    for hrm_id, report in rows:
        name = report.rsplit("/", 1)[-1]
        path = os.path.join(doc_dir, name)
        if os.path.isabs(report):
            real = os.path.realpath(report)
            inside = (real == os.path.join(doc_real, name)
                      and os.path.dirname(real) == doc_real)
        else:
            inside = contained(doc_dir, report)
        if not name or not inside:
            problems.append("HRMDocument {0}: {1} (path escapes the "
                            "document directory)".format(hrm_id, report))
        elif os.path.islink(path) or not os.path.isfile(path):
            problems.append("HRMDocument {0}: {1}".format(hrm_id, name))
        elif os.path.getsize(path) == 0:
            problems.append("HRMDocument {0}: {1} (zero bytes)".format(
                hrm_id, name))
    return problems


def unescape_batch_field(value: str) -> str:
    """Undo the mariadb batch-mode (-B) escaping of a field value. Applied
    once, by o19import.batch_rows, to every value the client returns;
    the phases receive decoded values and must not decode again."""
    out = []
    i = 0
    n = len(value)
    while i < n:
        c = value[i]
        if c == "\\" and i + 1 < n:
            nxt = value[i + 1]
            mapped = {"n": "\n", "t": "\t", "\\": "\\", "0": "\0"}.get(nxt)
            if mapped is not None:
                out.append(mapped)
                i += 2
                continue
        out.append(c)
        i += 1
    return "".join(out)


def image_ref_lookup(ref: str) -> str:
    """The filename CARLOS's image route looks up for an eForm reference.
    `${oscar_image_path}` expands to `/eform/displayImage?imagefile=`, so
    the browser drops a `#fragment` before the request ever leaves, but a
    `?query` stays INSIDE the imagefile value: `logo.png?v=2` names a file
    literally called that. Reconciliation checks what the route checks
    (image_refs already decoded the value; this keeps the older callers'
    contract for a raw reference)."""
    return ref.split("#", 1)[0]


def image_refs(form_html: str) -> List[str]:
    """The imagefile values the browser would send for every
    ${oscar_image_path} reference in the HTML: the whole attribute value
    after the token (a quoted value may carry spaces — `my scan[1].png`
    is a real form), HTML entities decoded, the `#fragment` dropped,
    anything after `&` (a second query parameter) cut, percent-encoding
    undone — what the servlet reads as `imagefile`."""
    refs = set()
    for m in IMAGE_TOKEN_RE.finditer(form_html):
        start = m.end()
        quote = form_html[m.start() - 1] if m.start() > 0 else ""
        if quote in ("\"", "'"):
            end = form_html.find(quote, start)
        else:
            quote = ""
            end = -1
        if end < 0:
            # unquoted: up to whitespace, a quote, a tag end or the closing
            # parenthesis of a CSS url(...) wrapper
            # ')' ends a CSS url(...) wrapper, but is legitimate in a
            # filename: only treat it as a terminator inside such a
            # wrapper. CSS keywords are case-insensitive and whitespace
            # is allowed on both sides of the parenthesis, so `URL(` and
            # `url ( ` are the same wrapper as `url(`.
            wrapper = re.search(r"(?i)\burl\s*\(\s*$", form_html[:m.start()])
            stop = (r"[^\s\"'()<>]*" if wrapper
                    else r"[^\s\"'<>]*")
            tail = re.match(stop, form_html[start:])
            value = tail.group(0) if tail else ""
        else:
            value = form_html[start:end]
        value = html_module.unescape(value)
        value = value.split("#", 1)[0].split("&", 1)[0]
        value = urllib.parse.unquote(value)
        if value:
            refs.add(value)
    return sorted(refs)


def classify_document_files(rows: List[Tuple[str, str]],
                            doc_dir: str) -> Tuple[List[str], List[str]]:
    """rows = (document_no, docfilename). Returns (missing, empty):
    filenames whose file is absent, and whose file exists but is empty."""
    missing, empty = [], []
    for doc_no, filename in rows:
        if not filename:
            continue
        if not contained(doc_dir, filename):
            # absolute or traversal filename — never let it be satisfied
            # by a file outside the restored tree
            missing.append("document {0}: {1} (path escapes the document "
                           "directory)".format(doc_no, filename))
            continue
        if "/" in filename or "\\" in filename:
            # PathValidationUtils.sanitizeFileName runs the value through
            # FilenameUtils.getName before opening it, so CARLOS looks
            # for the BASENAME in a flat document/ — a file sitting at
            # the nested path exists but is never served. Same rule the
            # eForm image references get, for the same reason.
            missing.append("document {0}: {1} (names a subdirectory — "
                           "CARLOS serves document/ flat and opens the "
                           "basename only)".format(doc_no, filename))
            continue
        if filename.startswith("."):
            # sanitizeFileName rejects a dot-leading basename outright
            missing.append("document {0}: {1} (a leading dot is refused "
                           "by CARLOS's filename validation)".format(
                               doc_no, filename))
            continue
        path = os.path.join(doc_dir, filename)
        if os.path.islink(path):
            # the extracted tree carries no links (tar member types are
            # refused); a link here is foreign and is never a document
            missing.append("document {0}: {1} (symlink refused)".format(
                doc_no, filename))
        elif not os.path.isfile(path):
            missing.append("document {0}: {1}".format(doc_no, filename))
        elif os.path.getsize(path) == 0:
            empty.append("document {0}: {1} (zero bytes)".format(
                doc_no, filename))
    return missing, empty


def find_orphans(doc_dir: str, known: set,
                 cap: int = 50) -> Tuple[int, List[str]]:
    """(total, sample): how many files under doc_dir no row references,
    and the first `cap` of them by name. The count is not capped — it is
    the figure the report states, and a capped one would understate what
    the clinic is carrying."""
    total = 0
    sample: List[str] = []
    if not os.path.isdir(doc_dir):
        return 0, sample
    for name in sorted(os.listdir(doc_dir)):
        if os.path.isfile(os.path.join(doc_dir, name)) \
                and name not in known:
            total += 1
            if len(sample) < cap:
                sample.append(name)
    return total, sample


# --------------------------------------------------------------------------
# filesystem operations
# --------------------------------------------------------------------------

def _same_file(src: str, dst_fd: int, name: str) -> bool:
    """A plain file already at its destination with identical content —
    what an interrupted merge leaves behind.

    The destination is opened THROUGH `dst_fd` with `O_NOFOLLOW`, never by
    path: this decides whether a file is left in place, and reading a
    different file than the one the merge would keep is how a race turns
    "identical, skip it" into a lie."""
    if not os.path.isfile(src) or os.path.islink(src):
        return False
    try:
        fd = os.open(name, os.O_RDONLY | os.O_NOFOLLOW, dir_fd=dst_fd)
    except OSError:
        # ELOOP (a symlink), ENOENT, or a directory: not an identical file
        return False
    try:
        if not stat.S_ISREG(os.fstat(fd).st_mode):
            return False
        if os.path.getsize(src) != os.fstat(fd).st_size:
            return False
        digest = hashlib.sha256()
        with os.fdopen(os.dup(fd), "rb") as fh:
            for chunk in iter(lambda: fh.read(1024 * 1024), b""):
                digest.update(chunk)
    finally:
        os.close(fd)
    return _sha256(src) == digest.hexdigest()


def _fd_dir(dst_fd: int) -> str:
    """A path naming the OPEN directory `dst_fd` refers to.

    `/proc/self/fd/N` resolves to the inode the descriptor holds, not to
    the name it was reached by, so it stays correct even if every
    component of the original path is swapped afterwards. It is the only
    way to hand an open directory to `tempfile`/`shutil`, which take
    paths and have no `dir_fd`.

    Refused outright rather than silently degraded to a path when /proc
    is not mounted: falling back would reintroduce exactly the race this
    exists to close, on the one deployment odd enough to warrant it."""
    path = "/proc/self/fd/{0}".format(dst_fd)
    if not os.path.isdir(path):
        die("cannot resolve an open directory through /proc "
            "({0}); the documents merge needs /proc mounted to move "
            "files without following a symlink".format(path))
    return path


def _move_into_place(src: str, dst_fd: int, name: str) -> None:
    """Move `src` to `name` inside the directory `dst_fd` names.

    `shutil.move` calls `os.path.isdir(dst)` internally, and that
    FOLLOWS a symlink: a directory symlink planted at the destination
    between the caller's check and the move sends a root-owned subtree of
    patient documents wherever it points. The destination tree is
    writable by the service account, so the planter does not need root.

    `os.rename` follows nothing AT THE FINAL COMPONENT -- it replaces a
    symlink rather than walking through it, and refuses outright
    (ENOTDIR) when the source is a directory and the destination is not
    one. But it does resolve every ANCESTOR, so a path-based rename is
    still divertible by swapping a parent directory that was checked a
    moment earlier (demonstrated: a document lands outside the tree).
    Hence the descriptor: a caller that opened each level with
    `O_NOFOLLOW` holds the inode, and renaming relative to it cannot be
    redirected by any later swap of the names above it.

    The cross-device fallback stages through a `mkstemp`/`mkdtemp` name
    inside that same open directory, which cannot be pre-planted because
    the kernel picks it, and lands it with the same descriptor-relative
    rename."""
    try:
        os.rename(src, name, dst_dir_fd=dst_fd)
        return
    except OSError as exc:
        if exc.errno != errno.EXDEV:
            raise
    parent = _fd_dir(dst_fd)
    if os.path.isdir(src) and not os.path.islink(src):
        staging = tempfile.mkdtemp(prefix=".o19-incoming-", dir=parent)
        # copytree wants to create the destination itself
        os.rmdir(staging)
        shutil.copytree(src, staging, symlinks=True)
        os.rename(os.path.basename(staging), name,
                  src_dir_fd=dst_fd, dst_dir_fd=dst_fd)
        shutil.rmtree(src)
    else:
        fd, staging = tempfile.mkstemp(prefix=".o19-incoming-", dir=parent)
        os.close(fd)
        shutil.copy2(src, staging)
        os.rename(os.path.basename(staging), name,
                  src_dir_fd=dst_fd, dst_dir_fd=dst_fd)
        os.unlink(src)


def _merge_entry(src: str, dst: str, resume: bool = False,
                 dst_fd: Optional[int] = None) -> int:
    """Move src into dst's place, merging directory into directory.
    Returns the number of leaf entries moved. Any file-level collision is
    fatal: the target must be a stock deploy (whose skeleton holds
    directories only, nested — eform/images, incomingdocs/1/Fax, ...).
    On a resume of an interrupted merge an identical file already in
    place is dropped from the source instead.

    `dst_fd` is an open descriptor for the directory CONTAINING `dst`,
    and every destination operation goes through it. `dst` itself is
    carried only to name the path in a refusal.

    Descending by descriptor is what makes the walk race-free. Checking
    each level with `lstat` and then acting by path leaves a window: the
    directory that passed the check a moment ago can be moved aside and a
    symlink put in its place, and the rename of a DESCENDANT then
    resolves through it -- the parent's own check never sees it, because
    the swap happens after. A descriptor names the inode, so once each
    level is opened `O_NOFOLLOW` no later rename of the names above it
    can redirect the move."""
    name = os.path.basename(dst)
    if os.path.islink(src):
        die("refusing to merge through a symlink ('{0}')".format(src))
    try:
        st = os.lstat(name, dir_fd=dst_fd)
    except FileNotFoundError:
        st = None
    if st is not None and stat.S_ISLNK(st.st_mode):
        die("refusing to merge through a symlink ('{0}')".format(dst))
    if st is None:
        # a whole subtree moves in one call: report what it actually
        # carried, not "1 entry" for a directory of thousands
        leaves = _count_leaves(src)
        _move_into_place(src, dst_fd, name)
        return leaves
    if os.path.isdir(src) and stat.S_ISDIR(st.st_mode):
        # O_NOFOLLOW on the open, not a check before it: between an
        # `lstat` and a later `open` by name the entry can change
        child_fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY
                           | os.O_NOFOLLOW, dir_fd=dst_fd)
        try:
            moved = 0
            for child in sorted(os.listdir(src)):
                moved += _merge_entry(os.path.join(src, child),
                                      os.path.join(dst, child), resume,
                                      child_fd)
        finally:
            os.close(child_fd)
        os.rmdir(src)
        return moved
    if resume and _same_file(src, dst_fd, name):
        os.unlink(src)
        return 0
    die("refusing to overwrite '{0}' in the documents tree — the target "
        "is not pristine".format(dst))
    return 0  # unreachable


def _count_leaves(path: str) -> int:
    """Files (and empty directories) under path, or 1 for a plain file —
    what a move of this entry actually relocates."""
    if not os.path.isdir(path) or os.path.islink(path):
        return 1
    total = 0
    for _, dirnames, filenames in os.walk(path):
        total += len(filenames)
        if not dirnames and not filenames:
            total += 1
    return total


def _collisions(src: str, dst: str, resume: bool = False,
                dst_fd: Optional[int] = None) -> List[str]:
    """Every path under src that cannot be merged into dst: a symlink on
    either side, or a file that already exists at the same path (on a
    resume, one with different content). Computed BEFORE anything moves
    so a refusal leaves the target untouched.

    Walks by descriptor for the same reason the move does, and `merge_
    move` hands it the SAME descriptor it will then move through: a scan
    that resolved the destination by path could reach a different
    directory than the move does, and its verdict -- "nothing here
    collides" -- would then be about a tree nobody writes to."""
    problems: List[str] = []
    name = os.path.basename(dst)
    if os.path.islink(src):
        problems.append("symlink at '{0}'".format(src))
        return problems
    try:
        st = os.lstat(name, dir_fd=dst_fd)
    except FileNotFoundError:
        return problems
    if stat.S_ISLNK(st.st_mode):
        problems.append("symlink at '{0}'".format(dst))
        return problems
    if os.path.isdir(src) and stat.S_ISDIR(st.st_mode):
        child_fd = os.open(name, os.O_RDONLY | os.O_DIRECTORY
                           | os.O_NOFOLLOW, dir_fd=dst_fd)
        try:
            for child in sorted(os.listdir(src)):
                problems.extend(_collisions(
                    os.path.join(src, child), os.path.join(dst, child),
                    resume, child_fd))
        finally:
            os.close(child_fd)
        return problems
    if resume and _same_file(src, dst_fd, name):
        return problems
    problems.append("'{0}' already exists".format(dst))
    return problems


def merge_move(src_ctx_dir: str, target_dir: str, resume: bool = False,
               private: Optional[Callable[[List[str]], None]] = None,
               mark_started: Optional[Callable[[], None]] = None
               ) -> List[str]:
    """Move the context tree's children into the target context dir,
    merging into the deploy's directory skeleton at any depth.

    A stock deploy's skeleton contains only directories (possibly nested),
    so merging never collides; an existing FILE at any path the tar also
    carries is a hard refusal (the target is not pristine) — except on a
    resume of an interrupted merge, where an identical file is already
    where it belongs. Cache directories are skipped with a note. Colliding
    paths go to the private callback (they are document names), the count
    to the error.

    `mark_started` is called once, after the collision scan has passed
    and before the first move — the caller records "a restore is under
    way" there. Recording it any earlier would make a refusal by the
    pre-scan (which moves nothing) look to the next run like an
    interrupted restore, whose message would then tell the operator to
    delete pre-existing files this import never placed.

    Returns report lines."""
    lines = []
    os.makedirs(target_dir, exist_ok=True)
    # opened ONCE and used for the scan AND every move below. Resolving
    # target_dir a second time would reopen the window a swapped
    # ancestor exploits, and would let the scan and the move disagree
    # about which directory they are talking about.
    root_fd = os.open(target_dir, os.O_RDONLY | os.O_DIRECTORY
                      | os.O_NOFOLLOW)
    try:
        return _merge_through(src_ctx_dir, target_dir, root_fd, resume,
                              private, mark_started, lines)
    finally:
        os.close(root_fd)


def _merge_through(src_ctx_dir: str, target_dir: str, root_fd: int,
                   resume: bool,
                   private: Optional[Callable[[List[str]], None]],
                   mark_started: Optional[Callable[[], None]],
                   lines: List[str]) -> List[str]:
    """`merge_move`'s body, with the destination descriptor already open
    so it is closed on every path out, refusals included."""
    children = [c for c in sorted(os.listdir(src_ctx_dir))
                if c not in CACHE_DIR_NAMES]
    problems: List[str] = []
    for child in children:
        problems.extend(_collisions(os.path.join(src_ctx_dir, child),
                                    os.path.join(target_dir, child), resume,
                                    root_fd))
    if problems:
        if private:
            private(["documents tree paths the merge refused:"] + problems)
        if resume:
            die("{0} path(s) under {1} hold content that differs from "
                "the tar (itemised in documents-details.txt). If the "
                "interrupted restore placed them, remove them; if they "
                "predate this import the target was never pristine, and "
                "the pre-import snapshot is the way back. Then --resume"
                .format(len(problems), target_dir))
        die("refusing to merge the documents tree — the target is not "
            "pristine ({0} collision(s); nothing was moved and nothing "
            "was recorded, itemised in documents-details.txt). Clear "
            "those paths from {1}, or restore the pre-import snapshot, "
            "then --resume".format(len(problems), target_dir))
    if mark_started:
        mark_started()
    loose: List[str] = []
    lines.extend(_merge_children(src_ctx_dir, target_dir, root_fd,
                                 resume, loose))
    if loose:
        if private:
            private(["loose files moved from the context directory root:"]
                    + loose)
        lines.append("moved {0} loose file(s) from the context directory "
                     "root (named in documents-details.txt)".format(
                         len(loose)))
    return lines


def _merge_children(src_ctx_dir: str, target_dir: str, root_fd: int,
                    resume: bool, loose: List[str]) -> List[str]:
    """One report line per child of the context root, moving each through
    `root_fd`. Split out so `merge_move` can close that descriptor on
    every path out, including a refusal."""
    lines: List[str] = []
    for child in sorted(os.listdir(src_ctx_dir)):
        src = os.path.join(src_ctx_dir, child)
        dst = os.path.join(target_dir, child)
        if child in CACHE_DIR_NAMES:
            lines.append("skipped derived cache directory '{0}' (the "
                         "application regenerates it)".format(child))
            continue
        try:
            os.lstat(child, dir_fd=root_fd)
            existed = True
        except FileNotFoundError:
            existed = False
        is_dir = os.path.isdir(src)
        moved = _merge_entry(src, dst, resume, root_fd)
        if is_dir:
            lines.append("{0} {1}/ ({2} entr{3})".format(
                "merged into existing" if existed else "moved",
                child, moved, "y" if moved == 1 else "ies"))
        else:
            # a loose file at the context root is a document, and a
            # document's name carries a patient's: the report gets a
            # count, the name goes to the root-only details file
            loose.append(child)
    return lines


def _sha256(path: str) -> str:
    """The file's SHA-256, read in 1 MiB chunks (document trees hold files
    too large to slurp)."""
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def relocate_hrm_reports(ctx_root: str,
                         private: Optional[Callable[[List[str]], None]] = None,
                         reserved: Optional[Set[str]] = None
                         ) -> List[str]:
    """Move O19's HRM report files into document/ (DOCUMENT_DIR), where
    CARLOS's HRM reader looks for them. O19 keeps them nested (OMD_hrm
    is <ctx>/hrm/, the downloads under hrm/sftp_downloads/<date>/
    decrypted/), so the whole hrm/ tree is walked, files only, links
    skipped. Two copies of one basename are fine when their content is
    identical (one is kept); differing content under one name, or a
    name already present in document/, is fatal — the basename rewrite
    of HRMDocument.reportFile could not tell them apart. Names go to the
    private callback, the count to the error.

    `reserved` is every docfilename the target's `document` rows claim.
    A name in it is refused even when no file sits at that path: the row
    whose file the tar did not carry would otherwise be satisfied by
    this HRM report, and reconciliation — which only asks whether a file
    of that name exists — would pass while one patient's chart served
    another patient's hospital report.

    Nothing moves until the whole scan is clean: the refusal tells the
    operator to resolve the names in the source tree, and that tree must
    still hold them when they go looking."""
    src_dir = os.path.join(ctx_root, HRM_INCOMING_DIR)
    if not os.path.isdir(src_dir):
        return []
    doc_dir = os.path.join(ctx_root, "document")
    os.makedirs(doc_dir, exist_ok=True)
    by_name: Dict[str, List[str]] = {}
    for dirpath, dirnames, filenames in os.walk(src_dir):
        dirnames[:] = sorted(d for d in dirnames
                             if not os.path.islink(os.path.join(dirpath, d)))
        for name in sorted(filenames):
            path = os.path.join(dirpath, name)
            if os.path.islink(path) or not os.path.isfile(path):
                continue
            by_name.setdefault(name, []).append(path)
    claimed = reserved or set()
    problems = []
    plan = []            # (name, keep_path, drop_paths, already_placed)
    for name in sorted(by_name):
        paths = by_name[name]
        dst = os.path.join(doc_dir, name)
        digests = {_sha256(p) for p in paths}
        if len(digests) > 1:
            problems.append("{0}: {1} differing copies under hrm/".format(
                name, len(paths)))
            continue
        if name in claimed:
            # a document row already answers to this name; see the
            # docstring — an absent file is the dangerous case, not the
            # present one
            problems.append("{0}: a document row already claims this "
                            "name".format(name))
            continue
        if os.path.lexists(dst):
            if os.path.isfile(dst) and not os.path.islink(dst) \
                    and _sha256(dst) in digests:
                # an interrupted relocation already placed it
                plan.append((name, None, paths, True))
                continue
            problems.append("{0}: collides with an existing document/ "
                            "file".format(name))
            continue
        plan.append((name, paths[0], paths[1:], False))
    if problems:
        if private:
            private(["HRM report files the import cannot relocate: "]
                    + problems)
        die("{0} HRM report name(s) cannot be relocated into document/ "
            "(differing copies of one name, a name a document row "
            "claims, or a name document/ already holds — itemised in "
            "documents-details.txt); resolve them in the source tree "
            "(nothing under hrm/ has been moved) and re-run with "
            "--resume".format(len(problems)))
    moved = deduped = 0
    for name, keep, drop, already in plan:
        if not already:
            shutil.move(keep, os.path.join(doc_dir, name))
            moved += 1
        for p in drop:
            os.unlink(p)
            deduped += 1
    # leave no empty dated directories behind (files only were moved)
    for dirpath, dirnames, filenames in os.walk(src_dir, topdown=False):
        if dirpath != src_dir and not dirnames and not filenames:
            try:
                os.rmdir(dirpath)
            except OSError:
                pass
    if not moved and not deduped:
        return []  # nothing left under hrm/ (a resume after the move)
    return ["moved {0} HRM report file(s) from {1}/ into document/ (the "
            "location CARLOS reads HRM reports from; {2} identical "
            "duplicate(s) dropped)".format(moved, HRM_INCOMING_DIR, deduped)]


def apply_ownership(root: str, dev_target: bool) -> None:
    """Hand the restored document tree to the service account.

    Refuses outright if the tree holds a symbolic link: neither the tar
    nor the merge path ever creates one, so a link here is foreign, and
    a root-run recursive chown that followed it would hand the
    unprivileged account ownership of whatever it points at. Skipped on
    a dev target or when not running as root."""
    if dev_target or os.geteuid() != 0:
        warn("skipping chown of {0} (dev target)".format(root))
        return
    # chown follows symlinks unless told not to; -h is what stops it.
    # This tree is owned by the unprivileged service account, which can
    # plant a link, and a root-run `chown -R carlos:carlos` would then
    # hand that account ownership of whatever the link points at. The tar
    # and merge paths refuse links, so one here is foreign — refused
    # rather than repaired, with -h as the belt to that braces.
    # -print0: a link whose name holds a space is one link, not two, and
    # the count in the refusal is what the operator goes looking for.
    stray = run(["find", root, "-type", "l", "-print0"],
                capture_output=True)
    if stray.returncode != 0:
        # an unreadable tree is not an absent one: chowning it blind is
        # exactly the case this check exists to prevent
        die("could not scan the documents tree for symbolic links (find "
            "exited {0}); {1} is not in a state this import can "
            "repair.".format(stray.returncode, root))
    links = [n for n in stray.stdout.split("\0") if n]
    if links:
        die("the documents tree holds {0} symbolic link(s); the import "
            "never creates one, so these are foreign. Remove them from "
            "{1} and re-run with --resume.".format(len(links), root))
    cp = run(["chown", "-Rh",
              "{0}:{0}".format(SERVICE_USER), root])
    if cp.returncode != 0:
        die("chown of the documents tree failed")
    # directories setgid 2750, files 0640 (matches the tmpfiles skeleton);
    # a failed repair would leave files the service cannot read, which the
    # root-run reconciliation below would not notice — so it is fatal
    for kind, mode in (("d", "2750"), ("f", "0640")):
        cp = run(["find", root, "-type", kind, "-exec", "chmod", mode,
                  "{}", "+"])
        if cp.returncode != 0:
            die("permission repair of the documents tree failed (find "
                "-type {0} -exec chmod {1})".format(kind, mode))


# --------------------------------------------------------------------------
# reconciliation + CSV export drivers
# --------------------------------------------------------------------------

def reconcile(query, dst_schema: str, ctx_root: str
              ) -> Tuple[List[str], List[str], List[str]]:
    """(blocking_problems, report_lines, private_lines) for the restored
    tree. File and form names can carry a patient's or a clinician's
    name: they appear in the problems (written to the root-only details
    file) and the private lines, never in the report lines."""
    problems: List[str] = []
    lines: List[str] = []
    private: List[str] = []
    unroutable: List[str] = []
    doc_dir = os.path.join(ctx_root, "document")

    rows = [(r[0], r[1]) for r in query(
        "SELECT document_no, docfilename FROM `{0}`.document"
        .format(dst_schema)) if len(r) >= 2]
    missing, empty = classify_document_files(rows, doc_dir)
    problems.extend("missing file for " + m for m in missing)
    problems.extend("empty file for " + e for e in empty)

    _, hrm_select = hrm_rewrite_sql(dst_schema, os.path.dirname(ctx_root))
    hrm_rows = [(r[0], r[1]) for r in query(hrm_select)
                if len(r) >= 2]
    # HRM reports were relocated INTO document/ and are referenced by
    # HRMDocument, not by a document row: counting them as orphans would
    # make every HRM clinic's orphan figure meaningless
    known = {f for _, f in rows}
    known |= {os.path.basename(p.replace("\\", "/"))
              for _, p in hrm_rows if p}
    orphan_total, orphan_sample = find_orphans(doc_dir, known)
    if orphan_total:
        lines.append("{0} orphan file(s) on disk with no document or HRM "
                     "row (report-only; the first {1} are named in "
                     "documents-details.txt)".format(
                         orphan_total, len(orphan_sample)))
        private.append("orphan files with no document or HRM row: "
                       + ", ".join(orphan_sample))
    lines.append("{0} document row(s) reconciled against {1}".format(
        len(rows), doc_dir))

    image_dir = os.path.join(ctx_root, "eform", "images")
    eform_rows = query(
        "SELECT fid, form_name, form_html FROM `{0}`.eform WHERE "
        "form_html LIKE '%oscar_image_path%'".format(dst_schema))
    checked = 0
    for r in eform_rows:
        if len(r) < 3:
            continue
        fid, form_name, html = r[0], r[1], r[2]
        for ref in image_refs(html):
            checked += 1
            # image_refs already stripped the fragment and query and
            # percent-decoded: decoding twice would split a name that
            # legitimately contains '#' or '&' as %23 / %26
            asset = ref
            if not asset:
                continue  # a bare `#fragment`: no request is made
            if not contained(image_dir, asset):
                # a traversal-shaped reference stays BLOCKING: unlike a
                # subdirectory or a query suffix it is not a form
                # addressing a present asset wrongly, and the operator
                # should not be able to complete a migration carrying one
                problems.append(
                    "eForm '{0}' (fid {1}) image reference escapes "
                    "eform/images: {2}".format(form_name, fid, ref))
            elif "/" in asset or "\\" in asset:
                # the route validates imagefile as ONE path component:
                # a subdirectory reference is a broken image at runtime
                unroutable.append(
                    "eForm '{0}' (fid {1}) image reference {2} names a "
                    "subdirectory — CARLOS serves eform/images as a flat "
                    "directory".format(form_name, fid, ref))
            elif not os.path.isfile(os.path.join(image_dir, asset)):
                bare = asset.split("?", 1)[0] if "?" in asset else ""
                if bare and os.path.isfile(os.path.join(image_dir, bare)):
                    unroutable.append(
                        "eForm '{0}' (fid {1}) image reference {2} carries "
                        "a query suffix that CARLOS does not strip (the "
                        "asset {3} is present)".format(
                            form_name, fid, ref, bare))
                else:
                    problems.append(
                        "eForm '{0}' (fid {1}) references missing image "
                        "asset: {2}".format(form_name, fid, ref))
    lines.append("{0} eForm image reference(s) checked".format(checked))
    if unroutable:
        # These two shapes are the form HTML addressing a PRESENT image
        # wrongly, not a missing file: the only fix is an edit to
        # eform.form_html in the target. Blocking a cutover on them would
        # leave the operator with a refusal they cannot clear (there is
        # no --accept for it, and no tar can change a form's HTML), for a
        # broken image on a template — never an unreadable clinical
        # record. Reported, not blocking. A reference that ESCAPES
        # eform/images is a different shape and stays blocking above.
        lines.append("{0} eForm image reference(s) name an asset CARLOS "
                     "cannot route to (report-only; the assets are "
                     "present — each needs eform.form_html edited in the "
                     "target, itemised in documents-details.txt)".format(
                         len(unroutable)))
        private.append("eForm image references CARLOS cannot route to "
                       "(edit eform.form_html for each):")
        private.extend(unroutable)

    problems.extend("missing HRM report for " + p
                    for p in classify_hrm_files(hrm_rows, doc_dir))
    lines.append("{0} HRM report row(s) reconciled against {1}".format(
        len(hrm_rows), doc_dir))
    return problems, lines, private


def export_archive_csv(query, archive_schema: str, out_dir: str,
                       stream=None) -> List[str]:
    """Write every o19_archive table as CSV so the clinic holds a readable
    copy of everything that became archive-only. SQL NULL is told from a
    stored value by a companion `IS NULL` flag per column (the batch client
    prints both a NULL and the four-letter string NULL identically), so a
    NULL becomes an empty field and every stored value survives verbatim.

    `stream` reads one statement's rows unbuffered (o19import.
    make_row_stream); `query` is used only for the information_schema
    lookups, whose results are small and bounded. This used to page the
    rows with LIMIT/OFFSET to keep them out of memory, which re-sorted
    the whole table per window -- measured at 4x on 500k rows and growing
    with the table. Streaming holds one row at a time AND reads the table
    once."""
    os.makedirs(out_dir, mode=0o750, exist_ok=True)
    # a caller with no streaming reader (the unit tests, which drive a
    # fake `query`) still gets the buffered read: correctness is the
    # same, only the memory and the scan count differ
    rows_of = stream if stream is not None else query
    tables = [r[0] for r in query(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
        "TABLE_SCHEMA = '{0}' ORDER BY TABLE_NAME".format(archive_schema))]
    lines = []
    for table in tables:
        # archive table names derive from the staged dump's own table
        # names: the ETL refuses names outside the identifier class before
        # it creates any of these, so a stray one here is a broken archive
        # schema, never something to improvise a file name for
        if not o19etl.IDENTIFIER_RE.match(table):
            die("archive table name {0!r} is outside the identifier class "
                "the import accepts — the archive schema was not written "
                "by this import".format(table))
        cols = [r[0] for r in query(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE "
            "TABLE_SCHEMA = '{0}' AND TABLE_NAME = '{1}' ORDER BY "
            "ORDINAL_POSITION".format(archive_schema, table))]
        if any(not o19etl.IDENTIFIER_RE.match(c) for c in cols):
            die("archive table {0} carries a column name outside the "
                "identifier class the import accepts".format(table))
        select = ", ".join("{0}, ({0} IS NULL)".format(o19etl.ident(c))
                           for c in cols)
        # ordered so a re-run of P5 rewrites the same file, not the same
        # rows in a different order (the clinic diffs these between
        # passes). It no longer has a second job: the read used to be
        # paged, and a total order was what gave every LIMIT/OFFSET
        # window a well-defined slice.
        base = "SELECT {0} FROM {1}.{2} ORDER BY {3}".format(
            select, o19etl.ident(archive_schema), o19etl.ident(table),
            ", ".join(str(i) for i in range(1, len(cols) * 2 + 1, 2)))
        path = os.path.join(out_dir, table + ".csv")
        # created with the final mode: the rows are archived clinical data
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o640)
        os.fchmod(fd, 0o640)  # the mode argument applies to a NEW file only
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as fh:
            # QUOTE_NOTNULL (3.12+) writes SQL NULL as a bare empty field
            # and quotes every string, so a stored '' is distinguishable
            # from NULL. On an older interpreter both come out empty —
            # the archive is still complete, only that one distinction is
            # lost, which is why this degrades rather than refusing.
            quoting = getattr(csv, "QUOTE_NOTNULL", None)
            writer = (csv.writer(fh, quoting=quoting) if quoting is not None
                      else csv.writer(fh))
            writer.writerow(cols)
            width = len(cols) * 2
            written = 0
            for r in rows_of(base):
                if len(r) != width:
                    # a padded or truncated row would write a plausible
                    # but wrong archive of clinical data with no signal:
                    # for an archive-only table this file is the only
                    # copy the clinic keeps
                    die("archive table {0}: a row came back with {1} "
                        "field(s) where {2} were selected — the export "
                        "cannot be trusted; re-run P5 with --resume once "
                        "the archive schema is readable".format(
                            table, len(r), width))
                out = [None if r[i + 1] == "1" else r[i]
                       for i in range(0, width, 2)]
                writer.writerow(out)
                written += 1
        lines.append("{0}.csv: {1} row(s)".format(table, written))
    return lines


# --------------------------------------------------------------------------
# P5 driver
# --------------------------------------------------------------------------

def run_docs(ctx) -> None:
    """Execute P5. Expects ctx keys: state_dir, state, query, documents
    (tar path or None), accepted, dev_target, target_db, and the
    report/mark_done helpers from o19import."""
    from . import o19import

    state = ctx["state"]
    state_dir = ctx["state_dir"]
    query = ctx["query"]
    documents_root = ctx.get("documents_root", DOCUMENTS_ROOT)
    ctx_root = os.path.join(documents_root, TARGET_CTX)

    prev = state.get("phases", {}).get("documents", {})
    details_path = os.path.join(state_dir, "documents-details.txt")

    def private(lines):
        o19import.append_private(details_path, "\n".join(lines) + "\n")

    if ctx["documents"] is None:
        if prev.get("restored") or prev.get("restoring"):
            die("the documents tree under {0} was already restored from "
                "the tar with sha256 {1}... — --skip-documents cannot "
                "retire it; resume with that tar (reconciliation is what "
                "is left), or restore the pre-import snapshot".format(
                    ctx_root, str(prev.get("tar_sha256"))[:12]))
        if "no-documents" not in ctx["accepted"]:
            die("no documents tar and no --accept no-documents sign-off")
        warn("importing WITHOUT documents (acknowledged) — document rows "
             "will reference files that are not there")
        o19import.report_append(state_dir, "P5 documents",
                                "SKIPPED (no-documents acknowledged)")
        o19import.mark_done(state_dir, state, "documents",
                            skipped="no-documents")
        return

    # one details file per pass: every step below re-itemises what it
    # finds, and this must truncate before the FIRST private() write —
    # the twins refusal below is one, and repeated attempts would
    # otherwise stack undelimited blocks in the file they cite
    o19import.write_private(details_path, "P5 documents:\n")

    # Reads only the target database, so it runs BEFORE the tar is
    # touched: two rows reaching one basename through different paths
    # would be folded onto one file by the rewrite, and the remedy this
    # names (re-export) is only reachable while the tree is untouched.
    twins = query(hrm_basename_twins_sql(ctx["target_db"]))
    if twins:
        private(["HRM report basenames reached through more than one "
                 "path (the basename rewrite cannot tell them apart):"]
                + ["{0}: {1} path(s)".format(r[0], r[1]) for r in twins
                   if len(r) >= 2])
        die("{0} HRM report name(s) are referenced through different "
            "paths by HRMDocument rows (itemised in documents-details.txt) "
            "— CARLOS keeps reports flat under document/, so rename the "
            "duplicates in the source and re-export (nothing has been "
            "restored yet)".format(len(twins)))

    tar_path = ctx["documents"]
    tar_sha = o19import.sha256_file(tar_path)
    if prev.get("skipped") == "no-documents" and prev.get("status") == "done":
        die("this import recorded --skip-documents (no-documents "
            "acknowledged) and the phase is complete; a documents tar "
            "cannot be added afterwards — restore the pre-import snapshot "
            "and start over with the tar")
    already_restored = (prev.get("tar_sha256") == tar_sha
                        and prev.get("restored"))
    # an interrupted merge (crash between the first move and the restored
    # mark): the tree holds part of the tar; the merge below is
    # idempotent for identical files, so the same tar is re-extracted
    # and completed
    resuming_merge = (prev.get("tar_sha256") == tar_sha
                      and prev.get("restoring") and not prev.get("restored"))
    if (prev.get("restored") or prev.get("restoring")) \
            and not (already_restored or resuming_merge):
        # a different tar after a restore: re-extracting over the
        # restored tree could only collide, so say exactly what to do
        die("the documents tree under {0} was already restored from a "
            "tar with sha256 {1}...; this tar differs ({2}...). To restore "
            "a different tar, restore the pre-import snapshot (or move "
            "{0} aside and recreate its skeleton) and re-run with --resume."
            .format(ctx_root, str(prev.get("tar_sha256"))[:12],
                    tar_sha[:12]))

    if not already_restored:
        gz = tar_path.endswith(".gz")
        try:
            # from the archive's own headers, not a formatted listing:
            # tar quotes non-ASCII member names, and a clinic tree full
            # of accented document names would otherwise be unreadable
            entries = o19bundle.read_tar_entries(tar_path, gz)
        except o19bundle.ARCHIVE_ERRORS as exc:
            die("cannot read documents tar: {0}".format(str(exc)[:300]))
        try:
            # plain files + directories only, relative traversal-free
            # names: the tree is extracted as root
            names = o19bundle.validate_tar_members(
                [(kind, name) for kind, name, _ in entries],
                allow_dirs=True)
            old_ctx = detect_context_dir(names)
        except ValueError as exc:
            die(str(exc))

        incoming = os.path.join(documents_root, ".o19-incoming")
        if os.path.isdir(incoming):
            shutil.rmtree(incoming)
        os.makedirs(incoming, mode=0o700)
        log("extracting documents tar (context '{0}') ...".format(old_ctx))
        # ownership/permissions are applied by apply_ownership from the
        # host policy, never restored from the clinic's archive
        cp = run(["tar", "-xzf" if gz else "-xf", tar_path,
                  "-C", incoming, "--no-same-owner",
                  "--no-same-permissions"])
        if cp.returncode != 0:
            die("documents tar extraction failed")

        def _mark_restoring():
            # recorded once the merge is past its collision scan and
            # about to move: a crash from here on is a resumable state
            # (same tar, idempotent merge), while a refusal by the scan
            # leaves no mark at all — it moved nothing
            state.setdefault("phases", {})["documents"] = {
                "status": "in-progress", "tar_sha256": tar_sha,
                "restoring": True, "old_ctx": old_ctx}
            o19import.save_state(state_dir, state)

        if resuming_merge:
            log("documents: completing the interrupted restore of the same "
                "tar (files already in place are verified, not replaced)")
        try:
            move_lines = merge_move(os.path.join(incoming, old_ctx),
                                    ctx_root,
                                    resume=bool(resuming_merge),
                                    private=private,
                                    mark_started=_mark_restoring)
        finally:
            # a refusal must not leave a second full copy of the clinic's
            # documents in the state volume
            shutil.rmtree(incoming, ignore_errors=True)
        # the tree is in place: record it NOW so a failure in any of the
        # (idempotent) steps below resumes without re-extracting
        state["phases"]["documents"] = {
            "status": "in-progress", "tar_sha256": tar_sha,
            "restored": True, "old_ctx": old_ctx}
        o19import.save_state(state_dir, state)
        o19import.report_append(state_dir, "P5 documents restore",
                                "\n".join(move_lines))
    else:
        log("documents: tree already restored (sha256 match) — "
            "re-running ownership repair and reconciliation")

    # every pass, not only the first (a no-op once hrm/ is gone): a
    # relocation that failed after the restore was recorded must be retried
    # on --resume rather than skipped
    claimed = {r[0] for r in query(
        "SELECT DISTINCT docfilename FROM `{0}`.document"
        .format(ctx["target_db"])) if r and r[0]}
    hrm_lines = relocate_hrm_reports(ctx_root, private=private,
                                     reserved=claimed)
    if hrm_lines:
        o19import.report_append(state_dir, "P5 HRM relocation",
                                "\n".join(hrm_lines))
    # every pass, not only the first: an operator who fixed the tree by
    # hand (the documented remedy) leaves root-owned files behind, and a
    # root-run reconciliation would never notice
    apply_ownership(ctx_root, ctx["dev_target"])
    update_sql, _ = hrm_rewrite_sql(ctx["target_db"], documents_root)
    query(update_sql)  # idempotent: basename into DOCUMENT_DIR

    problems, lines, private_lines = reconcile(query, ctx["target_db"],
                                               ctx_root)
    if private_lines:
        private(["P5 reconciliation notes:"] + private_lines)
    csv_lines = export_archive_csv(
        query, ctx.get("archive_schema", "o19_archive"),
        os.path.join(state_dir, "o19-archive-export"),
        stream=ctx.get("row_stream"))
    o19import.report_append(
        state_dir, "P5 reconciliation",
        "\n".join(lines + ["archive CSV export:"]
                  + ["  " + line for line in csv_lines]))
    if problems:
        # document names can carry patient names: itemised in the private
        # file, counted in the shareable report and on the console
        private(["P5 reconciliation failures:"] + problems)
        o19import.report_append(
            state_dir, "P5 reconciliation FAILURES",
            "{0} problem(s) — itemised in documents-details.txt "
            "(root-only)".format(len(problems)))
        die("documents reconciliation FAILED ({0} problem(s), itemised in "
            "{1}) — the clinical record must not go live with unreadable "
            "documents. For a missing file, fix the tree in place (add it "
            "under {2}) and re-run with --resume; to restore a different "
            "tar instead, restore the pre-import snapshot first. An eForm "
            "image reference that ESCAPES eform/images is not a missing "
            "file and no tar can clear it: correct that form's form_html "
            "in the target, then --resume. (Disabling the form does not "
            "clear it — the check reads every eform row that mentions "
            "oscar_image_path, whatever its status.)"
            .format(len(problems), details_path, ctx_root))
    o19import.mark_done(state_dir, state, "documents", tar_sha256=tar_sha,
                        restored=True)
    log("documents restored and reconciled clean")
