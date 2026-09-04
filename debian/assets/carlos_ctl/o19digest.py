# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Content digests: prove the migration carried the VALUES, not just the
row counts.

`row_parity`, `preserved_parity` and `archived_column_parity` all count.
They prove no row was orphaned, which is a different claim from "every
row arrived intact": a copy that moves the right NUMBER of rows with the
wrong values passes all three. This module builds the digest that closes
that gap, and the comparison that reports it.

The primitive, and why each part of it is there -- every one of these was
measured against MariaDB 10.11 before being written down, because the
naive spelling of this check is worse than no check at all:

* `CONCAT_WS` SKIPS NULLs, so `('a', NULL, 'c')` and `('a', 'c', NULL)`
  hash IDENTICALLY. A column-swap bug would verify clean. Every column
  therefore carries an explicit NULL marker.
* A bare marker (`~`) is forged by a literal `~` in clinic data. Each
  value is length-prefixed, which makes the encoding prefix-free: no
  arrangement of values can imitate another.
* `BIT_XOR` alone cannot see a deleted IDENTICAL PAIR -- two equal rows
  cancel, so losing both leaves the digest unchanged. Hence the SUM lane.
* `SUM` alone promotes to DOUBLE past 2^53 and silently goes inexact.
  Hence the DECIMAL(30, 0) cast.
* The two lanes read DIFFERENT 16-hex-digit halves of the same SHA-256,
  so they cannot fail together on a collision in one half.

Charset: the clinic's OSCAR 19 stores latin1 and live CARLOS is utf8mb4,
so the same logical text has different STORED BYTES (`Santé` is
`53 61 6E 74 E9` there and `53 61 6E 74 C3 A9` here). Every value is
normalised to utf8mb4 before hashing, or the P7 comparison would
disagree on every accented row of every clinic. At P2 the two sides
usually agree already -- mysqldump writes each table's RESOLVED charset
into its DDL (measured: a table created with no `DEFAULT CHARSET` in a
latin1 database still dumps as `DEFAULT CHARSET=latin1`), so the restored
staging tables are latin1 like the clinic's and the CONVERT is a no-op
there. It is kept anyway: it costs nothing, and it is what keeps the
transfer check from failing a correct restore taken under a client or
server whose character-set defaults differ.

