#!/bin/bash

SENDER=marc
KEY=test

DBNAME=oscar_15
USERNAME=oscar

# DB_PASSWORD must be set in the environment before running this script.
# Example: export DB_PASSWORD=yourpassword
# Or add to /etc/environment: DB_PASSWORD=yourpassword
# Or add to your shell profile (~/.bashrc or ~/.profile): export DB_PASSWORD=yourpassword
if [ -z "${DB_PASSWORD}" ]; then
    echo "ERROR: DB_PASSWORD is not set." >&2
    echo "Set it before running this script, e.g.:" >&2
    echo "  export DB_PASSWORD=yourpassword" >&2
    echo "  or add it to /etc/environment or your shell profile (~/.bashrc, ~/.profile)" >&2
    exit 1
fi
PASSWORD="${DB_PASSWORD}"

rm -f results.txt

#run query
echo "SELECT count(*) from drugs where create_date >= DATE_SUB(NOW(), INTERVAL 30 day) and customName is not NULL;" | MYSQL_PWD="${PASSWORD}" mysql -u $USERNAME $DBNAME | tail -1 > results.txt

DATA=`cat results.txt`

#upload results using POST to avoid sensitive data in URL/logs
wget --post-data="sender=${SENDER}&key=${KEY}&data=${DATA}" "https://download.oscar-emr.com/MedispanQueryService/uploadResults.jsp"

