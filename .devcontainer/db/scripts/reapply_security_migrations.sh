#!/usr/bin/env sh
# Re-apply every forward Flyway migration that touches the sec* security tables.
#
# development.sql truncate-reloads those tables, wiping security objects added by
# migrations after the snapshot was taken (issue #4369). Discovery is by content, in
# version order, so a future migration is covered without editing this script; it relies
# on the repo rule that forward migrations are idempotent.
#
# Usage: reapply_security_migrations.sh <migration-dir> <database> [mariadb args...]
#   e.g. reapply_security_migrations.sh /database/mysql/migration carlos -h db -u root
# Used by populate_db.sh (fresh volume) and development/setup/seed_data.sh (existing volume).
set -e
MIG="$1"; DB="$2"; shift 2
# Same set and ordering populate_db.sh feeds the genesis load: common + on, V1.0.N for N>=3.
for f in "${MIG}/common/"V*.sql "${MIG}/on/"V*.sql; do
  [ -f "$f" ] || continue
  case "$f" in
    */V1__*|*/V1.0.1__*|*/V1.0.2__*) continue ;;
  esac
  printf '%s\n' "$f"
done \
  | awk -F'/V1\\.0\\.' '{ n=$2; sub(/__.*/,"",n); print n "\t" $0 }' \
  | sort -n | cut -f2 \
  | while IFS= read -r f; do
      if grep -qE 'secObjectName|secObjPrivilege|secPrivilege|secRole|secUserRole' "$f"; then
        echo "  re-applying $(basename "$f")"
        mariadb "$@" "${DB}" < "$f"
      fi
    done
