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

# Establish stable absolute path references so file lookups remain correct
# regardless of the current working directory at any point in the script.
#
#   RELEASE_DIR  — absolute path to the release/ directory (where THIS script lives).
#                  Use for release-specific files: postinst, config, *.sql, *.sh, etc.
#   REPO_ROOT    — absolute path to the repository root (parent of release/).
#                  Use for source files: src/main/resources/, database/mysql/, target/, etc.
#
RELEASE_DIR="${SCRIPT_DIR}"
REPO_ROOT="$(dirname "${SCRIPT_DIR}")"
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

buildDateTime=$(date)
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

if [ -d "${RELEASE_DIR}/${DEBNAME}" ]; then
	# A previous partial build exists; remove it so we start clean.
	echo prexisting directory with this build found
	SKIP_NEW_WAR=true
	rm -R "${RELEASE_DIR:?}/${DEBNAME:?}/"
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

# Remove any leftover temp files and document scratch directories from previous builds.
rm -f "${RELEASE_DIR}"/tmp*
rm -R -f "${RELEASE_DIR}/oscar_documents"

# --- Package skeleton setup (run from release/ directory before cd'ing to repo root) ---
echo "loading documents"
mkdir -p "${RELEASE_DIR}/${DEBNAME}/var/lib/doc/${PACKAGE}/"
[ -f "${RELEASE_DIR}/copyright" ] && cp -R "${RELEASE_DIR}/copyright" "${RELEASE_DIR}/${DEBNAME}/var/lib/doc/${PACKAGE}/" \
    || echo "WARNING: release/copyright not found, skipping"

echo "loading control scripts"
mkdir -p "${RELEASE_DIR}/${DEBNAME}/DEBIAN/"

# --- Move to repo root for Maven build ---
# All file paths after this point are relative to the repository root,
# so use ${RELEASE_DIR}/xxx for release/ files and ./xxx for repo-root files.
cd "${REPO_ROOT}" || { echo "ERROR: Failed to cd to ${REPO_ROOT}" >&2; exit 1; }
mvn -Dmaven.test.skip=true -Dcheckstyle.skip=true package
mkdir -p "${RELEASE_DIR}/${DEBNAME}${C_BASE}webapps/"
cp "${REPO_ROOT}/target/carlos-0-SNAPSHOT.war" "${RELEASE_DIR}/${DEBNAME}${C_BASE}webapps/carlos.war"

SHA1=$(sha1sum "${REPO_ROOT}/target/${TARGET}")
echo The ${TARGET} SHA1=$SHA1


echo "changelog"

# --- Generate changelog.Debian from the last 5 commits on the develop branch ---
# The Debian policy changelog format requires a specific header/trailer.
# We pull recent git history to give packagers and users a concise summary of what changed.
CHANGELOG="${RELEASE_DIR}/changelog.Debian"
{
    echo "${PACKAGE} (${VERSION}-${REVISION}) stable; urgency=low"
    echo ""
    # cd to repo root to access git history, then come back.
    (cd "${REPO_ROOT}" && git log --oneline -5 origin/develop 2>/dev/null) | while IFS= read -r line; do
        # Truncate to 79 chars (Debian requires lines <= 80 chars including the leading "  * ")
        printf "  * %.75s\n" "$line"
    done
    echo ""
    echo " -- CARLOS EMR Maintainers <maintainer@carlos-emr.io>  $(date -R)"
} > "${CHANGELOG}"

echo "+++++++++++++++++++++++"
echo build=$BUILD
echo buildDateTime=$buildDateTime
echo SHA1=$SHA1
echo DEBNAME=${DEBNAME}
echo ""
echo "CARLOS changes (last 5 commits to develop):"
cat "${CHANGELOG}"
echo ""
echo "+++++++++++++++++++++++"


