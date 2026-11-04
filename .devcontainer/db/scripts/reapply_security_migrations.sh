#!/usr/bin/env sh
# Re-apply every forward Flyway migration that touches the sec* security tables.
#
# development.sql truncate-reloads those tables, wiping security objects added by
# migrations after the snapshot was taken (issue #4369). Discovery is by content, in
# version order, so a future migration is covered without editing this script; it relies
# on idempotent forward migrations, with the two schema-guarded myCarlos migrations
# below replayed only through their existing security seeds.
#
# Usage: reapply_security_migrations.sh <migration-dir> <database> [mariadb args...]
#   e.g. reapply_security_migrations.sh /database/mysql/migration carlos -h db -u root
# Used by populate_db.sh (fresh volume) and development/setup/seed_data.sh (existing volume).
set -e
MIG="$1"; DB="$2"; shift 2

# These migrations deliberately refuse existing tables. Replay only their exact,
# guarded security tail after the demo snapshot replaces the sec* rows. Validate
# the complete tail before invoking MariaDB so a changed layout cannot replay DDL.
mycarlos_security_seed() {
  case "$(basename "$1")" in
    V1.0.81__mycarlos_accepted_device_approval.sql)
      object='_portal.mycarlos.device_approval'
      description='Approve and remove myCarlos devices'
      privilege='x'
      provider="'999998'"
      ;;
    V1.0.82__mycarlos_sender_outbox.sql)
      object='_mycarlos_delivery'
      description='Send documents to approved myCarlos devices'
      privilege='w'
      provider='NULL'
      ;;
    *) return 1 ;;
  esac
  awk -v object="$object" -v description="$description" \
      -v privilege="$privilege" -v provider="$provider" '
    /^INSERT INTO secObjectName / { seed = 1 }
    seed && NF { lines[++count] = $0 }
    END {
      q = sprintf("%c", 39)
      expected[1] = "INSERT INTO secObjectName (objectName,description,orgapplicable)"
      expected[2] = "SELECT " q object q "," q description q ",0"
      expected[3] = "WHERE NOT EXISTS (SELECT 1 FROM secObjectName WHERE objectName=" q object q ");"
      expected[4] = "INSERT INTO secObjPrivilege (roleUserGroup,objectName,privilege,priority,provider_no)"
      expected[5] = "SELECT " q "admin" q "," q object q "," q privilege q ",0," provider
      expected[6] = "WHERE NOT EXISTS (SELECT 1 FROM secObjPrivilege WHERE roleUserGroup=" q "admin" q " AND objectName=" q object q ");"
      if (count != 6) exit 1
      for (i = 1; i <= count; i++) if (lines[i] != expected[i]) exit 1
      for (i = 1; i <= count; i++) print lines[i]
    }
  ' "$1"
}

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
      case "$f" in
        */V1.0.81__mycarlos_accepted_device_approval.sql|*/V1.0.82__mycarlos_sender_outbox.sql)
          echo "  re-applying $(basename "$f")"
          if ! seed_sql="$(mycarlos_security_seed "$f")"; then
            echo "Refusing changed myCarlos security seed layout: $(basename "$f")" >&2
            exit 1
          fi
          printf '%s\n' "$seed_sql" | mariadb "$@" "${DB}"
          ;;
        *)
          if grep -qE 'secObjectName|secObjPrivilege|secPrivilege|secRole|secUserRole' "$f"; then
            echo "  re-applying $(basename "$f")"
            mariadb "$@" "${DB}" < "$f"
          fi
          ;;
      esac
    done
