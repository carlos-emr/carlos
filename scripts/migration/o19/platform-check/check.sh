#!/bin/bash
# Platform checks for the OSCAR 19 import tooling, run on the interpreter
# and shells the package actually ships against (see ./README.md).
#
# Every check here is pure userspace, so a container is enough; nothing
# needs systemd, a service, or a database. Not `set -e`: each check
# reports its own pass/fail and we want the whole picture, not the first
# failure.
# run from the repository root (works mounted anywhere)
[ -f debian/carlos-emr.postinst ] || { echo "run me from the repo root"; exit 2; }
PASS=0; FAIL=0
ok()   { echo "  PASS  $*"; PASS=$((PASS + 1)); }
bad()  { echo "  FAIL  $*"; FAIL=$((FAIL + 1)); }
hdr()  { echo; echo "=== $* ==="; }
# `cmd && ok ... || bad ...` reads as if-then-else but is not one: when
# ok() returns non-zero the bad() branch runs too. Every check routes
# through here instead.
verdict() { # 0-or-1, pass-message, fail-message
  if [ "$1" -eq 0 ]; then ok "$2"; else bad "$3"; fi
}

hdr "platform"
# shellcheck source=/dev/null
. /etc/os-release; echo "  $PRETTY_NAME"
echo "  python3: $(python3 -V 2>&1)"
echo "  bash:    $(bash --version | head -1)"
echo "  dash:    $(dpkg -s dash 2>/dev/null | sed -n 's/^Version: //p')"
echo "  dpkg:    $(dpkg --version | head -1)"
echo "  mariadb: $(mariadb --version 2>/dev/null || echo '(not installed)')"

hdr "maintainer scripts parse under the shells dpkg uses"
for f in debian/carlos-emr.postinst debian/carlos-emr.postrm; do
  [ -f "$f" ] || { bad "$f missing"; continue; }
  if head -1 "$f" | grep -q 'bin/sh'; then SH=dash; else SH=bash; fi
  # dpkg runs #!/bin/sh scripts under dash; check the declared shell, and
  # dash as well when they differ (running dash twice just inflated the
  # count by two)
  if $SH -n "$f" 2>/tmp/e; then ok "$SH -n $f"; else bad "$SH -n $f: $(cat /tmp/e)"; fi
  if [ "$SH" != dash ]; then
    if dash -n "$f" 2>/tmp/e; then ok "dash -n $f"; else bad "dash -n $f: $(cat /tmp/e)"; fi
  fi
done

hdr "postinst OSCAR-19-in-progress gate: full ledger matrix"
# extracted FROM the postinst, never a copy: a duplicated predicate
# silently stops testing the shipped one the moment it changes
PRED=$(sed -n "/&& ! python3 -c '/,/^' \/var\/lib/p" debian/carlos-emr.postinst \
        | sed "1s/.*python3 -c '//" | sed '$d')
[ -n "$PRED" ] || { echo "  FAIL  could not extract the predicate"; exit 1; }
T=$(mktemp -d)
check() { # file-content, expected-rc, label
  printf '%s' "$1" > "$T/s.json"
  python3 -c "$PRED" "$T/s.json"; rc=$?
  if [ "$rc" = "$2" ]; then ok "$3 (rc=$rc)"; else bad "$3 expected rc=$2 got rc=$rc"; fi
}
check '{"phases":{"verify":{"status":"done"}}}'    0 "verify done  -> no gate"
check '{"phases":{"verify":{"status":"started"}}}' 1 "verify started -> GATE"
check '{"phases":{"verify":{}}}'                   1 "verify no status -> GATE"
check '{"phases":{"verify":"done"}}'               1 "verify not a dict -> GATE"
check '{"phases":{"etl":{"status":"done"}}}'        1 "no verify key -> GATE"
check '{"phases":{"stage":{"status":"done"}}}'      0 "assessment leftover (stage only) -> no gate"
check '{"phases":{"stage":{"status":"done"},"etl":{"status":"done"}}}' 1 "real run in progress -> GATE"
check '{"phases":{}}'                              1 "empty phases (mid-P0) -> GATE"
check '{"inputs":{}}'                              1 "no phases key (malformed) -> GATE"
check '{"phases":[]}'                              1 "phases not a dict -> GATE"
check '[1,2]'                                      1 "top-level array -> GATE, no traceback"
check 'not json at all'                            1 "corrupt JSON -> GATE (fail closed)"
check ''                                           1 "empty file -> GATE (fail closed)"
# The WHOLE shell condition, lifted from the postinst and re-pointed at a
# scratch ledger -- not a hand-written model of it. A model passed happily
# against a postinst whose gate tested the wrong path (`-f .../o19-WRONG/`),
# which can never fire: dpkg would clobber an in-progress import, and this
# harness would still have said 30/30.
COND=$(sed -n '/^        if \[ -s \/var\/lib\/carlos-emr\/o19-import\/state.json \]/,/^        then$/p' \
        debian/carlos-emr.postinst | sed '$d')
LEDGER=/var/lib/carlos-emr/o19-import/state.json
if [ -z "$COND" ]; then
  bad "could not lift the gate condition from the postinst (shape changed?)"
elif [ "$(printf '%s\n' "$COND" | grep -c -- "$LEDGER")" != 2 ]; then
  # once in the [ -s ] test, once as python3's argv: if either moves, the
  # substitution below would quietly test something else
  bad "the postinst gate no longer references $LEDGER exactly twice"
else
  ok "gate condition lifted from the postinst (both ledger references)"