Binary columns are hexed instead: converting a scanned document through a
character set is not a round trip. A type in NEITHER list is refused
rather than guessed at: CONVERT is not injective over binary values (two
different BIT values both render as `?`) and HEX rounds a decimal to an
integer, so the wrong choice yields a digest that agrees while the data
differs.
"""

import json

from typing import (Callable, Dict, List, NamedTuple, Optional,
                    Sequence, Tuple)

#: Field separator inside a row's hashed form. A unit separator cannot
#: appear unescaped in the length-prefixed encoding, but it costs nothing
#: to keep the join unambiguous on its own.
SEP = "0x1f"

#: The marker a NULL contributes. It can never collide with a value:
#: a value always contributes `<length>:<text>`, which starts with a
#: digit, and this does not.
NULL_MARK = "'~'"

#: Column types whose bytes are not text and must be HEXed, never run
#: through a character set. A scanned document converted "to utf8mb4" is
#: not the same document -- and worse than lossy, the conversion is not
#: INJECTIVE: measured on MariaDB 10.11, `CONVERT(<bit> USING utf8mb4)`
#: renders both 0xC3 and 0xAA as `?` (0x3F), so two different BIT values
#: hash the same and a change between them is invisible. GEOMETRY loses
#: bytes the same way (0xF0 0x3F -> 0x3F 0x3F).
HEXED_TYPES = (
    "blob", "tinyblob", "mediumblob", "longblob",
    "binary", "varbinary", "bit",
    "geometry", "point", "linestring", "polygon",
    "multipoint", "multilinestring", "multipolygon",
    "geometrycollection",
)

#: Column types with ONE unambiguous string rendering, which `CONVERT`
#: produces and normalises across the clinic's latin1 and the staging
#: schema's utf8mb4. Numbers and dates carry no character set at all, so
#: the CONVERT is a no-op for them -- but they must not be HEXed instead:
#: measured, `HEX()` treats a numeric argument as a longlong, so
#: HEX(1.4) is '1' and HEX(1.5) is '2' and the two round together.
CONVERTED_TYPES = (
    "char", "varchar", "tinytext", "text", "mediumtext", "longtext",
    "enum", "set", "json",
    "tinyint", "smallint", "mediumint", "int", "integer", "bigint",
    "decimal", "numeric", "float", "double", "real",
    "date", "time", "datetime", "timestamp", "year",
    "inet4", "inet6", "uuid",
)


class Digest(NamedTuple):
    """One table's content digest.

    `rows` alone is what the old parity checks compared. `total` and
    `parity` are the two independent lanes over the row hashes.
    """

    rows: int
    total: int
    parity: int

    @classmethod
    def from_row(cls, row: Sequence[str]) -> "Digest":
        """Build from the three columns `digest_sql` selects. An empty
        table yields NULLs for the aggregates, which read as zero."""
        if len(row) < 3:
            raise ValueError(
                "a digest row must carry (rows, total, parity); got "
                "{0!r}".format(row))
        return cls(int(row[0] or 0), int(row[1] or 0), int(row[2] or 0))


def value_expr(col: str, coltype: str) -> str:
    """The normalised, unambiguous contribution of one column to a row's
    hash.

    `coltype` is the information_schema DATA_TYPE. Opaque columns are
    hexed; the rest are converted to utf8mb4, so a clinic's latin1 and
    live CARLOS's utf8mb4 agree on the same logical text.

    A type in NEITHER list raises `ValueError`, and the caller reports
    the table as unmeasured. That is deliberate: the two renderings are
    wrong for each other's types -- CONVERT collapses distinct binary
    values onto `?`, HEX rounds a decimal to a longlong -- so guessing
    for an unrecognised type would produce a digest that AGREES while
    the data differs, which is worse than having no digest at all.
    """
    quoted = "`{0}`".format(col.replace("`", "``"))
    normalised = (coltype or "").lower()
    if normalised in HEXED_TYPES:
        rendered = "HEX({0})".format(quoted)
    elif normalised in CONVERTED_TYPES:
        rendered = "CONVERT({0} USING utf8mb4)".format(quoted)
    else:
        raise ValueError(
            "column `{0}` has type {1!r}, which the digest has no "
            "rendering for; neither HEX nor CONVERT is safe for an "
            "unknown type".format(col, coltype))
    # length-prefixed on the RENDERED form, so the prefix describes what
    # is actually hashed
    return ("IFNULL(CONCAT(CHAR_LENGTH({0}), ':', {0}), {1})"
            .format(rendered, NULL_MARK))


def row_hash_expr(columns: Sequence[str], types: Dict[str, str]) -> str:
    """SHA-256 of one row, over `columns` in the order given.

    The order is part of the digest: two sides must present the same
    columns in the same sequence or they are not comparing the same
    thing. Callers pass the manifest's column order, never
    information_schema's."""
    if not columns:
        raise ValueError("a row hash needs at least one column")
    parts = ", ".join(value_expr(c, types.get(c, "")) for c in columns)
    return "SHA2(CONCAT_WS({0}, {1}), 256)".format(SEP, parts)


def digest_sql(schema: Optional[str], table: str, columns: Sequence[str],
               types: Dict[str, str], where: Optional[str] = None) -> str:
    """`SELECT rows, total, parity` for one table.

    `schema` may be None, which leaves the table unqualified for a
    connection that already has the right database selected. The clinic
    side runs that way (one `mysql <db>` per query, no cross-schema
    reach); the import side always qualifies, because it holds staging,
    archive and live open at once and an unqualified name there would
    silently digest whichever schema was last selected.

    Both lanes are computed from the same hash in one pass; the hash is
    spelled twice rather than materialised in a derived table because
    MariaDB evaluates it per row either way and the derived form loses
    the index-free single scan on very large archives."""
    h = row_hash_expr(columns, types)
    ident = "`{0}`".format(table.replace("`", "``"))
    if schema is not None:
        ident = "`{0}`.{1}".format(schema.replace("`", "``"), ident)
    clause = " WHERE {0}".format(where) if where else ""
    return (
        UTC_SESSION + ";\n"
        "SELECT COUNT(*), "
        "IFNULL(SUM(CAST(CONV(SUBSTR({h}, 1, 16), 16, 10) "
        "AS DECIMAL(30, 0))), 0), "
        "IFNULL(BIT_XOR(CONV(SUBSTR({h}, 17, 16), 16, 10)), 0) "
        "FROM {t}{w}".format(h=h, t=ident, w=clause))


