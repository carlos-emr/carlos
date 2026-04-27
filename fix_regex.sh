#!/bin/bash
sed -i 's/Pattern\.compile("'"'"'.+?'"'"'")/Pattern.compile("'"'"'[^'"'"']+'"'"'")/' src/main/java/io/github/carlos_emr/carlos/decisionSupport/model/conditionValue/DSValue.java
sed -i 's/Pattern\.compile("([^\\s]+$)")/Pattern.compile("[^\\s]+$")/' src/main/java/io/github/carlos_emr/carlos/decisionSupport/model/conditionValue/DSValue.java

sed -i 's/Pattern\.compile("'"'"'\[\^'"'"'\]\+'"'"'")/'"Pattern.compile(\"'[^']+'\")"'/g' src/main/java/io/github/carlos_emr/carlos/decisionSupport/model/conditionValue/DSValue.java
