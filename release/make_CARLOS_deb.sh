#!/bin/bash

# release/make_CARLOS_deb.sh
# makes a debian installer release from source

#====================================================================
# Copyright Peter Hutten-Czapski 2012-2026 released under the GPL v2+
#====================================================================


# for CARLOS
# v 1 - pre-release

# Debian versioning conventions don't allow _ so use .
VERSION=0.1
PREVIOUS=0.1
DEB_SUBVERSION=1

# you can tick up when a newer build of the installer is made 
# or when the release tag needs to change eg beta to RC
BUILD=alpha
REVISION=${DEB_SUBVERSION}~${BUILD}
echo REVISION=$REVISION

# --- sanity checks
if [ "$(id -u)" != "0" ];
then
	echo "This script must be run as root" 1>&2
	exit 1
fi

# Get the absolute path of the script's directory, handling symlinks and different invocation methods
SCRIPT_DIR="$(dirname "$(realpath "$0")")"
# Get the basename of the script's directory
DIR_BASENAME="$(basename "$SCRIPT_DIR")"
# Check if the directory name is "release"
if [ "$DIR_BASENAME" = "release" ]; then
    echo "Directory is release"
else
    echo "This needs to be run in release/ but its $DIR_BASENAME"
    exit;
fi
echo "#########" `date` "#########" 

PROGRAM=carlos
PACKAGE=carlos-emr
custom=true

# To ease convesion the database should be oscar_15
db_name=oscar_15

## database switches are needed to provide expected behavior for OSCAR 15
## enforce UTF-8 encoding so that foreign characters are stored 一種語言永遠不夠 
## handle 0000-00-00 date errors by rounding to 0001-01-01
## allow hibernate to alter column names
## tolerate fields without default values that are not named in the query
db_switch=\'?characterEncoding=UTF-8\\\&zeroDateTimeBehavior=round\\\&useOldAliasMetadataBehavior=true\\\&jdbcCompliantTruncation=false\'

# and the target of mvn 3 is
TARGET=carlos-0-SNAPSHOT.war

buildDateTime=date 
SHA1=""

echo "+++++++++++++++++++++++"

echo buildDateTime=$buildDateTime
echo "current date="$(date)

ICD=9

# For simplicity lets pick Tomcat 9
TOMCAT=tomcat9
#C_HOME=/var/lib/${TOMCAT}/
C_BASE=/var/lib/${TOMCAT}/
#tomcat_path=${C_HOME}
TODAY=$(date)

# used to pick up virgin properties file and if building a deb directly from source
SRC=carlos

DEBNAME="carlos_emr${VERSION}-${REVISION}"

if [ -d "$DEBNAME" ]; then
	echo prexisting directory with this build found
	SKIP_NEW_WAR=true
	rm -R ./${DEBNAME}/
else
    if [ $custom ]; then
        echo custom build
    else
        echo cleaning up prior build
	    rm ${TARGET}
        ##rm drugref2-1.0-SNAPSHOT.war
    fi
fi

echo "cleaning up"

rm tmp*
rm -R -f ./oscar_documents

# echo "loading documents"
mkdir -p ./${DEBNAME}/var/lib/doc/${PACKAGE}/
cp -R copyright ./${DEBNAME}/var/lib/doc/${PACKAGE}/

# echo "loading control scripts"
mkdir -p ./${DEBNAME}/DEBIAN/


cd ..
mvn -Dmaven.test.skip=true -Dcheckstyle.skip=true package
cp target/carlos-0-SNAPSHOT.war ./${DEBNAME}${C_BASE}webapps/carlos.war

SHA1=$(sha1sum ${TARGET})
echo The ${TARGET} SHA1=$SHA1


echo "changelog"


## now populate the DEB control files with the specifics
sed \
-e 's/yyy-1.0/'"$VERSION"'-'"$BUILD"'/' \
-e 's/package/'"$PROGRAM"'/' \
-e 's/releasedate/'"$TODAY"'/' \
changestemplate > tmp

head -n 1 tmp > tmp2

# lots of cleanup to extract the pith from the changes and then truncate at 80 columns as per DEBIAN requirement

echo  ${OSCAR_COMMIT} > tmp3
echo  ${D_COMMIT} >> tmp3

