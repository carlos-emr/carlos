#!/bin/bash

SENDER=marc
KEY=test

DBNAME=oscar_15
USERNAME=oscar
# Set DB_PASSWORD environment variable before running this script
PASSWORD="${DB_PASSWORD:-}"

rm -f results.txt

#run query
echo "SELECT count(*) from drugs where create_date >= DATE_SUB(NOW(), INTERVAL 30 day) and customName is not NULL;" | MYSQL_PWD="${PASSWORD}" mysql -u $USERNAME $DBNAME | tail -1 > results.txt

DATA=`cat results.txt`

#upload results using POST to avoid sensitive data in URL/logs
wget --post-data="sender=${SENDER}&key=${KEY}&data=${DATA}" "https://download.oscar-emr.com/MedispanQueryService/uploadResults.jsp"

