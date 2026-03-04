#!/bin/bash

SENDER=marc
KEY=test

DBNAME=oscar_15
USERNAME=oscar
PASSWORD=oscar

rm -f results.txt

#run query
echo "SELECT count(*) from drugs where create_date >= DATE_SUB(NOW(), INTERVAL 30 day) and customName is not NULL;" | mysql -u $USERNAME --password=$PASSWORD $DBNAME | tail -1 >  results.txt

DATA=`cat results.txt`

#ftp upload the file to oscar
wget "https://download.oscar-emr.com/MedispanQueryService/uploadResults.jsp?sender=$SENDER&key=$KEY&data=$DATA"

