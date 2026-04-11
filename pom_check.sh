#!/bin/bash
mvn validate -B -Denforcer.skip=true -Dcheckstyle.skip=true -Dlicense.skip=true
echo "Exit code: $?"