def compare(name: str, expected: Digest, actual: Digest) -> List[str]:
    """Mismatch lines for one table, empty when the two agree.

    Reports WHICH lane disagreed, because they fail differently: a row
    count that matches while a lane does not means the rows were altered
    rather than lost, and that is the case a count-only check has always
    missed."""
    if expected == actual:
        return []
    if expected.rows != actual.rows:
        return ["{0}: {1} row(s) expected, {2} found".format(
            name, expected.rows, actual.rows)]
    return ["{0}: {1} row(s) on both sides but the CONTENT differs "
            "(sum {2} vs {3}, parity {4} vs {5}) — same number of rows, "
            "different values".format(
                name, expected.rows, expected.total, actual.total,
                expected.parity, actual.parity)]


#: Prelude every digest statement carries.
#:
#: A TIMESTAMP is STORED as UTC and RENDERED in the session's time zone.
#: Measured on MariaDB 10.11: one stored instant reads
#: '2020-06-01 12:00:00' at +00:00 and '2020-06-01 17:30:00' at +05:30.
#: The clinic's server and the CARLOS host are different machines whose
#: local time routinely differs, so without this every table carrying a
#: TIMESTAMP column would disagree at P2 on a perfectly faithful
#: transfer -- the false-alarm failure mode that gets a check switched
#: off. DATETIME is unaffected (stored and rendered verbatim), but the
#: setting is unconditional so the two sides can never differ about WHEN
#: it applies. Each statement runs in its own client process, so this
#: leaks into nothing else.
UTC_SESSION = "SET time_zone = '+00:00'"

#: Version of the digest DOCUMENT the clinic emits and the import reads.
#: Bumped only when the shape or the hash changes; a document the import
#: does not recognise is refused rather than half-understood, because a
#: digest compared under the wrong rules is worse than no digest.
DIGEST_FORMAT = 1


def digest_entry(columns: Sequence[Sequence[str]],
                 digest: Digest) -> Dict[str, object]:
    """One table's entry in a digest document.

    `columns` is the ordered `(name, information_schema DATA_TYPE)`
    sequence the digest was taken over; it is carried WITH the numbers
    because the other side must hash the same columns in the same order,
    and must be able to say so when it cannot.

    `total` and `parity` are strings: the SUM lane is a DECIMAL(30, 0)
    and outruns a 64-bit integer, which several JSON readers (and every
    spreadsheet an operator might open the file in) silently round."""
    return {"columns": [[c, t] for c, t in columns],
            "rows": digest.rows,
            "total": str(digest.total),
            "parity": str(digest.parity)}


def entry_digest(entry: Dict[str, object]) -> Digest:
    """The `Digest` an entry carries; the inverse of `digest_entry`.

    Raises `ValueError` on anything it cannot read, so a truncated or
    hand-edited document fails the comparison instead of comparing
    against zeros -- which would pass for every empty table."""
    try:
        return Digest(int(entry["rows"]), int(entry["total"]),
                      int(entry["parity"]))
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(
            "digest entry is not readable: {0}".format(exc))


def entry_columns(entry: Dict[str, object]) -> List[List[str]]:
    """The ordered `[name, type]` pairs an entry was taken over."""
    cols = entry.get("columns")
    if not isinstance(cols, list) or not cols:
        raise ValueError("digest entry carries no column list")
    out = []
    for pair in cols:
        if not isinstance(pair, (list, tuple)) or len(pair) != 2:
            raise ValueError(
                "digest column entry {0!r} is not a [name, type] "
                "pair".format(pair))
        out.append([str(pair[0]), str(pair[1])])
    return out


class Comparison(NamedTuple):
    """The outcome of comparing one side against a digest document.

    Three lists, because the three outcomes call for different actions:
    `failed` is a positive disagreement and needs a decision; `unverified`
    is a table nobody could measure and needs an acknowledgement; only
    `verified` is a claim.
    """

    verified: List[str]
    failed: List[Tuple[str, str]]
    unverified: List[Tuple[str, str]]

    def summary(self) -> str:
        return ("{0} table(s) verified, {1} disagreed, {2} could not be "
                "compared".format(len(self.verified), len(self.failed),
                                  len(self.unverified)))


