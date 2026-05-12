#!/bin/sh
# required parameters : $1=user $2=password $3=databasename
if [ $# -ne 3 ] && [ $# -ne 4 ]; then
 	echo "Usage: ./createdatabase_on.sh [database user] [database password] [database name]"
  echo "or [database user] [database password] [database name] suppressPwdGen"
	exit
fi
if [ $# -ne 3 ]; then
 	./createdatabase_generic.sh "$1" "$2" "$3" on 9 "$4"
	exit
fi
./createdatabase_generic.sh "$1" "$2" "$3" on 9 
