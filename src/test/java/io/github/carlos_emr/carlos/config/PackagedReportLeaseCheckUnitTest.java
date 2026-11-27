/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("unit")
@Tag("read")
class PackagedReportLeaseCheckUnitTest {
    @TempDir
    Path directory;

    private void writeDefaults() throws Exception {
        Files.writeString(directory.resolve("jdbc-defaults.properties"), "");
        Files.writeString(directory.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\n");
    }

    @Test
    void shouldResolveTimeoutFromInstalledSources_inStartupOrder() throws Exception {
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        String[] args = {directory.toString(), managed.toString()};
        Files.writeString(directory.resolve("jdbc-defaults.properties"), "site.lease=420\n");
        Files.writeString(directory.resolve("carlos.properties"), "db_remove_abandoned_timeout=360\nsite.lease=15\n");
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
        Path managed = directory.resolve("managed.properties");
        Path home = directory.resolve("home.properties");
        Path candidate = directory.resolve("candidate.properties");
        String[] args = {directory.toString(), managed.toString(), candidate.toString()};
        writeDefaults();
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
        Files.writeString(directory.resolve("carlos.properties"), "db_remove_abandoned_timeout=60\n");
        assertEquals(15, PackagedReportLeaseCheck.status(args, home));
    }

    @Test
    void shouldInspectEnvSelectedPropertiesWithoutMigratingManaged_whenEnvOverridesProperties() throws Exception {
        writeDefaults();
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path site = directory.resolve("site.properties");
        Path env = directory.resolve("carlos-emr.env");
        Path home = directory.resolve("home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(site, "db_remove_abandoned_timeout = 15\n");
        String[] args = {directory.toString(), managed.toString(), candidate.toString()};
        var launch = new PackagedReportLeaseCheck.LaunchConfig(env, directory.resolve("base"),
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
        writeDefaults();
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path site = directory.resolve("site.properties");
        Path customHomeDir = Files.createDirectory(directory.resolve("custom-home"));
        Path env = directory.resolve("carlos-emr.env");
        Path defaultHome = directory.resolve("default-home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(site, "db_remove_abandoned_timeout = 15\n");
        String[] args = {directory.toString(), managed.toString(), candidate.toString()};
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
        writeDefaults();
        Path managed = directory.resolve("carlos.properties.managed");
        Path env = directory.resolve("carlos-emr.env");
        Path home = directory.resolve("home.properties");
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        String[] args = {directory.toString(), managed.toString()};
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
        writeDefaults();
        Path managed = directory.resolve("carlos.properties.managed");
        Path candidate = directory.resolve("carlos.properties.candidate");
        Path home = directory.resolve("home.properties");
        Path env = directory.resolve("carlos-emr.env");
        Path base = Files.createDirectories(directory.resolve("catalina-base/bin"));
        Path catalinaHome = Files.createDirectories(directory.resolve("catalina-home/bin"));
        Path systemdHigh = Files.createDirectory(directory.resolve("systemd-high"));
        Path systemd = Files.createDirectory(directory.resolve("systemd"));
        Path dropInDir = Files.createDirectory(systemd.resolve("carlos-emr.service.d"));
        Files.writeString(managed, "db_remove_abandoned_timeout = 15\n");
        Files.writeString(candidate, "");
        Files.writeString(env, "CARLOS_JAVA_OPTS=\"\"\n");
        String[] args = {directory.toString(), managed.toString(), candidate.toString()};
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
        Files.writeString(envBase.resolve("setenv.sh"), "export JAVA_OPTS=-Duser.home=/tmp\n");
        Files.writeString(env, "CATALINA_BASE=" + envBase.getParent() + "\n");
        assertEquals(1, PackagedReportLeaseCheck.status(args, home, launch));
    }

    @Test
    void shouldLoadLaunchConfigFromSystemProperties_whenPropertiesAreConfigured() {
        String prevEnv = System.getProperty("carlos.reportLeaseCheck.envFile");
        String prevDirs = System.getProperty("carlos.reportLeaseCheck.systemdDirs");
        try {
            System.setProperty("carlos.reportLeaseCheck.envFile", "");
            System.setProperty("carlos.reportLeaseCheck.systemdDirs", "/etc/systemd/system::/usr/lib/systemd/system");
            var config = PackagedReportLeaseCheck.LaunchConfig.fromSystemProperties();
            assertEquals(null, config.envFile());
            assertEquals(List.of(Path.of("/etc/systemd/system"), Path.of("/usr/lib/systemd/system")),
                    config.systemdDirs());
        } finally {
            restoreProperty("carlos.reportLeaseCheck.envFile", prevEnv);
            restoreProperty("carlos.reportLeaseCheck.systemdDirs", prevDirs);
        }
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
