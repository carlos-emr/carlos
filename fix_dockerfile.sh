cat << 'INNER_EOF' > .devcontainer/development/Dockerfile.patch
--- .devcontainer/development/Dockerfile
+++ .devcontainer/development/Dockerfile
@@ -21,6 +21,7 @@
 ARG DOCS_PATH='/home/oscar/development/volumes'
 ARG OSCAR_DOCUMENT="${DOCS_PATH}/OscarDocument"
 ARG DB_DOCS="/db-data/documents"
+RUN mkdir -p /var/lib/OscarDocument
 ARG DOCS_DEST="/var/lib/OscarDocument/oscar/document"

 WORKDIR /workspace
INNER_EOF
patch -p0 < .devcontainer/development/Dockerfile.patch
