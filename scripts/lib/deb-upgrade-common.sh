#!/bin/bash
# Copyright (C) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later
# Shared input/query handling for the packaged upgrade snapshot and verifier.
DB_NAME="${DB_NAME-carlos}"
ADMIN_USER="${ADMIN_USER-carlosdoc}"
MYSQL_SOCKET="${MYSQL_SOCKET:-}"
CARLOS_ETC_DIR="${CARLOS_ETC_DIR:-/etc/carlos-emr}"
CARLOS_STATE_DIR="${CARLOS_STATE_DIR:-/var/lib/carlos-emr}"
CARLOS_SHARE_DIR="${CARLOS_SHARE_DIR:-/usr/share/carlos-emr}"
DOC_DIR="${DOC_DIR:-$CARLOS_STATE_DIR/CarlosDocument/carlos/document}"
FRONT_URL="${FRONT_URL:-https://127.0.0.1/carlos/}"
[[ $DB_NAME =~ ^[A-Za-z_][A-Za-z0-9_]*$ && ${#DB_NAME} -le 64 ]] || {
    echo 'ERROR: DB_NAME must be a SQL identifier of at most 64 ASCII characters.' >&2
    exit 2
}
[[ -n $ADMIN_USER ]] || { echo 'ERROR: ADMIN_USER must not be empty.' >&2; exit 2; }
[[ $DOC_DIR == /* && -d $DOC_DIR ]] || {
    echo 'ERROR: DOC_DIR must be an existing absolute document directory.' >&2
    exit 2
}
[[ $FRONT_URL == http://* || $FRONT_URL == https://* ]] || {
    echo 'ERROR: FRONT_URL must use HTTP or HTTPS.' >&2; exit 2
}
export DB_NAME ADMIN_USER MYSQL_SOCKET CARLOS_ETC_DIR CARLOS_STATE_DIR CARLOS_SHARE_DIR DOC_DIR FRONT_URL
# Hex encoding works independently of SQL mode and preserves quotes/backslashes
# and UTF-8 names without interpolating operator input into an SQL string.
admin_hex=$(printf '%s' "$ADMIN_USER" | od -An -tx1 | tr -d ' \n')
ADMIN_SQL="CONVERT(X'$admin_hex' USING utf8mb4)"
db_query() {
    # Ignore client option files: an inherited `force` option can turn a failed
    # SQL statement into exit status zero. These root checks use a local socket.
    local args=(--no-defaults --protocol=socket --user=root --batch --skip-column-names --database="$DB_NAME")
    [[ -z $MYSQL_SOCKET ]] || args+=(--socket="$MYSQL_SOCKET")
    mariadb "${args[@]}" --execute="$1"
}
