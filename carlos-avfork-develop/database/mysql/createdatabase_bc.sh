#!/bin/sh
# required parameters : $1=user $2=password $3=databasename
if [ "$#" -ne 3 ]; then
  echo "Usage: ./createdatabase_bc.sh [database user] [database password] [database name]"
  exit
fi
./createdatabase_generic.sh $@ bc 9