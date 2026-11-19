#!/usr/bin/env bash
#
# test-encoder-null-safety.sh
#
# Smoke test for scripts/lint/check-encoder-null-safety.py.
#
# Invokes the lint script as an actual subprocess (not via importlib) so a
# regression in argv handling, main() flow, or exit-code logic gets caught.
#
# Fixtures:
#   - encoder-class-c-positive.jsp      : MUST be flagged (lint exits non-zero)
#   - encoder-class-c-tag-positive.jsp  : MUST be flagged (lint exits non-zero)
#   - encoder-class-c-negative.jsp      : MUST NOT be flagged (lint exits zero)
#   - encoder-el-nested-positive.jsp    : e:forXxx calls the old
#                                         expression-leading pattern missed
#                                         (#5477); MUST be flagged, Class B only
#   - encoder-el-nested-negative.jsp    : near misses (wrapper nested in a
#                                         wrapper, escaped EL, comments);
#                                         MUST NOT be flagged
#   - encoder-taglib-positive.jsp       : OWASP encoder taglib declared; MUST
#                                         be flagged as Class D even when the
#                                         file is on the allowlist
#   - encoder-taglib-xml-positive.jspx  : XML-syntax and legacy-URI
#                                         declarations; MUST be flagged
#
# Each fixture is staged into a fresh temp `src/main/webapp/WEB-INF/...`
# layout so the lint's hard-coded JSP_ROOT walk picks it up. We can't
# point the lint at a different root without forking the script, but
# staging fixtures into a temp tree lets us exercise the real entrypoint.
#
# Exit 0 = lint behavior is correct, 1 = regression in the lint itself.
#
# This is the regression armor for the d2db61d4 bug class — if the
# Class C regex breaks (e.g., a future edit drops `\$\{` escapes), the
# lint would silently report PASS on real violations. This test catches
# that.
#
# @since 2026-04-25

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE_DIR="$SCRIPT_DIR/test-fixtures"
LINT_SCRIPT="$SCRIPT_DIR/check-encoder-null-safety.py"

fails=0

stage_and_run() {
  local fixture_name="$1"           # e.g. "encoder-class-c-positive.jsp"
  local expected_exit="$2"          # "0" or "1"
  # Text that must appear in stdout (empty = no check). Several required
  # snippets may be given, one per line.
  local expected_stdout_match="$3"
  local label="$4"
  # Optional: path the fixture is staged at, relative to src/main/webapp,
  # and an allowlist glob (relative to the staged repository root).
  local staged_name="${5:-billingONFixture.jsp}"
  local allowlist_entry="${6:-}"

  local stage_root
  stage_root="$(mktemp -d)"
  trap 'rm -rf "$stage_root"' RETURN

  # Replicate the lint's hard-coded REPO_ROOT/src/main/webapp layout so the
  # walk hits the fixture file. The lint also reads the allowlist relative
  # to its own directory, so we run with cwd somewhere harmless and the
  # default allowlist (we don't want to change CI behavior).
  local jsp_target="$stage_root/src/main/webapp/$staged_name"
  mkdir -p "$(dirname "$jsp_target")"
  cp "$FIXTURE_DIR/$fixture_name" "$jsp_target"

  # The lint resolves REPO_ROOT relative to its own location:
  # `Path(__file__).resolve().parent.parent.parent`.
  # We need to invoke a copy of the lint inside the staged tree so its
  # parent-walking lands on $stage_root.
  mkdir -p "$stage_root/scripts/lint"
  cp "$LINT_SCRIPT" "$stage_root/scripts/lint/check-encoder-null-safety.py"
  # Empty allowlist so no fixture is silently exempted, unless the case
  # deliberately allowlists its fixture.
  printf '%s\n' "$allowlist_entry" > "$stage_root/scripts/lint/encode-null-safety-allowlist.txt"

  local out
  local rc
  out=$(python3 "$stage_root/scripts/lint/check-encoder-null-safety.py" 2>&1)
  rc=$?

  local pass_label="PASS"
  local mode="ok"
  if [[ "$rc" != "$expected_exit" ]]; then
    pass_label="FAIL"
    mode="exit_mismatch"
  elif [[ -n "$expected_stdout_match" ]]; then
    local needle
    while IFS= read -r needle; do
      if [[ -n "$needle" && "$out" != *"$needle"* ]]; then
        pass_label="FAIL"
        mode="output_mismatch"
      fi
    done <<< "$expected_stdout_match"
  fi

  if [[ "$pass_label" == "PASS" ]]; then
    echo "PASS: $label (exit=$rc)"
  else
    echo "FAIL: $label ($mode)"
    echo "  expected exit: $expected_exit  got: $rc"
    if [[ -n "$expected_stdout_match" ]]; then
      echo "  expected output to contain: $expected_stdout_match"
    fi
    echo "  --- stdout/stderr ---"
    echo "$out" | sed 's/^/    /'
    fails=$((fails+1))
  fi

  rm -rf "$stage_root"
  trap - RETURN
}

stage_and_run \
  "encoder-class-c-positive.jsp" \
  "1" \
  "Class C — forHtmlContent in attribute context" \
  "positive Class-C fixture flagged"

stage_and_run \
  "encoder-class-c-tag-positive.jsp" \
  "1" \
  "Class C — forHtmlContent in attribute context" \
  "positive Class-C tag fixture flagged"

stage_and_run \
  "encoder-class-c-negative.jsp" \
  "0" \
  "" \
  "negative fixture not flagged"

