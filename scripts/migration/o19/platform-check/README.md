# Platform checks — Ubuntu 26.04

`carlos-emr` ships against **Ubuntu 26.04** (python3 3.14, dash 0.5.12,
dpkg 1.23). The development container runs **Python 3.11**. That gap is
not academic: `o19docs.export_archive_csv` uses `csv.QUOTE_NOTNULL` when
the interpreter provides it (3.12+), so a SQL `NULL` stays
distinguishable from a stored empty string in the archive CSV a clinic
keeps. On 3.11 the constant does not exist, so the unit tests only ever
exercised the *fallback* — the branch that actually ships had never been
executed anywhere, and failed the first time it met 26.04.

This harness runs the platform-sensitive checks on the real thing.

## Run

From the repository root:

```sh
docker build -t carlos-u2604 scripts/migration/o19/platform-check
docker run --rm -v "$PWD:/work:ro" \
  -v "$PWD/scripts/migration/o19/platform-check/check.sh:/check.sh:ro" \
  -w /work carlos-u2604 bash /check.sh
```

Behind a proxy that only handles CONNECT, pass it for the build only —
plain-HTTP archive traffic must go direct:

```sh
docker build --network host --build-arg https_proxy="$https_proxy" \
  -t carlos-u2604 scripts/migration/o19/platform-check
```

`check.sh` needs `bash` (it is a bash script, and shells out to `bash -n`
and `bash -c` for the syntax and word-splitting checks), plus `python3`,
`dash` and coreutils. Nothing else — so it also runs unchanged on a real
26.04 host or VM; the container is simply the cheapest way to get one.

## What it covers

- Both maintainer scripts parse under `dash`, which is what dpkg runs
  them with.
- The full ledger matrix for the postinst "an OSCAR 19 import is in
  progress" gate, with the predicate **extracted from
  `debian/carlos-emr.postinst` rather than copied** — a duplicated
  predicate silently stops testing the shipped one.
- The postrm shred/fallback flag logic.
- The optional `--default-character-set` argument in
  `build-o19-fixture.sh` under `set -euo pipefail` (word count, not
  rendering).
- The `carlos_ctl` unit suite on the target interpreter.
- That `o19_preflight.py` is still standalone (no `carlos_ctl` imports)
  and still runs `--help`.

## What it does NOT cover

Anything needing systemd, a live dpkg install/upgrade transaction, a
running `carlos-emr` service, or MariaDB. Those need a VM or a real
host; this harness deliberately stays in the part a container serves.
