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

Charset: the clinic's OSCAR 19 stores latin1 and the staging schema is
utf8mb4, so the same logical text has different STORED BYTES (`Santé` is
`53 61 6E 74 E9` there and `53 61 6E 74 C3 A9` here). Every value is
normalised to utf8mb4 before hashing, or the check would disagree on
every accented row of every clinic. Binary columns are hexed instead:
converting a scanned document through a character set is not a
round trip.
"""

from typing import Dict, List, NamedTuple, Optional, Sequence

#: Field separator inside a row's hashed form. A unit separator cannot
#: appear unescaped in the length-prefixed encoding, but it costs nothing
#: to keep the join unambiguous on its own.
SEP = "0x1f"

#: The marker a NULL contributes. It can never collide with a value:
#: a value always contributes `<length>:<text>`, which starts with a
#: digit, and this does not.
NULL_MARK = "'~'"

#: Column types whose bytes are not text and must not be run through a
#: character set. A scanned document converted "to utf8mb4" is not the
#: same document.
BINARY_TYPES = ("blob", "tinyblob", "mediumblob", "longblob",
                "binary", "varbinary")


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

    `coltype` is the information_schema DATA_TYPE. Binary columns are
    hexed; everything else is converted to utf8mb4 so the clinic's latin1
    and the staging schema's utf8mb4 agree on the same logical text.
    """
    quoted = "`{0}`".format(col.replace("`", "``"))
    if (coltype or "").lower() in BINARY_TYPES:
        rendered = "HEX({0})".format(quoted)
    else:
        rendered = "CONVERT({0} USING utf8mb4)".format(quoted)
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


def digest_sql(schema: str, table: str, columns: Sequence[str],
               types: Dict[str, str], where: Optional[str] = None) -> str:
    """`SELECT rows, total, parity` for one table.

    Both lanes are computed from the same hash in one pass; the hash is
    spelled twice rather than materialised in a derived table because
    MariaDB evaluates it per row either way and the derived form loses
    the index-free single scan on very large archives."""
    h = row_hash_expr(columns, types)
    ident = "`{0}`.`{1}`".format(schema.replace("`", "``"),
                                 table.replace("`", "``"))
    clause = " WHERE {0}".format(where) if where else ""
    return (
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
