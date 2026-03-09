#!/usr/bin/env bash
# backup.sh
# a script file for CARLOS that copies compressed archives
# that have been date stamped for easy sorting
# to the backup folder usually where a browser can access
# from a given MySQL database and documents folder

# Modified from a script by Jay Gallagher by Thom Luxford
# Adapted for CARLOS by Peter Hutten-Czapski
# Version 20

# The script has two modes.  A full backup and a daily backup.
# parameters in your oscar_context.properties file should be in the form:
# db_name= oscar_context
# db_username= oscar
# db_password= password
# backup_path = /usr/oscar_backup/oscar_context/
# BASE_DOCUMENT_DIR: /var/lib/OscarDocument
# BACKUPSERVERS= user@192.168.1.151:~/backup/ user@mybackuphostname.org:~/backup/
# COMMON_BACKUPSERVER_PORT= 22
# 
# The full backup backs up the database and the whole OscarDocument/oscar_context directory, it omits the document_cache.
# The incremental method backups up the database and only files from the document directory that
# are newer than the last full backup. It omits CarlosBackup.sql*, oscar.war and document_cache.
# The database is dumped and compressed separately from the documents.
# So to recover you will need the full backup and the latest daily backup files, document and database
# 
# Set the EMAIL variable to be notified if you are running out of diskspace

USAGE=" call like: $0\n or $0 -r to upload "


PROGRAM=carlos

PATH=$PATH:/bin/:/usr/bin/
SCRIPT_FILE=$(basename "$0")
LOCKDIR=/tmp/${SCRIPT_FILE}.lock
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
SCRIPT_PATH="${SCRIPT_DIR}/${SCRIPT_FILE}"

BWLIMIT="0" # rsynch bandwith limit in kbps, 0 means no limit


# --- sanity check run as root
if [ "$(id -u)" != "0" ];
then
        echo "The ${SCRIPT_FILE} script must be run as root" 1>&2
        exit 1
fi

# --- prevent more than one instance running at a time
if ! mkdir "$LOCKDIR"; then
    echo "The ${SCRIPTFILE} script is already running." 1>&2
    exit 1
fi
# Remove lockdir when the script finishes, or when it receives a signal
trap 'rm -rf "$LOCKDIR"' 0 1 2   # remove directory when script finishes EXIT(0), terminal closes SIGHUP(1) or SIGINT(2) Ctrl-C


function getTimeStamp() {
        date '+%F-%T' 2>> $LOG_ERR
}
function errorExit() {
        echo -e "ERROR: $1\n`getTimeStamp`" >> $LOG_ERR
        exit
}
function confirmVar() {
  if [ -z "$1" ]; then
  errorExit "USAGE: $USAGE"
  fi
}
function checkFile { # returns true if File exists and size is greater than 0, false otherwise
        FULL_PATH_TO_FILE=$1
        if [ -s $FULL_PATH_TO_FILE ]
        then
               return 0 #true
        else
               return 1 #false
        fi
}


if [ -z "$BWLIMIT" ]; then
  BWLIMIT=0
fi

# TOMCAT is used to find and archive the war and to set ownership of the backup
TOMCAT=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $1 }')
if [ -f /usr/share/tomcat10/bin/version.sh ] ; then
        TOMCAT=tomcat10
        TOMCAT_USER=tomcat
        else
        if [ -f /usr/share/tomcat9/bin/version.sh ] ; then
                TOMCAT=tomcat9
                TOMCAT_USER=tomcat
            else
            if [ -f /usr/share/tomcat8/bin/version.sh ] ; then
                TOMCAT=tomcat8
                TOMCAT_USER=tomcat8
                else
                if [ -f /usr/share/tomcat7/bin/version.sh ] ; then
                    TOMCAT=tomcat7
                    TOMCAT_USER=tomcat7
                fi
            fi
        fi
fi

