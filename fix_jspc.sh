cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp
@@ -6,6 +6,7 @@
 <%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
 <%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
 <%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
+<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>

 <%
     String ctxPath = request.getContextPath();
INNER_EOF
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/attachmentFrameset.jsp.patch

cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch
--- src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
+++ src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp
@@ -25,6 +25,7 @@
 <%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
 <%@ taglib uri="http://www.springframework.org/tags/form" prefix="form" %>
 <%@ taglib uri="http://www.springframework.org/tags" prefix="spring" %>
+<%@ taglib uri="http://carlos.github.io/tags/encode" prefix="carlos" %>
 <%@ page import="java.text.SimpleDateFormat" %>
 <%@ page import="java.util.*" %>
 <%@ page import="io.github.carlos_emr.carlos.demographic.DemographicExt" %>
INNER_EOF
patch -p0 < src/main/webapp/WEB-INF/jsp/messenger/generatePreviewPDF.jsp.patch
