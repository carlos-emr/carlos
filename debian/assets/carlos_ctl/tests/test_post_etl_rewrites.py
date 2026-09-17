# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""`POST_ETL_REWRITTEN` — the tables P7 deliberately does not compare.

`copy_content_parity` and `merge_content_parity` pair a migrated row
with its live twin and compare values. A later import step that
REWRITES those rows makes the comparison wrong by design, so the two
checks consult one shared list and report the table as
`NOT CHECKED — <table>: <why>` instead.

The list is therefore load-bearing in both directions. Missing an entry
turns P7 red on every clinic, and the operator's only way past that is
`--accept content-migration`, which acknowledges rather than fixes. A
stale entry is quieter and no better: it keeps a checkable table
permanently unchecked.

So this module does not trust the list. It re-derives it from the
import's own source on every run — which is how `HRMDocument` was found
after the list was first written by hand.

Run (from debian/assets):
    python3 -m unittest carlos_ctl.tests.test_post_etl_rewrites -v
"""

import io
import re
import unittest

from carlos_ctl import o19docs, o19etl, o19map_schema, o19roles


class TestEveryEntryIsUsable(unittest.TestCase):

    def test_every_entry_names_a_table_a_value_check_would_visit(self):
        """Only the copy and merge classes are compared by value. An
        entry for any other class excuses nothing and hides a mistake
        about which check owns the table."""
        for table in sorted(o19etl.POST_ETL_REWRITTEN):
            self.assertIn(
                o19map_schema.TABLES[table]["class"], ("copy", "merge"),
                "{0} is neither copy nor merge class, so no value check "
                "would have looked at it".format(table))

    def test_every_entry_gives_a_reason_an_operator_can_read(self):
        """The reason is what the report prints. "skipped" is not an
        answer an operator can act on."""
        for table, why in sorted(o19etl.POST_ETL_REWRITTEN.items()):
            self.assertTrue(
                why and len(why) > 20,
                "{0} has no usable reason: {1!r}".format(table, why))


class TestTheListMatchesTheImport(unittest.TestCase):
    """Read the import's source and compare it against the list."""

    #: The modules an import runs that write to the TARGET schema.
    #: `dbops.py` is deliberately absent: its one UPDATE is the
    #: carlos-emr package's own break-glass credential reset, which no
    #: import phase calls.
    MODULES = (o19etl, o19roles, o19docs)

    #: Tables whose UPDATE runs BEFORE the ETL fills them, so what the
    #: ETL wrote is still what the target holds when P7 looks. File a
    #: table here rather than in POST_ETL_REWRITTEN — an entry there
    #: stops the check running at all.
    PRE_ETL_UPDATED = {}

    #: `UPDATE `{0}`.tbl` / `UPDATE `{0}`.`tbl``. A statement whose
    #: table is itself a placeholder does not match, and does not need
    #: to: the merge's `archived_backfill_statement` is the only one,
    #: and `merge_content_parity` models it rather than skipping it.
    PATTERN = re.compile(r"UPDATE\s+`\{\d\}`\.`?(\w+)`?")

    def rewritten_tables(self):
        found = {}
        for mod in self.MODULES:
            with io.open(mod.__file__, encoding="utf-8") as fh:
                src = fh.read()
            for name in self.PATTERN.findall(src):
                entry = o19map_schema.TABLES.get(name)
                if entry and entry["class"] in ("copy", "merge"):
                    found.setdefault(name, set()).add(
                        mod.__name__.rsplit(".", 1)[-1])
        return found

    def test_the_scan_finds_the_rewrites_it_is_meant_to_find(self):
        """A regex that matched nothing would make both assertions below
        pass vacuously."""
        found = self.rewritten_tables()
        self.assertIn("security", found)
        self.assertIn("o19etl", found["security"])
        self.assertIn("secObjPrivilege", found)

    def test_every_rewritten_table_is_named_or_ruled_pre_etl(self):
        unnamed = sorted(
            "{0} (rewritten in {1})".format(t, ", ".join(sorted(mods)))
            for t, mods in self.rewritten_tables().items()
            if t not in o19etl.POST_ETL_REWRITTEN
            and t not in self.PRE_ETL_UPDATED)
        self.assertEqual(
            unnamed, [],
            "copy/merge-class table(s) an import rewrites but "
            "POST_ETL_REWRITTEN does not name. P7 would report a "
            "mismatch on every clinic. Add each with its reason, or — "
            "if the UPDATE runs BEFORE the ETL writes the table — to "
            "this test's PRE_ETL_UPDATED with why:\n  "
            + "\n  ".join(unnamed))

    def test_no_named_table_has_stopped_being_rewritten(self):
        """A stale entry is not harmless: it keeps a table the import no
        longer rewrites permanently unchecked."""
        found = self.rewritten_tables()
        stale = sorted(t for t in o19etl.POST_ETL_REWRITTEN
                       if t not in found)
        self.assertEqual(
            stale, [],
            "POST_ETL_REWRITTEN names table(s) no import module "
            "rewrites any more; each is now excluded from P7's value "
            "check for no reason: " + ", ".join(stale))


if __name__ == "__main__":
    unittest.main()
