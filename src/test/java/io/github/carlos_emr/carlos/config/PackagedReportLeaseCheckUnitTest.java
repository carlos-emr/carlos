/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
@Tag("read")
class PackagedReportLeaseCheckUnitTest {
    @TempDir
    Path directory;

    private Path writePackagedWebapp() throws Exception {
        Path webapp = directory.resolve("packaged-webapp");
        Path classes = Files.createDirectories(webapp.resolve("WEB-INF/classes"));
        Files.writeString(classes.resolve("jdbc-defaults.properties"), "");
        Files.writeString(classes.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\n");
        return classes;
    }

    private void writeContextXml(Path catalinaBase, Path webappDir) throws Exception {
        Path localhostDir = Files.createDirectories(catalinaBase.resolve("conf/Catalina/localhost"));
        Files.writeString(localhostDir.resolve("carlos.xml"),
                "<Context docBase=\"" + webappDir + "\">\n"
                        + "  <Resources cachingAllowed=\"true\"/>\n"
                        + "</Context>\n");
    }

    @Test
    void shouldResolveTimeoutFromInstalledSources_inStartupOrder() throws Exception {
        Path classes = writePackagedWebapp();
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        String[] args = {classes.toString(), managed.toString()};
        Files.writeString(classes.resolve("jdbc-defaults.properties"), "site.lease=420\n");
        Files.writeString(classes.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\nsite.lease=15\n");
        Files.writeString(managed, "");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(managed, "db_remove_abandoned_timeout=${site.lease}\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=420\n");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=+015\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "site.lease=${db_remove_abandoned_timeout}\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home));
    }

    @Test
    void shouldApproveOnlyRemovalOfTheManagedOverride_whenItRestoresTheDefault() throws Exception {
        Path classes = writePackagedWebapp();
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        Path candidate = directory.resolve("candidate.properties");
        String[] args = {classes.toString(), managed.toString(), candidate.toString()};
        Files.writeString(managed, "site.setting=keep\ndb_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "site.setting=keep\n");
        assertEquals(10, PackagedReportLeaseCheck.status(args, home));

        Files.writeString(home, "db_remove_abandoned_timeout=15\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(home, "db_remove_abandoned_timeout=420\n");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home));
        Files.delete(home);
        Files.createSymbolicLink(home, managed);
        assertEquals(10, PackagedReportLeaseCheck.status(args, home));

        Files.writeString(candidate, "site.setting=changed\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
        Files.writeString(candidate, "site.setting=keep\n");
        Files.writeString(classes.resolve("carlos.properties"), "db_remove_abandoned_timeout=60\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
    }

    @Test
    void shouldInspectEnvSelectedPropertiesWithoutMigratingManaged_whenEnvOverridesProperties() throws Exception {
        Path classes = writePackagedWebapp();
        Path catalinaBase = directory.resolve("base");
        writeContextXml(catalinaBase, classes.getParent().getParent());
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path site = directory.resolve("site.properties");
        Path env = directory.resolve("carlos-emr.env");
        Path home = directory.resolve("home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(site, "db_remove_abandoned_timeout = 15\n");
        String[] args = {classes.toString(), managed.toString(), candidate.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(env, catalinaBase,
                directory.resolve("home"), List.of(directory.resolve("systemd")));

        Files.writeString(env, "# site override\nexport PROPERTIES=\"${CONF_DIR}/site.properties\" # inline\n");
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, launch));

        Files.writeString(site, "db_remove_abandoned_timeout = 420\n");
        assertEquals(20, PackagedReportLeaseCheck.status(args, home, launch));

        // Explicit selection of the same managed file still permits migration.
        Files.writeString(env, "PROPERTIES='" + managed + "'\nCARLOS_JAVA_OPTS=\"-Dhttps.proxyHost=proxy.local\"\n");
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));

        // Symlinked PROPERTIES pointing to the managed file resolves as the same file.
        Path alias = directory.resolve("alias.properties");
        Files.createSymbolicLink(alias, managed);
        Files.writeString(env, "PROPERTIES=$CONF_DIR/alias.properties\n");
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));
    }

    @Test
    void shouldHonorJvmOptionOverridesAndPrecedence_whenEnvSetsJavaOptions() throws Exception {
        Path classes = writePackagedWebapp();
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path site = directory.resolve("site.properties");
        Path customHomeDir = Files.createDirectory(directory.resolve("custom-home"));
        Path env = directory.resolve("carlos-emr.env");
        Path defaultHome = directory.resolve("default-home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(site, "db_remove_abandoned_timeout = 15\n");
        String[] args = {classes.toString(), managed.toString(), candidate.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(env, null, null, List.of());

        // CARLOS_JAVA_OPTS overrides the wrapper's -Dcarlos_override_properties.
        Files.writeString(env, "CARLOS_JAVA_OPTS=\"-Dfoo=bar -Dcarlos_override_properties=" + site + "\"\n");
        assertEquals(25, PackagedReportLeaseCheck.status(args, defaultHome, launch));

        // JAVA_OPTS appears before CATALINA_OPTS in catalina.sh, so it cannot beat wrapper PROPERTIES,
        // while -Duser.home in JAVA_OPTS still changes the home override path.
        Files.writeString(customHomeDir.resolve("carlos.properties"), "db_remove_abandoned_timeout = 15\n");
        Files.writeString(env, "JAVA_OPTS=\"-Dcarlos_override_properties=" + site
                + " -Duser.home=" + customHomeDir + "\"\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, defaultHome, launch));

        // _JAVA_OPTIONS overrides command-line -Dcarlos_override_properties.
        Files.writeString(site, "db_remove_abandoned_timeout = 420\n");
        Files.writeString(env, "_JAVA_OPTIONS=\"-Dcarlos_override_properties=" + site + "\"\n");
        assertEquals(20, PackagedReportLeaseCheck.status(args, defaultHome, launch));

        // Unreadable wrapper PROPERTIES fails closed even when CARLOS_JAVA_OPTS overrides it.
        Files.writeString(env, "PROPERTIES=" + directory.resolve("missing.properties")
                + "\nCARLOS_JAVA_OPTS=\"-Dcarlos_override_properties=" + site + "\"\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, defaultHome, launch));
    }

    @Test
    void shouldFailClosed_whenEnvSyntaxOrJvmOptionsAreUnverifiable() throws Exception {
        Path classes = writePackagedWebapp();
        Path managed = directory.resolve("carlos.properties.managed");
        Path env = directory.resolve("carlos-emr.env");
        Path home = directory.resolve("home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        String[] args = {classes.toString(), managed.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(env, null, null, List.of());

        List<String> invalidEnvs = List.of(
                "PROPERTIES=$(echo /etc/carlos-emr/site.properties)\n",
                "PROPERTIES=`echo /etc/carlos-emr/site.properties`\n",
                "PROPERTIES=\"`echo /tmp/x`\"\n",
                "PROPERTIES=\"$(echo /tmp/x)\"\n",
                "PROPERTIES=\"${CONF_DIR:-/etc}/site.properties\"\n",
                "PROPERTIES=\"${CONF_DIR/a/b}\"\n",
                "PROPERTIES=\"${UNTERMINATED\"\n",
                "PROPERTIES=\"unterminated\n",
                "PROPERTIES='unterminated\n",
                "PROPERTIES='ok' trailing\n",
                "PROPERTIES=\"ok\" trailing\n",
                "PROPERTIES=\"line\\\ncontinued\"\n",
                "PROPERTIES=unquoted with spaces\n",
                "PROPERTIES=bad;semi\n",
                "PROPERTIES=relative/path.properties\n",
                "PROPERTIES=\n",
                "PROPERTIES=\"/tmp/has space.properties\"\n",
                "if true; then PROPERTIES=/tmp/x; fi\n",
                "123BAD=/tmp/x\n",
                "PROPERTIES=\"$1\"\n",
                "CARLOS_JAVA_OPTS=\"@/etc/carlos-emr/jvm.args\"\n",
                "CARLOS_JAVA_OPTS=\"-XX:Flags=/etc/carlos-emr/flags\"\n",
                "CARLOS_JAVA_OPTS=\"-XX:VMOptionsFile=/etc/carlos-emr/vm.options\"\n",
                "CARLOS_JAVA_OPTS=\"$SITE_OPTS\"\n",
                "CARLOS_JAVA_OPTS=\"${SITE_OPTS}\"\n",
                "CARLOS_JAVA_OPTS=\"-Dcarlos_override_properties\"\n",
                "CARLOS_JAVA_OPTS=\"-Duser.home\"\n",
                "CARLOS_JAVA_OPTS=\"-Dcarlos_override_properties=relative.properties\"\n",
                "CARLOS_JAVA_OPTS=\"-Dfoo='quoted'\"\n"
        );
        for (String content : invalidEnvs) {
            Files.writeString(env, content);
            assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch), "Expected fail-closed for: " + content);
        }

        // Valid escapes, literal trailing '$', and explicitly defined empty variables parse cleanly.
        Files.writeString(env, "UNRELATED=\"a\\$b\\\\c\\\"d\\e$\"\nEMPTY=\nCARLOS_JAVA_OPTS=\"$EMPTY ${EMPTY}\"\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home, launch));

        // Directory instead of regular file for envFile fails closed.
        Path envDir = Files.createDirectory(directory.resolve("env-dir"));
        var dirLaunch = new PackagedReportLeaseCheck.LaunchConfig(envDir, null, null, List.of());
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, dirLaunch));
    }

    @Test
    void shouldFailClosed_whenSetenvOrSystemdOverridesAlterLaunch() throws Exception {
        Path classes = writePackagedWebapp();
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path home = directory.resolve("home.properties");
        Path env = directory.resolve("carlos-emr.env");
        Path base = Files.createDirectories(directory.resolve("catalina-base/bin"));
        writeContextXml(base.getParent(), classes.getParent().getParent());
        Path catalinaHome = Files.createDirectories(directory.resolve("catalina-home/bin"));
        Path systemdHigh = Files.createDirectory(directory.resolve("systemd-high"));
        Path systemd = Files.createDirectory(directory.resolve("systemd"));
        Path dropInDir = Files.createDirectory(systemd.resolve("carlos-emr.service.d"));
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(env, "CARLOS_JAVA_OPTS=\"\"\n");
        String[] args = {classes.toString(), managed.toString(), candidate.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(env, base.getParent(), catalinaHome.getParent(),
                List.of(systemdHigh, systemd, directory.resolve("missing-systemd")));

        // Packaged-style benign drop-in and masked symlink to /dev/null both remain verifiable.
        Path benign = dropInDir.resolve("10-eform-renderer.conf");
        Files.writeString(benign, """
                [Unit]
                Wants=other.service

                # comment
                [Service]
                Environment=EFORM_RENDER_SANDBOX=true
                MemoryMax=4G
                """);
        Files.writeString(dropInDir.resolve("README.txt"), "ignored non-conf file\n");
        Path mask = systemd.resolve("carlos-emr.service");
        Files.createSymbolicLink(mask, Path.of("/dev/null"));
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));

        // Non-mask custom unit override fails closed.
        Files.delete(mask);
        Files.writeString(mask, "[Service]\nExecStart=/usr/local/bin/custom\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch));
        Files.delete(mask);

        // Quoted benign Environment with comments inside line continuation passes; launch-affecting drop-ins fail closed.
        Path custom = dropInDir.resolve("20-site.conf");
        Files.writeString(custom, "[Service]\nEnvironment=\"FOO=1\" \\\n# comment mid-continuation\n; semicolon comment\n 'BAR=2'\n");
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));

        for (String dropIn : List.of(
                "[Service]\nExecStart=/bin/true\n",
                "[Service]\nEnvironmentFile=/etc/other.env\n",
                "[Service]\nPrivateTmp=no\n",
                "[Service]\nProtectHome=no\n",
                "[Service]\nEnvironment=CARLOS_JAVA_OPTS=-Dcarlos_override_properties=/tmp/x\n",
                "[Service]\nEnvironment=\"PROPERTIES=/tmp/x\"\n",
                "[Service]\nEnvironment=\n",
                "[Service]\nEnvironment=\"BAD\\ESC=1\"\n",
                "[Service]\nEnvironment=\"UNTERMINATED=1\n",
                "[Service]\nEnvironment=NO_EQUALS\n",
                "[Service]\nMalformedLine\n",
                "[Service]\nMemoryMax=4G\\\n")) {
            Files.writeString(custom, dropIn);
            assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch), "Expected fail-closed for: " + dropIn);
        }

        // A higher-priority /dev/null mask shadows the lower-priority drop-in of the same name.
        Path highDropInDir = Files.createDirectory(systemdHigh.resolve("carlos-emr.service.d"));
        Path maskedDropIn = highDropInDir.resolve("20-site.conf");
        Files.createSymbolicLink(maskedDropIn, Path.of("/dev/null"));
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));
        Files.delete(maskedDropIn);
        Files.delete(custom);
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));

        // Prefix (carlos-.service.d) and service-wide (service.d) drop-ins also apply and fail closed when overriding launch.
        for (String dropInDirName : List.of("carlos-.service.d", "service.d")) {
            Path extraDropInDir = Files.createDirectory(systemd.resolve(dropInDirName));
            Path override = extraDropInDir.resolve("10-override.conf");
            Files.writeString(override, "[Service]\nEnvironment=PROPERTIES=/tmp/other.properties\n");
            assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch), "Expected fail-closed for: " + dropInDirName);
            Files.delete(override);
            Files.delete(extraDropInDir);
        }

        // Symlink to managed file updates on rename (10), whereas a hard-link/bind-mount alias does not (25).
        Path symlinkAlias = directory.resolve("symlink-carlos.properties");
        Files.createSymbolicLink(symlinkAlias, managed);
        Files.writeString(env, "PROPERTIES=" + symlinkAlias + "\n");
        assertEquals(10, PackagedReportLeaseCheck.status(args, home, launch));

        Path hardlinkAlias = directory.resolve("hardlink-carlos.properties");
        Files.createLink(hardlinkAlias, managed);
        Files.writeString(env, "PROPERTIES=" + hardlinkAlias + "\n");
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, launch));
        Files.writeString(env, "CARLOS_JAVA_OPTS=\"\"\n");

        // Symlinked drop-in directory fails closed.
        Path symlinkSystemd = Files.createDirectory(directory.resolve("systemd-sym"));
        Files.createSymbolicLink(symlinkSystemd.resolve("carlos-emr.service.d"), dropInDir);
        assertEquals(1, PackagedReportLeaseCheck.status(args, home,
                new PackagedReportLeaseCheck.LaunchConfig(env, base.getParent(), catalinaHome.getParent(),
                        List.of(symlinkSystemd))));

        // setenv.sh under CATALINA_BASE or CATALINA_HOME or env-redirected CATALINA_BASE fails closed.
        Path setenvBase = base.resolve("setenv.sh");
        Files.writeString(setenvBase, "CATALINA_OPTS=\"$CATALINA_OPTS -Dcarlos_override_properties=/tmp/x\"\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch));
        Files.delete(setenvBase);

        Path envBase = Files.createDirectories(directory.resolve("env-catalina-base/bin"));
        writeContextXml(envBase.getParent(), classes.getParent().getParent());
        Files.writeString(envBase.resolve("setenv.sh"), "export JAVA_OPTS=-Duser.home=/tmp\n");
        Files.writeString(env, "CATALINA_BASE=" + envBase.getParent() + "\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch));
    }

    @Test
    void shouldInspectActiveTomcatContextDocBase_andRefuseUnverifiableContexts() throws Exception {
        Path packagedClasses = writePackagedWebapp();
        Path customWebapp = directory.resolve("custom-webapp");
        Path customClasses = Files.createDirectories(customWebapp.resolve("WEB-INF/classes"));
        Files.writeString(customClasses.resolve("jdbc-defaults.properties"), "db_remove_abandoned_timeout=15\n");
        Files.writeString(customClasses.resolve("carlos.properties"), "");

        Path catalinaBase = directory.resolve("ctx-base");
        Path catalinaHome = directory.resolve("ctx-home");
        writeContextXml(catalinaBase, customWebapp);
        Files.writeString(catalinaBase.resolve("conf/context.xml"),
                "<Context><WatchedResource>WEB-INF/web.xml</WatchedResource><CookieProcessor sameSiteCookies=\"lax\"/></Context>\n");

        Path managed = directory.resolve("managed-ctx.properties");
        Path candidate = directory.resolve("candidate-ctx.properties");
        Path home = directory.resolve("home-ctx.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        String[] args = {packagedClasses.toString(), managed.toString(), candidate.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(null, catalinaBase, catalinaHome, List.of());

        // Custom docBase supplying 15 must report 25 (separate 15), never 10 (migrate).
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, launch));

        // Custom docBase supplying 360 with empty managed reports 20 (separate OK).
        Files.writeString(customClasses.resolve("jdbc-defaults.properties"), "db_remove_abandoned_timeout=360\n");
        Files.writeString(managed, "");
        assertEquals(20, PackagedReportLeaseCheck.status(args, home, launch));

        // server.xml with custom Engine, Host alias, xmlBase, appBase, and empty docBase resolving to appBase/carlos.
        Path hostAppCarlos = Files.createDirectories(catalinaBase.resolve("apps/carlos/WEB-INF/classes"));
        Files.writeString(hostAppCarlos.resolve("jdbc-defaults.properties"), "db_remove_abandoned_timeout=15\n");
        Files.writeString(hostAppCarlos.resolve("carlos.properties"), "");
        Path customXmlBase = Files.createDirectories(catalinaBase.resolve("conf/CustomEngine/custom-xml"));
        Files.writeString(catalinaBase.resolve("conf/server.xml"), """
                <Server port="-1" shutdown="SHUTDOWN">
                  <Service name="Catalina">
                    <Engine name="CustomEngine" defaultHost="clinic-alias">
                      <Host name="primary-host" appBase="apps" xmlBase="${catalina.base}/conf/CustomEngine/custom-xml">
                        <Alias>clinic-alias</Alias>
                      </Host>
                    </Engine>
                  </Service>
                </Server>
                """);
        Files.writeString(customXmlBase.resolve("carlos.xml"), "<Context docBase=\"\"/>\n");
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, launch));

        // Unverifiable Tomcat XML constructs fail closed (1).
        for (String badCarlosXml : List.of(
                "<Context className=\"custom.Context\" docBase=\"" + customWebapp + "\"/>",
                "<Context docBase=\"" + customWebapp + "\"><Parameter name=\"a\" value=\"b\"/></Context>",
                "<Context docBase=\"" + customWebapp + "\"><Resources className=\"custom.Res\"/></Context>",
                "<Context docBase=\"" + customWebapp + "\"><Resources><PreResources base=\"/x\"/></Resources></Context>",
                "<Context docBase=\"${unknown.prop}/webapp\"/>",
                "<Context docBase=\"" + directory.resolve("missing-webapp") + "\"/>",
                "<NotContext/>",
                "<!DOCTYPE Context [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><Context/>",
                "<Context broken")) {
            Files.writeString(customXmlBase.resolve("carlos.xml"), badCarlosXml);
            assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch), "Expected fail-closed for: " + badCarlosXml);
        }

        // Inline <Context> or missing/ambiguous Host in server.xml fails closed (1).
        Files.writeString(customXmlBase.resolve("carlos.xml"), "<Context docBase=\"" + customWebapp + "\"/>\n");
        for (String badServerXml : List.of(
                "<Server><Service><Engine name=\"E\" defaultHost=\"h\"><Host name=\"h\"><Context path=\"/carlos\"/></Host></Engine></Service></Server>",
                "<Server><Service><Engine name=\"E\" defaultHost=\"missing\"><Host name=\"h\"/></Engine></Service></Server>",
                "<Server><Service><Engine name=\"E\" defaultHost=\"dup\"><Host name=\"dup\"/><Host name=\"dup\"/></Engine></Service></Server>",
                "<Server><Service/><Service/></Server>",
                "<Server><Service><Engine name=\"\" defaultHost=\"h\"/></Service></Server>")) {
            Files.writeString(catalinaBase.resolve("conf/server.xml"), badServerXml);
            assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch), "Expected fail-closed for: " + badServerXml);
        }
    }

    @Test
    void shouldFailClosed_whenPathsResolveIntoIsolatedServiceMountNamespaces() throws Exception {
        Path nonTmpDir = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "ns-check-");
        try {
            Path packagedWebapp = nonTmpDir.resolve("packaged-webapp");
            Path packagedClasses = Files.createDirectories(packagedWebapp.resolve("WEB-INF/classes"));
            Files.writeString(packagedClasses.resolve("jdbc-defaults.properties"), "");
            Files.writeString(packagedClasses.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\n");

            Path catalinaBase = nonTmpDir.resolve("ns-base");
            writeContextXml(catalinaBase, packagedWebapp);
            Path packagedSystemd = Files.createDirectories(nonTmpDir.resolve("usr/lib/systemd/system"));
            Files.writeString(packagedSystemd.resolve("carlos-emr.service"), """
                    [Unit]
                    Description=CARLOS EMR
                    [Service]
                    PrivateTmp=YES
                    ProtectHome=no
                    """);
            Path managed = nonTmpDir.resolve("managed-ns.properties");
            Path candidate = nonTmpDir.resolve("candidate-ns.properties");
            Path safeHome = nonTmpDir.resolve("safe-home.properties");
            Path env = nonTmpDir.resolve("carlos-emr.env");
            Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
            Files.writeString(candidate, "");
            Files.writeString(env, "CARLOS_JAVA_OPTS=\"\"\n");
            String[] args = {packagedClasses.toString(), managed.toString(), candidate.toString()};
            var launch = new PackagedReportLeaseCheck.LaunchConfig(env, catalinaBase, null, List.of(packagedSystemd));

            // Non-/tmp paths migrate normally (10) while PrivateTmp=YES is active.
            assertEquals(10, PackagedReportLeaseCheck.status(args, safeHome, launch));

            // Host-only /tmp carlos.properties with 360 must NOT mask managed 15 under PrivateTmp=yes.
            Path tmpHomeDir = Files.createDirectories(directory.resolve("private-tmp-home"));
            Path tmpHome = tmpHomeDir.resolve("carlos.properties");
            Files.writeString(tmpHome, "db_remove_abandoned_timeout = 360\n");
            assertEquals(1, PackagedReportLeaseCheck.status(args, tmpHome, launch));

            // Symlink outside /tmp pointing into /tmp (both existing and missing child) fails closed.
            Path symlinkToTmpDir = nonTmpDir.resolve("symlink-home");
            Files.createSymbolicLink(symlinkToTmpDir, tmpHomeDir);
            assertEquals(1, PackagedReportLeaseCheck.status(args, symlinkToTmpDir.resolve("carlos.properties"), launch));
            assertEquals(1, PackagedReportLeaseCheck.status(args, symlinkToTmpDir.resolve("sub/carlos.properties"), launch));

            // Selected properties or docBase under /tmp when managed is outside /tmp also fails closed.
            Path tmpSite = directory.resolve("tmp-site.properties");
            Files.writeString(tmpSite, "db_remove_abandoned_timeout = 360\n");
            Files.writeString(env, "PROPERTIES=" + tmpSite + "\n");
            assertEquals(1, PackagedReportLeaseCheck.status(args, safeHome, launch));
            Files.writeString(env, "CARLOS_JAVA_OPTS=\"\"\n");

            Path tmpWebapp = writePackagedWebapp().getParent().getParent();
            writeContextXml(catalinaBase, tmpWebapp);
            assertEquals(1, PackagedReportLeaseCheck.status(args, safeHome, launch));
            writeContextXml(catalinaBase, packagedWebapp);

            // Non-existent /var/tmp and ProtectHome (/home, /root) paths fail closed when enabled.
            assertEquals(1, PackagedReportLeaseCheck.status(args, Path.of("/var/tmp/carlos-nonexistent/carlos.properties"), launch));
            Files.writeString(packagedSystemd.resolve("carlos-emr.service"), """
                    [Service]
                    PrivateTmp=no
                    ProtectHome=YES
                    """);
            assertEquals(1, PackagedReportLeaseCheck.status(args, Path.of("/home/clinic/carlos.properties"), launch));
            assertEquals(1, PackagedReportLeaseCheck.status(args, Path.of("/root/carlos.properties"), launch));

            // When PrivateTmp=no and ProtectHome=false, host paths are shared with the service view.
            Files.writeString(packagedSystemd.resolve("carlos-emr.service"), """
                    [Service]
                    PrivateTmp=no
                    ProtectHome=false
                    """);
            assertEquals(0, PackagedReportLeaseCheck.status(args, tmpHome, launch));
        } finally {
            try (var walk = Files.walk(nonTmpDir)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    @Test
    void shouldHonorSystemdManagerEnvironmentAndDeferWhenUnavailable() throws Exception {
        Path classes = writePackagedWebapp();
        Path catalinaBase = directory.resolve("mgr-base");
        writeContextXml(catalinaBase, classes.getParent().getParent());
        Path managed = directory.resolve("managed-mgr.properties");
        Path site = directory.resolve("site-mgr.properties");
        Path home = directory.resolve("home-mgr.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 360\n");
        Files.writeString(site, "db_remove_abandoned_timeout = 15\n");
        String[] args = {classes.toString(), managed.toString()};

        // 1. DefaultEnvironment in system.conf / system.conf.d sets _JAVA_OPTIONS selecting site.properties (15).
        Path usrSystemd = Files.createDirectory(directory.resolve("usr-systemd"));
        Path etcSystemd = Files.createDirectory(directory.resolve("etc-systemd"));
        Files.createDirectories(usrSystemd.resolve("system.conf.d"));
        Files.createDirectories(etcSystemd.resolve("system.conf.d"));
        Files.writeString(usrSystemd.resolve("system.conf"),
                "[Manager]\nDefaultEnvironment=\"_JAVA_OPTIONS=-Dcarlos_override_properties=" + site + "\"\n");
        var confLaunch = new PackagedReportLeaseCheck.LaunchConfig(
                null, catalinaBase, null, List.of(), null, false,
                List.of(usrSystemd, etcSystemd), null, List.of());
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, confLaunch));

        // Empty DefaultEnvironment= in higher-priority /etc/systemd/system.conf resets earlier DefaultEnvironment.
        Files.writeString(etcSystemd.resolve("system.conf"), "[Manager]\nDefaultEnvironment=\n");
        assertEquals(0, PackagedReportLeaseCheck.status(args, home, confLaunch));

        // Drop-in in system.conf.d applies after system.conf; /dev/null mask in /etc shadows /usr/lib drop-in.
        Files.writeString(usrSystemd.resolve("system.conf.d/10-java.conf"),
                "[Manager]\nDefaultEnvironment='_JAVA_OPTIONS=-Dcarlos_override_properties=" + site + "'\n");
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, confLaunch));
        Files.createSymbolicLink(etcSystemd.resolve("system.conf.d/10-java.conf"), Path.of("/dev/null"));
        assertEquals(0, PackagedReportLeaseCheck.status(args, home, confLaunch));

        // 2. Live managerEnvFile (NUL-delimited) sets _JAVA_OPTIONS -> 25; managerEnvUnavailable -> 1.
        Path managerEnv = directory.resolve("manager.env");
        Files.write(managerEnv, ("PATH=/usr/bin\0_JAVA_OPTIONS=-Dcarlos_override_properties=" + site + "\0")
                .getBytes(StandardCharsets.UTF_8));
        var liveLaunch = new PackagedReportLeaseCheck.LaunchConfig(
                null, catalinaBase, null, List.of(), managerEnv, false, List.of(), null, List.of());
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, liveLaunch));

        var unavailableLaunch = new PackagedReportLeaseCheck.LaunchConfig(
                null, catalinaBase, null, List.of(), null, true, List.of(), null, List.of());
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, unavailableLaunch));

        // 3. /etc/environment and environment.d/*.conf assignments (with unrelated generator syntax ignored).
        Path envDir = Files.createDirectory(directory.resolve("env.d"));
        Path etcEnv = directory.resolve("etc-environment");
        Files.writeString(envDir.resolve("10-os.conf"), "UNRELATED_GEN=${FOO:-bar}\n");
        Files.writeString(etcEnv, "export _JAVA_OPTIONS=\"-Dcarlos_override_properties=" + site + "\"\n");
        var etcEnvLaunch = new PackagedReportLeaseCheck.LaunchConfig(
                null, catalinaBase, null, List.of(), null, false, List.of(), etcEnv, List.of(envDir));
        assertEquals(25, PackagedReportLeaseCheck.status(args, home, etcEnvLaunch));

        // Malformed manager environment entry or DefaultEnvironment fails closed (1).
        Files.write(managerEnv, "MALFORMED_NO_EQUALS\0".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, liveLaunch));
        Files.writeString(etcSystemd.resolve("system.conf"), "[Manager]\nDefaultEnvironment=NO_EQUALS\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, confLaunch));
    }

    @Test
    void shouldLoadLaunchConfigFromSystemProperties_whenPropertiesAreConfigured() {
        String prevEnv = System.getProperty("carlos.reportLeaseCheck.envFile");
        String prevDirs = System.getProperty("carlos.reportLeaseCheck.systemdDirs");
        String prevUnavailable = System.getProperty("carlos.reportLeaseCheck.managerEnvUnavailable");
        try {
            System.setProperty("carlos.reportLeaseCheck.envFile", "");
            System.setProperty("carlos.reportLeaseCheck.systemdDirs", "/etc/systemd/system::/usr/lib/systemd/system");
            System.setProperty("carlos.reportLeaseCheck.managerEnvUnavailable", "true");
            var config = PackagedReportLeaseCheck.LaunchConfig.fromSystemProperties();
            assertNull(config.envFile());
            assertEquals(List.of(Path.of("/etc/systemd/system"), Path.of("/usr/lib/systemd/system")),
                    config.systemdDirs());
            assertTrue(config.managerEnvUnavailable());
        } finally {
            restoreProperty("carlos.reportLeaseCheck.envFile", prevEnv);
            restoreProperty("carlos.reportLeaseCheck.systemdDirs", prevDirs);
            restoreProperty("carlos.reportLeaseCheck.managerEnvUnavailable", prevUnavailable);
        }
        assertFalse(PackagedReportLeaseCheck.LaunchConfig.none().managerEnvUnavailable());
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void shouldReportAnInspectionFailure_whenArgumentsOrFilesAreMissing() {
        Path home = directory.resolve("home.properties");
        assertEquals(1, PackagedReportLeaseCheck.status(new String[0], home));
        String[] args = {directory.toString(), directory.resolve("missing.properties").toString()};
        assertEquals(1, PackagedReportLeaseCheck.status(args, home));
    }
}
