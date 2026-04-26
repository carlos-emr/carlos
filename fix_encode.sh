#!/bin/bash

FILES="src/main/webapp/WEB-INF/jsp/appointment/appointmentsearch.jsp
src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp
src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp
src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp"

for FILE in $FILES; do
    echo "Processing $FILE"
    if [ -f "$FILE" ]; then
        sed -i 's/Encode\.for/SafeEncode.for/g' "$FILE"
        sed -i 's/${e:for/${carlos:for/g' "$FILE"
        if ! grep -q "import=\"io.github.carlos_emr.carlos.utility.SafeEncode\"" "$FILE"; then
            sed -i '/<%@ page/a <%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>' "$FILE"
        fi
        if ! grep -q "uri=\"/WEB-INF/carlos.tld\"" "$FILE"; then
            sed -i '/<%@ page/a <%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>' "$FILE"
        fi
    else
        echo "File not found: $FILE"
    fi
done
