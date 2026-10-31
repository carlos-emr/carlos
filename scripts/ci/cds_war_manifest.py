#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
"""Bounded content evidence for the exact packaged WAR; no contents are published."""
from __future__ import annotations

from collections import Counter
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import stat
import struct
import tempfile
import time
from typing import NamedTuple
import zipfile
import zlib

BUFFER_BYTES = 65536
REQUIRED_CLASSES = frozenset({
    "WEB-INF/classes/io/github/carlos_emr/carlos/demographic/cdsimport/service/CdsImportJobService.class",
    "WEB-INF/classes/io/github/carlos_emr/carlos/demographic/cdsimport/cli/CdsImportCli.class",
})
REQUIRED_MIGRATION = "WEB-INF/classes/db/migration/common/V1.0.59.8.1__cds_import_ledger.sql"
BUILD_METADATA = frozenset({"META-INF/MANIFEST.MF", "WEB-INF/classes/git.properties",
    "WEB-INF/classes/build-info.properties", "WEB-INF/classes/META-INF/build-info.properties"})
FAILURE_CODES = frozenset({
    "WAR_MANIFEST_ARCHIVE_CHANGED", "WAR_MANIFEST_ARCHIVE_INVALID", "WAR_MANIFEST_COMPRESSION_UNSUPPORTED",
    "WAR_MANIFEST_CONTENT_CAPACITY", "WAR_MANIFEST_DEADLINE", "WAR_MANIFEST_DIRECTORY_CAPACITY",
    "WAR_MANIFEST_DIRECTORY_INVALID", "WAR_MANIFEST_DUPLICATE_MEMBER", "WAR_MANIFEST_ENTRY_CAPACITY",
    "WAR_MANIFEST_ENTRY_CRC", "WAR_MANIFEST_ENTRY_LENGTH", "WAR_MANIFEST_EXPANDED_CAPACITY",
    "WAR_MANIFEST_LIMITS_INVALID", "WAR_MANIFEST_LOCAL_HEADER_INVALID", "WAR_MANIFEST_NAME_CAPACITY",
    "WAR_MANIFEST_NAME_INVALID", "WAR_MANIFEST_OUTPUT_CAPACITY", "WAR_MANIFEST_OUTPUT_EXISTS",
    "WAR_MANIFEST_PREFIX_COLLISION", "WAR_MANIFEST_PUBLICATION_ROLLBACK_REPLACED",
    "WAR_MANIFEST_RAW_CAPACITY", "WAR_MANIFEST_REQUIRED_CONTENT_MISSING", "WAR_MANIFEST_SPECIAL_MEMBER",
    "WAR_MANIFEST_UNREADABLE",
})


class Limits(NamedTuple):
    entries: int = 32768
    name_bytes: int = 1024
    central_bytes: int = 8 << 20
    manifest_bytes: int = 8 << 20
    entry_bytes: int = 512 << 20
    expanded_bytes: int = 4 << 30
    raw_bytes: int = 2 << 30
    seconds: float = 120


class ManifestError(ValueError):
    """Static safe codes only; never include a member's content or exception text."""


def safe_failure_code(error: Exception) -> str | None:
    if (isinstance(error, ManifestError) and len(error.args) == 1 and isinstance(error.args[0], str)
            and error.args[0] in FAILURE_CODES):
        return error.args[0]
    return None


def require(condition: bool, code: str) -> None:
    if not condition:
        raise ManifestError(code)


def checkpoint(deadline: float) -> None:
    require(time.monotonic() < deadline, "WAR_MANIFEST_DEADLINE")


def identity(value: os.stat_result) -> tuple:
    return (value.st_dev, value.st_ino, value.st_mode, value.st_size, value.st_mtime_ns, value.st_ctime_ns)


def stream_hash(source, maximum: int, deadline: float, expected_crc: int | None = None) -> tuple[int, str]:
    digest = hashlib.sha256()
    length = 0
    crc = 0
    while True:
        checkpoint(deadline)
        block = source.read(BUFFER_BYTES)
        if not block:
            break
        length += len(block)
        require(length <= maximum, "WAR_MANIFEST_CONTENT_CAPACITY")
        digest.update(block)
        if expected_crc is not None:
            crc = zlib.crc32(block, crc)
    require(expected_crc is None or crc == expected_crc, "WAR_MANIFEST_ENTRY_CRC")
    return length, digest.hexdigest()


