#!/bin/sh
# required parameters : $1=user $2=password $3=databasename
if [ $# -ne 3 ] && [ $# -ne 4 ]; then
 	echo "Usage: ./createdatabase_on.sh [database user] [database password] [database name]"
  echo "or [database user] [database password] [database name] supressPwdGen"
	exit
fi
./createdatabase_generic.sh $@ on 9
