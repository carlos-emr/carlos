#!/bin/bash
#
# Fax Gateway
# Picks up the files dropped by OSCAR
# If a mutt line is uncommented will send a fax to that fax gateway
# Otherwise it just clears the files dropped by OSCAR
# Make sure you adjust the paths and mutt switches appropriately
#===================================================================
# Copyright Peter Hutten-Czapski 2013-2024 released under the GPL v2
#===================================================================
# v 19.0

# --- pick your highest tomcat that is supported
TOMCAT=
TOMCAT_USER=
for TOMCAT_CANDIDATE in tomcat11 tomcat10 tomcat9 tomcat8 tomcat7; do
  if [ -f "/usr/share/${TOMCAT_CANDIDATE}/bin/version.sh" ] ; then
    TOMCAT=${TOMCAT_CANDIDATE}
    case "${TOMCAT_CANDIDATE}" in
      tomcat11|tomcat10|tomcat9) TOMCAT_USER=tomcat ;;
      *) TOMCAT_USER=${TOMCAT_CANDIDATE} ;;
    esac
    break
  fi
done
if [ -z "${TOMCAT}" ]; then
  echo "No supported Tomcat installation found (expected tomcat11, tomcat10, tomcat9, tomcat8, or tomcat7)." >&2
  exit 1
fi
TMP=$(find /tmp -type d -wholename "*${TOMCAT}*/tmp" 2>/dev/null | head -1)
TMP="${TMP:-/tmp/${TOMCAT}-${TOMCAT}-tmp}"


if test -n "$(find ${TMP} -maxdepth 1 -name '*.txt' -print -quit 2>/dev/null)"; then
	echo "Faxes found to be sent"
	for f in "${TMP}"/*.txt; do
		[ -e "$f" ] || continue
		t=$(echo "$f" | sed -e s"/${TMP}\////" -e s"/[._][0-9]*.txt//" -e s"/prescription_/Rx-/")
#		mutt -s "Oscar Fax $t" 1$(sed s"/ *//g" "$f"|tr -d "\n")@srfax.com -a "$(echo "$f" | sed s"/txt/pdf/")" < /dev/null
#		mutt -s "Oscar Fax" 1$(sed s"/ *//g" "$f"|tr -d "\n")@metrofax.com -a "$(echo "$f" | sed s"/txt/pdf/")" < /dev/null
#		mutt -s "Oscar Fax" 1$(sed s"/ *//g" "$f"|tr -d "\n")@rcfax.com -a "$(echo "$f" | sed s"/txt/pdf/")" < /dev/null
#		mutt -s "Oscar Fax 2442" $(sed s"/ *//g" "$f"|tr -d "\n")@prestofax.com -a "$(echo "$f" | sed s"/txt/pdf/")" < /dev/null
		# Remove processed fax files to prevent reprocessing on next cron run
		rm -- "$f"
		rm -- "$(echo "$f" | sed 's/txt$/pdf/')"
	done
fi
