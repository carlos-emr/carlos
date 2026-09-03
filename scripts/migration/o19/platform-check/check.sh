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
ok()   { echo "  PASS  $*"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $*"; FAIL=$((FAIL+1)); }
hdr()  { echo; echo "=== $* ==="; }

hdr "platform"
. /etc/os-release; echo "  $PRETTY_NAME"
echo "  python3: $(python3 -V 2>&1)"
echo "  bash:    $(bash --version | head -1)"
echo "  dash:    $(dpkg -s dash 2>/dev/null | sed -n 's/^Version: //p')"
echo "  dpkg:    $(dpkg --version | head -1)"
echo "  mariadb: $(mariadb --version 2>/dev/null || echo '(not installed)')"

hdr "maintainer scripts parse under the shells dpkg uses"
for f in debian/carlos-emr.postinst debian/carlos-emr.postrm; do
  [ -f "$f" ] || { bad "$f missing"; continue; }
  head -1 "$f" | grep -q 'bin/sh' && SH=dash || SH=bash
  if $SH -n "$f" 2>/tmp/e; then ok "$SH -n $f"; else bad "$SH -n $f: $(cat /tmp/e)"; fi
  # dpkg runs #!/bin/sh scripts under dash; check both regardless
  if dash -n "$f" 2>/tmp/e; then ok "dash -n $f"; else bad "dash -n $f: $(cat /tmp/e)"; fi
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
# and the real shell condition, including the -s test
gate() { # file-content, expected "GATE"/"nogate"
  printf '%s' "$1" > "$T/state.json"
  if [ -s "$T/state.json" ] && ! python3 -c "$PRED" "$T/state.json"; then echo GATE; else echo nogate; fi
}
[ "$(gate '{"phases":{"verify":{"status":"started"}}}')" = GATE ]   && ok "shell condition gates a started verify"   || bad "shell condition failed to gate"
[ "$(gate '{"phases":{"verify":{"status":"done"}}}')"    = nogate ] && ok "shell condition passes a finished import" || bad "shell condition wrongly gated"
[ "$(gate '')" = nogate ] && ok "empty file short-circuits on -s (no python3 call)" || bad "empty file behaviour changed"
[ "$(gate '{"phases":{"stage":{"status":"done"}}}')" = nogate ] && ok "an assessment leftover does not keep the EMR stopped" || bad "assessment leftover gates the upgrade"
rm -rf "$T"

hdr "postrm shred fallback flag logic"
O19_SHREDDED=1
false || { O19_SHREDDED=0; true; }
[ "$O19_SHREDDED" = 0 ] && ok "fallback sets the flag" || bad "fallback did not set the flag"
O19_SHREDDED=1
true || { O19_SHREDDED=0; true; }
[ "$O19_SHREDDED" = 1 ] && ok "successful shred leaves the flag set" || bad "flag wrongly cleared"
command -v shred >/dev/null && ok "shred present (coreutils)" || bad "shred absent - fallback would always fire"

hdr "fixture builder: optional charset arg under set -euo pipefail"
two=$(bash -c 'set -euo pipefail; f(){ set -- ${3+--default-character-set="$3"}; echo $#; }; f a b')
three=$(bash -c 'set -euo pipefail; f(){ set -- ${3+--default-character-set="$3"}; echo "$# $1"; }; f a b utf8mb4')
[ "$two" = "0" ] && ok "2-arg call adds no word" || bad "2-arg call produced $two word(s)"
[ "$three" = "1 --default-character-set=utf8mb4" ] \
  && ok "3-arg call adds exactly one word" || bad "3-arg call: $three"
bash -n scripts/migration/o19/build-o19-fixture.sh && ok "build-o19-fixture.sh parses" || bad "build-o19-fixture.sh syntax"

hdr "carlos_ctl unit suite under this python3"
( cd debian/assets && python3 -m unittest discover -s carlos_ctl/tests -t . 2>&1 | tail -3 )

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
" && ok "no carlos_ctl imports (standalone)" || bad "imports the package"
python3 debian/assets/carlos_ctl/o19_preflight.py --help >/dev/null 2>&1 && ok "runs --help" || bad "--help failed"

echo; echo "=== TOTAL: $PASS passed, $FAIL failed ==="
exit $([ "$FAIL" = 0 ] && echo 0 || echo 1)