TMPBACKDIR=/root/backuptmp
TOMCAT_OWNER=${TOMCAT_USER}.${TOMCAT_USER}
CATALINA_HOME=/usr/share/${TOMCAT}
SRC=/var/lib/carlos-emr
#DAYS_TO_KEEP=20
#FILES_TO_ADD=('/var/log/syslog' '/var/log/auth.log' '/var/log/tomcat6/catalina.out' '/var/log/mysql/error.log' '/var/log/puppet.log')

OUTFILE=''
PROPFILE=$CATALINA_HOME/carlos.properties
COMPLETE_BACKUP=0
NO_GZIP_MYSQLDUMP_FLAG=0
REMOTE_UPLOAD_FLAG=0
# Admin email for being notified that backup is running out of space!
#EMAIL=valid.admin.email@server.org

# NOTE review or change the code execution as some options will trump others
while getopts ":c:u:p:d:b:t:f:n:r" optionName; do
case "$optionName" in
  f) PROPFILE="$OPTARG";;
  n) NO_GZIP_MYSQLDUMP_FLAG=1;;
  r) REMOTE_UPLOAD_FLAG=1;;
  c) COMPLETE_BACKUP=1;;
  u) db_username="$OPTARG";;
  p) db_password="$OPTARG";;
  d) DATABASE="$OPTARG";;
  b) BASE_DOCUMENT_DIR="$OPTARG";;
  t) TMPBACKDIR="$OPTARG";;
esac
done

DATABASE="${DATABASE:-$(sed '/^\#/d' $PROPFILE | grep 'db_name'  | tail -n 1 | cut -d "=" -f2- | cut -d "?" -f1 - | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"
BACKUPDIR=$(sed '/^\#/d' $PROPFILE | grep 'backup_path'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
BACKUPSERVERS=$(grep BACKUPSERVERS $PROPFILE | sed -e "s/.*[=:][[:space:]]*//" -e "s/#.*//")
COMMON_BACKUPSERVER_PORT=$(grep COMMON_BACKUPSERVER_PORT $PROPFILE | sed -e "s/.*[=:][[:space:]]*//" -e "s/#.*//")
db_username="${db_username:-$(sed '/^\#/d' $PROPFILE | grep 'db_username'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"
db_password="${db_password:-$(sed '/^\#/d' $PROPFILE | grep 'db_password'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"
BASE_DOCUMENT_DIR="${BASE_DOCUMENT_DIR:-$(sed '/^\#/d' $PROPFILE | grep 'BASE_DOCUMENT_DIR'  | tail -n 1 | cut -d "=" -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"
DAYS_TO_KEEP="${DAYS_TO_KEEP:-$(sed '/^\#/d' $PROPFILE | grep 'DAYS_TO_KEEP'  | tail -n 1 | cut -d '=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"
EMAIL="${EMAIL:-$(sed '/^\#/d' $PROPFILE | grep 'ADMIN_EMAIL'  | tail -n 1 | cut -d '=' -f2- | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')}"

LOG_PATH=$BACKUPDIR
if [ -z "$LOG_PATH" ]; then
  LOG_PATH='/var/log'
fi
mkdir -p ${LOG_PATH}
LOG_FILE="backup.log"
if [ -z "$LOG_FILE" ]; then
  LOG_FILE='syslog'
fi
BACKUP_SCRIPT_USER=$(whoami)
if [ -z "$BACKUP_SCRIPT_USER" ]; then
  BACKUP_SCRIPT_USER='root'
fi

LOG_ERR="${LOG_PATH}/${LOG_FILE}"


confirmVar ${db_password}
export DB_PASSWORD_6606913a="${db_password}"
MyISAM_TABLES=$(MYSQL_PWD="${db_password}" mysql -s -u"${db_username}" -e "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DATABASE}' AND ENGINE='MyISAM';")

IGNORED_TABLES_STRING=""
T=(${MyISAM_TABLES})
for TABLE in "${T[@]}"
do :
        IGNORED_TABLES_STRING+=" --ignore-table=${DATABASE}.${TABLE}"
done

TOMCAT_HOME="/var/lib/${TOMCAT}"
confirmVar ${DATABASE}
WAR_FILE="${TOMCAT_HOME}/webapps/carlos.war"
confirmVar ${BACKUPDIR}
ARCH="${BACKUPDIR}/archive"
#--opt is shorthand for --add-drop-table --add-locks --create-options --disable-keys --extended-insert --lock-tables --quick --set-charset.
DUMP_OPTIONS="--opt --dump-date --log-error=${LOG_ERR} --single-transaction --skip-lock-tables  ${IGNORED_TABLES_STRING} "
#if you do bin logging you can use the following"--single-transaction --routines --log-error=${LOG_ERR} --master-data=1 --flush-logs"
ENC_OPTIONS="-aes-256-cbc -salt"
# -aes-256-cbc is the encryption algorythem
# -salt uses a salt (randomly generated)
#ENC_OPTIONS="-aes-256-cbc -salt -pbkdf2"
# for OpenSSL 1.1.1 Sept 2018 -pbkdf2 to use PBKDF2 algorithm with default iteration count unless otherwise specified.
confirmVar ${BWLIMIT}
RSYNC_OPTIONS="--partial -v -v"
KEXALGORITHMS="diffie-hellman-group-exchange-sha256,diffie-hellman-group-exchange-sha1,diffie-hellman-group14-sha1"
confirmVar $KEXALGORITHMS

SSH_OPTIONS="ssh -C -o 'Protocol=2' -o 'KexAlgorithms $KEXALGORITHMS'"

DY=$(date +%d)
DATE_TIME="`getTimeStamp`"
SHUTDOWN_WAIT=120

echo " >> ${DATE_TIME} Backup Start" >> $LOG_ERR

pid=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $2 }')
echo "${TOMCAT} is running on pid ${pid}" >> $LOG_ERR

