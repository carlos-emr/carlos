/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.deb;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.utility.MariaDbJdbcCompatibility;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mariadb.jdbc.Configuration;

/**
 * Compiles the packaged Flyway launcher ({@code debian/assets/flyway-runner/src/main}) and runs its two
 * self-tests, {@code FlywayRunnerNotesTest} and {@code FlywayRunnerJdbcTest}, the way
 * {@code debian/build-flyway-runner.sh} does during the package build.
 *
 * <p>Those self-tests are plain {@code main()} programs that only the package build compiles and runs, so
 * no release plan check executed them (#6336). The JDBC self-test carries the #5969 case added in
 * {@code b05f8a381b} (PR #6113): Connector/J {@code yes}/{@code no} Boolean spellings for
 * {@code allowPublicKeyRetrieval}, {@code useServerPrepStmts} and {@code tcpKeepAlive}, in any case, must reach
 * MariaDB Connector/J as Booleans after {@code FlywayRunner.jdbcUrl}.
 *
 * <p>The build is mirrored step by step: the launcher class path holds only {@code flyway-core},
 * {@code flyway-mysql} and exactly {@code mariadb-java-client-3.5.11.jar} (taken from this test JVM's class
 * path, which carries the same Maven artifacts the WAR's {@code WEB-INF/lib} does), plus the WAR's three MariaDB
 * compatibility classes (with their nested classes) and the TLS plugin service registration; the launcher is
 * compiled into {@code classes}, the self-tests into {@code test-classes}, and each self-test runs in its own JVM
 * on {@code test-classes:classes:<launcher class path>}. A self-test passes by exiting 0 after printing its PASS
 * line. The #5969 translation is also asserted directly against the compiled launcher's {@code jdbcUrl}.
 */
@Tag("unit")
@Tag("packaging")
class FlywayRunnerSelfTestUnitTest {
    private static final Path SOURCES = Path.of("debian/assets/flyway-runner/src");
    private static final Path RUNNER =
            SOURCES.resolve("main/java/io/github/carlos_emr/carlos/deb/FlywayRunner.java");
    private static final Path NOTES_TEST =
            SOURCES.resolve("test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerNotesTest.java");
    private static final Path JDBC_TEST =
            SOURCES.resolve("test/java/io/github/carlos_emr/carlos/deb/FlywayRunnerJdbcTest.java");
    /** The WAR libraries build-flyway-runner.sh extracts, each required by name. */
    private static final List<String> WAR_LIBRARIES = List.of("flyway-core", "flyway-mysql", "mariadb-java-client");
    private static final String PINNED_CONNECTOR = "mariadb-java-client-3.5.11.jar";
    private static final Pattern COMPATIBILITY_CLASS = Pattern.compile(
            "(MariaDbJdbcCompatibility|MariaDbCompatibilityDriver|MariaDbPreferredTlsPlugin)([$][^/]+)?[.]class");
    private static final String TLS_SERVICE = "META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin";

    @TempDir
    static Path work;
    private static Path classes;
    private static String selfTestClassPath;

    @BeforeAll
    static void buildTheLauncherLikeThePackageBuild() throws Exception {
        List<Path> libraries = warLibraries();
        classes = Files.createDirectories(work.resolve("classes"));
        copyCompatibilityAdapter(classes);
        String launcherClassPath = classes + File.pathSeparator
                + libraries.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
        compile(launcherClassPath, classes, RUNNER);
        // The self-tests stay out of the launcher's classes, as they stay out of the installed jar.
        Path testClasses = Files.createDirectories(work.resolve("test-classes"));
        compile(classes + File.pathSeparator + launcherClassPath, testClasses, NOTES_TEST, JDBC_TEST);
        selfTestClassPath = testClasses + File.pathSeparator + classes + File.pathSeparator + launcherClassPath;
    }

    @Test
    void shouldPassTheExistingObjectNotesSelfTest_againstTheWarFlywayApi() throws Exception {
        SelfTestRun result = runSelfTest("io.github.carlos_emr.carlos.deb.FlywayRunnerNotesTest");

        assertThat(result.exitCode()).as(result.describe()).isZero();
        assertThat(result.lastLine()).as(result.describe()).isEqualTo(
                "PASS Flyway existing-object notes, warnings, errors, delegation and configuration resets");
    }

    @Test
    void shouldPassTheJdbcSelfTest_withTheWarDriverAndTranslator() throws Exception {
        SelfTestRun result = runSelfTest("io.github.carlos_emr.carlos.deb.FlywayRunnerJdbcTest");

        assertThat(result.exitCode()).as(result.describe()).isZero();
        assertThat(result.lastLine()).as(result.describe())
                .isEqualTo("PASS Flyway MariaDB driver, legacy URL compatibility and TLS policy");
    }