sed -r 's/(^.{80}).*/\1/' tmp3 > tmp4
tail -n 1 tmp > tmp5


cat tmp2 \
tmp4 \
tmp5 \
> changelog.Debian

cat latestStable >> cumulative
cat latestDrugref >> cumulative


echo "+++++++++++++++++++++++"
echo build=$BUILD
echo buildDateTime=$buildDateTime
echo SHA1=$SHA1
echo DEBNAME=${DEBNAME}
echo ""
echo "CARLOS changes"
head -n 5 changelog.Debian
echo ""
echo "+++++++++++++++++++++++"


gzip -9 changelog.Debian
mv changelog.Debian.gz ./${DEBNAME}/var/lib/doc/${PACKAGE}/
#  6      4     4
# user   group  world
# r+w    r      r
# 4+2+0  4+0+0  4+0+0  = 644
#chmod 644 ./${DEBNAME}/DEBIAN/changelog

echo "Configuring config"
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^db_switch.*/db_switch='"$db_switch"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
config > ./${DEBNAME}/DEBIAN/config

# 7       5     5
# user   group  world
# r+w+x  r+x    r+x
# 4+2+1  4+0+1  4+0+1  = 755
chmod 755 ./${DEBNAME}/DEBIAN/config

echo "Configuring control"
sed -e 's/Version: 8-x.x/Version: '"$VERSION"'-'"$REVISION"'/' \
control > ./${DEBNAME}/DEBIAN/control

chmod 644 ./${DEBNAME}/DEBIAN/control

echo "Configuring postinst"
# note that this requires a drugref.sql with DROP TABLE syntax
# mysqldump -uroot -p --add-drop-table drugref > drugref.sql

# determine the date of the drugref.sql that we have to load 
# lets take the last history revision on the line by cutting backwards
# NOTE requires a one line insert like
# INSERT INTO `history` (`id`, `date_time`, `action`) VALUES (1, '2022-09-20 19:04:45', 'update db');

newdate=$(grep "INSERT INTO \`history\`" drugref.sql | rev | cut -f 4 -d "'" | rev | cut -f 1 -d " ")

echo "SANITY CHECK THIS..."
echo "... the DEB will provide a drugref from " $newdate
echo ""

sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
-e 's/^newdate.*/newdate='"$newdate"'/' \
postinst > ./${DEBNAME}/DEBIAN/postinst
#
chmod 755 ./${DEBNAME}/DEBIAN/postinst

echo "Configuring postrm"
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
postrm > ./${DEBNAME}/DEBIAN/postrm

chmod 755 ./${DEBNAME}/DEBIAN/postrm

echo "Configuring prerm"
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
prerm > ./${DEBNAME}/DEBIAN/prerm


chmod 755 ./${DEBNAME}/DEBIAN/prerm

cp -R templates ./${DEBNAME}/DEBIAN/

chmod 644 ./${DEBNAME}/DEBIAN/templates

echo "loading utilities and properties"
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/

echo "make up the appropriate source.txt for this build"
echo SHA1=${SHA1}


sed -e 's/SHA1/'"$SHA1"'/' \
-e 's/yyy-x.x/'"$VERSION"'-'"$REVISION"'/' \
-e 's/oscarprogram/'"$PROGRAM"'/' \
-e 's/build xxx/build '"$BUILD"'/' \
source.txt > ./${DEBNAME}/var/lib/${PACKAGE}/source.txt

echo "make up the appropriate rebooting script"
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
reOscar.sh > ./${DEBNAME}/var/lib/${PACKAGE}/reOscar.sh
# note that the origional scripts are .sh
# end users should rename to prevent overwrites

chmod 711 ./${DEBNAME}/var/lib/${PACKAGE}/reCarlos.sh
cp gateway.sh ./${DEBNAME}/var/lib/${PACKAGE}/gateway.sh
cp letsencrypt.cron ./${DEBNAME}/var/lib/${PACKAGE}/letsencrypt.sh
chmod 755 ./${DEBNAME}/var/lib/${PACKAGE}/gateway.sh
chmod 755 ./${DEBNAME}/var/lib/${PACKAGE}/letsencrypt.sh


echo "copying over utility scripts"
cp -R ExcellerisDownload.sh ./${DEBNAME}/var/lib/${PACKAGE}/