gzip -9 "${CHANGELOG}"
mv "${CHANGELOG}.gz" "${RELEASE_DIR}/${DEBNAME}/var/lib/doc/${PACKAGE}/"
#  6      4     4
# user   group  world
# r+w    r      r
# 4+2+0  4+0+0  4+0+0  = 644
#chmod 644 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/changelog

echo "Configuring config"
# config: debconf configuration script — runs before postinst to collect admin answers
# (database password, province, upgrade vs new install, etc.)
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^db_switch.*/db_switch='"$db_switch"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
"${RELEASE_DIR}/config" > ${RELEASE_DIR}/${DEBNAME}/DEBIAN/config

# 7       5     5
# user   group  world
# r+w+x  r+x    r+x
# 4+2+1  4+0+1  4+0+1  = 755
chmod 755 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/config

echo "Configuring control"
# control: dpkg package metadata (name, version, dependencies, description)
sed -e 's/Version: 8-x.x/Version: '"$VERSION"'-'"$REVISION"'/' \
"${RELEASE_DIR}/control" > ${RELEASE_DIR}/${DEBNAME}/DEBIAN/control
chmod 644 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/control

echo "Configuring postinst"
# postinst: the main post-installation script:
#   - new install  → creates the oscar_15 database from schema/ scripts based on province
#   - OSCAR 19 migration → copies old tables into oscar_15 then applies 019toCARLOS.sql
#   - CARLOS revision update → applies the incremental patch.sql and restarts Tomcat
# drugref is delivered as a WAR file downloaded at build time (see "getting and loading wars" below).
# No drugref.sql is bundled; the drugref webapp manages its own schema on first startup.

sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
"${RELEASE_DIR}/postinst" > ${RELEASE_DIR}/${DEBNAME}/DEBIAN/postinst
#
chmod 755 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/postinst

echo "Configuring postrm"
# postrm: post-removal script — stops Tomcat and optionally drops the database on purge.
sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
-e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
-e 's/^db_name.*/db_name='"$db_name"'/' \
-e 's/^VERSION.*/VERSION='"$VERSION"'/' \
-e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
-e 's/^REVISION.*/REVISION='"$REVISION"'/' \
-e 's/^buildDateTime.*/buildDateTime=\"'"$buildDateTime"'\"/' \
"${RELEASE_DIR}/postrm" > ${RELEASE_DIR}/${DEBNAME}/DEBIAN/postrm

chmod 755 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/postrm

echo "Configuring prerm"
# prerm: optional pre-removal script run by dpkg before the package is removed.
if [ -f "release/prerm" ]; then
    sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
    -e 's/^PACKAGE.*/PACKAGE='"$PACKAGE"'/' \
    -e 's/^db_name.*/db_name='"$db_name"'/' \
    -e 's/^VERSION.*/VERSION='"$VERSION"'/' \
    -e 's/^PREVIOUS.*/PREVIOUS='"$PREVIOUS"'/' \
    -e 's/^REVISION.*/REVISION='"$REVISION"'/' \
    release/prerm > ${RELEASE_DIR}/${DEBNAME}/DEBIAN/prerm
    chmod 755 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/prerm
else
    echo "WARNING: release/prerm not found, skipping (package will not have a pre-removal script)"
fi

# templates: debconf question templates displayed to the administrator during install.
cp -R "${RELEASE_DIR}/templates" ${RELEASE_DIR}/${DEBNAME}/DEBIAN/
chmod 644 ${RELEASE_DIR}/${DEBNAME}/DEBIAN/templates

echo "loading utilities and properties"
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/

echo "make up the appropriate source.txt for this build"
echo SHA1=${SHA1}

# source.txt is an optional build metadata file; skip gracefully if absent.
if [ -f "release/source.txt" ]; then
    sed -e 's/SHA1/'"$SHA1"'/' \
    -e 's/yyy-x.x/'"$VERSION"'-'"$REVISION"'/' \
    -e 's/oscarprogram/'"$PROGRAM"'/' \
    -e 's/build xxx/build '"$BUILD"'/' \
    release/source.txt > ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/source.txt
