#!/bin/bash
# ExcellerisDownload.sh
# a script file for OSCAR that copies lab reports from Excelleris
# that have been date stamped for easy sorting
# to the HL7 folder from where they can be uploaded by Mule
# as per Excelleris EMR Interface Guide v. 1.3.3 Oct 2023

# Modified from a script by Tom Le and Muki
# Adapted for OSCAR 19 by Peter Hutten-Czapski Aug 2024
# Version 1.0.5 Oct 12, 2024

<<'###BLOCK-COMMENT'
INSTRUCTIONS
* Place this script into a DIRECTORY in your server and make it executable

* Copy the Excelleris PFX file for Clinic to a subdirectory with structure similar to
<SCRIPT DIR>/excelleris_download/cert/QA Peter Hutten Czapski MPC.pfx

* Create a config_inc.txt file in the top <SCRIPT DIR> with the following contents:

CONTEXT="<Clinic Name>"
USERNAME="<Clinic Excelleris User ID>"
PASSWORD="<Clinic Excelleris Password>"
PFX="<Excelleris supplied PFX file>"
CERT_PASS="<Clinic Excelleris certificate passphrase>"
HL7URLPATH="<test or production URI for the given province eg https://api.on.excelleris.com/hl7pull.aspx>"
MULE_HOME="<path to mule-1.3.3 directory that you installed>"
MULE_HL7PATH="<path to downloaded lifelabs reports from your Mule configuration>"
MULE_ERRORS="<path you configured/will configure for reports that fail to upload>"
MULE_DONE="<path you configured/will configure for uploaded files>"
MULE_LOG="<path to Mule log file>"
LOG_FILE="<path to Downloading script log file>"
EMAIL="<address@which2sendERRORS>"
SLEEP=<no quotes just the number of seconds it takes for the OSCAR server to upload the results>

* NOTES
CONTEXT is not used by the script but is included to reduce risks of mixing up credentials
The passed HL7URLPATH for ONTARIO for testing is
https://api.ontest.excelleris.com/hl7pull.aspx
for production is
https://api.on.excelleris.com/hl7pull.aspx
for BC simply replace on with bc
https://api.bc.excelleris.com/hl7pull.aspx

* Setup a crontab to run the script

* Setup automated uploading for the received files eg hl7_file_management
https://bitbucket.org/oscaremr/hl7_file_management/src/master/

###BLOCK-COMMENT

# Script starts here
set -e

print_usage() {
  printf "Usage: As root run $0 -h for this text\n"
  printf "$0 -a to just activate mule uploads\n"
  printf "$0 -s to supress mule uploads\n"
  printf "$0 -v for verbose logging\n"
  exit;
}

verbose='false'
aflag=''
sflag=''
while getopts 'asvh' flag; do
  case "${flag}" in
    a) aflag='true' ;;
    s) sflag='true' ;;
    v) verbose='true' ;;
    h) print_usage ;;
    *) print_usage ;;
  esac
done

if [ "$EUID" -ne 0 ]
  then echo "Please run as root"
  exit
fi

# get the script's source file pathname, strip to just the path portion, cds to that path
# THEN use pwd to return the (effectively) full path of the script
# WARNING this is not robust enough to deal with symbolic links
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# source excelleris credentials for the clinic and the URL
. $SCRIPT_DIR/config_inc.txt

# send stdout and stderr output into the log file
exec > >(tee -a ${LOG_FILE}) 2>&1
echo `date '+%F-%T'` - ">>>>> running" $0

# get some info for date stamping files
YY=$(date +%Y)
MM=$(date +%m)
DD=$(date +%d)
TIME=$(date +%H%M%S)

# params as if you were using the web browser
LOGINPARAMS="Page=Login&Mode=Silent&UserID=${USERNAME}&Password=${PASSWORD}"
# Excelleris spec requires the Pending=Yes for the pull
PULLXMLPARAMS="Page=HL7&Query=NewRequests&Pending=Yes"
PULLXMLPARAMS2="Page=HL7&Query=NewRequests"
ACKHL7PULLPARAMS="Page=HL7&ACK="
LOGOUTPARAMS="Logout=Yes"
# Excelleris spec requires a user agent to identify destination software, here as OSCAR19
USER_AGENT="Mozilla\/5.0 (Windows NT 10.0; OSCAR19; 1.0.4) Gecko\/20100101 Firefox\/128.0"

DOWNLOAD_DIR="$SCRIPT_DIR/excelleris_download"
HL7_OUTPUTDIR="$DOWNLOAD_DIR/hl7"
if [ ! -d "$HL7_OUTPUTDIR" ]; then
	mkdir -p $HL7_OUTPUTDIR
fi

OUTPUTFILE="$HL7_OUTPUTDIR/${YY}${MM}${DD}-${TIME}.xml"
OUTPUTFILE2="$HL7_OUTPUTDIR/${YY}${MM}${DD}-${TIME}b.xml"
COOKIEFILE="${DOWNLOAD_DIR}/cookie.txt"
CERTFILE="${DOWNLOAD_DIR}/cert/${PFX}:${CERT_PASS}"

function notify_error () {
    DownSubject="Labs Auto-Downloader Status - Failure"
    error="Labs Auto-Downloader ON server(`hostname`) has stopped working.  Please check!!!\n"
    echo -e "$error" | mail -s "$DownSubject on $(hostname)" "$EMAIL"
    echo "Auto-Downloader failed, sent email to $EMAIL"
    echo `date '+%F-%T'` - "<<<<<< finished running script WITH ERRORS"
    echo
}