#if [ -n "$pid" ]; then
#		service ${TOMCAT} stop
#		let kwait=$SHUTDOWN_WAIT
#		count=0;
#		until [ `ps -p $pid | grep -c $pid` = '0' ] || [ $count -gt $kwait ]
#		do
#			echo -n -e "\nwaiting for processes to exit";
#			sleep 1
#			let count=$count+1;
#		done
#	else
#		echo "#########" `date` "#########" 1>> $LOG_ERR
#		echo "WARNING ${TOMCAT} pid not found.  Is tomcat running...?" >> $LOG_ERR		
#fi
#if [ $count -gt $kwait ]; then
#		echo "Tomcat process had to be killed" >> $LOG_FILE
#		kill -9 $pid
#        sleep ${SHUTDOWN_WAIT}
#fi


mkdir -pv ${BACKUPDIR} >> $LOG_ERR 2>&1
echo "PROPFILE=${PROPFILE} DATABASE=${DATABASE} BACKUPDIR=${BACKUPDIR}" >> $LOG_ERR
if [ -z "${DAYS_TO_KEEP}" ]; then
  DAYS_TO_KEEP=30
fi
if [ -z "${COMMON_BACKUPSERVER_PORT}" ]; then
  COMMON_BACKUPSERVER_PORT=22
fi

#confirmVar $BASE_DOCUMENT_DIR
if [ -z "${BASE_DOCUMENT_DIR}" ]; then
  BASE_DOCUMENT_DIR="${SRC}/CarlosDocument"
fi
echo "BASE_DOCUMENT_DIR=${BASE_DOCUMENT_DIR}" >> $LOG_ERR
echo "DAYS_TO_KEEP=${DAYS_TO_KEEP}" >> $LOG_ERR

#Check for a Full.CarlosBackup
test $COMPLETE_BACKUP = 0 && if [ $(ls -tr ${BACKUPDIR}/Full.CarlosBackup.${DATABASE}.*.tar.gz.enc 2>> $LOG_ERR|tail -n1) ]; then
  #find the most recent
  LAST_FULL_BACKUP_FILE=$(ls -tr ${BACKUPDIR}/Full.CarlosBackup.${DATABASE}.*.tar.gz.enc | tail -n1) >> $LOG_ERR 2>&1
  #size greater than zero, if not, override
  checkFile $LAST_FULL_BACKUP_FILE || COMPLETE_BACKUP=1