else
    echo "WARNING: release/source.txt not found, skipping source.txt in package"
fi

# --- Optional helper scripts (not all may be present in every build) ---

# reOscar.sh → reCarlos.sh: the Tomcat restart helper; patched with PROGRAM name.
if [ -f "release/reOscar.sh" ]; then
    sed -e 's/^PROGRAM.*/PROGRAM='"$PROGRAM"'/' \
    release/reOscar.sh > ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/reCarlos.sh
    # Note: the original scripts keep the .sh extension; end users should rename to
    # prevent the packager from overwriting customised copies on upgrade.
    chmod 711 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/reCarlos.sh
else
    echo "WARNING: release/reOscar.sh not found, reCarlos.sh will be absent from package"
fi

# gateway.sh: optional HTTPS-redirect/reverse-proxy helper.
if [ -f "release/gateway.sh" ]; then
    cp release/gateway.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/gateway.sh
    chmod 755 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/gateway.sh
else
    echo "WARNING: release/gateway.sh not found, skipping"
fi

# letsencrypt.cron: cron helper for Let's Encrypt certificate renewal.
if [ -f "release/letsencrypt.cron" ]; then
    cp release/letsencrypt.cron ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/letsencrypt.sh
    chmod 755 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/letsencrypt.sh
else
    echo "WARNING: release/letsencrypt.cron not found, skipping"
fi

echo "copying over utility scripts"
# ExcellerisDownload.sh: optional lab-download helper for Excelleris HL7 feeds.
[ -f "release/ExcellerisDownload.sh" ] && cp release/ExcellerisDownload.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ || echo "WARNING: ExcellerisDownload.sh not found, skipping"
cp -R release/demo.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/OfficeCodes.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
# rbr2014.zip: Rourke Baby Record 2014 eform archive.
[ -f "release/rbr2014.zip" ] && cp release/rbr2014.zip ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ && chmod 644 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/rbr2014.zip || echo "WARNING: rbr2014.zip not found, skipping"
# ndss.zip: National Diabetes Surveillance System eform archive.
[ -f "release/ndss.zip" ] && cp release/ndss.zip ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ && chmod 644 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ndss.zip || echo "WARNING: ndss.zip not found, skipping"
cp -R release/RourkeEform.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/RourkeEformNational.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/ndss.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/tallMAN.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/tallMANdrugref.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/ontarioLab.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/FIT.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/opr2017.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/

# 019toCARLOS.sql: SQL migration patch for OSCAR 19 → CARLOS upgrade path.
# Copied as patch.sql so the postinst script can reference it generically.
if [ -f "release/019toCARLOS.sql" ]; then
    cp release/019toCARLOS.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/patch.sql
elif [ -f "release/patch19.sql" ]; then
    cp release/patch19.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/patch.sql
else
    echo "WARNING: neither release/019toCARLOS.sql nor release/patch19.sql found; patch.sql will be absent"
fi
# OpenO_compatibility.sql: optional compatibility shim for legacy OpenO installations.
[ -f "release/OpenO_compatibility.sql" ] && cp release/OpenO_compatibility.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ || echo "WARNING: release/OpenO_compatibility.sql not found, skipping"

# --- Pull carlos.properties from source ---
# For new installs and OSCAR 19 migrations the postinst config step will substitute
# the correct MySQL credentials into this file using the debconf answers.
# Source path is relative to the repo root (current dir after the cd .. above).
if [ -f "./src/main/resources/carlos.properties" ]; then
    cp ./src/main/resources/carlos.properties ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/carlos.properties
else
    echo "ERROR: src/main/resources/carlos.properties not found — cannot continue" >&2
    exit 1
fi