# Regression for #5477: ${carlos:forJavaScript(e:forUriComponent(x))} in
# DisplayWaitingList.jsp passed the old expression-leading EL pattern.
stage_and_run \
  "encoder-el-nested-positive.jsp" \
  "1" \
  "Class B — \${e:forXxx} EL function]: src/main/webapp/billingONFixture.jsp (6 site(s))
Total violating files                      : 1" \
  "nested and deferred e:forXxx EL calls flagged as Class B only"

stage_and_run \
  "encoder-el-nested-negative.jsp" \
  "0" \
  "" \
  "nested-call near misses not flagged"

# Class D ignores the allowlist: the jar is gone, so the page cannot translate.
stage_and_run \
  "encoder-taglib-positive.jsp" \
  "1" \
  "Class D — OWASP encoder taglib declared]: src/main/webapp/WEB-INF/jsp/legacy/allowlisted.jsp
Declares owasp.encoder.jakarta.
Total violating files                      : 1" \
  "OWASP taglib declaration flagged on an allowlisted path" \
  "WEB-INF/jsp/legacy/allowlisted.jsp" \
  "src/main/webapp/WEB-INF/jsp/legacy/*.jsp"

stage_and_run \
  "encoder-taglib-xml-positive.jspx" \
  "1" \
  "Class D — OWASP encoder taglib declared]: src/main/webapp/billingONFixture.jspx
Declares https://www.owasp.org/index.php/OWASP_Java_Encoder_Project, urn:jsptld:owasp.encoder.jakarta.advanced." \
  "XML-syntax and legacy-URI OWASP taglib declarations flagged in a .jspx" \
  "billingONFixture.jspx"

stage_and_run \
  "encoder-taglib-xml-negative.jspx" \
  "0" \
  "" \
  "XML comments do not declare taglibs or execute EL" \
  "billingONFixture.jspx"

stage_and_run \
  "encoder-taglib-xml-cdata.jspx" \
  "1" \
  "Class B — \${e:forXxx} EL function]: src/main/webapp/billingONFixture.jspx (1 site(s))" \
  "EL in JSP document CDATA is checked" \
  "billingONFixture.jspx"

if ! python3 - "$SCRIPT_DIR/../../.claude/hooks/validate-owasp-encoding.py" <<'PY'
import json
import subprocess
import sys

cases = (
    ('<carlos:encode value="${param.foo}" context="htmlAttribute"/>', 0),
    ('<carlos:encode context="htmlAttribute" value="prefix-${param.foo}"/>', 0),
    ('<carlos:encode value="fixed" context="${param.foo}"/>', 2),
    ('<carlos:encode value="${param.foo}" context="htmlAttribute"/>${param.foo}', 2),
    ('<span>${param.foo}</span>', 2),
)
for markup, expected in cases:
    payload = {"tool_name": "Write", "tool_input": {"file_path": "fixture.jsp", "content": markup}}
    result = subprocess.run([sys.executable, sys.argv[1]], input=json.dumps(payload), text=True, capture_output=True)
    assert result.returncode == expected and "Error in OWASP validation hook" not in result.stderr, result.stderr
print("PASS: the encoder hook checks exactly the CARLOS value attribute")
PY
then
  fails=$((fails+1))
fi

# The hook sees only the edited text. Jasper discards JSP comments, so naming the
# retired taglib or an e:forXxx call inside one must not block an edit; the same
# text outside a JSP comment, or inside an HTML comment (whose EL still runs), must.
if ! python3 - "$SCRIPT_DIR/../../.claude/hooks/validate-owasp-encoding.py" <<'PY'
import json
import subprocess
import sys

# labDisplay.jsp lines 454-455, verbatim.
LAB_DISPLAY_COMMENT = (
    "        <%-- Pre-declare i18n messages used in JavaScript so they can be safely embedded\n"
    "        in JavaScript string literals using OWASP ${e:forJavaScript(msgComment)} encoding --%>"
)
cases = (
    (LAB_DISPLAY_COMMENT, 0),
    (LAB_DISPLAY_COMMENT.split("\n")[1], 0),                     # edit ends a comment opened before it
    ("<%-- see ${e:forHtml(x)} for the old form", 0),            # edit opens a comment closed after it
    ("<%-- was owasp.encoder.jakarta.advanced (#5477) --%>", 0),
    ("<%-- ${param.foo} --%>", 0),
    ("<%-- note --%>${e:forHtml(x)}", 2),
    ("<!-- ${e:forHtml(x)} -->", 2),
    ("${e:forHtml(x)}<% count--%>", 2),                         # a scriptlet's --%> closes no comment
    ('<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>', 2),
    ("<%-- note --%><span>${param.foo}</span>", 2),
)
for markup, expected in cases:
    payload = {"tool_name": "Edit", "tool_input": {"file_path": "fixture.jsp", "old_string": "x", "new_string": markup}}
    result = subprocess.run([sys.executable, sys.argv[1]], input=json.dumps(payload), text=True, capture_output=True)
    assert result.returncode == expected and "Error in OWASP validation hook" not in result.stderr, \
        f"{markup!r}: expected {expected}, got {result.returncode}\n{result.stderr}"
print("PASS: the encoder hook ignores JSP comments and still checks the code around them")
PY
then
  fails=$((fails+1))
fi

if [[ $fails -eq 0 ]]; then
  echo "All encoder-null-safety lint fixtures pass."
  exit 0
else
  echo "$fails fixture failure(s)."
  exit 1
fi