else
  #no full backup files that match the pattern, create
  COMPLETE_BACKUP=1
fi
#test

df -H | grep -vE '^Filesystem|tmpfs|cdrom' | awk '{ print $5 " " $1 " " $6 }' | while read output;
do
  echo $output
  mntpt=$(echo $output | awk '{ print $3 }' )
  if [ $mntpt = "/" ]; then
      usep=$(echo $output | awk '{ print $1}' | cut -d'%' -f1  )
       echo "root is ${usep} full"  >> $LOG_ERR
      partition=$(echo $output | awk '{ print $2 }' )
      if [ $usep -ge 90 ]; then
	    ## if your disk is 400G 90% leaves 40G
	    echo "Running out of space \"$partition ($usep%)\" on $(hostname) as on $(date)" >> $LOG_ERR 2>&1
        	echo "Running out of space \"$partition ($usep%)\" on $(hostname) as on $(date)" | mail -s "Alert: Almost out of disk space $usep%" ${EMAIL}
      fi
      if [ $usep -ge 95 ]; then
	    ## for a disk or 400G 95% leaves 20G
	    echo "Purging some backups to make room" >> $LOG_ERR 2>&1
        	echo "Purging files on \"$partition ($usep%)\" on $(hostname) as on $(date)" | mail -s "Alert: Purging files $usep%" ${EMAIL}
	    ##Purge older back up files leaving just yesterdays and the full one
	    find ${BACKUPDIR} -name "CarlosBackup.${DATABASE}.*.tar.gz.enc" -type f -mtime +1|sort|head --lines='-2'| xargs /bin/rm -vf >> $LOG_ERR 2>&1
      fi
  fi
done


echo ${DATABASE}
echo "Log file =$LOG_ERR"

if [ ${NO_GZIP_MYSQLDUMP_FLAG} == 1 ]; then
  MYSQL_PWD="${db_password}" mysqldump ${DUMP_OPTIONS} ${DATABASE} -u${db_username} > $BASE_DOCUMENT_DIR/carlos/CarlosBackup.sql 2>> $LOG_ERR
  MYSQL_PWD="${db_password}" mysqldump ${DATABASE} ${MyISAM_TABLES} -u${db_username} > $BASE_DOCUMENT_DIR/carlos/MyISAMBackup.sql 2>> $LOG_ERR
  MYSQL_PWD="${db_password}" mysqldump ${DUMP_OPTIONS} drugref -u${db_username} > $BASE_DOCUMENT_DIR/carlos/drugref.sql 2>> $LOG_ERR
else
  MYSQL_PWD="${db_password}" mysqldump ${DUMP_OPTIONS} ${DATABASE} -u${db_username}|gzip --rsyncable -c -9 > $BASE_DOCUMENT_DIR/carlos/CarlosBackup.sql.gz 2>> $LOG_ERR
  MYSQL_PWD="${db_password}" mysqldump ${DATABASE} ${MyISAM_TABLES} -u${db_username} |gzip --rsyncable -c -9 > $BASE_DOCUMENT_DIR/carlos/MyISAMBackup.sql.gz 2>> $LOG_ERR
  MYSQL_PWD="${db_password}" mysqldump ${DUMP_OPTIONS} drugref -u${db_username}|gzip --rsyncable -c -9 > $BASE_DOCUMENT_DIR/carlos/drugref.sql.gz 2>> $LOG_ERR
fi
cp -u $WAR_FILE $BASE_DOCUMENT_DIR/carlos/ 2>> $LOG_ERR
cp -u $PROPFILE $BASE_DOCUMENT_DIR/carlos/ 2>> $LOG_ERR
cp -u $SCRIPT_PATH $BASE_DOCUMENT_DIR/carlos/ 2>> $LOG_ERR