fi
# substitute the scratch path in, so the REAL condition runs unmodified
# except for where it looks
COND_T=$(printf '%s\n' "$COND" | sed "s|$LEDGER|$T/state.json|g")
gate() { # file-content, expected "GATE"/"nogate"
  printf '%s' "$1" > "$T/state.json"
  # the lifted condition is `[ -s L ] && ! python3 ...`, so it succeeds
  # exactly when the postinst would enter its gate branch
  if eval "${COND_T#*if }"; then echo GATE; else echo nogate; fi
}
shell_gate() { # file-content, expected, pass-message, fail-message
  got=$(gate "$1")
  if [ "$got" = "$2" ]; then ok "$3"; else bad "$4 (got $got)"; fi
}
shell_gate '{"phases":{"verify":{"status":"started"}}}' GATE \
  "shell condition gates a started verify" "shell condition failed to gate"
shell_gate '{"phases":{"verify":{"status":"done"}}}' nogate \
  "shell condition passes a finished import" "shell condition wrongly gated"
shell_gate '' nogate \
  "empty file short-circuits on -s (no python3 call)" \
  "empty file behaviour changed"
shell_gate '{"phases":{"stage":{"status":"done"}}}' nogate \
  "an assessment leftover does not keep the EMR stopped" \
  "assessment leftover gates the upgrade"
rm -rf "$T"

hdr "postrm credential-shred block (lifted from the postrm)"
# The REAL block, not a model of it. Modelling `A || { X=0; }` proved
# nothing: that sets X on every POSIX shell ever written, so the check
# could not fail and said so twice. Lifting it exercises the actual
# `shred || { fallback } || flag` chain, including the part with real
# subtlety -- `find -exec shred {} +` exits 0 when nothing matches.
SHRED_BLOCK=$(sed -n '/^        O19_SHREDDED=1$/,/^        fi$/p' \
        debian/carlos-emr.postrm)
if [ -z "$SHRED_BLOCK" ]; then
  bad "could not lift the shred block from the postrm (shape changed?)"
else
  ok "shred block lifted from the postrm"
  run_shred() { # stub-dir-on-PATH, expected "SHREDDED"/"FELLBACK"/"FAILED"
    d=$(mktemp -d); mkdir -p "$d/state/o19-import"
    printf 'secret\n' > "$d/state/o19-import/admin-credentials.txt"
    got=$(PATH="$1:$PATH" STATE="$d/state" sh -c "
      set -e
      $SHRED_BLOCK
      if [ \"\$O19_DELETE_FAILED\" = 1 ]; then echo FAILED
      elif [ \"\$O19_SHREDDED\" = 0 ]; then echo FELLBACK
      else echo SHREDDED; fi" 2>/dev/null)
    rm -rf "$d"
    [ "$got" = "$2" ]
    verdict $? "shred path: $2" "shred path: expected $2, got ${got:-<none>}"
  }
  stubdir=$(mktemp -d)
  run_shred /nonexistent-stub-dir SHREDDED
  printf '#!/bin/sh\nexit 1\n' > "$stubdir/shred"; chmod +x "$stubdir/shred"
  run_shred "$stubdir" FELLBACK
  rm -rf "$stubdir"
fi
verdict "$(command -v shred >/dev/null; echo $?)" \
  "shred present (coreutils)" "shred absent - fallback would always fire"

hdr "fixture builder: optional charset arg under set -euo pipefail"
two=$(bash -c 'set -euo pipefail; f(){ set -- ${3+--default-character-set="$3"}; echo $#; }; f a b')
three=$(bash -c 'set -euo pipefail; f(){ set -- ${3+--default-character-set="$3"}; echo "$# $1"; }; f a b utf8mb4')
verdict "$([ "$two" = "0" ]; echo $?)" \
  "2-arg call adds no word" "2-arg call produced $two word(s)"
verdict "$([ "$three" = "1 --default-character-set=utf8mb4" ]; echo $?)" \
  "3-arg call adds exactly one word" "3-arg call: $three"
verdict "$(bash -n scripts/migration/o19/build-o19-fixture.sh; echo $?)" \
  "build-o19-fixture.sh parses" "build-o19-fixture.sh syntax"

hdr "carlos_ctl unit suite under this python3"
# the status is CAPTURED, not piped: `... | tail -3` reports tail's exit
# code, so a suite that failed to even import would have been summarised
# in three quiet lines and counted as a pass by a harness whose whole job
# is catching that.
suite_out=$(cd debian/assets && python3 -m unittest discover \
    -s carlos_ctl/tests -t . 2>&1)
suite_rc=$?
echo "$suite_out" | tail -3
verdict "$suite_rc" "unit suite passes on this interpreter" \
  "unit suite FAILED on this interpreter"

hdr "o19_preflight.py is standalone and import-clean"
python3 -c "
import ast,sys
src=open('debian/assets/carlos_ctl/o19_preflight.py').read()
t=ast.parse(src)
bad=[]
for n in ast.walk(t):
    if isinstance(n,ast.ImportFrom) and n.module and 'carlos_ctl' in n.module: bad.append(n.module)
    if isinstance(n,ast.Import):
        for a in n.names:
            if 'carlos_ctl' in a.name: bad.append(a.name)
print('  package imports:', bad or 'none')
sys.exit(1 if bad else 0)
"
verdict $? "no carlos_ctl imports (standalone)" "imports the package"
python3 debian/assets/carlos_ctl/o19_preflight.py --help >/dev/null 2>&1
verdict $? "runs --help" "--help failed"

echo; echo "=== TOTAL: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
exit 0
