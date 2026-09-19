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
# A predictable /tmp path written by a script that runs as root is a
# symlink-truncate of whatever an unprivileged user points it at; mktemp
# gives an unpredictable name in a directory we then own.
ERRTMP=$(mktemp) || { echo "cannot create a temp file" >&2; exit 1; }
trap 'rm -f "$ERRTMP"' EXIT INT TERM
for f in debian/carlos-emr.postinst debian/carlos-emr.postrm; do
  [ -f "$f" ] || { bad "$f missing"; continue; }
  if head -1 "$f" | grep -q 'bin/sh'; then SH=dash; else SH=bash; fi
  # dpkg runs #!/bin/sh scripts under dash; check the declared shell, and
  # dash as well when they differ (running dash twice just inflated the
  # count by two)
  if $SH -n "$f" 2>"$ERRTMP"; then ok "$SH -n $f"; else bad "$SH -n $f: $(cat "$ERRTMP")"; fi
  if [ "$SH" != dash ]; then
    if dash -n "$f" 2>"$ERRTMP"; then ok "dash -n $f"; else bad "dash -n $f: $(cat "$ERRTMP")"; fi
  fi
done

hdr "OSCAR-19-in-progress guard: the shipped script over the full ledger matrix"
# The predicate ships as ONE file, debian/assets/bin/carlos-emr-o19-guard,
# consulted by carlos-emr.service (ExecCondition=, so a reboot mid-import
# does not start the EMR), by the postinst's o19_import_in_progress()
# (configure and the abort-* restart) and here. Run THAT file, not a copy:
# a duplicated predicate silently stops testing the shipped one the moment
# it changes. Exit 1 = an import is in progress (the gate fires; the unit
# stays down); exit 0 = it may start.
GUARD=debian/assets/bin/carlos-emr-o19-guard
[ -f "$GUARD" ] || { bad "$GUARD is missing"; exit 1; }
if dash -n "$GUARD" 2>"$ERRTMP"; then ok "dash -n $GUARD"; else bad "dash -n $GUARD: $(cat "$ERRTMP")"; fi
verdict "$(head -1 "$GUARD" | grep -q '^#!/bin/sh$'; echo $?)" \
  "the guard runs under /bin/sh (dash), as the unit invokes it" \
  "the guard's interpreter line is not #!/bin/sh"
