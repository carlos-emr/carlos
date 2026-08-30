#!/bin/bash
# DTD-validate every struts*.xml the way Struts itself does at deploy time.
#
# Why this exists: the config files are only DTD-validated when the webapp
# context starts. A change that is perfectly well-formed XML — a <result>
# accidentally landing at package level next to a self-closing <action/>, for
# example — passes every XML parse, compiles, packages, and then takes the
# WHOLE application down at deploy with "Dispatcher initialization failed".
# That exact failure shipped in a local build once; this check is the reason it
# cannot again.
#
# The DTDs are taken from the struts2-core jar in the local Maven repository,
# so validation always matches the Struts version the build actually uses.
set -euo pipefail
cd "$(dirname "$0")/../.."

STRUTS_VERSION=$(grep -oPm1 '(?<=<struts.version>)[^<]+' pom.xml || true)
if [ -z "$STRUTS_VERSION" ]; then
    # struts version may be inline in the dependency rather than a property
    STRUTS_VERSION=$(grep -A2 'struts2-core' pom.xml | grep -oPm1 '(?<=<version>)[^<]+' || true)
fi
JAR=$(find ~/.m2 local_repo -path "*struts2-core*${STRUTS_VERSION}*.jar" 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    JAR=$(find ~/.m2 local_repo -name 'struts2-core-*.jar' 2>/dev/null | sort | tail -1)
fi
[ -n "$JAR" ] || { echo "SKIP: no struts2-core jar found to take the DTDs from"; exit 0; }

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
unzip -o -j -q "$JAR" '*.dtd' -d "$WORK"

cat > "$WORK/DtdCheck.java" <<'JAVA'
import javax.xml.parsers.*;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import java.io.*;

public class DtdCheck {
    public static void main(String[] args) throws Exception {
        String dtdDir = args[0];
        boolean ok = true;
        for (int i = 1; i < args.length; i++) {
            File f = new File(args[i]);
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setValidating(true);
            SAXParser sp = spf.newSAXParser();
            final boolean[] failed = {false};
            try {
                sp.parse(f, new DefaultHandler() {
                    @Override public InputSource resolveEntity(String pub, String sys) {
                        String name = sys.substring(sys.lastIndexOf('/') + 1);
                        try { return new InputSource(new FileInputStream(new File(dtdDir, name))); }
                        catch (Exception e) { return null; }
                    }
                    @Override public void error(SAXParseException e) {
                        System.out.println("INVALID " + f + ":" + e.getLineNumber() + " " + e.getMessage());
                        failed[0] = true;
                    }
                    @Override public void fatalError(SAXParseException e) { error(e); }
                });
            } catch (Exception e) {
                System.out.println("INVALID " + f + " " + e.getMessage());
                failed[0] = true;
            }
            if (failed[0]) ok = false;
        }
        System.out.println(ok ? "Struts config DTD validation: PASS" : "Struts config DTD validation: FAIL");
        System.exit(ok ? 0 : 1);
    }
}
JAVA
JAVAC=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/javac
JAVA=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/java
"$JAVAC" -d "$WORK" "$WORK/DtdCheck.java"
"$JAVA" -cp "$WORK" DtdCheck "$WORK" src/main/webapp/WEB-INF/classes/struts*.xml
