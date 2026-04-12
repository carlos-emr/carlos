#!/bin/sh
#
# CARLOS EMR - App Container Health Check
#
# Returns 0 if the app is responding on /carlos/, 1 otherwise. Used by
# docker-compose healthcheck directive.
#
# The app's cold-start takes 60-120 seconds on first deploy because Tomcat
# must explode the WAR, Spring must initialize, and Hibernate must validate
# the schema. Compose healthcheck uses start_period=120s to accommodate this.
#
# We check for HTTP 200 OR 302 - the login page may redirect to /login.jsp
# which is still a valid "up" signal.
set -e

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/carlos/ || echo "000")

case "$HTTP_CODE" in
    200|301|302|303)
        exit 0
        ;;
    *)
        echo "Healthcheck failed: HTTP $HTTP_CODE" >&2
        exit 1
        ;;
esac