# README.txt: optional human-readable notes shipped with the package.
[ -f "release/README.txt" ] && cp release/README.txt ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/ || echo "WARNING: release/README.txt not found, skipping"
cp -R release/RNGPA.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/special.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/unDemo.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/OLIS.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/indicatorTemplatePANEL.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
# DoBC_dashboard.sql and bc_billing_dashboard.sql: BC-specific billing dashboard queries.
cp -R release/DoBC_dashboard.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/bc_billing_dashboard.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/


# Tomcat server.xml configuration templates.
# tomcat9server.xml   → plain HTTP + self-signed TLS (development/internal use).
# tomcat9LEserver.xml → Let's Encrypt signed TLS (production use).
cp -R release/tomcat9server.xml ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
cp -R release/tomcat9LEserver.xml ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/

# run_rxquery.sh: cron helper that queries the DrugRef web service for drug interaction data.
cp -R release/run_rxquery.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/run_rxquery.sh
#cp -R 2FA.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
#chmod 711 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/2FA.sh

echo "copying over backup and restore scripts"
# carlos_backup.sh: full database + OscarDocument backup to encrypted archive.
cp -R release/carlos_backup.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/carlos_backup.sh
#mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/carlos_backup/
# restore.sh: decrypts and restores an archive created by carlos_backup.sh.
cp -R release/restore.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
chmod 711 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/restore.sh
# drugrefUpdate.cron: cron job to pull updated drug data into the local DrugRef database.
cp -R release/drugrefUpdate.cron ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/
chmod +x ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/drugrefUpdate.cron

# --- Bundle database schema scripts for new installs ---
# The postinst script calls these at install time (for new installs only) to create and
# populate the oscar_15 schema.  Both ON (Ontario) and BC (British Columbia) variants are
# included; the installer selects the appropriate one based on the province debconf answer.
echo "bundling database schema scripts from database/mysql/"
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
# Core schema (required for all provinces)
cp ./database/mysql/oscarinit.sql          ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/oscarinit_2025.sql     ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/oscardata.sql          ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/oscardata_additional.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/measurementMapData.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/expire_openodoc.sql    ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
# ICD-9 and ICD-10 diagnostic code tables
cp ./database/mysql/icd9.sql              ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/icd10.sql             ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/icd9_issue_groups.sql  ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/icd10_issue_groups.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
# Ontario (ON) province-specific data
cp ./database/mysql/oscarinit_on.sql      ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/oscardata_on.sql      ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
# Ontario OLIS (Ontario Laboratory Information System) data
cp -R ./database/mysql/olis/              ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/olis/
# BC (British Columbia) province-specific data
cp ./database/mysql/oscarinit_bc.sql       ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/oscardata_bc.sql       ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/bc_billingServiceCodes.sql    ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/bc_professionalSpecialists.sql ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/bc_pharmacies.sql     ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
# CAISI (Computerized Assessment and Integration System) community data — used by both provinces
cp -R ./database/mysql/caisi/             ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/caisi/
# Database creation helper scripts — postinst calls the province-appropriate one.
# createdatabase_generic.sh [user] [pass] [dbname] [on|bc] [icd_version]
# createdatabase_on.sh      [user] [pass] [dbname]   (wraps generic with on/9)
# createdatabase_bc.sh      [user] [pass] [dbname]   (wraps generic with bc/9)
cp ./database/mysql/createdatabase_generic.sh ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/createdatabase_on.sh      ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
cp ./database/mysql/createdatabase_bc.sh      ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/
chmod 755 ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/schema/createdatabase_*.sh

# Bundle incremental update scripts (update-2026-*.sql) for CARLOS revision upgrades.
# The postinst script applies these after the WAR is deployed to bring the schema current.
echo "bundling incremental database update scripts from database/mysql/updates/"
_update_sql_count=0
for _upd_sql in ./database/mysql/updates/update-2026-*.sql; do
    if [ -f "${_upd_sql}" ]; then
        cp "${_upd_sql}" "${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/"
        _update_sql_count=$((_update_sql_count + 1))
    fi
done
echo "Bundled ${_update_sql_count} incremental update SQL files into package"