    @Test
    void shouldTranslateConnectorJYesNoBooleans_throughTheCompiledLauncher() throws Exception {
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
                FlywayRunnerSelfTestUnitTest.class.getClassLoader())) {
            Method jdbcUrl = Class.forName("io.github.carlos_emr.carlos.deb.FlywayRunner", true, loader)
                    .getDeclaredMethod("jdbcUrl", String.class);
            jdbcUrl.setAccessible(true);

            String launcherUrl = (String) jdbcUrl.invoke(null, "jdbc:mysql://127.0.0.1:3306/carlos"
                    + "?useSSL=false&allowPublicKeyRetrieval=yes&useServerPrepStmts=No&tcpKeepAlive=YES");
            var settings = MariaDbJdbcCompatibility.settings(launcherUrl, new Properties());
            Configuration configuration = Configuration.parse(settings.url(), settings.properties());

            assertThat(configuration).as("MariaDB Connector/J accepts the launcher URL").isNotNull();
            assertThat(configuration.database()).isEqualTo("carlos");
            assertThat(configuration.allowPublicKeyRetrieval()).as("allowPublicKeyRetrieval=yes").isTrue();
            assertThat(configuration.useServerPrepStmts()).as("useServerPrepStmts=No").isFalse();
            assertThat(configuration.tcpKeepAlive()).as("tcpKeepAlive=YES").isTrue();
        }
    }

    /** flyway-core, flyway-mysql and the pinned MariaDB connector, refused by name as the build refuses them. */
    private static List<Path> warLibraries() {
        List<Path> classPath = Arrays.stream(System.getProperty("surefire.test.class.path",
                        System.getProperty("java.class.path")).split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();
        List<Path> selected = new ArrayList<>();
        for (String library : WAR_LIBRARIES) {
            Pattern jar = Pattern.compile(Pattern.quote(library) + "-[^/]*\\.jar");
            List<Path> matches = classPath.stream()
                    .filter(entry -> entry.getFileName() != null
                            && jar.matcher(entry.getFileName().toString()).matches())
                    .toList();
            assertThat(matches).as("%s is a WAR library the launcher compiles against", library).isNotEmpty();
            selected.addAll(matches);
        }
        List<String> connectors = selected.stream()
                .map(entry -> entry.getFileName().toString())
                .filter(name -> name.startsWith("mariadb-java-client-"))
                .toList();
        assertThat(connectors).as("build-flyway-runner.sh requires exactly %s", PINNED_CONNECTOR)
                .containsExactly(PINNED_CONNECTOR);
        return selected;
    }

    /** The WAR classes build-flyway-runner.sh extracts from WEB-INF/classes, with their nested classes. */
    private static void copyCompatibilityAdapter(Path target) throws Exception {
        Path webInfClasses = Path.of(MariaDbJdbcCompatibility.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI());
        assertThat(webInfClasses).as("the compiled application classes the WAR packages").isDirectory();
        Path utility = webInfClasses.resolve("io/github/carlos_emr/carlos/utility");
        Path destination = Files.createDirectories(target.resolve("io/github/carlos_emr/carlos/utility"));
        List<String> copied = new ArrayList<>();
        try (Stream<Path> files = Files.list(utility)) {
            for (Path file : files.filter(file -> COMPATIBILITY_CLASS.matcher(file.getFileName().toString()).matches())
                    .sorted().toList()) {
                Files.copy(file, destination.resolve(file.getFileName().toString()));
                copied.add(file.getFileName().toString());
            }
        }
        assertThat(copied).contains("MariaDbJdbcCompatibility.class", "MariaDbCompatibilityDriver.class",
                "MariaDbPreferredTlsPlugin.class");
        Path service = webInfClasses.resolve(TLS_SERVICE);
        assertThat(service).as("the MariaDB TLS compatibility service registration").isRegularFile();
        Files.createDirectories(target.resolve(TLS_SERVICE).getParent());
        Files.copy(service, target.resolve(TLS_SERVICE));
    }

    /** javac -nowarn -encoding UTF-8 --release 25, as build-flyway-runner.sh invokes it. */
    private static void compile(String classPath, Path output, Path... sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("a JDK compiler, as the package build uses javac").isNotNull();
        List<String> arguments = new ArrayList<>(List.of("-nowarn", "-encoding", "UTF-8", "--release", "25",
                "-cp", classPath, "-d", output.toString()));
        for (Path source : sources) {
            arguments.add(source.toString());
        }
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int status = compiler.run(null, diagnostics, diagnostics, arguments.toArray(String[]::new));
        assertThat(status).as("javac %s%n%s", Arrays.toString(sources), diagnostics.toString(StandardCharsets.UTF_8))
                .isZero();
    }

    private static SelfTestRun runSelfTest(String mainClass) throws Exception {
        Path stdout = Files.createTempFile(work, "stdout", ".txt");
        Path stderr = Files.createTempFile(work, "stderr", ".txt");
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", selfTestClassPath, mainClass)
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile());
        // FlywayRunnerJdbcTest requires its launcher children's stderr to be exactly one error line; a JVM
        // would prepend a "Picked up ..." notice for these variables, which the package build never sets.
        builder.environment().keySet().removeAll(List.of("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS"));
        Process process = builder.start();
        try {
            assertThat(process.waitFor(5, TimeUnit.MINUTES)).as("%s finishes", mainClass).isTrue();
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
        return new SelfTestRun(process.exitValue(), Files.readString(stdout), Files.readString(stderr));
    }

    private record SelfTestRun(int exitCode, String stdout, String stderr) {
        String lastLine() {
            List<String> lines = stdout.lines().filter(line -> !line.isBlank()).toList();
            return lines.isEmpty() ? "" : lines.getLast();
        }

        String describe() {
            return "exit " + exitCode + System.lineSeparator() + "stdout:" + System.lineSeparator() + stdout
                    + "stderr:" + System.lineSeparator() + stderr;
        }
    }
}