def valid_name(name: str, limits: Limits) -> None:
    require(isinstance(name, str) and bool(name), "WAR_MANIFEST_NAME_INVALID")
    require(len(name.encode("utf-8")) <= limits.name_bytes, "WAR_MANIFEST_NAME_CAPACITY")
    require(not any(ord(char) < 32 or ord(char) == 127 for char in name)
            and "\\" not in name and ":" not in name, "WAR_MANIFEST_NAME_INVALID")
    path = PurePosixPath(name)
    canonical = path.as_posix() + ("/" if name.endswith("/") else "")
    require(not path.is_absolute() and ".." not in path.parts and name == canonical
            and path.as_posix() != ".", "WAR_MANIFEST_NAME_INVALID")


def directory_preflight(source, raw_size: int, limits: Limits, deadline: float) -> int:
    """Bound/count central records before ZipFile allocates its entry objects."""
    source.seek(max(0, raw_size - 65557))
    tail = source.read(65557)
    index = tail.rfind(b"PK\x05\x06")
    require(index >= 0 and index + 22 <= len(tail), "WAR_MANIFEST_DIRECTORY_INVALID")
    end = struct.unpack_from("<4s4H2LH", tail, index)
    position = raw_size - len(tail) + index
    require(index + 22 + end[7] == len(tail) and end[1] == end[2] == 0
            and end[3] == end[4], "WAR_MANIFEST_DIRECTORY_INVALID")
    count, size, offset = end[4], end[5], end[6]
    central_end = position
    if position >= 20:
        source.seek(position - 20)
        locator = source.read(20)
        if locator[:4] == b"PK\x06\x07":
            _, disk, record, disks = struct.unpack("<4sLQL", locator)
            require(disk == 0 and disks == 1 and 0 <= record <= position - 76,
                    "WAR_MANIFEST_DIRECTORY_INVALID")
            source.seek(record)
            fixed = source.read(56)
            require(len(fixed) == 56, "WAR_MANIFEST_DIRECTORY_INVALID")
            large = struct.unpack("<4sQ2H2L4Q", fixed)
            require(large[0] == b"PK\x06\x06" and 44 <= large[1] <= 1024
                    and record + 12 + large[1] == position - 20
                    and large[4] == large[5] == 0 and large[6] == large[7],
                    "WAR_MANIFEST_DIRECTORY_INVALID")
            require((count == 65535 or count == large[7])
                    and (size == 0xffffffff or size == large[8])
                    and (offset == 0xffffffff or offset == large[9]), "WAR_MANIFEST_DIRECTORY_INVALID")
            count, size, offset, central_end = large[7], large[8], large[9], record
    require(0 < count <= limits.entries and 0 < size <= limits.central_bytes
            and offset >= 0 and offset + size == central_end, "WAR_MANIFEST_DIRECTORY_CAPACITY")
    cursor = offset
    found = 0
    while cursor < central_end:
        checkpoint(deadline)
        source.seek(cursor)
        fixed = source.read(46)
        require(len(fixed) == 46, "WAR_MANIFEST_DIRECTORY_INVALID")
        entry = struct.unpack("<4s6H3I5H2I", fixed)
        require(entry[0] == b"PK\x01\x02" and not entry[3] & 1 and entry[13] == 0
                and entry[4] in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED),
                "WAR_MANIFEST_DIRECTORY_INVALID")
        name_length, extra_length, comment_length = entry[10:13]
        require(0 < name_length <= limits.name_bytes, "WAR_MANIFEST_NAME_CAPACITY")
        name = source.read(name_length).decode("utf-8" if entry[3] & 0x800 else "cp437")
        valid_name(name, limits)  # In particular, reject NUL before ZipInfo can truncate it.
        cursor += 46 + name_length + extra_length + comment_length
        found += 1
        require(cursor <= central_end and found <= limits.entries, "WAR_MANIFEST_DIRECTORY_CAPACITY")
    require(cursor == central_end and found == count, "WAR_MANIFEST_DIRECTORY_INVALID")
    return found


