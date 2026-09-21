# Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later.
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import score_nhs_fixture as scorer


class ClaimDatesTest(unittest.TestCase):
    def test_slash_and_iso_dates_are_normalized(self):
        self.assertEqual({"05/01/26", "06/01/26"},
                         scorer.claim_dates("Started on 05/01/26 and reviewed on 2026-01-06."))
        self.assertEqual({"05/01/26", "06/01/26"}, scorer.claim_dates("Given 05/01/26-06/01/26."))

    def test_a_slash_separated_note_id_list_is_not_a_date(self):
        # Observed 2026-09-21: this list was scored as the dates 10/12/13 and 14/15/18.
        self.assertEqual(set(), scorer.claim_dates(
            "note-10/12/13/14/15/18/20 do not specify the final discharge dose"))

    def test_an_impossible_calendar_date_is_not_a_date(self):
        self.assertEqual(set(), scorer.claim_dates("Ratio 14/15/18 was recorded."))


if __name__ == "__main__":
    unittest.main()