CACHEDIR="$BASE_DOCUMENT_DIR/carlos/document_cache"
mkdir -pv $CACHEDIR >> $LOG_ERR 2>&1
CACHEDIR_TAG="${CACHEDIR}/CACHEDIR.TAG"
checkFile ${CACHEDIR_TAG} || echo -e "Signature: 8a477f597d28d172789f06886806bc55\n# This file is a cache directory tag\n" > ${CACHEDIR_TAG}

if [ ${COMPLETE_BACKUP} = 1 ]; then 
  echo "NEW Full.CarlosBackup" >> $LOG_ERR
  OUTFILE=${BACKUPDIR}/Full.CarlosBackup.${DATABASE}.${DATE_TIME}.tar.gz.enc 2>> $LOG_ERR
  cd "$BASE_DOCUMENT_DIR" 2>> $LOG_ERR || { echo "Failed to cd to $BASE_DOCUMENT_DIR" >> $LOG_ERR; exit 1; }
  tar --exclude-caches -c carlos 2>> $LOG_ERR | gzip -v -c -9 2>> $LOG_ERR | openssl enc $ENC_OPTIONS -pass env:DB_PASSWORD_6606913a > $OUTFILE 2>> $LOG_ERR
  ls -l $OUTFILE --time-style=long-iso | awk '{ print  "<OUTFILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
	if [ ${REMOTE_UPLOAD_FLAG} == 1 ]; then
		for REMOTE_SERVER in "${BACKUPSERVERS[@]}"
		do
		nice rsync -t $RSYNC_OPTIONS --bwlimit=${BWLIMIT} --rsh="$SSH_OPTIONS -p $COMMON_BACKUPSERVER_PORT" $OUTFILE $REMOTE_SERVER >> $LOG_ERR 2>&1
		done
	fi
  chown $TOMCAT_OWNER $OUTFILE 2>> $LOG_ERR
  ##Purge older back up files