def classification(name: str, directory: bool) -> str:
    if directory:
        return "directory"
    if name.startswith("WEB-INF/classes/") and name.endswith(".class"):
        return "production-class"
    if name.startswith("WEB-INF/lib/") and name.endswith(".jar"):
        return "dependency-jar"
    if name.startswith("WEB-INF/classes/db/migration/") and name.endswith(".sql"):
        return "migration"
    if name in BUILD_METADATA or name.startswith("META-INF/maven/") and name.endswith("/pom.properties"):
        return "build-metadata"
    return "resource"


def build_manifest(war: Path, limits: Limits = Limits()) -> dict:
    """Every entry is hashed, including empty directories; classifications grant no parity exemption."""
    require(all(value > 0 for value in limits), "WAR_MANIFEST_LIMITS_INVALID")
    require(not war.is_symlink() and war.is_file(), "WAR_MANIFEST_ARCHIVE_INVALID")
    deadline = time.monotonic() + limits.seconds
    try:
        with war.open("rb") as source:
            before = os.fstat(source.fileno())
            require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= limits.raw_bytes,
                    "WAR_MANIFEST_RAW_CAPACITY")
            raw_length, raw_sha = stream_hash(source, limits.raw_bytes, deadline)
            require(raw_length == before.st_size, "WAR_MANIFEST_ARCHIVE_CHANGED")
            count = directory_preflight(source, raw_length, limits, deadline)
            rows = []
            nodes = {}
            expanded = encoded = 0
            with zipfile.ZipFile(source) as archive:
                entries = archive.infolist()
                require(len(entries) == count, "WAR_MANIFEST_DIRECTORY_INVALID")
                for entry in entries:
                    valid_name(entry.orig_filename, limits)
                    require(entry.orig_filename == entry.filename and not entry.flag_bits & 1,
                            "WAR_MANIFEST_NAME_INVALID")
                    require(entry.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED),
                            "WAR_MANIFEST_COMPRESSION_UNSUPPORTED")
                    kind = stat.S_IFMT(entry.external_attr >> 16)
                    directory = entry.is_dir()
                    require(kind in (0, stat.S_IFDIR if directory else stat.S_IFREG),
                            "WAR_MANIFEST_SPECIAL_MEMBER")
                    name = entry.filename
                    node = name.rstrip("/")
                    require(node not in nodes, "WAR_MANIFEST_DUPLICATE_MEMBER")
                    nodes[node] = directory
                    require(0 <= entry.file_size <= limits.entry_bytes and (not directory or entry.file_size == 0),
                            "WAR_MANIFEST_ENTRY_CAPACITY")
                    expanded += entry.file_size
                    require(expanded <= limits.expanded_bytes, "WAR_MANIFEST_EXPANDED_CAPACITY")
                for node in nodes:
                    for parent in PurePosixPath(node).parents:
                        require(parent.as_posix() not in nodes or nodes[parent.as_posix()],
                                "WAR_MANIFEST_PREFIX_COLLISION")
                for entry in sorted(entries, key=lambda item: item.filename):
                    require(0 <= entry.header_offset <= raw_length - 30, "WAR_MANIFEST_LOCAL_HEADER_INVALID")
                    source.seek(entry.header_offset)
                    local = source.read(30)
                    require(len(local) == 30, "WAR_MANIFEST_LOCAL_HEADER_INVALID")
                    header = struct.unpack("<4s5H3I2H", local)
                    require(header[0] == b"PK\x03\x04" and header[2] == entry.flag_bits
                            and not header[2] & 0x2041 and header[3] == entry.compress_type,
                            "WAR_MANIFEST_LOCAL_HEADER_INVALID")
                    with archive.open(entry) as incoming:
                        length, digest = stream_hash(incoming, limits.entry_bytes, deadline, entry.CRC)
                    require(length == entry.file_size, "WAR_MANIFEST_ENTRY_LENGTH")
                    category = classification(entry.filename, entry.is_dir())
                    require(category not in ("production-class", "dependency-jar") or length > 0,
                            "WAR_MANIFEST_REQUIRED_CONTENT_MISSING")
                    row = {"name": entry.filename, "kind": "directory" if entry.is_dir() else "file",
                           "bytes": length, "sha256": digest, "classification": category}
                    encoded += len(json.dumps(row, sort_keys=True, separators=(",", ":")).encode()) + 1
                    require(encoded + 2048 <= limits.manifest_bytes, "WAR_MANIFEST_OUTPUT_CAPACITY")
                    rows.append(row)
            names = {row["name"] for row in rows if row["kind"] == "file" and row["bytes"] > 0}
            counts = dict(sorted(Counter(row["classification"] for row in rows).items()))
            require(REQUIRED_CLASSES <= names and REQUIRED_MIGRATION in names
                    and counts.get("dependency-jar", 0) > 0, "WAR_MANIFEST_REQUIRED_CONTENT_MISSING")
            source.seek(0)
            require(stream_hash(source, limits.raw_bytes, deadline) == (raw_length, raw_sha)
                    and identity(os.fstat(source.fileno())) == identity(before)
                    and identity(war.lstat()) == identity(before), "WAR_MANIFEST_ARCHIVE_CHANGED")
            return {"schemaVersion": 1, "scope": "carlos-war-entry-manifest", "warSha256": raw_sha,
                    "entryCount": len(rows), "fileCount": sum(row["kind"] == "file" for row in rows),
                    "directoryCount": sum(row["kind"] == "directory" for row in rows),
                    "countsByClassification": counts, "entries": rows}
    except ManifestError:
        raise
    except (OSError, EOFError, UnicodeError, zipfile.BadZipFile, RuntimeError, struct.error):
        raise ManifestError("WAR_MANIFEST_UNREADABLE") from None