def load_document(path: str) -> Dict[str, object]:
    """Read and vet a digest document.

    Raises `ValueError` on anything it cannot read under the rules it
    knows. A document of a FORMAT this build does not recognise is
    refused rather than half-understood: the numbers would be compared
    under different rules than they were taken under, and the result --
    agreement or disagreement -- would mean nothing either way.
    """
    try:
        with open(path, encoding="utf-8") as fh:
            doc = json.load(fh)
    except (OSError, ValueError) as exc:
        raise ValueError("cannot read digest document {0}: {1}"
                         .format(path, exc))
    if not isinstance(doc, dict):
        raise ValueError("{0} is not a digest document".format(path))
    fmt = doc.get("digest_format")
    if fmt != DIGEST_FORMAT:
        raise ValueError(
            "{0} carries digest format {1!r}; this build reads format {2}. "
            "Re-run the clinic assessment with a matching o19_preflight.py"
            .format(path, fmt, DIGEST_FORMAT))
    if not isinstance(doc.get("tables"), dict):
        raise ValueError("{0} carries no table digests".format(path))
    if not isinstance(doc.get("errors", {}), dict):
        raise ValueError("{0} carries a malformed error list".format(path))
    return doc


def _rendering(coltype: str) -> str:
    """Which of the two renderings a type gets, or "" for neither.

    The COMPARISON cares about the class, not the exact type: a column
    the clinic called `varchar` and staging calls `text` still hashes
    identically, and refusing that pair would fail a correct restore."""
    normalised = (coltype or "").lower()
    if normalised in HEXED_TYPES:
        return "hex"
    if normalised in CONVERTED_TYPES:
        return "convert"
    return ""


def resolve_table(name: str, present: Dict[str, str]) -> Optional[str]:
    """`name` as the other side spells it, or None.

    An exact match always wins; case folding stands in only when there is
    none, because a host running `lower_case_table_names=1` lower-cases
    every restored table name and the two sides would otherwise disagree
    on every table with a capital in it. `present` maps folded name ->
    actual spelling."""
    if name in present.values():
        return name
    return present.get(name.lower())


def compare_document(document: Dict[str, object],
                     other_columns: Dict[str, List[Tuple[str, str]]],
                     run: Callable[[str], Digest],
                     schema: Optional[str] = None,
                     where: Optional[str] = None) -> Comparison:
    """Compare every table in `document` against the schema `run` reads.

    `other_columns` is that schema's `{table: [(column, DATA_TYPE)]}`;
    `run` takes the digest SQL and returns the `Digest` (it raises on a
    query failure, which is recorded as unverified, never as agreement).

    Shape disagreements -- a table the restore did not create, a column it
    dropped, a column whose type changed rendering class -- are `failed`,
    not `unverified`: each of them IS a difference between the two sides,
    and reporting one as merely unmeasurable would understate it.
    """
    verified: List[str] = []
    failed: List[Tuple[str, str]] = []
    unverified: List[Tuple[str, str]] = []
    tables = document.get("tables") or {}
    present = dict((t.lower(), t) for t in other_columns)

    for name in sorted(tables):
        entry = tables[name]
        try:
            expected = entry_digest(entry)
            columns = entry_columns(entry)
        except (ValueError, TypeError, AttributeError) as exc:
            unverified.append((name, str(exc)))
            continue
        other = resolve_table(name, present)
        if other is None:
            failed.append((name, "the clinic digested this table; the "
                                 "other side has no such table"))
            continue
        their = dict((c.lower(), t) for c, t in other_columns[other])
        shape = []
        for col, coltype in columns:
            if col.lower() not in their:
                shape.append("column `{0}` is missing".format(col))
            elif _rendering(their[col.lower()]) != _rendering(coltype):
                shape.append(
                    "column `{0}` is {1} here and {2} at the clinic, which "
                    "hash differently".format(col, their[col.lower()],
                                              coltype))
        if shape:
            failed.append((name, "; ".join(shape)))
            continue
        types = dict((c, t) for c, t in columns)
        try:
            sql = digest_sql(schema, other, [c for c, _t in columns],
                             types, where)
            actual = run(sql)
        except Exception as exc:                      # noqa: BLE001
            # a table nobody could measure is not a table that agreed
            unverified.append((name, str(exc).strip().splitlines()[-1]
                               if str(exc).strip() else "digest failed"))
            continue
        problems = compare(name, expected, actual)
        if problems:
            failed.append((name, problems[0]))
        else:
            verified.append(name)

    for name, reason in sorted((document.get("errors") or {}).items()):
        unverified.append(
            (name, "the clinic could not measure it: {0}".format(reason)))

    measured = set(tables) | set(document.get("errors") or {})
    folded = set(n.lower() for n in measured)
    for other in sorted(other_columns):
        if other not in measured and other.lower() not in folded:
            unverified.append(
                (other, "present here but absent from the clinic's digest "
                        "document"))
    return Comparison(verified, failed, unverified)
