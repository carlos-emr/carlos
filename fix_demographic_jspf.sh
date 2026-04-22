cat << 'INNER_EOF' > src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf.patch
--- src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf
+++ src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf
@@ -19,6 +19,7 @@
     @since 2026-04-21
 --%>
+<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
 <script type="text/javascript">
     function applyDemographicFieldLengthLimits() {
         var fieldLengthLimits = {
             last_name: <%= Demographic.LAST_NAME_MAX_LENGTH %>,
INNER_EOF
patch -p0 < src/main/webapp/WEB-INF/jspf/demographic-field-length-limits.jspf.patch
