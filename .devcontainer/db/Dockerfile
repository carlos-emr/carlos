FROM mariadb:10.5

# Labels
LABEL author="OpenOSP"
LABEL version=0.0.2
LABEL description="OpenOSP-EMR Docker Image for Development Environment"

# Install required packages
RUN for attempt in 1 2 3; do \
      if apt-get update -o Acquire::Retries=3 -o Acquire::http::Timeout=20 && \
         DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends --fix-missing dos2unix; then \
        apt-get autoremove -y \
          && apt-get clean \
          && rm -rf /var/lib/apt/lists/*; \
        break; \
      fi; \
      echo "apt-get failed (attempt ${attempt}/3), retrying..."; \
      apt-get clean || true; \
      rm -rf /var/lib/apt/lists/*; \
      if [ "$attempt" = 3 ]; then \
        exit 1; \
      fi; \
      sleep 5; \
    done

# Adding required files
# Currently this needs the database configuration for post-scripts from the OpenOSP-EMR
# repository it is building a database for; this build process could use fine tuning for
# building older versions with different DB schema.  This works, but further work here is
# something for later.

COPY ./.devcontainer/db/scripts/populate_db.sh /docker-entrypoint-initdb.d/populate_db.sh
RUN mkdir /scripts
COPY ./.devcontainer/db/scripts/development.sql /scripts/development.sql
COPY ./database /database
COPY ./.devcontainer/development/config/shared/my.cnf /etc/mysql/my.cnf

# Change permissions
RUN chmod 744 /etc/mysql/my.cnf
RUN chmod 755 /scripts

RUN mkdir -p /var/lib/OscarDocument && chown -R mysql:mysql /var/lib/OscarDocument

# Converting script files using dos2unix (Required for Windows)
RUN dos2unix /docker-entrypoint-initdb.d/populate_db.sh
RUN dos2unix /database/mysql/*.sh

# Ports
EXPOSE 3306
