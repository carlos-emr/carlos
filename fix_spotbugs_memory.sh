cat << 'INNER_EOF' > .github/workflows/spotbugs.yml.patch
--- .github/workflows/spotbugs.yml
+++ .github/workflows/spotbugs.yml
@@ -169,6 +169,7 @@
             echo 'Compiling and running SpotBugs analysis'
             echo '========================================='
             mvn -B compile spotbugs:spotbugs \
+              -Dspotbugs.maxHeap=4096 \
               -Pspotbugs,skip-dependency-lock \
               -DskipTests \
               -T 1C
INNER_EOF
patch -p0 < .github/workflows/spotbugs.yml.patch
