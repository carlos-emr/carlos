#!/bin/sh
##
# Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
#
# This software is published under the GPL GNU General Public License.
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# CARLOS EMR Project
# https://github.com/carlos-emr/carlos
##

set -eu

document_dir=${1:?usage: validate_hrm_fixtures.sh DOCUMENT_DIR REPORT_FILE_LIST}
report_file_list=${2:?usage: validate_hrm_fixtures.sh DOCUMENT_DIR REPORT_FILE_LIST}
missing_count=0
invalid_path_count=0

if [ ! -f "${report_file_list}" ]; then
    echo "ERROR: HRM report-file list does not exist: ${report_file_list}" >&2
    exit 1
fi

while IFS= read -r report_file; do
    [ -n "${report_file}" ] || continue

    case "${report_file}" in
        /*|..|../*|*/..|*/../*)
            echo "Invalid seeded HRM fixture path outside document directory: ${report_file}" >&2
            invalid_path_count=$((invalid_path_count + 1))
            continue
            ;;
    esac

    if [ ! -f "${document_dir}/${report_file}" ]; then
        echo "Missing seeded HRM fixture: ${report_file}" >&2
        missing_count=$((missing_count + 1))
    fi
done < "${report_file_list}"

if [ "${missing_count}" -ne 0 ]; then
    echo "ERROR: ${missing_count} seeded HRM report(s) have no document fixture." >&2
fi

if [ "${invalid_path_count}" -ne 0 ]; then
    echo "ERROR: ${invalid_path_count} seeded HRM report(s) have invalid fixture paths." >&2
fi

if [ "${missing_count}" -ne 0 ] || [ "${invalid_path_count}" -ne 0 ]; then
    exit 1
fi

echo "Validated seeded HRM document references."
