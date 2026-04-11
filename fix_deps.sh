#!/bin/bash
# Remove the old downloaded artifact
rm -rf local_repo/com/github/openosp/ultrabuk-htmltopdf-java
mvn dependency:resolve
mvn se.vandmo:dependency-lock-maven-plugin:1.1.1:update
