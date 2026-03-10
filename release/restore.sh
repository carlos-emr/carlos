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

# Derive Tomcat package name from the running process command (not the process owner)
_TOMCAT_CMD=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{print $11}')
if echo "$_TOMCAT_CMD" | grep -q 'tomcat9'; then
    TOMCAT=tomcat9
elif echo "$_TOMCAT_CMD" | grep -q 'tomcat8'; then
    TOMCAT=tomcat8
elif echo "$_TOMCAT_CMD" | grep -q 'tomcat7'; then
    TOMCAT=tomcat7
fi

# Fall back to installed-version checks if process detection did not yield a name
if [ -z "$TOMCAT" ]; then
    if [ -f /usr/share/tomcat9/bin/version.sh ]; then
        TOMCAT=tomcat9
    elif [ -f /usr/share/tomcat8/bin/version.sh ]; then
        TOMCAT=tomcat8
    elif [ -f /usr/share/tomcat7/bin/version.sh ]; then
        TOMCAT=tomcat7
    fi
fi

TMP=/tmp/${TOMCAT}-${TOMCAT}-tmp
data_path=/var/lib/carlos-emr
PROGRAM=carlos
LOG_FILE=${data_path}/${PROGRAM}.log
LOG_ERR=${data_path}/${PROGRAM}.err
C_HOME=/usr/share/${TOMCAT}/
DOCS=${data_path}/OscarDocument/${PROGRAM}/
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

for f in "${SCRIPT_DIR}"/*.tar.gz.enc
do
	echo "Decrypting file - $f"
        openssl enc -d -aes-256-cbc -salt -in "$f" -out "${f%%.*}" -pass env:DB_PASSWORD
	echo "Expanding contents of file - ${f%%.*}"
	# --- use p to preserve permissions in the untarring
	tar -pxzf "${f%%.*}" -C "$DOCS" && echo "Extraction successful." || { echo "Extraction failed." >> /dev/stderr; exit 1; }
	echo "Cleanup, deleting files - $f and ${f%%.*}"
	rm "$f"
	rm "${f%%.*}"
done

echo "Changing directories to ${DOCS}"
# --- thats where all the files have been extracted including the OscarBackup.sql
cd "${DOCS}" || { echo "Failed to change to ${DOCS}" >&2; exit 1; }

if [ -f CarlosBackup.sql.gz ] ; then
	gunzip CarlosBackup.sql.gz
	echo "Loading backup database into mysql... you might have time for a coffee"
	MYSQL_PWD="${db_password}" mysql -uroot ${db_name} < CarlosBackup.sql
	echo "Cleanup, deleting CarlosBackup.sql... its huge"
	rm CarlosBackup.sql
else
	echo "Failed, unable to find the Backup sql"

fi