find ${BACKUPDIR} -name "*.${DATABASE}.*.tar.gz.enc" -type f -mtime +$DAYS_TO_KEEP|sort|head --lines='-2'| xargs /bin/rm -vf >> $LOG_ERR 2>&1
else
  LAST_FULL_BACKUP_FILE=$(ls -tr ${BACKUPDIR}/Full.CarlosBackup.${DATABASE}.*.tar.gz.enc | tail -n1) 2>> $LOG_ERR
  confirmVar $LAST_FULL_BACKUP_FILE
  OUTFILE="${BACKUPDIR}/CarlosBackup.${DATABASE}.${DATE_TIME}.Document.tar.gz.enc" 2>> $LOG_ERR
  DB_OUTFILE="${BACKUPDIR}/CarlosBackup.${DATABASE}.${DATE_TIME}.Database.tar.gz.enc" 2>> $LOG_ERR
  cd "$BASE_DOCUMENT_DIR/carlos" 2>> $LOG_ERR || { echo "Failed to cd to $BASE_DOCUMENT_DIR/carlos" >> $LOG_ERR; exit 1; }
  tar -c CarlosBackup.sql* MyISAMBackup.sql* 2>> $LOG_ERR | gzip -v -c -9 2>> $LOG_ERR | openssl enc $ENC_OPTIONS -pass env:DB_PASSWORD_6606913a > $DB_OUTFILE 2>> $LOG_ERR
  find . -type f -newer $LAST_FULL_BACKUP_FILE -print|grep -Ev "^\./document_cache|^\./CarlosBackup.sql$|^\./CarlosBackup.sql.gz$|^\./carlos.war" > filelist.txt 2>> $LOG_ERR
  tar -c -T filelist.txt 2>> $LOG_ERR | gzip -v -c -9 2>> $LOG_ERR | openssl enc $ENC_OPTIONS -pass env:DB_PASSWORD_6606913a > $OUTFILE 2>> $LOG_ERR

  getTimeStamp >> $LOG_ERR 2>&1
  if [ ${REMOTE_UPLOAD_FLAG} == 1 ]; then
    for REMOTE_SERVER in ${BACKUPSERVERS[@]} 
		do
		  ls -l $DB_OUTFILE --time-style=long-iso | awk '{ print  "<DB_OUTFILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
		  nice rsync $RSYNC_OPTIONS --bwlimit=${BWLIMIT} --rsh="$SSH_OPTIONS -p $COMMON_BACKUPSERVER_PORT" $DB_OUTFILE $REMOTE_SERVER >> $LOG_ERR 2>&1
		  getTimeStamp >> $LOG_ERR 2>&1
		  ls -l $OUTFILE --time-style=long-iso | awk '{ print  "<OUTFILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
		  nice rsync $RSYNC_OPTIONS --bwlimit=${BWLIMIT} --rsh="$SSH_OPTIONS -p $COMMON_BACKUPSERVER_PORT" $OUTFILE $REMOTE_SERVER >> $LOG_ERR 2>&1
		  getTimeStamp >> $LOG_ERR 2>&1
    done
    for REMOTE_SERVER in ${BACKUPSERVERS[@]} 
    do
      ls -l $LAST_FULL_BACKUP_FILE --time-style=long-iso | awk '{ print  "<LAST_FULL_BACKUP_FILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
      killall -q -v -u $BACKUP_SCRIPT_USER -o 1h rsync - >> $LOG_ERR 2>&1 && sleep 60 >> $LOG_ERR 2>&1  
      test ${BWLIMIT} -gt 1 && BWLIMIT=$(($BWLIMIT * 3 / 4))
      nice rsync --append-verify --backup -t $RSYNC_OPTIONS --bwlimit=${BWLIMIT} --rsh="$SSH_OPTIONS -p $COMMON_BACKUPSERVER_PORT" $LAST_FULL_BACKUP_FILE $REMOTE_SERVER >> $LOG_ERR 2>&1
      getTimeStamp >> $LOG_ERR 2>&1
    done
  else
    echo "Not Uploading, REMOTE_UPLOAD_FLAG=0" >> $LOG_ERR
    ls -l $DB_OUTFILE --time-style=long-iso | awk '{ print  "<DB_OUTFILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
    ls -l $OUTFILE --time-style=long-iso | awk '{ print  "<OUTFILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
    ls -l $LAST_FULL_BACKUP_FILE --time-style=long-iso | awk '{ print  "<LAST_FULL_BACKUP_FILE SIZE=\""$5 "\" filename=\""  $8 "\" /> "}' >> $LOG_ERR
    getTimeStamp >> $LOG_ERR 2>&1
  fi
  chown $TOMCAT_OWNER $OUTFILE 2>> $LOG_ERR
  chown $TOMCAT_OWNER $DB_OUTFILE 2>> $LOG_ERR
  ####ARCHIVE THE Database snapshot on the FIRST OF THE MONTH BACKUP
  if [ "$DY" == "01" ]; then
    mkdir -pv $ARCH >> $LOG_ERR 2>&1
    cp -vb $DB_OUTFILE ${ARCH}/CarlosBackup.${DATABASE}.${DATE_TIME}.Database.tar.gz.enc.archive >> $LOG_ERR 2>&1
  fi
  ##Purge older back up files 
find ${BACKUPDIR} -name "*.${DATABASE}.*.tar.gz.enc" -type f -mtime +$DAYS_TO_KEEP|sort |head --lines='-2'| xargs /bin/rm -vf >> $LOG_ERR 2>&1
fi

chown -R $TOMCAT_OWNER $BASE_DOCUMENT_DIR/carlos 2>> $LOG_ERR

DATE_TIME="`getTimeStamp`"

pid=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $2 }')
echo ${pid}
if [ -n "$pid" ]; then
        echo "${TOMCAT} is running on PID = ${pid}" >> $LOG_ERR
else
        echo "tomcat is being restarted now"  >> $LOG_ERR
        service ${TOMCAT} restart 
fi
echo " << ${DATE_TIME} Backup Stop" >> $LOG_ERR

echo "" >> $LOG_ERR
