#!/bin/bash
# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Run ONLY inside an owned disposable container: installs fixed /etc test fixtures.
set -euo pipefail
if [[ ${CARLOS_DISPOSABLE_HOST:-} != true || ! -f /.dockerenv || $EUID != 0 ]]; then
  echo 'This native probe requires an owned disposable Docker container as root.' >&2
  exit 2
fi
groupadd --system carlos
useradd --system --gid carlos --home-dir /var/lib/carlos-emr --create-home carlos
install -d -m 0755 /usr/lib/jvm /usr/lib/carlos-emr /usr/share/carlos-emr/lib /etc/carlos-emr
ln -s "$JAVA_HOME" /usr/lib/jvm/default-java
temporary=$(mktemp -d)
trap 'rm -rf "$temporary"' EXIT
sh debian/build-configuration-preflight.sh "$temporary"
install -m 0644 "$temporary/carlos-configuration-preflight.jar" /usr/share/carlos-emr/lib/
install -m 0755 debian/assets/bin/carlos-emr-config-preflight /usr/lib/carlos-emr/
helper=/usr/lib/carlos-emr/carlos-emr-config-preflight
config=/etc/carlos-emr/carlos.properties
printf 'encryption.util.secret.key=AAAAAAAAAAAAAAAAAAAAAA==\nAbandonOldChart=false\n' > "$config"
chown root:carlos "$config"
chmod 0640 "$config"
before_inode=$(stat -c %i "$config")
# Model the published CLI's process-lifetime lock; the pre-start must not acquire it.
install -d -m 0755 /run/lock
exec 9>/var/lib/carlos-emr/.finish-install.lock
flock -x 9
JAVA_TOOL_OPTIONS=-invalid-option "$helper" reconcile 2> "$temporary/root.log"
grep -q 'overriding the stored AbandonOldChart' "$temporary/root.log"
test "$(stat -c %i "$config")" = "$before_inode"
runuser -u carlos -- "$helper" verify
sha256sum "$config" > "$temporary/unchanged"
"$helper" reconcile
sha256sum --check "$temporary/unchanged"
test "$(stat -c '%U:%G %a' "$config")" = 'root:carlos 640'
# A managed read-only file that already contains true needs no write access.
chmod 0440 "$config"
"$helper" reconcile
runuser -u carlos -- "$helper" verify
chmod 0640 "$config"
cp "$config" "$temporary/original"
# Root never follows a service-user-controlled legacy home file for repairs.
printf 'AbandonOldChart=false\n' > /var/lib/carlos-emr/carlos.properties
chown root:carlos /var/lib/carlos-emr/carlos.properties
chmod 0440 /var/lib/carlos-emr/carlos.properties
if runuser -u carlos -- "$helper" verify > "$temporary/home.log" 2>&1; then
  echo 'Read-only false home override unexpectedly passed' >&2; exit 1
fi
grep -q 'legacy home configuration' "$temporary/home.log"
cmp "$config" "$temporary/original"
rm /var/lib/carlos-emr/carlos.properties
# Missing key must fail without generating configuration or changing its contents.
printf 'AbandonOldChart=true\n' > "$config"
cp "$config" "$temporary/missing-key"
if runuser -u carlos -- "$helper" verify > "$temporary/missing.log" 2>&1; then
  echo 'Missing persisted key unexpectedly passed' >&2; exit 1
fi
cmp "$config" "$temporary/missing-key"
grep -q 'No persisted encryption key' "$temporary/missing.log"
# A root invocation refuses a managed path redirected through a writable directory.
rm "$config"
install -d -m 0777 /etc/carlos-untrusted-probe
printf 'AbandonOldChart=false\n' > /etc/carlos-untrusted-probe/config
ln -s /etc/carlos-untrusted-probe/config "$config"
if "$helper" reconcile > "$temporary/untrusted.log" 2>&1; then
  echo 'Untrusted parent unexpectedly passed' >&2; exit 1
fi
grep -q 'not root-controlled' "$temporary/untrusted.log"
grep -qx 'AbandonOldChart=false' /etc/carlos-untrusted-probe/config
echo 'Native preflight PASS: root/service-user permissions, inode preservation, CLI flock compatibility, key refusal and untrusted paths'
