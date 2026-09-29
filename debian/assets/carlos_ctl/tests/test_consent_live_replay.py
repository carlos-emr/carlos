# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Replay the Consent helper build and copy against an in-memory database
and compare the rows that ARRIVE, not the statement text.

CARLOS allows one live Consent row per patient and consent type
(uq_consent_live_type, #3845); an OSCAR 19 clinic can hold several, and
NULL flags. The import keeps the record the application reads as
deciding (ConsentRecords.DECIDING_FIRST) and retires the others, and
every case below is one clause of that rule with synthetic rows.

sqlite3 (stdlib) stands in for MariaDB, as in test_privilege_replay: the
statements are translated token-for-token where the dialects differ, and
the target carries a partial unique index equivalent to the generated
column + unique key the migration adds.

The manifest entry is built HERE, the way the generator would build it
from the overlay (overrides_schema.VALUE_EXPRS): the shipped manifest is
regenerated from an OSCAR 19 checkout and may predate the ruling.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import importlib.util
import re
import sqlite3
import unittest
from pathlib import Path

from carlos_ctl import o19etl, o19map_schema

SRC, DST, ARCH = "src", "dst", "arch"

OVERRIDES = Path(__file__).resolve().parents[4] / "scripts" / "migration" / \
    "o19" / "overrides_schema.py"

COLLATED = re.compile(
    r"CONVERT\(([ds]\.`\w+`) USING utf8mb4\) COLLATE utf8mb4_bin")


def translate(sql):
    return COLLATED.sub(r"\1", sql).replace(" <=> ", " IS ")


def rename_pairs(sql):
    """The (source, destination) pairs of a MySQL `RENAME TABLE` swap;
    see test_privilege_replay.rename_pairs."""
    out = []
    for pair in sql[len("RENAME TABLE "):].split(", "):
        src, dst = (x.strip() for x in pair.split(" TO "))
        out.append((src, dst.split(".", 1)[-1]))
    return out


def load_overrides():
    spec = importlib.util.spec_from_file_location(
        "overrides_schema", OVERRIDES)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def ranked_entry():
    """The Consent entry a regenerated manifest carries: the shipped
    one, plus the overlay's expressions, plus every expression's target
    in `cols` (generate_manifests._shared_table_columns)."""
    entry = dict(o19map_schema.TABLES["Consent"])
    exprs = dict(load_overrides().VALUE_EXPRS["Consent"])
    entry["value_exprs"] = exprs
    entry["cols"] = list(entry["cols"]) + [
        c for c in sorted(exprs) if c not in entry["cols"]]
    return entry


def col(dtype, nullable=True, primitive=False):
    return {"type": dtype, "column_type": dtype, "nullable": nullable,
            "char_len": 0, "octet_len": 0, "has_default": False,
            "default": None, "auto_increment": False,
            "primitive": primitive}


#: the target after V1.0.33: the three flags NOT NULL, and mapped to
#: Java primitives (o19map_schema.PRIMITIVE_COLUMNS)
DST_COLS = {
    "id": col("int", nullable=False),
    "demographic_no": col("int"),
    "consent_type_id": col("int"),
    "explicit": col("tinyint", nullable=False, primitive=True),
    "optout": col("tinyint", nullable=False, primitive=True),
    "last_entered_by": col("varchar"),
    "consent_date": col("datetime"),
    "optout_date": col("datetime"),
    "edit_date": col("datetime"),
    "deleted": col("tinyint", nullable=False, primitive=True),
}

SOURCE_COLS = ["id", "demographic_no", "consent_type_id", "explicit",
               "optout", "last_entered_by", "consent_date", "optout_date",
               "edit_date"]

#: legacy consent type -> CARLOS consent type. 1 and 2 are two clinic
#: types that merged onto ONE CARLOS type (consentType merges on its
#: `type` name); 9 is absent on purpose -- a reference already dangling
#: in the clinic's data.
ID_MAP = [(1, 10), (2, 10), (3, 30)]

EARLY, LATE = "2020-01-01 00:00:00", "2024-06-01 00:00:00"
ZERO = "0000-00-00 00:00:00"


def row(rid, patient=100, ctype=1, explicit=0, optout=0, edited=EARLY,
        deleted=0):
    return {"id": rid, "demographic_no": patient, "consent_type_id": ctype,
            "explicit": explicit, "optout": optout,
            "last_entered_by": "999", "consent_date": EARLY,
            "optout_date": None, "edit_date": edited, "deleted": deleted}


@unittest.skipUnless(OVERRIDES.is_file(), "overlay not in this checkout")
class ConsentReplayBase(unittest.TestCase):

    #: whether the staged dump's Consent has a `deleted` column
    SOURCE_HAS_DELETED = True
    #: how the dump spells it (MySQL column names fold case)
    DELETED_SPELLING = "deleted"
    #: columns a lower patch level of the dump does not have
    SOURCE_LACKS = ()

    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        for schema in (SRC, DST, ARCH):
            self.db.execute("ATTACH DATABASE ':memory:' AS {0}".format(schema))
        self.src_names = [c for c in SOURCE_COLS
                          if c not in self.SOURCE_LACKS]
        if self.SOURCE_HAS_DELETED:
            self.src_names.append(self.DELETED_SPELLING)
        self.db.execute("CREATE TABLE src.Consent ({0})".format(
            ", ".join(self.src_names)))
        self.db.execute(
            "CREATE TABLE dst.Consent (id INTEGER PRIMARY KEY, "
            "demographic_no INTEGER, consent_type_id INTEGER, "
            "explicit INTEGER NOT NULL DEFAULT 0, optout INTEGER NOT NULL, "
            "last_entered_by TEXT, consent_date TEXT, optout_date TEXT, "
            "edit_date TEXT, deleted INTEGER NOT NULL DEFAULT 0)")
        # live rows only, and a NULL patient or type is never a
        # duplicate: what live_demographic_no + the unique key enforce
        self.db.execute(
            "CREATE UNIQUE INDEX dst.uq_consent_live_type ON Consent "
            "(demographic_no, consent_type_id) WHERE deleted = 0")
        self.db.execute("CREATE TABLE arch.consentType__idmap "
                        "(old_id INTEGER PRIMARY KEY, new_id INTEGER)")
        self.db.executemany(
            "INSERT INTO arch.consentType__idmap VALUES (?, ?)", ID_MAP)
        self.src_cols = {c: {} for c in self.src_names}
        self.entry, _notes = o19etl.effective_entry(
            "Consent", ranked_entry(), self.src_cols,
            {"Consent", "consentType"})

    def run_sql(self, sql):
        return self.db.execute(translate(sql)).fetchall()

    def stage(self, *rows):
        for r in rows:
            values = [r["deleted"] if c.lower() == "deleted" else r[c]
                      for c in self.src_names]
            self.db.execute(
                "INSERT INTO src.Consent VALUES ({0})".format(
                    ", ".join("?" for _ in values)), values)

    def build(self):
        for sql in o19etl.consent_live_statements(
                self.entry, SRC, ARCH, DST_COLS, self.src_cols):
            if sql.startswith("CREATE TABLE IF NOT EXISTS"):
                continue      # CREATE ... LIKE: see test_privilege_replay
            if sql.startswith("RENAME TABLE"):
                for src, dst in rename_pairs(sql):
                    self.run_sql("DROP TABLE IF EXISTS {0}.{1}".format(
                        ARCH, dst))
                    try:
                        self.run_sql("ALTER TABLE {0} RENAME TO {1}".format(
                            src, dst))
                    except sqlite3.OperationalError as exc:
                        if "no such table" not in str(exc):
                            raise
                continue
            self.run_sql(sql)

    def copy(self, entry=None):
        self.run_sql(o19etl.copy_statement(
            "Consent", entry or self.entry, SRC, DST, DST_COLS, None, None,
            ARCH))

    def imported(self, *rows):
        """Stage, build the helper, copy; the rows that arrived as
        (id, deleted, optout, explicit)."""
        self.stage(*rows)
        self.build()
        self.copy()
        return self.arrived()

    def arrived(self):
        return self.db.execute(
            "SELECT id, deleted, optout, explicit FROM dst.Consent "
            "ORDER BY id").fetchall()

    def counts(self):
        return dict(
            (what, self.run_sql(sql)[0][0])
            for what, sql in o19etl.consent_live_count_sql(
                SRC, ARCH, self.src_cols))

    def assertCounts(self, **expected):
        """The counts named, every other one being 0."""
        counted = self.counts()
        self.assertEqual(
            counted, dict(dict.fromkeys(counted, 0), **expected))

    def record(self):
        """What the helper recorded, as {id: (prior_explicit,
        prior_optout, prior_deleted, reason)}."""
        return dict((r[0], r[1:]) for r in self.db.execute(
            "SELECT id, prior_explicit, prior_optout, prior_deleted, "
            "reason FROM arch.Consent__live"))

    def coverage(self):
        return self.run_sql(
            o19etl.consent_live_coverage_sql(SRC, ARCH))[0][0]

    def duplicates(self):
        return self.run_sql(o19etl.consent_live_duplicates_sql(DST))[0][0]

    def mismatches(self):
        return self.run_sql(o19etl.copy_value_mismatch_sql(
            "Consent", self.entry, SRC, DST, DST_COLS, ["id"], set(),
            ARCH))[0][0]


class TestTheDecidingRecordStaysLive(ConsentReplayBase):

    """One clause of ConsentRecords.DECIDING_FIRST per test."""

    def test_an_opt_out_beats_a_later_opt_in(self):
        self.assertEqual(
            self.imported(row(1, optout=1, edited=EARLY),
                          row(2, optout=0, edited=LATE)),
            [(1, 0, 1, 0), (2, 1, 0, 0)])

    def test_an_older_explicit_opt_in_beats_a_newer_implied_one(self):
        self.assertEqual(
            self.imported(row(1, explicit=1, edited=EARLY),
                          row(2, explicit=0, edited=LATE)),
            [(1, 0, 0, 1), (2, 1, 0, 0)])

    def test_of_two_opt_outs_the_explicit_one_stays(self):
        self.assertEqual(
            self.imported(row(1, optout=1, explicit=1, edited=EARLY),
                          row(2, optout=1, explicit=0, edited=LATE)),
            [(1, 0, 1, 1), (2, 1, 1, 0)])

    def test_an_implied_opt_out_beats_an_explicit_opt_in(self):
        self.assertEqual(
            self.imported(row(1, optout=1, explicit=0, edited=EARLY),
                          row(2, optout=0, explicit=1, edited=LATE)),
            [(1, 0, 1, 0), (2, 1, 0, 1)])

    def test_the_latest_edit_wins_among_equals(self):
        self.assertEqual(
            self.imported(row(1, edited=LATE), row(2, edited=EARLY),
                          row(3, edited="2022-03-03 00:00:00")),
            [(1, 0, 0, 0), (2, 1, 0, 0), (3, 1, 0, 0)])

    def test_a_dated_row_beats_an_undated_one(self):
        # the undated row has the HIGHER id, so it is the date and not
        # the id that decides. This fails if `(edit_date IS NULL)` is
        # reversed. It cannot fail if the term is REMOVED: MariaDB and
        # sqlite both sort NULL lowest, so `edit_date DESC` alone puts
        # an undated row last too. The term is the rule written out,
        # not a second behaviour, and its presence is pinned as text
        # (test_etl_sql: test_the_order_is_the_applications_rule).
        self.assertEqual(
            self.imported(row(1, edited=EARLY), row(2, edited=None)),
            [(1, 0, 0, 0), (2, 1, 0, 0)])

    def test_a_zero_date_counts_as_undated(self):
        # left as a value, the zero date would be a DATED row and beat
        # the NULL one; as undated the two tie and the higher id wins
        self.assertEqual(
            self.imported(row(1, edited=ZERO), row(2, edited=None)),
            [(1, 1, 0, 0), (2, 0, 0, 0)])
        self.assertEqual(self.db.execute(
            "SELECT edit_date FROM dst.Consent WHERE id = 1").fetchall(),
            [(None,)])

    def test_a_zero_date_loses_to_a_dated_row(self):
        self.assertEqual(
            self.imported(row(1, edited=EARLY), row(2, edited=ZERO)),
            [(1, 0, 0, 0), (2, 1, 0, 0)])

    def test_a_tie_on_the_edit_date_goes_to_the_higher_id(self):
        self.assertEqual(
            self.imported(row(1), row(2), row(3)),
            [(1, 1, 0, 0), (2, 1, 0, 0), (3, 0, 0, 0)])

    def test_one_live_row_is_left_alone(self):
        self.assertEqual(
            self.imported(row(1), row(2, patient=200), row(3, ctype=3)),
            [(1, 0, 0, 0), (2, 0, 0, 0), (3, 0, 0, 0)])
        self.assertCounts(duplicate=0, undecided=0)


class TestNullFlags(ConsentReplayBase):

    def test_a_null_optout_is_retired_and_stored_as_an_opt_out(self):
        self.assertEqual(self.imported(row(1, optout=None)),
                         [(1, 1, 1, 0)])
        self.assertCounts(duplicate=0, undecided=1)

    def test_a_null_optout_does_not_displace_a_live_opt_in(self):
        # stored as an opt-out, it would win the ranking if it were
        # ranked -- and retire a decision somebody did record
        self.assertEqual(
            self.imported(row(1, optout=0, edited=EARLY),
                          row(2, optout=None, edited=LATE)),
            [(1, 0, 0, 0), (2, 1, 1, 0)])
        self.assertCounts(duplicate=0, undecided=1)

    def test_a_null_deleted_is_retired(self):
        self.assertEqual(
            self.imported(row(1, deleted=None), row(2)),
            [(1, 1, 0, 0), (2, 0, 0, 0)])
        # it was not live in the clinic's data, so nothing was retired
        # as a duplicate on its account
        self.assertCounts(null_deleted=1)

    def test_a_null_deleted_with_a_null_optout(self):
        # retired once, for either reason, and stored as an opt-out;
        # it was never live, so it is not an "undecided live row"
        self.assertEqual(
            self.imported(row(1, optout=None, deleted=None), row(2)),
            [(1, 1, 1, 0), (2, 0, 0, 0)])
        self.assertCounts(null_deleted=1, not_live_undecided=1)
        self.assertEqual(self.record()[1], (0, None, None, "null_flag"))

    def test_a_null_explicit_is_stored_as_implied(self):
        self.assertEqual(self.imported(row(1, explicit=None)),
                         [(1, 0, 0, 0)])

    def test_a_null_explicit_ranks_as_implied(self):
        self.assertEqual(
            self.imported(row(1, explicit=1, edited=EARLY),
                          row(2, explicit=None, edited=LATE)),
            [(1, 0, 0, 1), (2, 1, 0, 0)])


class TestRowsAlreadyDeleted(ConsentReplayBase):

    def test_a_deleted_row_stays_deleted_and_is_not_ranked(self):
        # the deleted opt-out would win if it were ranked
        self.assertEqual(
            self.imported(row(1, optout=1, edited=LATE, deleted=1),
                          row(2, optout=0, edited=EARLY)),
            [(1, 1, 1, 0), (2, 0, 0, 0)])
        self.assertCounts(legacy_deleted=1)

    def test_deleted_rows_repeat_freely(self):
        self.assertEqual(
            self.imported(row(1, deleted=1), row(2, deleted=1),
                          row(3, deleted=1)),
            [(1, 1, 0, 0), (2, 1, 0, 0), (3, 1, 0, 0)])

    def test_a_deleted_row_with_no_decision_is_not_counted_as_retired(self):
        self.assertEqual(self.imported(row(1, optout=None, deleted=1)),
                         [(1, 1, 1, 0)])
        self.assertCounts(legacy_deleted=1, not_live_undecided=1)

    def test_a_deleted_row_of_an_unmapped_type_stays_deleted(self):
        self.assertEqual(
            self.imported(row(1, ctype=9, deleted=1),
                          row(2, ctype=9, deleted=1)),
            [(1, 1, 0, 0), (2, 1, 0, 0)])
        self.assertEqual(self.db.execute(
            "SELECT consent_type_id FROM dst.Consent").fetchall(),
            [(None,), (None,)])
        self.assertEqual(self.record()[1], (0, 0, 1, None))


class TestADumpThatSpellsTheColumnDifferently(ConsentReplayBase):

    DELETED_SPELLING = "Deleted"

    def test_the_column_is_found_whatever_its_case(self):
        self.assertEqual(
            self.imported(row(1, deleted=1), row(2)),
            [(1, 1, 0, 0), (2, 0, 0, 0)])


class TestADumpWithoutDeleted(ConsentReplayBase):

    SOURCE_HAS_DELETED = False

    def test_every_row_is_live(self):
        self.assertEqual(
            self.imported(row(1, edited=EARLY), row(2, edited=LATE),
                          row(3, patient=200)),
            [(1, 1, 0, 0), (2, 0, 0, 0), (3, 0, 0, 0)])
        self.assertCounts(duplicate=1, undecided=0)

    def test_a_null_optout_is_still_retired(self):
        self.assertEqual(self.imported(row(1, optout=None)),
                         [(1, 1, 1, 0)])
        self.assertCounts(duplicate=0, undecided=1)


class TestADumpWithoutExplicit(ConsentReplayBase):

    SOURCE_LACKS = ("explicit",)

    def test_every_row_is_implied_and_the_date_decides(self):
        self.assertEqual(
            self.imported(row(1, edited=LATE), row(2, edited=EARLY)),
            [(1, 0, 0, 0), (2, 1, 0, 0)])
        # a column the dump does not have is not a NULL flag
        self.assertEqual(self.record(), {
            1: (None, 0, 0, None),
            2: (None, 0, 0, "duplicate_retired")})
        self.assertEqual(self.mismatches(), 0)


class TestADumpWithoutEditDate(ConsentReplayBase):

    SOURCE_LACKS = ("edit_date",)

    def test_every_row_is_undated_and_the_id_decides(self):
        self.assertEqual(
            self.imported(row(1), row(2), row(3, explicit=1)),
            [(1, 1, 0, 0), (2, 1, 0, 0), (3, 0, 0, 1)])
        self.assertEqual(self.mismatches(), 0)


class TestTheRecordOfWhatChanged(ConsentReplayBase):

    """`Consent__live` keeps what each row held and why it differs."""

    def test_a_row_that_arrives_as_it_was_has_no_reason(self):
        self.imported(row(1, explicit=1, optout=1), row(2, patient=200),
                      row(3, patient=300, deleted=1))
        self.assertEqual(self.record(), {
            1: (1, 1, 0, None), 2: (0, 0, 0, None), 3: (0, 0, 1, None)})

    def test_a_retired_duplicate_keeps_what_it_held(self):
        self.imported(row(1, explicit=1, edited=EARLY),
                      row(2, explicit=0, edited=LATE))
        self.assertEqual(self.record(), {
            1: (1, 0, 0, None), 2: (0, 0, 0, "duplicate_retired")})

    def test_a_null_flag_is_recorded_as_the_null_it_was(self):
        self.imported(row(1, optout=None), row(2, patient=200,
                                               deleted=None),
                      row(3, patient=300, explicit=None))
        self.assertEqual(self.record(), {
            1: (0, None, 0, "null_flag"),
            2: (0, 0, None, "null_flag"),
            3: (None, 0, 0, "null_flag")})

    def test_a_row_changed_for_both_reasons_carries_both(self):
        self.imported(row(1, explicit=1), row(2, explicit=None))
        self.assertEqual(
            self.record()[2], (None, 0, 0, "null_flag,duplicate_retired"))

    def test_the_words_are_the_migrations(self):
        self.assertEqual(
            (o19etl.CONSENT_NULL_FLAG, o19etl.CONSENT_DUPLICATE),
            ("null_flag", "duplicate_retired"))

    def test_the_helper_covers_the_dump(self):
        self.imported(*MIXED)
        self.assertEqual(self.coverage(), 0)

    def test_a_staged_row_the_helper_does_not_know_is_counted(self):
        self.stage(row(1))
        self.build()
        self.stage(row(2))
        self.assertEqual(self.coverage(), 1)
        # and had the copy run all the same, it arrives retired
        self.copy()
        self.assertEqual(self.arrived(), [(1, 0, 0, 0), (2, 1, 0, 0)])

    def test_a_helper_row_without_a_staged_row_is_counted(self):
        self.stage(row(1), row(2, patient=200))
        self.build()
        self.db.execute("DELETE FROM src.Consent WHERE id = 2")
        self.assertEqual(self.coverage(), 1)


class TestADumpWithoutDeletedKeepsNoPriorFlag(ConsentReplayBase):

    SOURCE_HAS_DELETED = False

    def test_the_prior_deleted_is_null_and_is_not_a_reason(self):
        self.imported(row(1), row(2, patient=200, optout=None))
        self.assertEqual(self.record(), {
            1: (0, 0, None, None), 2: (0, None, None, "null_flag")})
        self.assertEqual(
            sorted(self.counts()), ["duplicate", "undecided"])


class TestTheKeyIsTheMappedOne(ConsentReplayBase):

    def test_two_legacy_types_on_one_carlos_type_rank_together(self):
        self.assertEqual(
            self.imported(row(1, ctype=1, edited=LATE),
                          row(2, ctype=2, edited=EARLY)),
            [(1, 0, 0, 0), (2, 1, 0, 0)])
        self.assertEqual(self.db.execute(
            "SELECT DISTINCT consent_type_id FROM dst.Consent").fetchall(),
            [(10,)])

    def test_different_carlos_types_do_not_rank_together(self):
        self.assertEqual(
            self.imported(row(1, ctype=1), row(2, ctype=3)),
            [(1, 0, 0, 0), (2, 0, 0, 0)])

    def test_rows_missing_the_patient_are_not_grouped(self):
        self.assertEqual(
            self.imported(row(1, patient=None), row(2, patient=None)),
            [(1, 0, 0, 0), (2, 0, 0, 0)])

    def test_rows_whose_type_does_not_map_are_not_grouped(self):
        # type 9 has no id-map entry, so both arrive with a NULL type
        self.assertEqual(
            self.imported(row(1, ctype=9), row(2, ctype=9),
                          row(3, ctype=None), row(4, ctype=None)),
            [(1, 0, 0, 0), (2, 0, 0, 0), (3, 0, 0, 0), (4, 0, 0, 0)])
        self.assertEqual(self.db.execute(
            "SELECT COUNT(*) FROM dst.Consent WHERE consent_type_id IS "
            "NOT NULL").fetchall(), [(0,)])

    def test_an_ungrouped_deleted_row_stays_deleted(self):
        self.assertEqual(
            self.imported(row(1, patient=None, deleted=1)),
            [(1, 1, 0, 0)])


MIXED = [
    row(1, optout=1, edited=EARLY), row(2, optout=0, edited=LATE),
    row(3, patient=200, explicit=1), row(4, patient=200, edited=LATE),
    row(5, patient=300, optout=None), row(6, patient=300, deleted=1),
    row(7, patient=None), row(8, patient=None), row(9, ctype=9),
    row(10, patient=400, ctype=1), row(11, patient=400, ctype=2),
    row(12, patient=400, ctype=3, deleted=None),
]


class TestTheWholeImport(ConsentReplayBase):

    def test_no_row_is_dropped(self):
        arrived = self.imported(*MIXED)
        self.assertEqual([r[0] for r in arrived],
                         [r["id"] for r in MIXED])
        self.assertEqual(
            self.db.execute("SELECT COUNT(*) FROM arch.Consent__live")
            .fetchall(), [(len(MIXED),)])

    def test_the_report_counts_what_was_retired(self):
        self.imported(*MIXED)
        # 2, 4 and 10 lost a ranking; 5 had no decision. 6 and 12 were
        # not live to begin with.
        self.assertCounts(duplicate=3, undecided=1, null_deleted=1,
                          legacy_deleted=1)
        self.assertEqual(
            [r[0] for r in self.arrived() if r[1]], [2, 4, 5, 6, 10, 12])

    def test_a_rebuild_replaces_the_helper(self):
        # a resumed run builds it again, over the one that is there
        self.stage(row(1), row(2))
        self.build()
        self.db.execute("DELETE FROM src.Consent WHERE id = 2")
        self.build()
        self.assertEqual(
            self.db.execute("SELECT id, deleted FROM arch.Consent__live")
            .fetchall(), [(1, 0)])

    def test_without_the_ranking_the_copy_violates_the_key(self):
        # the negative control: the same rows through the shipped
        # manifest's shape, where every row arrives live
        self.stage(*MIXED)
        unranked, _notes = o19etl.effective_entry(
            "Consent", dict(o19map_schema.TABLES["Consent"],
                            cols=list(SOURCE_COLS)),
            self.src_cols, {"Consent", "consentType"})
        with self.assertRaises(sqlite3.IntegrityError):
            self.copy(unranked)

    def test_the_duplicate_check_finds_nothing_after_the_import(self):
        self.imported(*MIXED)
        self.assertEqual(self.duplicates(), 0)

    def test_the_duplicate_check_counts_the_pairs_that_repeat(self):
        # what UNIQUE_CHECKS=0 can let through: no key to stop them
        self.db.execute("DROP INDEX dst.uq_consent_live_type")
        self.stage(*MIXED)
        self.db.execute(
            "INSERT INTO dst.Consent (id, demographic_no, "
            "consent_type_id, optout, deleted) SELECT id, demographic_no, "
            "consent_type_id, IFNULL(optout, 1), IFNULL(deleted, 1) "
            "FROM src.Consent")
        # patients 100 and 200 each hold two live rows of type 1.
        # Patient 400's rows are of different types as written here,
        # and rows without a patient never count.
        self.assertEqual(self.duplicates(), 2)

    def test_the_value_check_agrees_with_the_copy(self):
        self.imported(*MIXED)
        self.assertEqual(self.mismatches(), 0)

    def test_the_value_check_sees_a_row_made_live_afterwards(self):
        # the check covers `deleted`: a retired row flipped back is a
        # mismatch, which is what keeping Consent out of
        # POST_ETL_REWRITTEN buys
        self.imported(row(1, deleted=1), row(2, patient=200))
        self.db.execute("UPDATE dst.Consent SET deleted = 0 WHERE id = 1")
        self.assertEqual(self.mismatches(), 1)

    def test_the_staged_dump_is_never_written(self):
        self.stage(*MIXED)
        before = self.db.execute(
            "SELECT * FROM src.Consent ORDER BY id").fetchall()
        self.build()
        self.copy()
        self.assertEqual(self.db.execute(
            "SELECT * FROM src.Consent ORDER BY id").fetchall(), before)


class TestTheShippedManifest(unittest.TestCase):

    """A manifest generated before the ruling: no `deleted`, no
    expressions. The import must do exactly what it did."""

    def test_an_entry_without_the_expression_asks_for_no_helper(self):
        self.assertFalse(o19etl.consent_live_ranked(
            {"class": "copy", "cols": list(SOURCE_COLS),
             "fk_remap": {"consent_type_id": "consentType"}}))

    def test_an_entry_with_the_expression_does(self):
        if not OVERRIDES.is_file():
            self.skipTest("overlay not in this checkout")
        self.assertTrue(o19etl.consent_live_ranked(ranked_entry()))

    def test_a_ranked_copy_cannot_be_built_without_the_archive(self):
        if not OVERRIDES.is_file():
            self.skipTest("overlay not in this checkout")
        with self.assertRaisesRegex(ValueError, "deleted"):
            o19etl.copy_statement("Consent", ranked_entry(), SRC, DST,
                                  DST_COLS)

    def test_the_copy_of_such_an_entry_is_what_it_was(self):
        entry = {"class": "copy", "cols": list(SOURCE_COLS),
                 "fk_remap": {"consent_type_id": "consentType"}}
        self.assertEqual(
            o19etl.copy_statement("Consent", entry, SRC, DST, DST_COLS,
                                  None, None, ARCH),
            "INSERT INTO `dst`.`Consent` (`id`, `demographic_no`, "
            "`consent_type_id`, `explicit`, `optout`, `last_entered_by`, "
            "`consent_date`, `optout_date`, `edit_date`) SELECT "
            "IFNULL(s.`id`, 0), s.`demographic_no`, (SELECT m.new_id FROM "
            "`arch`.`consentType__idmap` m WHERE m.old_id = "
            "s.`consent_type_id`), IFNULL(s.`explicit`, 0), "
            "IFNULL(s.`optout`, 0), s.`last_entered_by`, "
            "NULLIF(s.`consent_date`, '0000-00-00 00:00:00'), "
            "NULLIF(s.`optout_date`, '0000-00-00 00:00:00'), "
            "NULLIF(s.`edit_date`, '0000-00-00 00:00:00') "
            "FROM `src`.`Consent` s")


if __name__ == "__main__":
    unittest.main()