echo "getting and loading wars"
# The webapps directory was already created above during the Maven build section.
# drugref.war: downloaded from upstream at package build time.
# The drugref webapp creates its own schema on first startup — no drugref.sql is needed.
DRUGREF_WAR="${RELEASE_DIR}/${DEBNAME}${C_BASE}webapps/drugref.war"
curl -o "${DRUGREF_WAR}" https://bitbucket.org/oscaremr/drugref2/downloads/drugref2.48.war
# Verify SHA256 checksum of drugref.war (update DRUGREF_SHA256 when upgrading drugref version).
# To obtain the hash after downloading: sha256sum "${DRUGREF_WAR}"
# Then export DRUGREF_SHA256=<hash> before running this script.
if [ -z "${DRUGREF_SHA256:-}" ]; then
    echo "ERROR: DRUGREF_SHA256 environment variable must be set to the expected SHA256 of drugref2.48.war." >&2
    echo "  Run: sha256sum ${DRUGREF_WAR}  to get the value, verify it against a trusted source, then re-run." >&2
    exit 1
fi
echo "${DRUGREF_SHA256}  ${DRUGREF_WAR}" | sha256sum -c - || { echo "Checksum mismatch for drugref.war — aborting build"; exit 1; }
cp "${REPO_ROOT}/target/${TARGET}" "${RELEASE_DIR}/${DEBNAME}${C_BASE}webapps/${PROGRAM}.war"

# --- OscarDocument directory skeleton ---
# Copy any checked-in document templates and set up the inbox directory structure
# expected by CARLOS at runtime.
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/
if [ -d "release/Document/oscar/" ]; then
    cp -r release/Document/oscar/ ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/
else
    echo "WARNING: release/Document/oscar/ not found, skipping document templates"
fi

echo "now adding in default inbox directories"
# These directories must exist before CARLOS starts or document routing will fail.
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Fax
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/File
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Mail
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/incomingdocs/1/Refile
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/fax-incoming

echo "now adding in Ontario Lab eform files"
# labDecisionSupport.js and the associated image are used by the Ontario Lab eform.
mkdir -p ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R release/labDecisionSupport.js ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
cp -R release/4422-84v9-1.png ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/

echo "now adding in Ontario OPR 2017 files"
# OPR-2017*.png: Ontario Physician Resource Planning survey form image assets.
for img in release/OPR-2017a.png release/OPR-2017b.png release/OPR-2017c.png release/OPR-2017d.png release/OPR-2017e.png; do
    if [ -f "$img" ]; then
        cp -R "$img" ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/
    else
        echo "WARNING: $img not found, skipping"
    fi
done

echo "now adding in Ontario pharmacy file for LU codes"
# data_extract.xml: Ontario Drug Benefit formulary data from the Ministry of Health.
# Source: http://www.health.gov.on.ca/en/pro/programs/drugs/data_extract.xml
curl -o ${RELEASE_DIR}/${DEBNAME}/var/lib/${PACKAGE}/OscarDocument/${PROGRAM}/eform/images/data_extract.xml \
     http://www.health.gov.on.ca/en/pro/programs/drugs/data_extract.xml \
     || echo "WARNING: Could not download Ontario pharmacy data_extract.xml — install without it"

echo "now invoking dpkg -b ${RELEASE_DIR}/${DEBNAME}"

# Build the .deb package from the assembled directory tree.
# Output: ${RELEASE_DIR}/${DEBNAME}.deb
dpkg -b "${RELEASE_DIR}/${DEBNAME}"
echo ""
echo "Testing the deb for update locally"
echo "#########" `date` "#########"
# Install the freshly built package on this machine for a smoke test.
dpkg -i "${RELEASE_DIR}/${DEBNAME}.deb"
echo ""
echo ""
echo ""
echo "the md5sum is"
md5sum "${RELEASE_DIR}/${DEBNAME}.deb"
echo "#########" `date` "#########"






