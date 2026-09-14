# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 CARLOS Contributors
"""WAF administration. Deb-only namespace: in carlos-podman the WAF is a
container tuned through env knobs; here it is host nginx + libmodsecurity3,
so the tool owns the mode flips and the triage view."""

import json
import os
import re

from .util import CONF_DIR, die, log, need_root, run, warn

MAIN = os.path.join(CONF_DIR, "modsecurity", "main.conf")
AUDIT = "/var/log/carlos-emr/modsec/modsec_audit.log"


def _engine() -> str:
    try:
        with open(MAIN, encoding="utf-8", errors="replace") as fh:
            for line in fh:
                m = re.match(r"^SecRuleEngine\s+(\S+)", line)
                if m:
                    return m.group(1)
    except OSError as e:
        die(f"cannot read the WAF policy: {e} — reinstall carlos-emr to restore it")
    return "?"


def _set_engine(value: str) -> None:
    """Atomic replace, never truncate-in-place: nginx workers (and a crash
    between truncate and write) must only ever see the old policy or the new
    one, not an empty or partial file guarding the front door."""
    try:
        with open(MAIN, encoding="utf-8", errors="replace") as fh:
            lines = fh.read().split("\n")
    except OSError as e:
        die(f"cannot read the WAF policy: {e} — reinstall carlos-emr to restore it")
    hits = sum(1 for line in lines if re.match(r"^SecRuleEngine\s", line))
    if hits == 0:
        # Rewriting nothing and then reporting the new mode would leave the
        # file and this tool's claim disagreeing with the running policy.
        die(f"no active SecRuleEngine directive found in {MAIN} — the policy file "
            "is damaged; reinstall carlos-emr or restore it from backup")
    out = [f"SecRuleEngine {value}" if re.match(r"^SecRuleEngine\s", line) else line
           for line in lines]
    text = "\n".join(out)
    if not text.endswith("\n"):
        text += "\n"
    st = os.stat(MAIN)
    tmp = MAIN + ".tmp"
    with open(tmp, "w", encoding="utf-8") as fh:
        fh.write(text)
        fh.flush()
        os.fsync(fh.fileno())
    os.chmod(tmp, st.st_mode & 0o7777)
    os.chown(tmp, st.st_uid, st.st_gid)
    os.replace(tmp, MAIN)


def _reload_or_rollback(previous_engine: str, context: str) -> None:
    """Config-test first so a typo cannot take the front door down — and on
    failure ROLL THE FILE BACK to the engine value that is actually running,
    so file and engine can never disagree (the file claiming On while the
    workers run DetectionOnly is the worst state this tool can produce)."""
    if run(["nginx", "-t"]).returncode != 0:
        _set_engine(previous_engine)
        die(f"nginx rejects the policy; the file was ROLLED BACK to "
            f"SecRuleEngine {previous_engine} (matching the running engine) — {context}")
    if run(["systemctl", "reload", "nginx"]).returncode != 0:
        _set_engine(previous_engine)
        die(f"nginx accepted the file but the reload FAILED; the file was rolled back "
            f"to SecRuleEngine {previous_engine} (systemctl status nginx)")


def cmd_waf(argv) -> int:
    sub = argv[0] if argv else "status"
    rest = argv[1:]

    if sub == "status":
        print(f"rule engine: {_engine()}")
        print(f"policy:      {MAIN}")
        print(f"exclusions:  {CONF_DIR}/modsecurity/local-exclusions-before-crs.conf (yours; ids 5000-5999)")
        print(f"             {CONF_DIR}/modsecurity/local-exclusions-after-crs.conf  (yours; SecRuleUpdateTarget*)")
        print(f"audit log:   {AUDIT}")
        return 0

    if sub == "detect-only":
        need_root("waf detect-only")
        prev = _engine()
        _set_engine("DetectionOnly")
        _reload_or_rollback(prev, "still blocking. Fix the file and re-run.")
        warn("the WAF is now LOGGING ONLY and blocks nothing. This is a triage mode.")
        warn("collect the false positives with 'carlos-ctl waf tail', add exclusions to")
        warn(f"{CONF_DIR}/modsecurity/local-exclusions-before-crs.conf (or -after-crs.conf),")
        warn("then run 'carlos-ctl waf blocking'. Do not leave a PHI system in this state.")
        return 0

    if sub == "blocking":
        need_root("waf blocking")
        prev = _engine()
        _set_engine("On")
        _reload_or_rollback(prev, "the WAF is still in DetectionOnly. Fix the file "
                            "and re-run 'carlos-ctl waf blocking'.")
        log("the WAF is blocking again")
        return 0

    if sub == "reload":
        need_root("waf reload")
        # No engine edit to roll back here; a failed test just leaves the
        # operator's hand-edit in place with the old rules still serving.
        if run(["nginx", "-t"]).returncode != 0:
            die("nginx rejects the edited files; the running rules still serve — fix and re-run")
        if run(["systemctl", "reload", "nginx"]).returncode != 0:
            die("nginx accepted the files but the reload failed (systemctl status nginx)")
        log("WAF rules reloaded")
        return 0

    if sub == "tail":
        # Query strings are stripped before display: on this application they
        # carry PHI-correlating identifiers, and the point of this verb is to
        # be safe to read over a shoulder. The full record stays in the
        # root-only audit file.
        # Validated, not guessed at: 'tail abc' used to silently show 200
        # lines, and 'tail 0' dumped [-0:] — the ENTIRE audit log.
        n = 200
        if rest:
            if not rest[0].isdigit() or int(rest[0]) < 1:
                die(f"waf tail takes a positive line count, got '{rest[0]}'")
            n = int(rest[0])
        try:
            with open(AUDIT, encoding="utf-8", errors="replace") as fh:
                lines = fh.readlines()[-n:]
        except OSError:
            die(f"{AUDIT} is unreadable (are you root or in group adm?)")
        for line in lines:
            try:
                rec = json.loads(line)
            except ValueError:
                continue
            t = rec.get("transaction", {})
            uri = (t.get("request", {}).get("uri") or "?").split("?")[0]
            for m in t.get("messages", []):
                det = m.get("details", {})
                match = det.get("match") or ""
                # Redact the matched VALUE: on a false positive it is the
                # content of a clinical field. Rule id + variable name +
                # message are what an exclusion needs; the value stays in the
                # root-only audit file for the rare case it matters.
                match = re.sub(r"\(Value: .*$", "(value redacted — see the audit file)",
                               match)[:160]
                print(f"{det.get('ruleId', '?')}  {match}")
                print(f"      uri: {uri}")
                print(f"      msg: {m.get('message', '')}")
        return 0

    die(f"unknown 'waf' subcommand: {sub} (status|detect-only|blocking|reload|tail [lines])")
