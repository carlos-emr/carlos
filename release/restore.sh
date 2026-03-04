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

TOMCAT=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $1 }')
if [ -z "$TOMCAT" ]; then
    #Tomcat is not running, find the highest installed version
    if [ -f /usr/share/tomcat9/bin/version.sh ] ; then
            TOMCAT=tomcat9
        else
        if [ -f /usr/share/tomcat8/bin/version.sh ] ; then
            TOMCAT=tomcat8
            else
            if [ -f /usr/share/tomcat7/bin/version.sh ] ; then
                TOMCAT=tomcat7
            fi
        fi
    fi
fi

TMP=/tmp/${TOMCAT}-${TOMCAT}-tmp
data_path=/usr/share/oscar-emr
PROGRAM=oscar
LOG_FILE=${data_path}/${PROGRAM}.log
LOG_ERR=${data_path}/${PROGRAM}.err
C_HOME=/usr/share/${TOMCAT}/
DOCS=${data_path}/OscarDocument/
SCRIPT_FILE=$(basename "$0")
LOCKDIR=/tmp/${SCRIPT_FILE}.lock


# --- sanity check run as root
if [ "$(id -u)" != "0" ];
then
        echo "The ${SCRIPTFILE} script must be run as root" 1>&2
        exit 1
fi

# --- prevent more than one instance running at a time
if ! mkdir "$LOCKDIR"; then
    echo "The ${SCRIPTFILE} script is already running." 1>&2
    exit 1
fi
# Remove lockdir when the script finishes, or when it receives a signal
trap 'rm -rf "$LOCKDIR"' 0 1 2   # remove directory when script finishes EXIT(0), terminal closes SIGHUP(1) or SIGINT(2) Ctrl-C


if [ -f ${C_HOME}${PROGRAM}.properties ] ; then
	# --- drop lines that start with a comment, then grep the property, just take the last instance of that, cut on the = delimiter, and trim whitespace
	echo "grep the password from the properties file"
	db_password=$(sed '/^\#/d' ${C_HOME}${PROGRAM}.properties | grep 'db_password'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//') 
	echo "grep the db_name from the properties file" 
	db_name=$(sed '/^\#/d' ${C_HOME}${PROGRAM}.properties | grep 'db_name'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//') 
fi

# --- prevent *.enc to be run through if there are no files in the directory
shopt -s nullglob

for f in *.tar.gz.enc
do
	echo "Decrypting file - $f"
        openssl enc -d -aes-256-cbc -salt -in $f -out ${f%%.*} -pass pass:${db_password}
	echo "Expanding contents of file - ${f%%.*}"
	# --- use p to preserve permissions in the untarring
	tar -pxzf ${f%%.*} -C $DOCS
	echo "Cleanup, deleting files - $f and ${f%%.*}"
	rm $f
	rm ${f%%.*}
done

echo "Changing directories to ${DOCS}"
# --- thats where all the files have been extracted including the OscarBackup.sql
cd ${DOCS}

if [ -f OscarBackup.sql.gz ] ; then
	gunzip OscarBackup.sql.gz
	echo "Loading backup database into mysql... you might have time for a coffee"
	mysql -uroot -p${db_password} ${db_name} < OscarBackup.sql 
	echo "Cleanup, deleting OscarBackup.sql... its huge"
	rm OscarBackup.sql
else
	echo "Failed, unable to find the Backup sql"

fi