cp -R demo.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R OfficeCodes.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R rbr2014.zip ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R ndss.zip ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R RourkeEform.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R RourkeEformNational.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R ndss.sql ./${DEBNAME}/var/lib/${PACKAGE}/

cp -R tallMAN.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R tallMANdrugref.sql ./${DEBNAME}/var/lib/${PACKAGE}/

cp -R ontarioLab.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R FIT.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R opr2017.sql ./${DEBNAME}/var/lib/${PACKAGE}/


chmod 644 ./${DEBNAME}/var/lib/${PACKAGE}/rbr2014.zip
chmod 644 ./${DEBNAME}/var/lib/${PACKAGE}/ndss.zip


cp -R patch19.sql ./${DEBNAME}/var/lib/${PACKAGE}/patch.sql
cp -R OpenO_compatibility.sql ./${DEBNAME}/var/lib/${PACKAGE}/OpenO_compatibility.sql


# use the stock properties file as config will fix as needed

cp -R ${SRC}/src/main/resources/carlos.properties ./${DEBNAME}/var/lib/${PACKAGE}/carlos.properties


cp -R README.txt ./${DEBNAME}/var/lib/${PACKAGE}/

cp -R RNGPA.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R special.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R unDemo.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R OLIS.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R indicatorTemplatePANEL.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R DoBC_dashboard.sql ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R bc_billing_dashboard.sql ./${DEBNAME}/var/lib/${PACKAGE}/


cp -R tomcat9server.xml ./${DEBNAME}/var/lib/${PACKAGE}/
cp -R tomcat9LEserver.xml ./${DEBNAME}/var/lib/${PACKAGE}/

cp -R run_rxquery.sh ./${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ./${DEBNAME}/var/lib/${PACKAGE}/run_rxquery.sh
#cp -R 2FA.sh ./${DEBNAME}/var/lib/${PACKAGE}/
#chmod 711 ./${DEBNAME}/var/lib/${PACKAGE}/2FA.sh

echo "copying over now the backup scripts"
cp -R carlos_backup.sh ./${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ./${DEBNAME}/var/lib/${PACKAGE}/carlos_backup.sh
#mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/carlos_backup/
cp -R restore.sh ./${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ./${DEBNAME}/var/lib/${PACKAGE}/restore.sh
cp -R drugrefUpdate.cron ./${DEBNAME}/var/lib/${PACKAGE}/
chmod +x ./${DEBNAME}/var/lib/${PACKAGE}/drugrefUpdate.cron

echo "getting and loading wars"
mkdir -p ./${DEBNAME}${C_BASE}webapps/

echo "build directory made to receive wars"



cp drugref2-1.0-SNAPSHOT.war drugref.war
cp drugref.war ./${DEBNAME}${C_BASE}webapps/drugref.war
cp $TARGET ./${DEBNAME}${C_BASE}webapps/$PROGRAM.war


 
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/
cp -r Document/oscar/ ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/

echo "now adding in default inbox directories"
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Fax
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/File
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Mail
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Refile
mkdir -p ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/fax-incoming

echo "now adding in Ontario Lab eform files"
cp -R labDecisionSupport.js ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R 4422-84v9-1.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/

echo "now adding in Ontario OPR 2017 files"
cp -R Document/${PROGRAM}/eform/images/OPR-2017a.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R Document/${PROGRAM}/eform/images/OPR-2017b.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R Document/${PROGRAM}/eform/images/OPR-2017c.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R Document/${PROGRAM}/eform/images/OPR-2017d.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R Document/${PROGRAM}/eform/images/OPR-2017e.png ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/

echo "now adding in Ontario pharmacy file for LU codes"
## get from source http://www.health.gov.on.ca/en/pro/programs/drugs/data_extract.xml
curl -o http://www.health.gov.on.ca/en/pro/programs/drugs/data_extract.xml ./${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/data_extract.xml

echo "now invoking dpkg -b ${DEBNAME}"

dpkg -b ${DEBNAME}
echo ""
echo "Testing the deb for update locally"
echo "#########" `date` "#########" 
dpkg -i ${DEBNAME}.deb
echo ""
echo ""
echo ""
echo "the md5sum is" 
md5sum ${DEBNAME}.deb
echo "#########" `date` "#########" 