def verify_archive_binding(war: Path, expected: str, limits: Limits = Limits(), deadline: float | None = None) -> None:
    require(not war.is_symlink() and war.is_file(), "WAR_MANIFEST_ARCHIVE_CHANGED")
    deadline = time.monotonic() + limits.seconds if deadline is None else deadline
    with war.open("rb") as source:
        before = os.fstat(source.fileno())
        require(before.st_size <= limits.raw_bytes, "WAR_MANIFEST_RAW_CAPACITY")
        require(stream_hash(source, limits.raw_bytes, deadline) == (before.st_size, expected)
                and identity(before) == identity(os.fstat(source.fileno())) == identity(war.lstat()),
                "WAR_MANIFEST_ARCHIVE_CHANGED")


def publish_manifest(war: Path, destination: Path, limits: Limits = Limits()) -> dict:
    """Publish one complete fsynced record; an unsuccessful attempt leaves no apparent success."""
    require(not destination.exists() and not destination.is_symlink(), "WAR_MANIFEST_OUTPUT_EXISTS")
    deadline = time.monotonic() + limits.seconds
    manifest = build_manifest(war, limits)
    data = (json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n").encode()
    require(len(data) <= limits.manifest_bytes, "WAR_MANIFEST_OUTPUT_CAPACITY")
    pending = None
    published_identity = None
    failure = None
    try:
        descriptor, name = tempfile.mkstemp(prefix=".war-manifest-", suffix=".pending", dir=destination.parent)
        pending = Path(name)
        with os.fdopen(descriptor, "wb") as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        require(not destination.exists() and not destination.is_symlink(), "WAR_MANIFEST_OUTPUT_EXISTS")
        verify_archive_binding(war, manifest["warSha256"], limits, deadline)
        # Atomic no-replace publication in the private, single-owner evidence directory.
        pending_stat = pending.lstat()
        os.link(pending, destination)
        published_identity = (pending_stat.st_dev, pending_stat.st_ino)
        pending.unlink()
        pending = None
        descriptor = os.open(destination.parent, os.O_RDONLY | os.O_DIRECTORY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        return {"file": destination.name, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(),
                "entryCount": manifest["entryCount"], "countsByClassification": manifest["countsByClassification"],
                "warSha256": manifest["warSha256"]}
    except BaseException as error:
        failure = error
        if published_identity is not None:
            try:
                current = destination.lstat()
                require((current.st_dev, current.st_ino) == published_identity,
                        "WAR_MANIFEST_PUBLICATION_ROLLBACK_REPLACED")
                destination.unlink()
                descriptor = os.open(destination.parent, os.O_RDONLY | os.O_DIRECTORY)
                try:
                    os.fsync(descriptor)
                finally:
                    os.close(descriptor)
            except FileNotFoundError:
                pass
            except (OSError, ManifestError):
                error.add_note("WAR_MANIFEST_PUBLICATION_ROLLBACK_FAILED")
        raise
    finally:
        if pending is not None:
            try:
                pending.unlink(missing_ok=True)
            except OSError:
                if failure is None:
                    raise
                failure.add_note("WAR_MANIFEST_PENDING_CLEANUP_FAILED")
