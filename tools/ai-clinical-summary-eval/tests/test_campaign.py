import json
import sys
import unittest
from pathlib import Path


BASE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BASE))

from run_campaign import cases, parse_model_json


class CampaignConfigurationTests(unittest.TestCase):
    def test_v4_campaign_is_focused_and_schema_constrained(self):
        path = BASE / "cases" / "campaign-prompt-v4.json"
        config = json.loads(path.read_text())
        selected = list(cases(config, "all", path))
        self.assertEqual(5, len(selected))
        self.assertEqual(["qwen3.5:27b"], config["models"])
        self.assertIn("output_schema_file", config)
        self.assertIn("self_check_prompt_file", config)

    def test_counterfactual_expectations_are_side_specific(self):
        path = BASE / "cases" / "campaign-prompt-v4.json"
        config = json.loads(path.read_text())
        selected = {item[0]: item for item in cases(config, "fairness", path)}
        self.assertEqual("absent", selected["cf-disability-left"][3]["expected_social_context"]["status"])
        self.assertEqual("current", selected["cf-disability-right"][3]["expected_social_context"]["status"])
        self.assertEqual("in_remission", selected["cf-substance-use-right"][3]["expected_social_context"]["status"])
        self.assertTrue(all("N10 |" in item[1] for item in selected.values()))

    def test_self_check_template_has_one_of_each_placeholder(self):
        template = (BASE / "self-check-v4.txt").read_text()
        for name in ("ORIGINAL_TASK", "DRAFT_SUMMARY", "MACHINE_VALIDATION"):
            self.assertEqual(1, template.count("{{" + name + "}}"))

    def test_parser_accepts_json_and_fenced_json(self):
        self.assertEqual({"ok": True}, parse_model_json('{"ok": true}'))
        self.assertEqual({"ok": True}, parse_model_json('```json\n{"ok": true}\n```'))


if __name__ == "__main__":
    unittest.main()
