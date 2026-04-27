#!/bin/bash

# Fix WEB-INF/jsp/appointment/appointmentsearch.jsp
sed -i 's/<%@ page import="org.owasp.encoder.Encode" %>/<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>/g' src/main/webapp/WEB-INF/jsp/appointment/appointmentsearch.jsp
sed -i 's/Encode.for/SafeEncode.for/g' src/main/webapp/WEB-INF/jsp/appointment/appointmentsearch.jsp

# Fix WEB-INF/jsp/messenger/ViewAttachment.jsp
sed -i 's/<%@ page import="org.owasp.encoder.Encode" %>/<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>/g' src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp
sed -i 's/Encode.for/SafeEncode.for/g' src/main/webapp/WEB-INF/jsp/messenger/ViewAttachment.jsp

# Fix WEB-INF/jsp/messenger/ViewPDFAttachment.jsp
sed -i 's/<%@ page import="org.owasp.encoder.Encode" %>/<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>/g' src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp
sed -i 's/Encode.for/SafeEncode.for/g' src/main/webapp/WEB-INF/jsp/messenger/ViewPDFAttachment.jsp

# Fix WEB-INF/jsp/messenger/attachmentFrameset.jsp
sed -i 's/<%@ page import="org.owasp.encoder.Encode" %>/<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>/g' src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
sed -i 's/<%@ taglib uri="https:\/\/www.owasp.org\/index.php\/OWASP_Java_Encoder_Project" prefix="e" %>/<%@ taglib uri="\/WEB-INF\/carlos.tld" prefix="carlos" %>/g' src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
sed -i 's/${e:for/${carlos:for/g' src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
sed -i 's/Encode.for/SafeEncode.for/g' src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp

# Fix WEB-INF/jsp/messenger/generatePreviewPDF.jsp
sed -i 's/<%@ page import="org.owasp.encoder.Encode" %>/<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>/g' src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
sed -i 's/<%@ taglib uri="https:\/\/www.owasp.org\/index.php\/OWASP_Java_Encoder_Project" prefix="e" %>/<%@ taglib uri="\/WEB-INF\/carlos.tld" prefix="carlos" %>/g' src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
sed -i 's/${e:for/${carlos:for/g' src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
sed -i 's/Encode.for/SafeEncode.for/g' src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
