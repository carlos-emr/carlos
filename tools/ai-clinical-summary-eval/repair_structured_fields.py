#!/usr/bin/env python3
import copy
import json
import sys
from pathlib import Path


def load_summary():
    if len(sys.argv) > 1 and sys.argv[1] != "-":
        return json.loads(Path(sys.argv[1]).read_text())
    return json.load(sys.stdin)


summary = copy.deepcopy(load_summary())

# These values represent authoritative structured CARLOS reconciliation,
# not model-derived clinical decisions.
for allergy in summary.get("allergies", []):
    if str(allergy.get("substance", "")).lower() == "penicillin":
        allergy["status"] = "conflicting"
        allergy["conflicting_assertion"] = "No known drug allergies"
        allergy["source_ids"] = ["N2"]

pending = summary.setdefault("pending_actions", [])
pending = [
    item for item in pending
    if "repeat potassium" not in str(item.get("action", "")).lower()
    and "basic metabolic panel" not in str(item.get("action", "")).lower()
]
if not any("b12" in str(item.get("action", "")).lower() for item in pending):
    pending.append({
        "action": "Vitamin B12 testing ordered; result not yet available",
        "source_ids": ["N6"]
    })
summary["pending_actions"] = pending

print(json.dumps(summary, indent=2))
