#!/bin/bash
# restore.sh
# a script file for OSCAR that decrypts and decompresses archives
# that have been generated using backup.sh
# encrytped files should be in the same directory as this script
# If using incrimental document backup that includes the last full 
# and 
# ALL incrimental document backups from that date
# run as root

#===================================================================
# Copyright Peter Hutten-Czapski 2013-2019 released under the GPL v2
#===================================================================
# v 19.02 altered cd ${DOCS}/${PROGRAM} to cd ${DOCS}


# --- Script Constants

# Derive Tomcat package name from the running process command (not the process owner).
_TOMCAT_CMD=$(ps -eo args= | grep org.apache.catalina.startup.Bootstrap | grep -v grep || true)
for TOMCAT_CANDIDATE in tomcat11 tomcat10 tomcat9 tomcat8 tomcat7; do
    if echo "$_TOMCAT_CMD" | grep -q "${TOMCAT_CANDIDATE}"; then
        TOMCAT=${TOMCAT_CANDIDATE}
        break
    fi
done

# Fall back to installed-version checks if process detection did not yield a name.
if [ -z "$TOMCAT" ]; then
    for TOMCAT_CANDIDATE in tomcat11 tomcat10 tomcat9 tomcat8 tomcat7; do
        if [ -f "/usr/share/${TOMCAT_CANDIDATE}/bin/version.sh" ]; then
            TOMCAT=${TOMCAT_CANDIDATE}
            break
        fi
    done
fi
if [ -z "$TOMCAT" ]; then
    echo "ERROR: No supported Tomcat installation found (expected tomcat11, tomcat10, tomcat9, tomcat8, or tomcat7)." >&2
    exit 1
fi

TMP=/tmp/${TOMCAT}-${TOMCAT}-tmp
data_path=/var/lib/carlos-emr
PROGRAM=carlos
LOG_FILE=${data_path}/${PROGRAM}.log
LOG_ERR=${data_path}/${PROGRAM}.err
C_HOME=/usr/share/${TOMCAT}/
DOC_ROOT=${data_path}/OscarDocument
DOCS=${DOC_ROOT}/${PROGRAM}/
SCRIPT_FILE=$(basename "$0")
SCRIPT_DIR=$(dirname "$(realpath "$0")")
LOCKDIR=/tmp/${SCRIPT_FILE}.lock


# --- sanity check run as root
if [ "$(id -u)" != "0" ];
then
        echo "The ${SCRIPT_FILE} script must be run as root" 1>&2
        exit 1
fi

# --- prevent more than one instance running at a time
if ! mkdir "$LOCKDIR"; then
    echo "The ${SCRIPT_FILE} script is already running." 1>&2
    exit 1
fi
# Remove lockdir when the script finishes, or when it receives a signal
trap 'rm -rf "$LOCKDIR"' 0 1 2   # remove directory when script finishes EXIT(0), terminal closes SIGHUP(1) or SIGINT(2) Ctrl-C


if [ -f ${C_HOME}${PROGRAM}.properties ] ; then
	# --- drop lines that start with a comment, then grep the property, just take the last instance of that, cut on the = delimiter, and trim whitespace
	echo "grep the password from the properties file"
	db_password=$(sed '/^\#/d' ${C_HOME}${PROGRAM}.properties | grep 'db_password'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
	echo "grep the db_name from the properties file"
	db_name=$(sed '/^\#/d' ${C_HOME}${PROGRAM}.properties | grep 'db_name'  | tail -n 1 | cut -d "=" -f2- | cut -d "?" -f1 | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
fi

# DB_PASSWORD is derived from the properties file above.
# If not found there, it can be set as an environment variable before running this script.
# Example: export DB_PASSWORD=yourpassword
# Or add to /etc/environment: DB_PASSWORD=yourpassword
# Or add to your shell profile (~/.bashrc or ~/.profile): export DB_PASSWORD=yourpassword
db_password="${db_password:-${DB_PASSWORD}}"
if [ -z "${db_password}" ]; then
    echo "ERROR: Database password could not be determined." >&2
    echo "Set DB_PASSWORD before running this script, e.g.:" >&2
    echo "  export DB_PASSWORD=yourpassword" >&2
    echo "  or add it to /etc/environment or your shell profile (~/.bashrc, ~/.profile)" >&2
    exit 1
fi
export DB_PASSWORD="${db_password}"

# --- prevent *.enc to be run through if there are no files in the directory
shopt -s nullglob
PROCESSED_DIR="${SCRIPT_DIR}/restored-inputs"

for f in "${SCRIPT_DIR}"/*.tar.gz.enc
do
	decrypted="${f%.enc}"
	echo "Decrypting file - $f"
	openssl enc -d -aes-256-cbc -salt -in "$f" -out "$decrypted" -pass env:DB_PASSWORD 		|| { echo "Decryption failed for $f" >&2; exit 1; }
	if tar -tzf "$decrypted" | grep -q '^carlos/'; then
		extract_target="$DOC_ROOT"
	else
		extract_target="$DOCS"
	fi
	echo "Expanding contents of file - $decrypted"
	# --- use p to preserve permissions in the untarring
	tar -pxzf "$decrypted" -C "$extract_target" && echo "Extraction successful." || { echo "Extraction failed." >> /dev/stderr; exit 1; }
	echo "Cleanup, deleting decrypted file - $decrypted"
	rm "$decrypted"
	mkdir -p "$PROCESSED_DIR"
	processed_input="${PROCESSED_DIR}/$(date +%Y%m%d%H%M%S)-$$-$(basename "$f")"
	echo "Moving processed encrypted input to $processed_input"
	mv "$f" "$processed_input"
done

echo "Changing directories to ${DOCS}"
# --- thats where all the files have been extracted including the OscarBackup.sql
cd "${DOCS}" || { echo "Failed to change to ${DOCS}" >&2; exit 1; }

if command -v mariadb >/dev/null 2>&1; then
	DB_CLIENT=mariadb
else
	echo "ERROR: mariadb client is required but was not found; install mariadb-client." >&2
	exit 1
fi

restore_sql_dump() {
	dump_file="$1"
	dump_label="$2"
	required="$3"
	if [ -f "${dump_file}.gz" ] ; then
		gunzip -f "${dump_file}.gz" || { echo "ERROR: gunzip of ${dump_file}.gz failed" >&2; exit 1; }
	fi
	if [ ! -f "${dump_file}" ] ; then
		if [ "${required}" = "required" ] ; then
			echo "Failed, unable to find ${dump_label}" >&2
			exit 1
		fi
		return 0
	fi
	if [ ! -s "${dump_file}" ] ; then
		echo "Skipping empty ${dump_label}"
		rm -f "${dump_file}"
		return 0
	fi
	echo "Loading ${dump_label} into MariaDB... you might have time for a coffee"
	# Gate the restore: if the load fails, leave the SQL file in place for a retry and abort.
	if ! MYSQL_PWD="${db_password}" "${DB_CLIENT}" -uroot "${db_name}" < "${dump_file}"; then
		echo "ERROR: restore of ${dump_label} into ${db_name} failed — leaving ${dump_file} in place for retry" >&2
		exit 1
	fi
	echo "Cleanup, deleting ${dump_file}... it is huge"
	rm "${dump_file}"
}

restore_sql_dump CarlosBackup.sql "backup database" required
restore_sql_dump MyISAMBackup.sql "MyISAM table backup" optional
