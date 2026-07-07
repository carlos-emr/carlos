#!/bin/sh

##CREATE DATABASE
## Dependency on MariaDb or MySQL

if [ -z "$6" ]; then
  # 6th parameter is not set so plan to create random password and pin
  # Check Apache htpasswd dependency (bash does not bcrypt)
  if ! command -v htpasswd >/dev/null 2>&1; then
    echo "Error: htpasswd not found; please install apache2-utils" >&2
    exit 1
  fi
else
  echo "using default password for DEVELOPMENT use only"
fi


USER=$1
PASSWORD=$2
DATABASE_NAME=$3

# should be "on" or "bc" corresponding to the oscarinit_XX.sql XX qualifier
LOCATION=$4

# should be "9" or "10" corresponding to the icdXX.sql qualifier
ICD=$5

mysqladmin -u$USER -p$PASSWORD create $DATABASE_NAME

mysql_cmd="mysql -u$USER -p$PASSWORD $DATABASE_NAME"
mysql_cmd_quiet="mysql -N -s -u$USER -p$PASSWORD $DATABASE_NAME"

echo "grant all on $DATABASE_NAME.* to $USER@localhost identified by \"$PASSWORD\"" | $mysql_cmd

echo 'updating character set to utf8'
echo "alter database $DATABASE_NAME DEFAULT CHARACTER SET utf8 DEFAULT COLLATE utf8_general_ci" | $mysql_cmd

echo 'loading oscarinit.sql...'
$mysql_cmd < oscarinit.sql
echo "loading oscarinit_$LOCATION.sql..."
$mysql_cmd < oscarinit_$LOCATION.sql
echo 'loading oscardata.sql...'
$mysql_cmd < oscardata.sql
echo 'loading oscardata_additional.sql...'
$mysql_cmd < oscardata_additional.sql
echo "loading oscardata_$LOCATION.sql..."
$mysql_cmd < oscardata_$LOCATION.sql

if [ $LOCATION = 'bc' ]; then
  echo 'loading bc_billingServiceCodes.sql...'
  $mysql_cmd < bc_billingServiceCodes.sql

  echo 'loading bc_professionalSpecialists.sql...'
  $mysql_cmd < bc_professionalSpecialists.sql

  echo 'loading bc_pharmacies.sql...'
  $mysql_cmd < bc_pharmacies.sql
else
  echo 'loading olisinit.sql...'
  cd olis
  $mysql_cmd < olisinit.sql
  cd ..
fi

echo "loading icd$ICD.sql..."
$mysql_cmd < icd$ICD.sql

cd caisi
echo 'loading initcaisi.sql...'
$mysql_cmd < initcaisi.sql
echo 'loading initcaisidata.sql...'
$mysql_cmd < initcaisidata.sql
cd ..

echo "loading icd${ICD}_issue_groups.sql..."
$mysql_cmd < icd${ICD}_issue_groups.sql
echo 'loading measurementMapData.sql...'
$mysql_cmd < measurementMapData.sql

echo "loading oscarinit_2025.sql"
$mysql_cmd < oscarinit_2025.sql

echo 'all done!'
echo 'the default user is carlosdoc'

if [ -z "$6" ]; then
  newpassword=$(tr -cd '[:alnum:]' < /dev/urandom | fold -w10 | head -n 1)
  bhash=$(htpasswd -bnBC 12 carlosdoc "${newpassword}" | cut -d: -f2)
  if [ -z "${bhash}" ]; then
    echo 'ERROR: failed to generate bcrypt hash with htpasswd'
    exit 1
  fi
  bhash="{bcrypt}${bhash}"
  password_update_rows=$($mysql_cmd_quiet <<SQL
  UPDATE security SET password='${bhash}' WHERE user_name='carlosdoc';
  SELECT ROW_COUNT();
SQL
  ) || {
    echo 'ERROR: failed to update generated password for carlosdoc' >&2
    exit 1
  }
  if [ "${password_update_rows}" -ne 1 ]; then
    echo "ERROR: expected to update exactly one carlosdoc password row, but updated ${password_update_rows} rows" >&2
    exit 1
  fi
  newpin=$(tr -cd '0-9' < /dev/urandom | fold -w4 | head -n 1)
  pin_update_rows=$($mysql_cmd_quiet <<SQL
  UPDATE security SET pin='${newpin}' WHERE user_name='carlosdoc';
  SELECT ROW_COUNT();
SQL
  ) || {
    echo 'ERROR: failed to update generated PIN for carlosdoc' >&2
    exit 1
  }
  if [ "${pin_update_rows}" -ne 1 ]; then
    echo "ERROR: expected to update exactly one carlosdoc PIN row, but updated ${pin_update_rows} rows" >&2
    exit 1
  fi

  # Expire the password
  echo 'expiring credentials (password set to expire in 1 month for security)'
  expire_update_rows=$($mysql_cmd_quiet <<SQL
  UPDATE security
  SET date_ExpireDate=DATE_ADD(CURDATE(), INTERVAL 1 MONTH), b_ExpireSet=1
  WHERE user_name='carlosdoc';
  SELECT ROW_COUNT();
SQL
  ) || {
    echo 'ERROR: failed to set credential expiry for carlosdoc' >&2
    exit 1
  }
  if [ "${expire_update_rows}" -ne 1 ]; then
    echo "ERROR: expected to update exactly one carlosdoc expiry row, but updated ${expire_update_rows} rows" >&2
    exit 1
  fi

  echo "password ${newpassword}"
  echo "pin ${newpin}"
  echo '***IMPORTANT: WRITE THESE CREDENTIALS DOWN***'
else
  echo "password carlos2026"
  echo "pin 2026"
fi
