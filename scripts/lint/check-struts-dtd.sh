#!/bin/bash
# DTD-validate every struts*.xml the way Struts does at deploy time.
#
# Why this exists: the config files are only DTD-validated when the webapp
# context starts. A change that is perfectly well-formed XML -- a <result>
# accidentally landing at package level next to a self-closing <action/>, for
# example -- passes every XML parse, compiles, packages, and then takes the
# WHOLE application down at deploy with "Dispatcher initialization failed". That
# exact failure shipped in a local build once. The CI source-lint job runs this
# check with JDK 21, and it remains useful locally before pushing struts*.xml changes.
#
# The DTD is vendored under scripts/lint/dtd so the check is hermetic -- no jar
# hunting, no Maven, no network -- and it validates with a tiny SAX program that
# needs only a JDK. If a config ever declares a DTD version that is not vendored
# (e.g. after a Struts upgrade), the check FAILS LOUDLY asking for the new DTD
# rather than silently validating against a stale one.
set -euo pipefail
cd "$(dirname "$0")/../.."

DTD_DIR="scripts/lint/dtd"
CONFIGS=(src/main/webapp/WEB-INF/classes/struts*.xml)

# Fail if any config points at a DTD we have not vendored.
missing=0
for f in "${CONFIGS[@]}"; do
    dtd=$(grep -o 'struts-[0-9.]*\.dtd' "$f" | head -1 || true)
    [ -z "$dtd" ] && { echo "WARN: $f declares no struts DTD DOCTYPE"; continue; }
    if [ ! -f "$DTD_DIR/$dtd" ]; then
        echo "MISSING VENDORED DTD: $f needs $DTD_DIR/$dtd — extract it from the struts2-core jar (unzip -j <jar> '$dtd' -d $DTD_DIR) and commit it."
        missing=1
    fi
done
[ "$missing" = 0 ] || exit 1

JAVAC=$(command -v javac || echo "${JAVA_HOME:-}/bin/javac")
JAVA=$(command -v java || echo "${JAVA_HOME:-}/bin/java")
# No javac means this check validated NOTHING. Exiting 0 there makes the script
# report success on any machine without a JDK -- including a CI runner missing
# its setup-java step, which is precisely where a silent pass is most harmful,
# since the deploy-time failure this guards against takes the whole webapp down.
# Fail by default and require an explicit opt-out to skip.
if [ ! -x "$JAVAC" ]; then
    if [ "${STRUTS_DTD_LINT_ALLOW_SKIP:-0}" = "1" ]; then
        echo "SKIP: no javac available to run the DTD validation (STRUTS_DTD_LINT_ALLOW_SKIP=1)"
        exit 0
    fi
    echo "FAIL: no javac available, so no struts*.xml was validated."
    echo "      Install a JDK, or set STRUTS_DTD_LINT_ALLOW_SKIP=1 to accept an unvalidated run."
    exit 1
fi

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
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
                        if (sys == null || sys.isEmpty()) { return null; }
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
"$JAVAC" -d "$WORK" "$WORK/DtdCheck.java"
"$JAVA" -cp "$WORK" DtdCheck "$DTD_DIR" "${CONFIGS[@]}"
