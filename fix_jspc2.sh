cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
@@ -53,7 +53,7 @@
 <%@ page import="io.github.carlos_emr.carlos.util.*" %>
 <%@ page import="org.owasp.encoder.Encode" %>

 <%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
-<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>
-<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
+<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
+<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>
 <fmt:setBundle basename="oscarResources"/>
INNER_EOF
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch

cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
@@ -54,7 +54,7 @@
 <%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
 <%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
-<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>
-<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
+<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
+<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>

 <%
INNER_EOF
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch
