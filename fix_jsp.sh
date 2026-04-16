cat << 'DIFF' > jsp.diff
--- src/main/java/io/github/carlos_emr/carlos/commn/dao/EFormReportToolDaoImpl.java
+++ src/main/java/io/github/carlos_emr/carlos/commn/dao/EFormReportToolDaoImpl.java
@@ -50,11 +50,11 @@
         super(EFormReportTool.class);
     }

-    private static final java.util.regex.Pattern INVALID_SQL_IDENTIFIER = java.util.regex.Pattern.compile(".*[`].*");
+    private static final java.util.regex.Pattern INVALID_SQL_IDENTIFIER = java.util.regex.Pattern.compile(".*[\`].*");

     private void validateIdentifier(String identifier) {
         if (identifier == null || INVALID_SQL_IDENTIFIER.matcher(identifier).matches()) {
-            throw new IllegalArgumentException("Invalid SQL identifier: " + identifier);
+            throw new IllegalArgumentException("Invalid SQL identifier");
         }
     }
DIFF
patch -p0 < jsp.diff
