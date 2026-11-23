# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""The upgrade baseline counts the filter key's logical definitions exactly as the postinst's rewrite does.

``scripts/deb-upgrade-baseline.sh`` records ``cfg.labFilter.logical`` so that ``scripts/deb-upgrade-verify.sh``
expects the one-time HL7_LAB_MEASUREMENT_FILTER rewrite only when the postinst would have done it, that is when
the key is defined ONCE in java.util.Properties terms (continuations joined, the key's escapes decoded). Its
parser is a copy of the postinst's ``filter_definitions``; this holds the two together over a matrix of spellings.

Run (from the repository root):
    python3 -m unittest discover -s debian/assets/tests
"""

import os
import re
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
POSTINST = ROOT / "debian" / "carlos-emr.postinst"
BASELINE = ROOT / "scripts" / "deb-upgrade-baseline.sh"
KEY = "HL7_LAB_MEASUREMENT_FILTER"


def load_parser(source, stop):
    """The python heredoc's definitions up to ``stop``, run with the postinst helper's argv, and the namespace."""
    code = source.split(stop, 1)[0]
    argv, sys.argv = sys.argv, ["helper", "/dev/null", "old", "new", "marker", KEY]
    try:
        namespace = {"__name__": "parser"}
        exec(compile(code, "<heredoc>", "exec"), namespace)
    finally:
        sys.argv = argv
    return namespace["filter_definitions"]


def postinst_parser():
    text = POSTINST.read_text(encoding="utf-8")
    body = re.search(r"python3 - \"\$1\" \"\$2\" \"\$3\" \"\$4\" \"\$\{5:-HL7_LAB_MEASUREMENT_FILTER\}\" <<'PY'\n(.*?)\nPY\n",
                     text, re.S).group(1)
    return load_parser(body, "def open_configuration")


def baseline_parser():
    text = BASELINE.read_text(encoding="utf-8")
    body = re.search(r"python3 - \"\$CARLOS_ETC_DIR/carlos\.properties\" <<'PY'[^\n]*\n(.*?)\nPY\n", text, re.S).group(1)
    return load_parser(body, "try:\n    with open(sys.argv[1]")


SPELLINGS = [
    b"",
    KEY.encode() + b"=stock\n",
    b"  " + KEY.encode() + b"=indented\n",
    KEY.encode() + b" stock-with-space\n",
    KEY.encode() + b":colon\n",
    KEY.encode() + b"\n",
    b"# " + KEY.encode() + b"=comment\n! " + KEY.encode() + b"=comment\n",
    b"HL7_LAB_MEASUREMENT\\_FILTER=escaped underscore\n",
    b"HL7_LAB_\\u004dEASUREMENT_FILTER=unicode escape\n",
    b"HL7_LAB_MEASUREMENT_\\\n   FILTER=continued key\n",
    b"other=value\\\n" + KEY.encode() + b"=inside another value\nafter=1\n",
    KEY.encode() + b"=first\n" + b"HL7_LAB_MEASUREMENT\\_FILTER=second\n",
    KEY.encode() + b"=first\r\n" + KEY.encode() + b"=second\r\n",
    KEY.encode() + b"=a\\\\\n" + KEY.encode() + b"=b\n",            # an escaped backslash does not continue the line
    KEY.encode() + b"=last, no newline",
    KEY.encode() + b"X=different key\n" + KEY.encode() + b"=this one\n",
    b"HL7_LAB_MEASUREMENT\\ FILTER=escaped space is part of another key\n",
    b"\\HL7_LAB_MEASUREMENT_FILTER=leading escape\n",
]


class TestLabFilterLogicalCount(unittest.TestCase):

    def test_the_baseline_counts_definitions_exactly_as_the_postinst_does(self):
        postinst, baseline = postinst_parser(), baseline_parser()
        for content in SPELLINGS:
            expected = [(start, end, logical) for start, end, logical in postinst(content)]
            actual = [(start, end, logical) for start, end, logical in baseline(content)]
            self.assertEqual(actual, expected, content)

    def test_the_matrix_really_has_one_two_and_no_definitions(self):
        baseline = baseline_parser()
        counts = sorted({len(list(baseline(content))) for content in SPELLINGS})
        self.assertEqual(counts, [0, 1, 2])
        # The spellings the plain-text digest cannot see are counted.
        self.assertEqual(len(list(baseline(KEY.encode() + b"=a\nHL7_LAB_MEASUREMENT\\_FILTER=b\n"))), 2)
        self.assertEqual(len(list(baseline(b"HL7_LAB_MEASUREMENT_\\\n   FILTER=x\n"))), 1)
        self.assertEqual(len(list(baseline(b"other=a\\\n" + KEY.encode() + b"=b\n"))), 0)


if __name__ == "__main__":
    unittest.main()