# a scratch directory WITH a space: the guard quotes its argument, and a
# TMPDIR holding one is a perfectly valid host
T=$(mktemp -d "${TMPDIR:-/tmp}/o19 guard.XXXXXX")
check() { # file-content, expected-rc, label
  printf '%s' "$1" > "$T/s.json"
  sh "$GUARD" "$T/s.json" 2>/dev/null; rc=$?
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
# the importer writes the ledger with os.replace(), so a 0-byte file is
# never a real mid-import state; the -s short-circuit treats it as absent,
# exactly as the postinst's old inline condition did
check ''                                           0 "empty file -> no gate (never a written state)"
rm -f "$T/s.json"
sh "$GUARD" "$T/s.json" 2>/dev/null; rc=$?
verdict "$([ "$rc" = 0 ]; echo $?)" \
  "no ledger at all -> no gate (a host that never imported)" \
  "a missing ledger gated the start (rc=$rc)"
# the refusal explains itself where the unit's journal will show it
printf '%s' '{"phases":{"etl":{"status":"done"}}}' > "$T/s.json"
MSG=$(sh "$GUARD" "$T/s.json" 2>&1 >/dev/null)
verdict "$(printf '%s\n' "$MSG" | grep -q -- '--resume'; echo $?)" \
  "a refused start names the remedy (--resume) on stderr" \
  "the refusal message no longer names --resume: ${MSG:-<silent>}"
# the guard reads its ONE argument and nothing from the environment: a
# root-run ExecCondition must not be steerable by an environment file
# shellcheck disable=SC2016  # the guard's LITERAL default, unexpanded
VARS=$(grep -o -E '\$\{?[A-Za-z_][A-Za-z0-9_]*' "$GUARD" | sort -u | tr '\n' ' ')
verdict "$(grep -c 'LEDGER="${1:-/var/lib/carlos-emr/o19-import/state.json}"' "$GUARD" \
           | grep -qx 1 && [ "$VARS" = '$LEDGER ' ] && ! grep -q getenv "$GUARD"; echo $?)" \
  "the guard names the ledger once, from its argument, not the environment" \
  "the guard reads something beyond its positional default (variables: ${VARS:-none})"
# Three callers, one file. The unit: ExecCondition= with the full-privilege
# prefix (the ledger is root-only and the unit is sandboxed to carlos).
verdict "$(grep -q '^ExecCondition=+/usr/lib/carlos-emr/carlos-emr-o19-guard$' \
             debian/carlos-emr.carlos-emr.service; echo $?)" \
  "carlos-emr.service consults the guard as ExecCondition=+ on every start" \
  "carlos-emr.service no longer runs the guard as ExecCondition=+"
# The package installs it where both point.
verdict "$(grep -q 'install -m 0755 debian/assets/bin/carlos-emr-o19-guard $(STAGE)/$(LIBDIR)/carlos-emr-o19-guard' \
             debian/rules; echo $?)" \
  "debian/rules installs the guard at /usr/lib/carlos-emr" \
  "debian/rules no longer installs the guard"
# The postinst's predicate is the guard, negated, and fails CLOSED when
# the file is missing (a broken unpack must not start the EMR).
FN_BODY=$(sed -n '/^o19_import_in_progress() {$/,/^}$/p' debian/carlos-emr.postinst)
verdict "$(printf '%s\n' "$FN_BODY" | grep -q '^    ! /usr/lib/carlos-emr/carlos-emr-o19-guard'; echo $?)" \
  "the postinst's o19_import_in_progress() runs the shipped guard" \
  "the postinst predicate no longer calls the shipped guard"
verdict "$(printf '%s\n' "$FN_BODY" | grep -A3 'if \[ ! -x /usr/lib/carlos-emr/carlos-emr-o19-guard \]' | grep -q 'return 0'; echo $?)" \
  "a missing guard reads as in progress in the postinst (fail closed)" \
  "the postinst no longer fails closed on a missing guard"
verdict "$(printf '%s\n' "$FN_BODY" | grep -c . | awk '{exit !($1 < 25)}'; echo $?)" \
  "the postinst predicate is the thin wrapper it should be" \
  "the postinst predicate grew past 25 lines (a second copy creeping in?)"
# The condition is only half the gate. Flipping the branch body to
# `MIGRATION_OK=1` leaves every check above passing -- the gate still
# fires and still prints "NOT migrating the schema and NOT starting the
# service" -- while postinst then runs Flyway and starts the webapp into
# the half-copied schema anyway. So assert the consequent too, and that
# the variable it sets is the one the migrate and start steps read.
CONSEQ=$(sed -n '/^        if o19_import_in_progress; then$/{n;p;q;}' \
         debian/carlos-emr.postinst)
verdict "$([ "$CONSEQ" = "            MIGRATION_OK=0" ]; echo $?)" \
  "the gate branch clears MIGRATION_OK" \
  "the gate branch no longer sets MIGRATION_OK=0 (got: ${CONSEQ:-<none>})"
# shellcheck disable=SC2016  # the patterns below are the postinst's
# LITERAL text: `${MIGRATION_OK}` must reach grep unexpanded. Switching to
# double quotes would search for this shell's (empty) value and the two
# checks would pass on any postinst at all.
verdict "$(grep -q '^            if \[ "${MIGRATION_OK}" = 1 \] && ! carlos-ctl db-migrate' \
             debian/carlos-emr.postinst; echo $?)" \
  "db-migrate is guarded by MIGRATION_OK" \
  "db-migrate no longer consults MIGRATION_OK"
# shellcheck disable=SC2016  # literal postinst text, as above (a
# directive covers only the command that follows it, so this repeats)
verdict "$(grep -q '^    \(if\|elif\) \[ "${MIGRATION_OK:-1}" = 0 \]' \
             debian/carlos-emr.postinst; echo $?)" \
  "the service start is guarded by MIGRATION_OK" \
  "the service start no longer consults MIGRATION_OK"
# release/2026.08's abort-* path restarts the application after a failed
# apt transaction (#3557); an import in progress must hold there too, or
# an unrelated package's abort starts the EMR into a half-copied schema.
ABORT_BLOCK=$(sed -n '/^    abort-upgrade|abort-remove|abort-deconfigure)$/,/^        ;;$/p' \
         debian/carlos-emr.postinst)
verdict "$(printf '%s\n' "$ABORT_BLOCK" \
         | grep -q '^            elif o19_import_in_progress; then$'; echo $?)" \
  "the abort-* restart consults the same predicate" \
  "the abort-* restart no longer consults o19_import_in_progress"
# The whole postinst function, run for real against a scratch ledger by
# pointing its guard path at the shipped file: a model of it passed
# happily against a postinst whose gate tested the wrong path once.
FN_T=$(printf '%s\n' "$FN_BODY" | sed "s|/usr/lib/carlos-emr/carlos-emr-o19-guard|\"\${GUARD_T}\"|g")
gate() { # file-content -> GATE / nogate, through the postinst's own function
  printf '%s' "$1" > "$T/state.json"
  # the shipped guard reads its ONE argument; hand it the scratch ledger
  # by wrapping it, so the postinst function stays exactly as shipped
  GUARD_T="$T/guard"
  printf '#!/bin/sh\nexec sh %s "%s"\n' "$(pwd)/$GUARD" "$T/state.json" > "$GUARD_T"
  chmod 0755 "$GUARD_T"
  if sh -c "GUARD_T=\"\$1\"; $FN_T; if o19_import_in_progress; then echo GATE; else echo nogate; fi" _ "$GUARD_T"; then :; fi
}
shell_gate() { # file-content, expected, pass-message, fail-message
  got=$(gate "$1" 2>/dev/null | tail -1)
  if [ "$got" = "$2" ]; then ok "$3"; else bad "$4 (got ${got:-<nothing>})"; fi
}
shell_gate '{"phases":{"verify":{"status":"started"}}}' GATE \
  "the postinst function gates a started verify" "the postinst function failed to gate"
shell_gate '{"phases":{"verify":{"status":"done"}}}' nogate \
  "the postinst function passes a finished import" "the postinst function wrongly gated"
shell_gate '{"phases":{"stage":{"status":"done"}}}' nogate \
  "an assessment leftover does not keep the EMR stopped" \
  "assessment leftover gates the upgrade"
rm -f "$T/guard"
got=$(sh -c "GUARD_T=\"$T/no-such-guard\"; $FN_T; if o19_import_in_progress; then echo GATE; else echo nogate; fi" 2>/dev/null | tail -1)
verdict "$([ "$got" = GATE ]; echo $?)" \
  "with the guard missing the postinst function fails closed (GATE)" \
  "with the guard missing the postinst function said ${got:-<nothing>}"
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
  # $3 is whether the credential file must be GONE afterwards. Reading
  # only the two flags proved nothing about the one property this block
  # exists for: delete the fallback's `find ... -delete` entirely and
  # both flag paths still reported PASS while the plaintext break-glass
  # password and the clinic's carried fax/OAuth/mail secrets stayed on
  # the disk -- under a postrm notice saying they had been removed.
  # $4 "none" seeds NO credential file. `find -exec shred -u {} +` exits
  # 0 when nothing matches, which is the one piece of real subtlety in
  # the block and was asserted only in a comment: a purge on a host that
  # never ran an import must report a clean shred, not a fallback and
  # not a failure.
  run_shred() { # stub-dir-on-PATH, expected flags, "gone"/"kept", [none]
    d=$(mktemp -d); mkdir -p "$d/state/o19-import"
    [ "${4:-}" = none ] \
      || printf 'secret\n' > "$d/state/o19-import/admin-credentials.txt"
    got=$(PATH="$1:$PATH" STATE="$d/state" sh -c "
      set -e
      $SHRED_BLOCK
      if [ \"\$O19_DELETE_FAILED\" = 1 ]; then echo FAILED
      elif [ \"\$O19_SHREDDED\" = 0 ]; then echo FELLBACK
      else echo SHREDDED; fi" 2>/dev/null)
    if [ -e "$d/state/o19-import/admin-credentials.txt" ]; then
      left=kept
    else
      left=gone
    fi
    rm -rf "$d"
    [ "$got" = "$2" ]
    verdict $? "shred path: $2" "shred path: expected $2, got ${got:-<none>}"
    [ "$left" = "$3" ]
    verdict $? "shred path $2: credential file $3" \
      "shred path $2: credential file expected $3, was $left"
  }
  stubdir=$(mktemp -d)
  run_shred /nonexistent-stub-dir SHREDDED gone
  run_shred /nonexistent-stub-dir SHREDDED gone none
  printf '#!/bin/sh\nexit 1\n' > "$stubdir/shred"; chmod +x "$stubdir/shred"
  run_shred "$stubdir" FELLBACK gone
  # The third limb: an undeletable file (immutable attribute, read-only
  # filesystem) so BOTH finds fail. Without the trailing
  # `|| O19_DELETE_FAILED=1` the block's exit status becomes 1, and
  # under the postrm's `set -e` that aborts the whole purge -- dpkg
  # leaves the package half-purged and the operator never sees the
  # warning, the kept/gone notice or the nginx reload. That limb was
  # unreachable here: run_shred was never called with FAILED.
  printf '#!/bin/sh\nexit 1\n' > "$stubdir/find"; chmod +x "$stubdir/find"
  run_shred "$stubdir" FAILED kept
  rm -rf "$stubdir"

  # Depth is part of this block's contract. An abandoned run (a P2
  # no-go, or a failure the operator gave up on -- and --cleanup REFUSES
  # a mid-import workspace) leaves the extracted bundle in place, and
  # bundle/oscar.properties is the superset the derived fragment was
  # distilled from. It must go with the fragment; the clinic's dump and
  # the run's own record must NOT, because purge keeps the workspace as
  # the record of the import and says so in its notice.
  d=$(mktemp -d); mkdir -p "$d/state/o19-import/bundle"
  printf 'x\n' > "$d/state/o19-import/o19-derived-carlos.properties"
  printf 'x\n' > "$d/state/o19-import/bundle/oscar.properties"
  printf 'x\n' > "$d/state/o19-import/bundle/o19.sql.gz"
  printf 'x\n' > "$d/state/o19-import/report.txt"
  STATE="$d/state" sh -c "set -e
$SHRED_BLOCK" >/dev/null 2>&1 || true
  [ ! -e "$d/state/o19-import/o19-derived-carlos.properties" ]
  verdict $? "purge shreds the derived properties fragment" \
    "the derived fragment survived purge (the clinic's carried secrets, in clear)"
  [ ! -e "$d/state/o19-import/bundle/oscar.properties" ]
  verdict $? "purge shreds the clinic's oscar.properties in the bundle" \
    "bundle/oscar.properties survived purge (the derived fragment's own source)"
  [ -e "$d/state/o19-import/bundle/o19.sql.gz" ] \
    && [ -e "$d/state/o19-import/report.txt" ]
  verdict $? "purge keeps the dump and the run record" \
    "purge removed more than the credentials"
  rm -rf "$d"
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

# The file's own docstring promises "no f-strings, no annotations" because it
# is copied alone to a 2014-era OSCAR 19 server. Stating that is not the same
# as holding to it: a `def f(path: str) -> None` slipped in and survived a
# review round, so the promise is checked here rather than remembered.
# (Annotations parse on 3.4 -- this defends the stated contract and the
# f-strings that would genuinely break it, in one pass.)
python3 -c "
import ast,sys
src=open('debian/assets/carlos_ctl/o19_preflight.py').read()
bad=[]
for n in ast.walk(ast.parse(src)):
    if isinstance(n,(ast.FunctionDef,ast.AsyncFunctionDef)):
        # every argument category, not just the positional ones: an
        # annotation on *args, **kwargs or a positional-only parameter is
        # the same contract break and would otherwise slip past
        a=n.args
        params=list(a.args)+list(a.kwonlyargs)+list(getattr(a,'posonlyargs',[]))
        params+=[x for x in (a.vararg, a.kwarg) if x is not None]
        if n.returns is not None or any(
                p.annotation is not None for p in params):
            bad.append('def '+n.name)
    elif isinstance(n,ast.AnnAssign):
        bad.append('annotated assignment on line %d' % n.lineno)
    elif isinstance(n,ast.JoinedStr):
        bad.append('f-string on line %d' % n.lineno)
print('  py3.4-incompatible or contract-breaking forms:', bad or 'none')
sys.exit(1 if bad else 0)
"
verdict $? "no annotations or f-strings (Python 3.4 contract)" \
  "carries annotations or f-strings the module docstring rules out"

echo; echo "=== TOTAL: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ] || exit 1
exit 0