# Create a lock so that script will not run twice
(
flock -x -w 10 200 || { notify_error; exit 1; }

echo -n "Step 1: Authentication"
# login to excelleris and get coookie
# -k accept self-signed -s silent -S show errors -X POST set request to post -L accept redirection -A user agent
result=$(curl -s -S -G -L -A "$USER_AGENT" --cookie-jar "$COOKIEFILE" --data "$LOGINPARAMS" --cert-type P12 --cert "$CERTFILE" "$HL7URLPATH")

# **** check result to see if login is successful
if [[ $result == "<Authentication>AccessGranted</Authentication>" ]]
    then echo "... Authenticated"
fi
if [[ $result == "<Authentication>AccessDenied</Authentication>" ]]
    then echo "... ERROR Access Denied"
    notify_error
    exit 1
fi
if [[ $result != "<Authentication>AccessGranted</Authentication>" ]]
    then echo "... ERROR NOT Authenticated"
    notify_error
    exit 1
fi

echo -n "Step 2: Query results"
# pull results and save to file; capture exit code immediately after curl
curl -s -S -G -L -A "$USER_AGENT" --output "$OUTPUTFILE" --cookie "$COOKIEFILE" --data "$PULLXMLPARAMS" --cert-type P12 --cert "$CERTFILE" "$HL7URLPATH"
status_code=$?
filecheck="1"
if [ ${status_code} != "0" ] || [ ! -s "${OUTPUTFILE}" ] ; then
	echo "... Download failed"
    ACKHL7PULLPARAMS=$ACKHL7PULLPARAMS"Negative"
else
    # Validate HL7 payload: a successful pull contains <HL7Messages> with a <Message> element.
    # An empty pull returns <HL7Messages/> or <HL7Messages MessageCount="0"/>.
    # otherwise <HL7Messages MessageFormat="HL7" MessageCount="5" Version="2.3"><Message MsgID="1"><![CDATA[MSH...
    TMP=$(head -1 "$OUTPUTFILE")
    if echo "${TMP}" | grep -q '<Message '; then
        echo "... Downloaded Reports to ${OUTPUTFILE}"
        ACKHL7PULLPARAMS=$ACKHL7PULLPARAMS"Positive"
        filecheck="0"
    else
        echo "... No Reports to Download"
        ACKHL7PULLPARAMS=$ACKHL7PULLPARAMS"Negative"
        rm -f "$OUTPUTFILE"
    fi
fi

# else copy to file to be picked up and imported

echo -n "Step 3: Acknowledgment"
#acknowledge hl7 downloaded, ensure that you send either a positive or negative ack
result=$(curl -s -S -G -L -A "$USER_AGENT"  --cookie $COOKIEFILE --data $ACKHL7PULLPARAMS --cert-type P12 --cert "$CERTFILE" $HL7URLPATH)

# check result to see if pulled labs are set successfully
# received positive or negative ack is <HL7Messages/>
# failed processing is <HL7Messages ReturnCode="1"/>
if [ "${result}" == "<HL7Messages ReturnCode=\"0\"/>" ]
    then echo "... Acknowledgement processed"
fi
if [ "${result}" != "<HL7Messages ReturnCode=\"0\"/>" ]
    then echo "... ERROR Acknowledgement NOT received"
fi

echo "Step 4: Excelleris logout... Sent"
#logout
result=$(curl -s -S -G -L  -A "$USER_AGENT" --cookie $COOKIEFILE --data $LOGOUTPARAMS --cert-type P12 --cert "$CERTFILE" $HL7URLPATH)
# the result will be empty
rm $COOKIEFILE

if [ "${sflag}" == "true" ] ; then
    echo "... Mule suppressed with -s flag, exiting"
    exit
fi

echo -n "Step 5: Transfer by Mule"
if [ "${filecheck}" == "1" ]  && [ "${aflag}" != "true" ] ; then
    echo "... No labs to transfer, exiting"
    exit
fi

mule_status=$($MULE_HOME/bin/mule status)

if [[ "${mule_status}" != *"Mule is running"* ]] ; then
    echo "... Mule is NOT running, exiting with error."
    notify_error
    exit 1
else
    echo "... ${mule_status}"
fi

if [ -f ${OUTPUTFILE} ] ; then
    echo "copying ${OUTPUTFILE} to ${MULE_HL7PATH} "
    cp "${OUTPUTFILE}" "${MULE_HL7PATH}"
    # compress the origional outputfile
    xz -v ${OUTPUTFILE}
    ## 12-10-24_13-15-22.706_20241024-143247.xml
    #chmod 666 "${MULE_HL7PATH}/*${YY}${MM}${DD}-${TIME}.xml"
fi

end=$((SECONDS+${SLEEP}))
while [ $SECONDS -lt $end ] ; do
    #check for progress every few seconds untill end
    sleep 5
    if grep -Fxq "file: ${YY}${MM}${DD}-${TIME}.xml, Successfully Uploaded" "${MULE_LOG}" ; then
        echo "Mule successfully uploaded ${YY}${MM}${DD}-${TIME}.xml to OSCAR"
        # compress the copy of the output file in the done directory, note its name is set by Mule
        xz ${MULE_DONE}/*.xml -v
        echo `date '+%F-%T'` - "<<<<<< finished running script with clean exit."
        echo
        exit 0
    else
        if grep -Fq "file: ${YY}${MM}${DD}-${TIME}.xml could not" "${MULE_LOG}" ; then
            echo "ERROR Mule could not upload ${YY}${MM}${DD}-${TIME}.xml to OSCAR due to errors."
            notify_error
            exit 1
        fi
    fi
done

echo "ERROR ${YY}${MM}${DD}-${TIME}.xml is taking too long to upload."
echo "Check catalina.out and ${MULE_LOG} for more detail"
notify_error
exit 1

) 200>/var/lock/.labscript.exclusivelock

echo `date '+%F-%T'` - "<<<<<< finished running script with clean exit."
# add blank line for visibility
echo
exit 0
