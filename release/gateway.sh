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
if [ -f /usr/share/tomcat9/bin/version.sh ] ; then
  TOMCAT=tomcat9
  TOMCAT_USER=tomcat
  TMP=$(find /tmp -type d -wholename "*tomcat*/tmp")           
else
  if [ -f /usr/share/tomcat8/bin/version.sh ] ; then
    TOMCAT=tomcat8
    TOMCAT_USER=tomcat8
  else
    if [ -f /usr/share/tomcat7/bin/version.sh ] ; then
      TOMCAT=tomcat7
      TOMCAT_USER=tomcat7
    fi
  fi
  TMP=/tmp/${TOMCAT}-${TOMCAT}-tmp
fi


if test -n "$(find ${TMP} -maxdepth 1 -name '*.txt' -print -quit)"; then
	echo "Faxes found to be sent"
	for f in $(ls "${TMP}"*.txt); do
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
