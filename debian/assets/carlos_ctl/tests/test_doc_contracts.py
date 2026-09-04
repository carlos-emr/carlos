# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""Prose that must agree with the code, asserted rather than reviewed.

A documentation pass fixes what is wrong today; this file is what stops
it going wrong again. Each contract derives the truth from the code and
requires the operator-facing text to match, so adding an --accept class
or a run artifact without documenting it fails the build instead of
shipping.

Every contract here earned its place: each one had a live discrepancy
when it was written -- an accept class missing from two surfaces that
both claimed to enumerate them, and two run artifacts the man page's
FILES section did not mention.

Run (from debian/assets):
    python3 -m unittest discover -v -s carlos_ctl/tests -t .
"""

import re
import unittest
from pathlib import Path

from carlos_ctl import o19_preflight, o19import

ROOT = Path(__file__).resolve().parents[4]
MAN_PAGE = ROOT / "debian" / "carlos-ctl.8"
GUIDE = ROOT / "docs" / "o19-import-deb.md"

#: man-page markup a plain-text search has to see through: `.B x`,
#: `.BR x " y"`, hyphens escaped as `\-`, and line breaks inside a list
MAN_NOISE = re.compile(r"^\.[A-Za-z]+ ?|[\\\"]")


def man_text():
    """The man page as searchable text: roff requests and escapes
    stripped, lines joined, so `.B archived-forms` matches the plain
    string."""
    lines = [MAN_NOISE.sub("", ln)
             for ln in MAN_PAGE.read_text(encoding="utf-8").splitlines()]
    return " ".join(" ".join(lines).split())


def man_sign_off_classes():
    """The classes the man page enumerates under "Sign-off classes for
    --accept", read as a list rather than searched for as substrings: a
    substring search cannot see an entry the code no longer has."""
    raw = MAN_PAGE.read_text(encoding="utf-8")
    block = raw.split("Sign-off classes for", 1)[1].split("\n.RE", 1)[0]
    return re.findall(r"^\.B ([a-z][a-z-]+)$", block, re.M)


def man_assessment_classes():
    """The classes the standalone preflight paragraph names."""
    text = man_text()
    listed = text.split("classes an assessment can acknowledge:", 1)[1]
    listed = listed.split(".SH", 1)[0].split(". ", 1)[0]
    return [c for c in re.split(r"[,\s]+and\s+|,\s*", listed.strip(" ."))
            if c]


@unittest.skipUnless(MAN_PAGE.is_file(), "man page not in this checkout")
class TestAcceptClassesAreDocumented(unittest.TestCase):
    """Both surfaces that enumerate the sign-off classes must enumerate
    all of them. `charset-repair` was missing from both when this was
    written, while the man page said "the five classes" of six."""

    def test_every_preflight_class_is_in_the_module_docstring(self):
        doc = o19_preflight.__doc__ or ""
        missing = [i for i in o19_preflight.ACCEPT_IDS if i not in doc]
        self.assertEqual(missing, [])

    def test_every_preflight_class_is_in_the_man_page(self):
        text = man_text()
        missing = [i for i in o19_preflight.ACCEPT_IDS if i not in text]
        self.assertEqual(missing, [])

    def test_every_import_class_is_in_the_man_page(self):
        text = man_text()
        missing = [i for i in o19import.ACCEPT_CLASSES if i not in text]
        self.assertEqual(missing, [])

    def test_every_preflight_class_is_in_the_guide(self):
        # the contract read the man page only, so the guide drifted
        # unnoticed: it named five classes to the man page's six, and an
        # operator reading the guide could not learn how to clear the
        # charset-mojibake blocker
        if not GUIDE.is_file():
            self.skipTest("guide not present in this checkout")
        text = GUIDE.read_text(encoding="utf-8")
        for name in o19_preflight.ACCEPT_IDS:
            self.assertIn("`{0}`".format(name), text)

    def test_the_guide_counts_the_assessment_classes_it_lists(self):
        # a bare count in prose is the half that rots first
        if not GUIDE.is_file():
            self.skipTest("guide not present in this checkout")
        text = GUIDE.read_text(encoding="utf-8")
        words = {2: "two", 3: "three", 4: "four", 5: "five", 6: "six",
                 7: "seven", 8: "eight", 9: "nine"}
        n = len(o19_preflight.ACCEPT_IDS)
        self.assertIn("only {0} of them".format(words[n]), text,
                      "the guide does not say the assessment evaluates "
                      "{0} classes".format(words[n]))
        for wrong in (w for k, w in words.items() if k != n):
            self.assertNotIn("only {0} of them".format(wrong), text)

    def test_the_man_page_counts_the_classes_it_lists(self):
        # "the six classes an assessment can acknowledge" -- a number
        # spelled in prose goes stale the moment a class is added
        words = ("zero", "one", "two", "three", "four", "five", "six",
                 "seven", "eight", "nine", "ten", "eleven", "twelve")
        n = len(o19_preflight.ACCEPT_IDS)
        self.assertLess(n, len(words),
                        "spell {0} here before adding the class".format(n))
        self.assertIn("knows only the {0} classes".format(words[n]),
                      man_text())

    def test_the_import_sign_off_list_is_exactly_the_code_s(self):
        """The man page's own enumeration, parsed as an enumeration.

        A stale entry sends an operator to a flag argparse rejects; a
        missing one hides a sign-off they are entitled to refuse."""
        self.assertEqual(sorted(man_sign_off_classes()),
                         sorted(o19import.ACCEPT_CLASSES))

    def test_the_assessment_sign_off_list_is_exactly_the_code_s(self):
        listed = man_assessment_classes()
        self.assertEqual(sorted(listed), sorted(o19_preflight.ACCEPT_IDS))


@unittest.skipUnless(MAN_PAGE.is_file(), "man page not in this checkout")
class TestRunArtifactsAreDocumented(unittest.TestCase):
    """Every file `--cleanup` retires is operator-facing by definition:
    it is a thing the operator will find, or miss, in the workspace."""

    def test_every_run_file_is_named_somewhere_an_operator_reads(self):
        text = man_text()
        guide = GUIDE.read_text(encoding="utf-8") if GUIDE.is_file() else ""
        missing = [name for name in o19import.RUN_FILES
                   if name not in text and name not in guide]
        self.assertEqual(missing, [])

    def test_the_man_pages_files_section_names_the_reports(self):
        # the two the operator is sent to first
        text = man_text()
        for name in ("report.txt", "import-report.txt",
                     "import-report.json"):
            self.assertIn(name, text)

    def test_the_credentials_file_is_documented_as_not_retired(self):
        # deliberately NOT in RUN_FILES; the man page has to say so, or
        # an operator looking for the break-glass password after cleanup
        # will look for a .completed- suffix that is not there
        self.assertNotIn("admin-credentials.txt", o19import.RUN_FILES)
        self.assertIn("admin-credentials.txt", man_text())


class TestPreservationIsDescribedConsistently(unittest.TestCase):
    """The prefix requirement B introduced appears in three operator
    surfaces; a rename in the code must not leave any of them behind."""

    def test_the_prefix_the_code_uses_is_the_one_the_docs_name(self):
        from carlos_ctl import o19etl
        prefix = o19etl.ARCHIVED_PREFIX
        self.assertIn(prefix, man_text())
        if GUIDE.is_file():
            self.assertIn(prefix, GUIDE.read_text(encoding="utf-8"))

    def test_the_preflight_no_longer_promises_the_rows_are_dropped(self):
        # the sentence a clinic signed off on: it said removed-module
        # rows are "dropped with no archive and no CSV export"
        source = Path(o19_preflight.__file__).read_text(encoding="utf-8")
        self.assertNotIn("dropped with no archive", source)
        self.assertNotIn("preserved in o19_archive shadow tables", source)


class TestFixtureProvenanceMatchesTheManifest(unittest.TestCase):

    """PROVENANCE.md is the instruction a future re-vendorer follows. It
    names the placeholder account names the fixture keeps byte-identical
    and asserts the toolchain still treats those keys as credential-
    shaped. A review round found those two statements contradicting each
    other; this keeps them from drifting apart again."""

    #: the placeholder account names the provenance record calls out, as
    #: `key=value` inside the parenthetical
    PLACEHOLDER_RE = re.compile(r"`([A-Za-z_][\w.]*)=[^`]*`")

    def test_placeholder_account_keys_are_credential_shaped(self):
        prov = (ROOT / "scripts" / "migration" / "o19" / "fixtures"
                / "PROVENANCE.md")
        if not prov.is_file():
            self.skipTest("fixture provenance not in this checkout")
        from carlos_ctl import o19map_props
        text = prov.read_text(encoding="utf-8")
        clause = text.split("placeholder account names", 1)
        self.assertEqual(len(clause), 2,
                         "PROVENANCE.md no longer names placeholder "
                         "account names -- the contract has lost its "
                         "subject and would pass vacuously")
        names = self.PLACEHOLDER_RE.findall(clause[1].split(".", 1)[0])
        self.assertTrue(names, "no `key=value` placeholders parsed")
        for key in names:
            self.assertIn(
                key, o19map_props.SECRET_DEFAULT_KEYS,
                "PROVENANCE.md calls {0} a placeholder account name, but "
                "the manifest does not classify it credential-shaped, so "
                "its stock value would ship in O19_DEFAULTS".format(key))


class TestNamedJavaGuardsExist(unittest.TestCase):

    """The guide points a reader at the maven test that fails the build
    on a positional INSERT in a new migration. That name is a promise
    the reader will grep for -- and it has already been broken once, by
    renaming the class to `...UnitTest` so Surefire would select it
    while the guide kept the old name. Derive the truth from disk."""

    #: `WordTest`-shaped identifiers in backticks: Java test classes the
    #: prose names, as opposed to python modules or SQL identifiers
    JAVA_TEST_RE = re.compile(r"`([A-Z][A-Za-z0-9]*Test)`")

    def test_every_java_test_the_guide_names_is_on_disk(self):
        if not GUIDE.is_file():
            self.skipTest("guide not present in this checkout")
        java_root = ROOT / "src" / "test" / "java"
        if not java_root.is_dir():
            self.skipTest("java sources not present in this checkout")
        named = sorted(set(self.JAVA_TEST_RE.findall(
            GUIDE.read_text(encoding="utf-8"))))
        # a contract over an empty set proves nothing: the guide names
        # this guard, and a rewrite that drops the reference entirely
        # should fail here rather than pass silently
        self.assertTrue(named, "the guide names no java test class")
        for name in named:
            self.assertTrue(
                list(java_root.rglob(name + ".java")),
                "the guide names {0}, which does not exist under "
                "src/test/java".format(name))


if __name__ == "__main__":
    unittest.main()
