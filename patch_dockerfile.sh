#!/bin/bash
sed -i 's/RUN apt-get update && apt-get install -y dos2unix  \\/RUN apt-get update \&\& apt-get install -y dos2unix  \\/' .devcontainer/db/Dockerfile
sed -i 's/    && apt-get autoremove/    \&\& apt-get autoremove \\\n    \&\& rm -rf \/var\/lib\/apt\/lists\/\*/' .devcontainer/db/Dockerfile
sed -i '/RUN dos2unix \/database\/mysql\/\*\.sh/a \
\n# Pre-create required directories for MariaDB initialization script to prevent permission denied errors\nRUN mkdir -p \/var\/lib\/OscarDocument \&\& chown -R mysql:mysql \/var\/lib\/OscarDocument' .devcontainer/db/Dockerfile
