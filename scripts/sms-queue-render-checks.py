#!/usr/bin/env python3
# Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later; no warranty.
"""Render the current queue JSP on an isolated loopback Tomcat with synthetic models.

Run: HEAVY_SLOTS=1 heavy python3 scripts/sms-queue-render-checks.py
Requires Java 21+, Tomcat 11 (CATALINA_HOME or --tomcat-home), and the project's
already cached Maven dependencies. Does not start CARLOS or connect to a database.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tomcat-home", type=Path, default=Path(os.environ.get("CATALINA_HOME", "/usr/local/tomcat")))
    parser.add_argument("--maven-repository", type=Path, default=Path.home() / ".m2/repository")
    args = parser.parse_args()
    repo = Path(__file__).resolve().parent.parent
    jars = [
        "org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar",
        "jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.2/jakarta.servlet.jsp.jstl-api-3.0.2.jar",
        "org/owasp/encoder/encoder/1.4.0/encoder-1.4.0.jar",
        "com/github/spotbugs/spotbugs-annotations/4.9.3/spotbugs-annotations-4.9.3.jar",
    ]
    dependencies = [args.maven_repository / jar for jar in jars]
    for path in [args.tomcat_home / "lib/catalina.jar", args.tomcat_home / "bin/tomcat-juli.jar", *dependencies]:
        if not path.is_file():
            parser.error(f"Required cached dependency missing: {path}")
    with tempfile.TemporaryDirectory(prefix="carlos-sms-queue-render-") as scratch:
        scratch = Path(scratch)
        webapp = scratch / "webapp"
        classes = webapp / "WEB-INF/classes"
        classes.mkdir(parents=True)
        lib = webapp / "WEB-INF/lib"
        lib.mkdir()
        for jar in dependencies[:3]:
            shutil.copy2(jar, lib / jar.name)
        for relative in ["WEB-INF/jsp/admin/smsQueue.jsp", "WEB-INF/jsp/admin/smsQueueRows.jspf",
                         "WEB-INF/jsp/includes/global-head.jspf", "WEB-INF/carlos-tag.tld"]:
            target = webapp / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(repo / "src/main/webapp" / relative, target)
        for bundle in (repo / "src/main/resources").glob("oscarResources*.properties"):
            shutil.copy2(bundle, classes / bundle.name)
        package = repo / "src/main/java/io/github/carlos_emr/carlos"
        sources = [package / relative for relative in ["utility/SafeEncode.java", "utility/tld/CarlosEncodeTag.java",
                   "sms/viewmodel/SmsQueueViewModel.java", "sms/viewmodel/SmsQueueWindow.java"]]
        sources.append(repo / "scripts/test/sms/SmsQueueRenderCheck.java")
        classpath = os.pathsep.join(map(str, [args.tomcat_home / "lib/*", args.tomcat_home / "bin/tomcat-juli.jar", *dependencies]))
        subprocess.run(["javac", "-cp", classpath, "-d", str(classes), *map(str, sources)], check=True, timeout=60)
        subprocess.run(["java", "-Xmx256m", "-cp", str(classes) + os.pathsep + classpath,
                        "SmsQueueRenderCheck", str(scratch / "tomcat"), str(webapp)], check=True, timeout=180)


if __name__ == "__main__":
    main()
