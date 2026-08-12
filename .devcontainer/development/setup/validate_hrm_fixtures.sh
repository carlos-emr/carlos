#!/bin/sh

set -eu

document_dir=${1:?usage: validate_hrm_fixtures.sh DOCUMENT_DIR}
missing_count=0

while IFS= read -r report_file; do
    [ -n "${report_file}" ] || continue

    if [ ! -f "${document_dir}/${report_file}" ]; then
        echo "Missing seeded HRM fixture: ${report_file}" >&2
        missing_count=$((missing_count + 1))
    fi
done

if [ "${missing_count}" -ne 0 ]; then
    echo "ERROR: ${missing_count} seeded HRM report(s) have no document fixture." >&2
    exit 1
fi

echo "Validated seeded HRM document references."
