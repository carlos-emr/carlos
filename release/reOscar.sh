#!/bin/bash
# reOscar.sh
# a script file for OSCAR that reboots Tomcat or the OS
#===================================================================
# Copyright Peter Hutten-Czapski 2016-2024 released under the GPL v2
# bug fixes as per Eugene Robertus
#===================================================================
# version 19.09

data_path=/usr/share/oscar-emr
PROGRAM=oscar
PROGRAM=oscar
LOCKDIR=/tmp/reOscar.lock
LOG_FILE=${data_path}/${PROGRAM}.log
LOG_ERR=${data_path}/${PROGRAM}.err
SHUTDOWN_WAIT=20
RUNNING_STATUS=`wget https://127.0.0.1:8443/oscar --no-check-certificate -O /dev/null -q ; echo $?`

# --- prevent more than one instance running at a time
if ! mkdir "$LOCKDIR"; then
    echo `basename "$0"` "script is already running." 1>&2
    exit 1
fi
# Remove lockdir when the script finishes, or when it receives a signal
trap 'rm -rf "$LOCKDIR"' 0 1 2   # remove directory when script finishes EXIT(0), terminal closes SIGHUP(1) or SIGINT(2) Ctrl-C

# --- sanity check run as root
if [ "$(id -u)" != "0" ];
then
        echo `basename "$0"` "script must be run as root" 1>&2
        exit 1
fi


# --- select the running Tomcat or the highest installed version
TOMCAT=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $1 }')
TOMCAT_PID=$(ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $2 }')
if [ -z "$TOMCAT" ]; then
    #Tomcat is not running, find the highest installed version
    if [ -f /usr/share/tomcat9/bin/version.sh ] ; then
            TOMCAT=tomcat9
            TMP=$(find /tmp -type d -wholename "*tomcat*/tmp")           
        else
        if [ -f /usr/share/tomcat8/bin/version.sh ] ; then
            TOMCAT=tomcat8
            else
            if [ -f /usr/share/tomcat7/bin/version.sh ] ; then
                TOMCAT=tomcat7
            fi
        fi
            TMP=/tmp/${TOMCAT}-${TOMCAT}-tmp
    fi
fi



# --- reboot the server
if [ -f ${TMP}/rebootServer.action ]; then
        echo "#########" `date` "#########" 1>> $LOG_FILE
        echo "reboot command sent" >> $LOG_FILE
        rm ${TMP}/rebootServer.action
    # diable shutdown of server by user from GUI
        #/sbin/shutdown -r now
        exit 0
fi

# --- reload Tomcat
tomcat_pid() {
        echo `ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $2 }'`
}

if [ -f ${TMP}/restartOscar.action ] || [ ${RUNNING_STATUS} != '0' ]; then
        echo "#########" `date` "#########" 1>> $LOG_FILE
        echo "restart Oscar command sent"
        rm ${TMP}/restartOscar.action
        pid=$(tomcat_pid)
        count=0;
        let kwait=$SHUTDOWN_WAIT
        if [ -n "$pid" ]; then
                /etc/init.d/${TOMCAT} restart
                until [ `ps -p $pid | grep -c $pid` = '0' ] || [ $count -gt $kwait ]
                do
                        echo -n -e "\nwaiting for processes to exit";
                        sleep 1
                        let count=$count+1;
                done
				pid2=$(tomcat_pid)
				echo "${TOMCAT} process ${pid} restarting on PID ${pid2} after ${count}s" >> $LOG_FILE
        else
                echo "Is ${TOMCAT} running..? Trying to restart..." >> $LOG_ERR
                /etc/init.d/${TOMCAT} restart
        fi
        if [ $count -gt $kwait ]; then
                #echo "WARNING ${TOMCAT} process on PID ${pid} had to be killed" >> $LOG_ERR
                #NOTE kill is reliable but the restart immediately afterwards can fail
                #kill -9 $pid
				#wait $!
                #service ${TOMCAT} restart
				#wait $!	
		kill -9 $pid
		/etc/init.d/${TOMCAT} stop
		/etc/init.d/${TOMCAT} start
		pid2=$(tomcat_pid)
		echo "WARNING ${TOMCAT} timed out restarting after ${count}s, check PID ${pid2}" >> $LOG_ERR			
        fi
        echo "#########" `date` "#########" 1>> $LOG_FILE
fi



exit 0

