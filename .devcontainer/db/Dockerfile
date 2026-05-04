FROM mariadb:10.5

# Labels
LABEL author="OpenOSP"
LABEL version=0.0.2
LABEL description="OpenOSP-EMR Docker Image for Development Environment"

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

# Normalize script line endings without requiring extra apt packages.
RUN sed -i 's/\r$//' /docker-entrypoint-initdb.d/populate_db.sh /database/mysql/*.sh

# Ports
EXPOSE 3306
