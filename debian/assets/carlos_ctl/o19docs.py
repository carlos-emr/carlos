# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Documents phase (P5) of the OSCAR 19 importer (experimental).

Restores the clinic's OscarDocument tree into the deb layout
(/var/lib/carlos-emr/OscarDocument/carlos/…), renames the O19 context
directory, rewrites HRMDocument absolute paths, and runs the BLOCKING
reconciliation of docs plan §5: every `document.docfilename` and every
eForm `${oscar_image_path}` asset must resolve to a real file; orphan
files are report-only; derived cache directories (`document_cache`, …)
are skipped with a report line and never migrated. Finally the
`o19_archive` schema is exported as CSV inside the documents tree so the
clinic holds a readable copy that rides the normal backup.
"""

import csv
import os
import re
import shutil
from typing import Dict, List, Optional, Tuple

from . import o19bundle, o19map_schema
from .util import STATE, die, log, run, warn

DOCUMENTS_ROOT = os.path.join(STATE, "OscarDocument")
TARGET_CTX = "carlos"
SERVICE_USER = "carlos"

# derived caches are regenerated on demand by the application
# (NioFileManagerImpl) — never migrated, never a reconciliation failure.
CACHE_DIR_NAMES = {"document_cache", ".o19-incoming"}

# eForm image references: the literal ${oscar_image_path} placeholder and
# the URL-encoded spellings CARLOS also honours ($%7B...%7D, %24%7B...%7D)
IMAGE_REF_RE = re.compile(
    r"(?:\$\{|\$%7[Bb]|%24%7[Bb])oscar_image_path(?:\}|%7[Dd])"
    r"([^\"'\s)<>]+)")

# an OscarDocument context directory is a plain directory basename; the
# name is interpolated into SQL (HRM path rewrite) and into filesystem
# paths, so anything else is refused outright
CONTEXT_NAME_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9_.\-]*$")


def _sql_str(value: str) -> str:
    """SQL string-literal escaping for values interpolated into generated
    statements (mirrors dbops.sql_escape; kept local so the pure helpers
    stay importable without the deployment modules)."""
    return value.replace("\\", "\\\\").replace("'", "\\'")


def contained(root: str, relative: str) -> bool:
    """True if root/relative resolves (symlinks followed) to a path inside
    root — the guard that keeps a document row or an eForm image reference
    from pointing reconciliation at an unrelated host file."""
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
    update = ("UPDATE `{0}`.HRMDocument SET reportFile = CONCAT('{1}', "
              "SUBSTRING_INDEX(reportFile, '/', -1)) WHERE {2}"
              .format(dst_schema, _sql_str(doc_dir), where))
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
    """Undo the mariadb batch-mode (-B) escaping of a field value."""
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


def image_ref_path(ref: str) -> str:
    """The on-disk asset name of an eForm image reference: a query string
    or fragment (`logo.png?v=2`, a cache-buster) is addressed to the
    servlet, never to the filesystem."""
    return re.split(r"[?#]", ref, 1)[0]


def image_refs(form_html: str) -> List[str]:
    return sorted(set(IMAGE_REF_RE.findall(form_html)))


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


def find_orphans(doc_dir: str, known: set, cap: int = 50) -> List[str]:
    orphans = []
    if not os.path.isdir(doc_dir):
        return orphans
    for name in sorted(os.listdir(doc_dir)):
        if os.path.isfile(os.path.join(doc_dir, name)) \
                and name not in known:
            orphans.append(name)
            if len(orphans) >= cap:
                break
    return orphans


# --------------------------------------------------------------------------
# filesystem operations
# --------------------------------------------------------------------------

def _merge_entry(src: str, dst: str) -> int:
    """Move src into dst's place, merging directory into directory.
    Returns the number of leaf entries moved. Any file-level collision is
    fatal: the target must be a stock deploy (whose skeleton holds
    directories only, nested — eform/images, incomingdocs/1/Fax, ...)."""
    if os.path.islink(src) or os.path.islink(dst):
        die("refusing to merge through a symlink ('{0}')".format(
            dst if os.path.islink(dst) else src))
    if not os.path.lexists(dst):
        shutil.move(src, dst)
        return 1
    if os.path.isdir(src) and os.path.isdir(dst):
        moved = 0
        for child in sorted(os.listdir(src)):
            moved += _merge_entry(os.path.join(src, child),
                                  os.path.join(dst, child))
        os.rmdir(src)
        return moved
    die("refusing to overwrite '{0}' in the documents tree — the target "
        "is not pristine".format(dst))
    return 0  # unreachable


def _collisions(src: str, dst: str) -> List[str]:
    """Every path under src that cannot be merged into dst: a symlink on
    either side, or a file that already exists at the same path. Computed
    BEFORE anything moves so a refusal leaves the target untouched."""
    problems: List[str] = []
    if os.path.islink(src) or os.path.islink(dst):
        problems.append("symlink at '{0}'".format(
            dst if os.path.islink(dst) else src))
        return problems
    if not os.path.lexists(dst):
        return problems
    if os.path.isdir(src) and os.path.isdir(dst):
        for child in sorted(os.listdir(src)):
            problems.extend(_collisions(os.path.join(src, child),
                                        os.path.join(dst, child)))
        return problems
    problems.append("'{0}' already exists".format(dst))
    return problems


def merge_move(src_ctx_dir: str, target_dir: str) -> List[str]:
    """Move the context tree's children into the target context dir,
    merging into the deploy's directory skeleton at any depth.

    A stock deploy's skeleton contains only directories (possibly nested),
    so merging never collides; an existing FILE at any path the tar also
    carries is a hard refusal (the target is not pristine). Cache
    directories are skipped with a note. Returns report lines."""
    lines = []
    os.makedirs(target_dir, exist_ok=True)
    children = [c for c in sorted(os.listdir(src_ctx_dir))
                if c not in CACHE_DIR_NAMES]
    problems: List[str] = []
    for child in children:
        problems.extend(_collisions(os.path.join(src_ctx_dir, child),
                                    os.path.join(target_dir, child)))
    if problems:
        die("refusing to merge the documents tree — the target is not "
            "pristine ({0} collision(s), nothing was moved):\n  {1}".format(
                len(problems), "\n  ".join(problems[:20])))
    for child in sorted(os.listdir(src_ctx_dir)):
        src = os.path.join(src_ctx_dir, child)
        dst = os.path.join(target_dir, child)
        if child in CACHE_DIR_NAMES:
            lines.append("skipped derived cache directory '{0}' (the "
                         "application regenerates it)".format(child))
            continue
        existed = os.path.lexists(dst)
        is_dir = os.path.isdir(src)
        moved = _merge_entry(src, dst)
        if is_dir:
            lines.append("{0} {1}/ ({2} entr{3})".format(
                "merged into existing" if existed else "moved",
                child, moved, "y" if moved == 1 else "ies"))
        else:
            lines.append("moved {0}".format(child))
    return lines


def relocate_hrm_reports(ctx_root: str) -> List[str]:
    """Move O19's <ctx>/hrm/ report files into document/ (DOCUMENT_DIR),
    where CARLOS's HRM reader looks for them; a name collision with an
    existing document is fatal rather than silently resolved."""
    src_dir = os.path.join(ctx_root, HRM_INCOMING_DIR)
    if not os.path.isdir(src_dir):
        return []
    doc_dir = os.path.join(ctx_root, "document")
    os.makedirs(doc_dir, exist_ok=True)
    moved = 0
    for name in sorted(os.listdir(src_dir)):
        src = os.path.join(src_dir, name)
        if not os.path.isfile(src) or os.path.islink(src):
            continue
        dst = os.path.join(doc_dir, name)
        if os.path.lexists(dst):
            die("HRM report '{0}' collides with an existing file in "
                "document/ — resolve the duplicate before importing"
                .format(name))
        shutil.move(src, dst)
        moved += 1
    return ["moved {0} HRM report file(s) from {1}/ into document/ (the "
            "location CARLOS reads HRM reports from)".format(
                moved, HRM_INCOMING_DIR)]


def apply_ownership(root: str, dev_target: bool) -> None:
    if dev_target or os.geteuid() != 0:
        warn("skipping chown of {0} (dev target)".format(root))
        return
    cp = run(["chown", "-R",
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
              ) -> Tuple[List[str], List[str]]:
    """(blocking_problems, report_lines) for the restored tree."""
    problems: List[str] = []
    lines: List[str] = []
    doc_dir = os.path.join(ctx_root, "document")

    rows = [(r[0], unescape_batch_field(r[1])) for r in query(
        "SELECT document_no, docfilename FROM `{0}`.document"
        .format(dst_schema)) if len(r) >= 2]
    missing, empty = classify_document_files(rows, doc_dir)
    problems.extend("missing file for " + m for m in missing)
    problems.extend("empty file for " + e for e in empty)
    known = {f for _, f in rows}
    orphans = find_orphans(doc_dir, known)
    if orphans:
        lines.append("{0} orphan file(s) on disk with no document row "
                     "(report-only): {1}".format(len(orphans), orphans[:10]))
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
        fid, form_name, html = r[0], r[1], unescape_batch_field(r[2])
        for ref in image_refs(html):
            checked += 1
            asset = image_ref_path(ref)
            if not asset:
                continue
            if not contained(image_dir, asset):
                problems.append(
                    "eForm '{0}' (fid {1}) image reference escapes "
                    "eform/images: {2}".format(form_name, fid, ref))
            elif not os.path.isfile(os.path.join(image_dir, asset)):
                problems.append(
                    "eForm '{0}' (fid {1}) references missing image "
                    "asset: {2}".format(form_name, fid, ref))
    lines.append("{0} eForm image reference(s) checked".format(checked))

    _, hrm_select = hrm_rewrite_sql(dst_schema, os.path.dirname(ctx_root))
    hrm_rows = [(r[0], unescape_batch_field(r[1])) for r in query(hrm_select)
                if len(r) >= 2]
    problems.extend("missing HRM report for " + p
                    for p in classify_hrm_files(hrm_rows, doc_dir))
    lines.append("{0} HRM report row(s) reconciled against {1}".format(
        len(hrm_rows), doc_dir))
    return problems, lines


def export_archive_csv(query, archive_schema: str, out_dir: str
                       ) -> List[str]:
    """Write every o19_archive table as CSV so the clinic holds a readable
    copy of everything that became archive-only."""
    os.makedirs(out_dir, mode=0o750, exist_ok=True)
    tables = [r[0] for r in query(
        "SELECT TABLE_NAME FROM information_schema.TABLES WHERE "
        "TABLE_SCHEMA = '{0}' ORDER BY TABLE_NAME".format(archive_schema))]
    lines = []
    for table in tables:
        cols = [r[0] for r in query(
            "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE "
            "TABLE_SCHEMA = '{0}' AND TABLE_NAME = '{1}' ORDER BY "
            "ORDINAL_POSITION".format(archive_schema, table))]
        rows = query("SELECT * FROM `{0}`.`{1}`".format(
            archive_schema, table))
        path = os.path.join(out_dir, table + ".csv")
        # created with the final mode: the rows are archived clinical data
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o640)
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerow(cols)
            for r in rows:
                # the batch client prints SQL NULL as the two characters
                # \N — that is not a value, so it becomes an empty field
                writer.writerow([None if v == "\\N"
                                 else unescape_batch_field(v) for v in r])
        os.chmod(path, 0o640)
        lines.append("{0}.csv: {1} row(s)".format(table, len(rows)))
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

    if ctx["documents"] is None:
        if "no-documents" not in ctx["accepted"]:
            die("no documents tar and no --accept no-documents sign-off")
        warn("importing WITHOUT documents (acknowledged) — document rows "
             "will reference files that are not there")
        o19import.report_append(state_dir, "P5 documents",
                                "SKIPPED (no-documents acknowledged)")
        o19import.mark_done(state_dir, state, "documents",
                            skipped="no-documents")
        return

    tar_path = ctx["documents"]
    tar_sha = o19import.sha256_file(tar_path)
    prev = state.get("phases", {}).get("documents", {})
    already_restored = (prev.get("tar_sha256") == tar_sha
                        and prev.get("restored"))
    if prev.get("restored") and not already_restored:
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
        cp = run(["tar", "-tvzf" if gz else "-tvf", tar_path],
                 capture_output=True)
        if cp.returncode != 0:
            die("cannot read documents tar: " + cp.stderr.strip())
        try:
            # plain files + directories only, relative traversal-free
            # names: the tree is extracted as root
            names = o19bundle.validate_tar_members(
                o19bundle.parse_tar_listing(cp.stdout.splitlines()),
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

        move_lines = merge_move(os.path.join(incoming, old_ctx), ctx_root)
        shutil.rmtree(incoming, ignore_errors=True)
        # the tree is in place: record it NOW so a failure in any of the
        # (idempotent) steps below resumes without re-extracting
        state.setdefault("phases", {})["documents"] = {
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
    hrm_lines = relocate_hrm_reports(ctx_root)
    if hrm_lines:
        o19import.report_append(state_dir, "P5 HRM relocation",
                                "\n".join(hrm_lines))
    # every pass, not only the first: an operator who fixed the tree by
    # hand (the documented remedy) leaves root-owned files behind, and a
    # root-run reconciliation would never notice
    apply_ownership(ctx_root, ctx["dev_target"])
    update_sql, _ = hrm_rewrite_sql(ctx["target_db"], documents_root)
    query(update_sql)  # idempotent: basename into DOCUMENT_DIR

    problems, lines = reconcile(query, ctx["target_db"], ctx_root)
    csv_lines = export_archive_csv(
        query, ctx.get("archive_schema", "o19_archive"),
        os.path.join(ctx_root, "o19_archive_export"))
    o19import.report_append(
        state_dir, "P5 reconciliation",
        "\n".join(lines + ["archive CSV export:"]
                  + ["  " + line for line in csv_lines]))
    if problems:
        o19import.report_append(state_dir, "P5 reconciliation FAILURES",
                                "\n".join(problems[:100]))
        die("documents reconciliation FAILED ({0} problem(s)) — the "
            "clinical record must not go live with unreadable documents:"
            "\n  ".format(len(problems)) + "\n  ".join(problems[:20])
            + "\nFix the tree in place (add the missing files under {0}) "
              "and re-run with --resume; to restore a different tar "
              "instead, restore the pre-import snapshot first."
              .format(ctx_root))
    o19import.mark_done(state_dir, state, "documents", tar_sha256=tar_sha,
                        restored=True)
    log("documents restored and reconciled clean")
