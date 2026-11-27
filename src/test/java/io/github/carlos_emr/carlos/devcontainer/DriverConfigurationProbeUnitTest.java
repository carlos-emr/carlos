/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.devcontainer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Runs the devcontainer DrugRef driver probe ({@code .devcontainer/drugref/.../DriverConfigurationProbe.java})
 * against the mounted development settings
 * ({@code .devcontainer/development/config/shared/volumes/drugref2.properties}).
 *
 * <p>#6282 (commit {@code f03cfea08e}) switched the mounted settings from the MariaDB driver and URL to MySQL
 * Connector/J, the driver the dev image's DrugRef master ships, and added this probe so CI refuses settings the
 * image's own driver cannot load or does not accept. Before this test the probe and the settings ran only in the
 * {@code container-images.yml} image job, which is not a release plan check (#6336).
 *
 * <p>The probe is compiled here with the JDK compiler, as the probe's own source file, against a stub WAR tree
 * laid out like the image's {@code drugref2.war}: the datasource bean's class in {@code WEB-INF/classes} with the
 * setters, getters and {@code close()} the probe calls, a {@code spring_config.xml} whose single
 * {@code dataSource} bean carries {@code ${db_driver}}/{@code ${db_url}} placeholders (the shape
 * {@code debian/assets/tests/test_jdbc_defaults.py} uses), and a stand-in for Connector/J's
 * {@code com.mysql.cj.jdbc.Driver} in a {@code WEB-INF/lib} jar that accepts only {@code jdbc:mysql:} URLs. The
 * probe then runs in its own JVM, so its exit status and console output are the evidence, as in CI.
 *
 * <p>Pinned: the real mounted settings pass and print only the PASS line (no URL, no password); a MariaDB URL,
 * the pre-#6282 MariaDB driver, ambiguous or incomplete datasource XML, unresolved or blank settings and a
 * DOCTYPE are all refused with a non-zero exit; the datasource is closed on both outcomes; and the exact
 * single-file source launch {@code check-driver-config.sh} performs works from an extracted WAR directory.
 */
@Tag("unit")
@Tag("devcontainer")
class DriverConfigurationProbeUnitTest {
    private static final Path PROBE_SOURCE =
            Path.of(".devcontainer/drugref/io/github/carlos_emr/carlos/devcontainer/DriverConfigurationProbe.java");
    private static final Path MOUNTED_SETTINGS =
            Path.of(".devcontainer/development/config/shared/volumes/drugref2.properties");
    private static final String PROBE_CLASS = "io.github.carlos_emr.carlos.devcontainer.DriverConfigurationProbe";
    private static final String PASS_LINE =
            "PASS mounted DrugRef settings load an image driver that accepts the effective URL";
    private static final String CLOSED_MARKER_PROPERTY = "fake.datasource.closed";

    private static final String DATA_SOURCE_CLASS = "io.github.carlos_emr.drugref2026.util.DrugrefDataSource";
    private static final String DATA_SOURCE_SOURCE = """
            package io.github.carlos_emr.drugref2026.util;

            /** Stand-in for the image WAR's datasource bean: keeps what the probe binds and never connects. */
            public class DrugrefDataSource implements AutoCloseable {
                private String driverClassName;
                private String jdbcUrl;

                public void setDriverClassName(String value) { driverClassName = value; }
                public String getDriverClassName() { return driverClassName; }
                public void setJdbcUrl(String value) { jdbcUrl = value; }
                public String getJdbcUrl() { return jdbcUrl; }

                @Override
                public void close() throws java.io.IOException {
                    String marker = System.getProperty("fake.datasource.closed");
                    if (marker != null) {
                        java.nio.file.Files.writeString(java.nio.file.Path.of(marker), "closed");
                    }
                }
            }
            """;

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final String DRIVER_SOURCE = """
            package com.mysql.cj.jdbc;

            /** Stand-in for the MySQL Connector/J driver the dev image's DrugRef ships: jdbc:mysql: URLs only. */
            public class Driver implements java.sql.Driver {
                @Override public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:mysql:"); }
                @Override public java.sql.Connection connect(String url, java.util.Properties info)
                        throws java.sql.SQLException {
                    throw new java.sql.SQLException("FAKE driver: the probe must never connect");
                }
                @Override public java.sql.DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) {
                    return new java.sql.DriverPropertyInfo[0];
                }
                @Override public int getMajorVersion() { return 0; }
                @Override public int getMinorVersion() { return 0; }
                @Override public boolean jdbcCompliant() { return false; }
                @Override public java.util.logging.Logger getParentLogger()
                        throws java.sql.SQLFeatureNotSupportedException {
                    throw new java.sql.SQLFeatureNotSupportedException();
                }
            }
            """;

    private static final String DRIVER_PROPERTY =
            "\n        <property name=\"driverClassName\" value=\"${db_driver}\" />";
    private static final String JDBC_URL_PROPERTY = "\n        <property name=\"jdbcUrl\" value=\"${db_url}\" />";
    private static final String DATA_SOURCE_BEAN = "\n    <bean id=\"dataSource\" class=\"" + DATA_SOURCE_CLASS
            + "\" destroy-method=\"close\">"
            + DRIVER_PROPERTY
            + JDBC_URL_PROPERTY
            + "\n        <property name=\"username\" value=\"${db_user}\" />"
            + "\n        <property name=\"password\" value=\"${db_password}\" />"
            + "\n    </bean>";
    /** Another bean with datasource-like properties: the probe must read only the dataSource bean's own. */
    private static final String OTHER_BEAN = "\n    <bean id=\"otherSource\" class=\"FAKE.UnusedBean\">"
            + "\n        <property name=\"jdbcUrl\" value=\"jdbc:fake:unused\" />"
            + "\n    </bean>";

    @TempDir
    static Path scratch;
    private static Path war;
    private static Path probeClasses;

    /** Per-test settings copies, XML fixtures, console captures and the datasource close marker. */
    @TempDir
    Path run;

    @BeforeAll
    static void compileTheProbeAgainstAStubWar() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("a JDK compiler builds the probe, as the single-file source launch does").isNotNull();
        war = scratch.resolve("drugref2");
        Path classes = Files.createDirectories(war.resolve("WEB-INF/classes"));
        Path lib = Files.createDirectories(war.resolve("WEB-INF/lib"));
        compile(compiler, classes, classes.toString(), source(scratch.resolve("datasource-src"), DATA_SOURCE_CLASS,
                DATA_SOURCE_SOURCE));
        Path driverClasses = Files.createDirectories(scratch.resolve("driver-classes"));
        compile(compiler, driverClasses, driverClasses.toString(), source(scratch.resolve("driver-src"), DRIVER_CLASS,
                DRIVER_SOURCE));
        jar(driverClasses, lib.resolve("fake-mysql-connector-j.jar"));
        Files.writeString(classes.resolve("spring_config.xml"), springConfig(DATA_SOURCE_BEAN + OTHER_BEAN));
        probeClasses = Files.createDirectories(scratch.resolve("probe-classes"));
        compile(compiler, probeClasses, String.join(File.pathSeparator, classes.toString(),
                lib.resolve("fake-mysql-connector-j.jar").toString()), PROBE_SOURCE.toAbsolutePath());
    }

    @Test
    void shouldPassWithoutPrintingTheUrlOrPassword_forTheMountedDevSettings() throws Exception {
        Properties mounted = load(MOUNTED_SETTINGS);

        ProbeRun result = probe(MOUNTED_SETTINGS.toAbsolutePath(), war.resolve("WEB-INF/classes/spring_config.xml"));

        assertThat(result.exitCode()).as(result.describe()).isZero();
        assertThat(result.stdout()).isEqualTo(PASS_LINE + System.lineSeparator());
        assertThat(result.output()).doesNotContain(mounted.getProperty("db_url"), mounted.getProperty("db_password"));
        assertThat(closedMarker()).as("the probe closes the datasource it created").exists();
    }

    @Test
    void shouldPairConnectorJWithAMysqlUrl_inTheMountedDevSettings() throws Exception {
        Properties mounted = load(MOUNTED_SETTINGS);

        // The pair the dev image's DrugRef master accepts (#6282); a MariaDB pair fails datasource creation there.
        assertThat(mounted.getProperty("db_driver")).isEqualTo(DRIVER_CLASS);
        assertThat(mounted.getProperty("db_url")).startsWith("jdbc:mysql:");
    }

    @Test
    void shouldRefuseAMariaDbUrl_whenTheImageDriverAcceptsOnlyMysqlUrls() throws Exception {
        Path settings = mountedSettingsWith(Map.of(
                "db_url", "jdbc:mariadb://db:3306/drugref2?password=FAKE-url-secret",
                "db_password", "FAKE-password-secret"));

        ProbeRun result = probe(settings, war.resolve("WEB-INF/classes/spring_config.xml"));

        assertThat(result.exitCode()).as(result.describe()).isNotZero();
        assertThat(result.stderr()).contains("The image driver refuses the configured JDBC URL");
        assertThat(result.stdout()).doesNotContain("PASS");
        assertThat(result.output()).doesNotContain("jdbc:mariadb:", "FAKE-url-secret", "FAKE-password-secret");
        assertThat(closedMarker()).as("the datasource is closed when the driver refuses the URL").exists();
    }

    @Test
    void shouldRefuseThePre6282MariaDbSettings_whenTheImageShipsOnlyConnectorJ() throws Exception {
        // The settings #6282 replaced: the image WAR has no MariaDB driver to load.
        Path settings = mountedSettingsWith(Map.of(
                "db_driver", "org.mariadb.jdbc.Driver",
                "db_url", "jdbc:mariadb://db:3306/drugref2"));

        ProbeRun result = probe(settings, war.resolve("WEB-INF/classes/spring_config.xml"));

        assertThat(result.exitCode()).as(result.describe()).isNotZero();
        assertThat(result.stderr()).contains("ClassNotFoundException: org.mariadb.jdbc.Driver");
        assertThat(result.stdout()).doesNotContain("PASS");
        assertThat(result.output()).doesNotContain("jdbc:mariadb:", load(MOUNTED_SETTINGS).getProperty("db_password"));
    }

    static Stream<Arguments> refusedDatasourceXml() {
        return Stream.of(
                Arguments.of("a duplicate dataSource bean",
                        DATA_SOURCE_BEAN + DATA_SOURCE_BEAN, "Duplicate datasource bean"),
                Arguments.of("no dataSource bean",
                        DATA_SOURCE_BEAN.replace("id=\"dataSource\"", "id=\"renamedSource\"") + OTHER_BEAN,
                        "No datasource bean in the image WAR"),
                Arguments.of("a missing jdbcUrl property",
                        DATA_SOURCE_BEAN.replace(JDBC_URL_PROPERTY, ""), "Missing datasource property: jdbcUrl"),
                Arguments.of("a duplicate driverClassName property",
                        DATA_SOURCE_BEAN.replace(DRIVER_PROPERTY, DRIVER_PROPERTY + DRIVER_PROPERTY),
                        "Duplicate datasource property: driverClassName"),
                Arguments.of("a placeholder the settings do not define",
                        DATA_SOURCE_BEAN.replace("${db_url}", "${db_undefined_url}"),
                        "Unresolved datasource property: jdbcUrl"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedDatasourceXml")
    void shouldRefuseTheDatasourceXml_whenItIsAmbiguousOrIncomplete(String fixture, String beans, String message)
            throws Exception {
        Path springXml = run.resolve("spring_config.xml");
        Files.writeString(springXml, springConfig(beans));

        ProbeRun result = probe(MOUNTED_SETTINGS.toAbsolutePath(), springXml);

        assertThat(result.exitCode()).as("%s%n%s", fixture, result.describe()).isNotZero();
        assertThat(result.stderr()).contains(message);
        assertThat(result.stdout()).doesNotContain("PASS");
        Properties mounted = load(MOUNTED_SETTINGS);
        assertThat(result.output()).doesNotContain(mounted.getProperty("db_url"), mounted.getProperty("db_password"));
    }

    @Test
    void shouldRefuseUnresolvedSettings_whenTheMountedFileOmitsOrBlanksDbUrl() throws Exception {
        for (Path settings : List.of(mountedSettingsWithout("db_url"), mountedSettingsWith(Map.of("db_url", "")))) {
            ProbeRun result = probe(settings, war.resolve("WEB-INF/classes/spring_config.xml"));

            assertThat(result.exitCode()).as(result.describe()).isNotZero();
            assertThat(result.stderr()).contains("Unresolved datasource property: jdbcUrl");
            assertThat(result.stdout()).doesNotContain("PASS");
        }
    }

    @Test
    void shouldRefuseADoctype_withoutResolvingItsExternalEntity() throws Exception {
        Path entity = run.resolve("FAKE-entity.txt");
        Files.writeString(entity, "FAKE-ENTITY-MARKER");
        Path springXml = run.resolve("spring_config.xml");
        Files.writeString(springXml, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE beans [<!ENTITY leak SYSTEM \"" + entity.toUri() + "\">]>\n"
                + "<beans xmlns=\"http://www.springframework.org/schema/beans\">"
                + DATA_SOURCE_BEAN.replace("${db_driver}", "&leak;") + "\n</beans>\n");

        ProbeRun result = probe(MOUNTED_SETTINGS.toAbsolutePath(), springXml);

        assertThat(result.exitCode()).as(result.describe()).isNotZero();
        assertThat(result.stderr()).contains("DOCTYPE is disallowed");
        assertThat(result.output()).doesNotContain("FAKE-ENTITY-MARKER").doesNotContain("PASS");
        assertThat(closedMarker()).as("the XML is refused before any datasource is created").doesNotExist();
    }

    @Test
    void shouldAcceptOnlyTheRepairedSettings_whenLaunchedAsTheWrapperDoes() throws Exception {
        // check-driver-config.sh: from the extracted WAR directory,
        // java --class-path 'WEB-INF/classes:WEB-INF/lib/*' <probe source> /drugref2.properties
        //     WEB-INF/classes/spring_config.xml
        ProbeRun repaired = wrapperLaunch(MOUNTED_SETTINGS.toAbsolutePath());
        ProbeRun original = wrapperLaunch(mountedSettingsWith(Map.of(
                "db_driver", "org.mariadb.jdbc.Driver",
                "db_url", "jdbc:mariadb://db:3306/drugref2")));

        assertThat(repaired.exitCode()).as(repaired.describe()).isZero();
        assertThat(repaired.stdout()).isEqualTo(PASS_LINE + System.lineSeparator());
        assertThat(original.exitCode()).as(original.describe()).isNotZero();
        assertThat(original.stderr()).contains("ClassNotFoundException: org.mariadb.jdbc.Driver");
    }

    /** Runs the compiled probe with the stub WAR's classes and library jars after it, as the image provides. */
    private ProbeRun probe(Path settings, Path springXml) throws Exception {
        String classPath = String.join(File.pathSeparator, probeClasses.toString(),
                war.resolve("WEB-INF/classes").toString(), war.resolve("WEB-INF/lib") + File.separator + "*");
        return launch(war, List.of("-D" + CLOSED_MARKER_PROPERTY + "=" + closedMarker(), "-cp", classPath,
                PROBE_CLASS, settings.toString(), springXml.toString()));
    }

    /** Runs the probe source file the way check-driver-config.sh does, relative to the extracted WAR. */
    private ProbeRun wrapperLaunch(Path settings) throws Exception {
        return launch(war, List.of("--class-path", "WEB-INF/classes" + File.pathSeparator + "WEB-INF/lib/*",
                PROBE_SOURCE.toAbsolutePath().toString(), settings.toString(), "WEB-INF/classes/spring_config.xml"));
    }

    private ProbeRun launch(Path directory, List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(arguments);
        Path stdout = Files.createTempFile(run, "stdout", ".txt");
        Path stderr = Files.createTempFile(run, "stderr", ".txt");
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile())
                .redirectOutput(stdout.toFile()).redirectError(stderr.toFile());
        // A JVM announces these variables on stderr; the probe's own console output is what is asserted.
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(2, TimeUnit.MINUTES)).as("the probe JVM finishes").isTrue();
        } finally {
            process.destroyForcibly();
        }
        return new ProbeRun(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
    }

    private Path closedMarker() {
        return run.resolve("datasource-closed");
    }

    /** A copy of the real mounted settings with the given keys set to new values. */
    private Path mountedSettingsWith(Map<String, String> values) throws IOException {
        List<String> lines = new ArrayList<>();
        int replaced = 0;
        for (String line : Files.readAllLines(MOUNTED_SETTINGS, StandardCharsets.UTF_8)) {
            String key = settingKey(line);
            if (key != null && values.containsKey(key)) {
                lines.add(key + "=" + values.get(key));
                replaced++;
            } else {
                lines.add(line);
            }
        }
        assertThat(replaced).as("the mounted settings define each of %s once", values.keySet())
                .isEqualTo(values.size());
        return writeSettings(lines);
    }

    /** A copy of the real mounted settings without the given key. */
    private Path mountedSettingsWithout(String removed) throws IOException {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(MOUNTED_SETTINGS, StandardCharsets.UTF_8)) {
            if (!removed.equals(settingKey(line))) {
                lines.add(line);
            }
        }
        assertThat(lines).as("the mounted settings define %s", removed)
                .hasSize(Files.readAllLines(MOUNTED_SETTINGS, StandardCharsets.UTF_8).size() - 1);
        return writeSettings(lines);
    }

    private Path writeSettings(List<String> lines) throws IOException {
        Path copy = Files.createTempFile(run, "drugref2-", ".properties");
        Files.write(copy, lines, StandardCharsets.UTF_8);
        return copy;
    }

    private static String settingKey(String line) {
        var matcher = Pattern.compile("^\\s*([A-Za-z0-9_.]+)\\s*[=:]").matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Properties load(Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private static String springConfig(String beans) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<beans xmlns=\"http://www.springframework.org/schema/beans\">" + beans + "\n</beans>\n";
    }

    private static Path source(Path root, String className, String text) throws IOException {
        Path file = root.resolve(className.replace('.', File.separatorChar) + ".java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }

    private static void compile(JavaCompiler compiler, Path output, String classPath, Path source) {
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = compiler.run(null, diagnostics, diagnostics, "-encoding", "UTF-8", "-proc:none",
                "-classpath", classPath, "-d", output.toString(), source.toString());
        assertThat(status).as("javac %s%n%s", source, diagnostics.toString(StandardCharsets.UTF_8)).isZero();
    }

    private static void jar(Path classes, Path target) throws IOException {
        try (OutputStream file = Files.newOutputStream(target);
                JarOutputStream jar = new JarOutputStream(file);
                Stream<Path> entries = Files.walk(classes)) {
            for (Path entry : entries.filter(Files::isRegularFile).sorted().toList()) {
                jar.putNextEntry(new JarEntry(classes.relativize(entry).toString().replace(File.separatorChar, '/')));
                Files.copy(entry, jar);
                jar.closeEntry();
            }
        }
    }

    private record ProbeRun(int exitCode, String stdout, String stderr) {
        String output() {
            return stdout + stderr;
        }

        String describe() {
            return "exit " + exitCode + System.lineSeparator() + "stdout:" + System.lineSeparator() + stdout
                    + "stderr:" + System.lineSeparator() + stderr;
        }
    }
}
