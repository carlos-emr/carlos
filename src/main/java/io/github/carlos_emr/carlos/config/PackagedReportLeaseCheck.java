/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;

/**
 * Read-only Debian configure check, run as the service user with the installed Spring libraries.
 *
 * <p>Resolves the service launcher's selected properties file and {@code user.home} override
 * (from {@code carlos-emr.env}, JVM option variables, {@code setenv.sh}, and systemd unit
 * overrides) before deciding whether migrating the managed file restores the packaged default.</p>
 *
 * @since 2026-11-26
 */
public final class PackagedReportLeaseCheck {
    static final int STATUS_OK = 0;
    static final int STATUS_UNVERIFIABLE = 1;
    static final int STATUS_MIGRATE = 10;
    static final int STATUS_MANAGED_FIFTEEN = 15;
    static final int STATUS_SEPARATE_OK = 20;
    static final int STATUS_SEPARATE_FIFTEEN = 25;

    private static final String KEY = "db_remove_abandoned_timeout";
    private static final String PROPERTIES_FILE_NAME = "carlos.properties";
    private static final String VAR_PROPERTIES = "PROPERTIES";
    private static final String VAR_CATALINA_BASE = "CATALINA_BASE";
    private static final String VAR_CATALINA_HOME = "CATALINA_HOME";
    private static final String ENV_FILE_PROPERTY = "carlos.reportLeaseCheck.envFile";
    private static final String CATALINA_BASE_PROPERTY = "carlos.reportLeaseCheck.catalinaBase";
    private static final String CATALINA_HOME_PROPERTY = "carlos.reportLeaseCheck.catalinaHome";
    private static final String SYSTEMD_DIRS_PROPERTY = "carlos.reportLeaseCheck.systemdDirs";
    private static final String MANAGER_ENV_FILE_PROPERTY = "carlos.reportLeaseCheck.managerEnvFile";
    private static final String MANAGER_ENV_UNAVAILABLE_PROPERTY = "carlos.reportLeaseCheck.managerEnvUnavailable";
    private static final String SYSTEMD_MANAGER_CONF_DIRS_PROPERTY = "carlos.reportLeaseCheck.systemdManagerConfDirs";
    private static final String ETC_ENVIRONMENT_PROPERTY = "carlos.reportLeaseCheck.etcEnvironment";
    private static final String ENVIRONMENT_DIRS_PROPERTY = "carlos.reportLeaseCheck.environmentDirs";

    private static final String DEFAULT_ENV_FILE = "/etc/carlos-emr/carlos-emr.env";
    private static final String DEFAULT_CATALINA_BASE = "/var/lib/carlos-emr/catalina";
    private static final String DEFAULT_CATALINA_HOME = "/usr/share/tomcat11";
    private static final String DEFAULT_SYSTEMD_DIRS =
            "/etc/systemd/system:/run/systemd/system:/usr/lib/systemd/system";
    private static final String DEFAULT_SYSTEMD_MANAGER_CONF_DIRS =
            "/usr/lib/systemd:/run/systemd:/etc/systemd";
    private static final String DEFAULT_ETC_ENVIRONMENT = "/etc/environment";
    private static final String DEFAULT_ENVIRONMENT_DIRS =
            "/usr/lib/environment.d:/run/environment.d:/etc/environment.d";
    private static final String SERVICE_UNIT = "carlos-emr.service";
    private static final List<String> SERVICE_DROPIN_DIRS = List.of(
            SERVICE_UNIT + ".d", "carlos-.service.d", "service.d");
    private static final String OVERRIDE_FLAG = "-Dcarlos_override_properties";
    private static final String HOME_FLAG = "-Duser.home";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");
    private static final Pattern SHELL_METACHARS = Pattern.compile("['\"\\\\`$;|&<>()*?\\[~!]");
    private static final Set<String> UNVERIFIABLE_SERVICE_KEYS = Set.of(
            "ExecStart", "ExecStartPre", "User", "EnvironmentFile",
            "PassEnvironment", "UnsetEnvironment", "WorkingDirectory",
            "RootDirectory", "RootImage", "BindPaths", "BindReadOnlyPaths",
            "PrivateTmp", "ProtectHome", "TemporaryFileSystem", "InaccessiblePaths", "OverlayPaths");
    private static final Set<String> LAUNCH_ENV_VARS = Set.of(
            VAR_PROPERTIES, "CONF_DIR", "CARLOS_JAVA_OPTS", "JAVA_OPTS", "CATALINA_OPTS",
            VAR_CATALINA_BASE, VAR_CATALINA_HOME, "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS", "HOME");

    /**
     * Paths and systemd environment sources inspected to determine how the packaged service launches.
     *
     * @param envFile Path optional {@code carlos-emr.env} file read by the Tomcat wrapper
     * @param catalinaBase Path Tomcat {@code CATALINA_BASE} directory checked for context descriptors and {@code bin/setenv.sh}
     * @param catalinaHome Path Tomcat {@code CATALINA_HOME} directory checked for {@code bin/setenv.sh}
     * @param systemdDirs List of systemd unit directories checked for unit files, overrides, and drop-ins
     * @param managerEnvFile Path optional NUL-delimited live systemd manager environment snapshot
     * @param managerEnvUnavailable boolean true when the live systemd manager environment could not be queried
     * @param systemdManagerConfDirs List of systemd config directories checked for {@code system.conf} and {@code system.conf.d}
     * @param etcEnvironment Path optional {@code /etc/environment} file
     * @param environmentDirs List of {@code environment.d} directories checked for generator assignments
     */
    record LaunchConfig(
            Path envFile,
            Path catalinaBase,
            Path catalinaHome,
            List<Path> systemdDirs,
            Path managerEnvFile,
            boolean managerEnvUnavailable,
            List<Path> systemdManagerConfDirs,
            Path etcEnvironment,
            List<Path> environmentDirs) {

        LaunchConfig(Path envFile, Path catalinaBase, Path catalinaHome, List<Path> systemdDirs) {
            this(envFile, catalinaBase, catalinaHome, systemdDirs, null, false, List.of(), null, List.of());
        }

        static LaunchConfig none() {
            return new LaunchConfig(null, null, null, List.of(), null, false, List.of(), null, List.of());
        }

        static LaunchConfig fromSystemProperties() {
            return new LaunchConfig(
                    optionalPath(System.getProperty(ENV_FILE_PROPERTY, DEFAULT_ENV_FILE)),
                    Path.of(System.getProperty(CATALINA_BASE_PROPERTY, DEFAULT_CATALINA_BASE)),
                    Path.of(System.getProperty(CATALINA_HOME_PROPERTY, DEFAULT_CATALINA_HOME)),
                    colonPaths(System.getProperty(SYSTEMD_DIRS_PROPERTY, DEFAULT_SYSTEMD_DIRS)),
                    optionalPath(System.getProperty(MANAGER_ENV_FILE_PROPERTY, "")),
                    Boolean.getBoolean(MANAGER_ENV_UNAVAILABLE_PROPERTY),
                    colonPaths(System.getProperty(SYSTEMD_MANAGER_CONF_DIRS_PROPERTY, DEFAULT_SYSTEMD_MANAGER_CONF_DIRS)),
                    optionalPath(System.getProperty(ETC_ENVIRONMENT_PROPERTY, DEFAULT_ETC_ENVIRONMENT)),
                    colonPaths(System.getProperty(ENVIRONMENT_DIRS_PROPERTY, DEFAULT_ENVIRONMENT_DIRS)));
        }

        private static Path optionalPath(String raw) {
            return raw == null || raw.isEmpty() ? null : Path.of(raw);
        }

        private static List<Path> colonPaths(String raw) {
            List<Path> paths = new ArrayList<>();
            for (String entry : raw.split(":")) {
                if (!entry.isEmpty()) {
                    paths.add(Path.of(entry));
                }
            }
            return paths;
        }
    }

    private record LaunchSelection(Path properties, Path home) { }

    private record LaunchResolution(Path classes, Path properties, Path home) { }

    record ParsedToken(String token, int nextIndex) { }

    private PackagedReportLeaseCheck() {
    }

    /**
     * Exits with:
     * <ul>
     *   <li>{@code 10} when the launcher deploys the packaged webapp with the managed file and publishing the candidate restores 360;</li>
     *   <li>{@code 0} when the launcher deploys the packaged webapp with the managed file and its effective timeout is not 15;</li>
     *   <li>{@code 15} when the launcher deploys the packaged webapp with the managed file and its effective timeout remains 15;</li>
     *   <li>{@code 20} when the launcher selects a separate properties file or webapp whose effective timeout is not 15;</li>
     *   <li>{@code 25} when the launcher selects a separate properties file or webapp whose effective timeout is 15;</li>
     *   <li>{@code 1} when launch selection, mount-namespace visibility, or configuration cannot be verified.</li>
     * </ul>
     * Property values must never reach package logs.
     *
     * @param args String[] installed WEB-INF/classes directory, managed properties file, and optional candidate file
     */
    public static void main(String[] args) {
        System.exit(status(args, Path.of(System.getProperty("user.home"), PROPERTIES_FILE_NAME),
                LaunchConfig.fromSystemProperties()));
    }

    static int status(String[] args, Path home) {
        return status(args, home, LaunchConfig.none());
    }

    static int status(String[] args, Path home, LaunchConfig launch) {
        if (args.length != 2 && args.length != 3) {
            return STATUS_UNVERIFIABLE;
        }
        try {
            Path packagedClasses = Path.of(args[0]);
            Path managed = Path.of(args[1]);
            LaunchResolution selection = resolveLaunch(packagedClasses, managed, home, launch);
            boolean selectsManaged = resolvesToManaged(managed, selection.properties())
                    && sameFile(packagedClasses, selection.classes());
            Properties defaults = PackagedConfigurationPreflight.readFile(
                    selection.classes().resolve("jdbc-defaults.properties"));
            defaults.putAll(PackagedConfigurationPreflight.readFile(
                    selection.classes().resolve(PROPERTIES_FILE_NAME)));
            Properties installed = PackagedConfigurationPreflight.readFile(selection.properties());
            Properties overrides = new Properties();
            if (!Files.notExists(selection.home()) && !Files.isSameFile(selection.properties(), selection.home())) {
                overrides = PackagedConfigurationPreflight.readFile(selection.home());
            }
            if (configuredTimeout(defaults, installed, overrides) != 15) {
                return selectsManaged ? STATUS_OK : STATUS_SEPARATE_OK;
            }
            if (selectsManaged && args.length == 3 && !overrides.containsKey(KEY)) {
                // Removing the physical line must remove exactly this logical property.
                // Reject duplicate/escaped keys and lines belonging to a continued value.
                Properties candidate = PackagedConfigurationPreflight.readFile(Path.of(args[2]));
                if (installed.remove(KEY) != null && installed.equals(candidate)
                        && configuredTimeout(defaults, candidate, overrides) == 360) {
                    return STATUS_MIGRATE;
                }
            }
            return selectsManaged ? STATUS_MANAGED_FIFTEEN : STATUS_SEPARATE_FIFTEEN;
        } catch (IOException | RuntimeException _) {
            // Spring diagnostics may contain the referenced property's value.
            return STATUS_UNVERIFIABLE;
        }
    }

    private static LaunchResolution resolveLaunch(
            Path packagedClasses, Path managed, Path defaultHome, LaunchConfig launch) throws IOException {
        verifySystemd(launch.systemdDirs());
        var isolation = PackagedReportLeaseContext.readPackagedUnitIsolation(launch.systemdDirs());
        Map<String, String> vars = buildLaunchVariables(managed, launch);
        verifyNoTomcatSetenv(launch, vars);
        Path effectiveCatalinaBase = effectiveCatalinaPath(vars, VAR_CATALINA_BASE, launch.catalinaBase());
        Path effectiveCatalinaHome = effectiveCatalinaPath(vars, VAR_CATALINA_HOME, launch.catalinaHome());
        Path deployedClasses = PackagedReportLeaseContext.resolveDeployedClasses(
                effectiveCatalinaBase, effectiveCatalinaHome, packagedClasses);

        Path envProperties = requireAbsoluteNoWhitespace(vars.get(VAR_PROPERTIES));
        Path selectedProperties = envProperties;
        Path selectedHome = defaultHome;
        // Exact precedence across HotSpot env vars, catalina.sh (JAVA_OPTS before CATALINA_OPTS),
        // and carlos-emr-tomcat (-Dcarlos_override_properties=${PROPERTIES} before ${CARLOS_JAVA_OPTS}).
        for (String name : List.of("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS")) {
            LaunchSelection updated = applyJvmOptions(vars.get(name), selectedProperties, selectedHome, false);
            selectedHome = updated.home();
        }
        for (String name : List.of("CARLOS_JAVA_OPTS", "_JAVA_OPTIONS")) {
            LaunchSelection updated = applyJvmOptions(vars.get(name), selectedProperties, selectedHome, true);
            selectedProperties = updated.properties();
            selectedHome = updated.home();
        }
        if (!sameFile(envProperties, selectedProperties) && !Files.isReadable(envProperties)) {
            throw new IOException("Wrapper PROPERTIES is unreadable");
        }
        PackagedReportLeaseContext.verifyServiceFilesystemView(
                managed, packagedClasses, selectedProperties, selectedHome, deployedClasses, isolation);
        return new LaunchResolution(deployedClasses, selectedProperties, selectedHome);
    }

    private static Path effectiveCatalinaPath(Map<String, String> vars, String key, Path fallback) throws IOException {
        return vars.containsKey(key) ? requireAbsoluteNoWhitespace(vars.get(key)) : fallback;
    }

    private static Map<String, String> buildLaunchVariables(Path managed, LaunchConfig launch) throws IOException {
        Map<String, String> vars = new HashMap<>();
        PackagedReportLeaseContext.loadManagerEnvironment(launch, vars);
        Path confDir = launch.envFile() != null && launch.envFile().getParent() != null
                ? launch.envFile().getParent() : Path.of("/etc/carlos-emr");
        vars.put("CONF_DIR", confDir.toString());
        vars.put(VAR_PROPERTIES, managed.toString());
        if (launch.catalinaBase() != null) {
            vars.put(VAR_CATALINA_BASE, launch.catalinaBase().toString());
        }
        if (launch.catalinaHome() != null) {
            vars.put(VAR_CATALINA_HOME, launch.catalinaHome().toString());
        }
        if (launch.envFile() != null && !Files.notExists(launch.envFile(), LinkOption.NOFOLLOW_LINKS)) {
            parseEnvFile(launch.envFile(), vars);
        }
        return vars;
    }

    private static void verifyNoTomcatSetenv(LaunchConfig launch, Map<String, String> vars) throws IOException {
        requireNoSetenv(launch.catalinaBase());
        requireNoSetenv(launch.catalinaHome());
        if (vars.containsKey(VAR_CATALINA_BASE)) {
            requireNoSetenv(requireAbsoluteNoWhitespace(vars.get(VAR_CATALINA_BASE)));
        }
        if (vars.containsKey(VAR_CATALINA_HOME)) {
            requireNoSetenv(requireAbsoluteNoWhitespace(vars.get(VAR_CATALINA_HOME)));
        }
    }

    private static void verifySystemd(List<Path> systemdDirs) throws IOException {
        Map<String, Set<String>> seenDropIns = new HashMap<>();
        for (Path dir : systemdDirs) {
            if (!Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
                verifyUnitOverride(dir);
                verifyServiceDropInDirs(dir, seenDropIns);
            }
        }
    }

    private static void verifyUnitOverride(Path dir) throws IOException {
        if (!isPackagedSystemdDir(dir)) {
            Path unitOverride = dir.resolve(SERVICE_UNIT);
            if (!Files.notExists(unitOverride, LinkOption.NOFOLLOW_LINKS) && !isMaskSymlink(unitOverride)) {
                throw new IOException("Custom systemd unit override cannot be verified");
            }
        }
    }

    private static void verifyServiceDropInDirs(Path dir, Map<String, Set<String>> seenDropIns) throws IOException {
        for (String dropInName : SERVICE_DROPIN_DIRS) {
            Path dropInDir = dir.resolve(dropInName);
            if (!Files.notExists(dropInDir, LinkOption.NOFOLLOW_LINKS)) {
                verifyDropInDirectory(dropInDir, seenDropIns.computeIfAbsent(dropInName, _ -> new HashSet<>()));
            }
        }
    }

    private static void verifyDropInDirectory(Path dropInDir, Set<String> seen) throws IOException {
        if (Files.isSymbolicLink(dropInDir) || !Files.isDirectory(dropInDir)) {
            throw new IOException("Unexpected systemd drop-in entry");
        }
        try (var entries = Files.list(dropInDir)) {
            for (Path entry : entries.toList()) {
                verifyDropInEntry(entry, seen);
            }
        }
    }

    private static void verifyDropInEntry(Path entry, Set<String> seen) throws IOException {
        String fileName = entry.getFileName().toString();
        if (fileName.endsWith(".conf") && seen.add(fileName) && !isMaskSymlink(entry)) {
            verifyDropIn(entry);
        }
    }

    static boolean isPackagedSystemdDir(Path dir) {
        String normalized = dir.normalize().toString();
        return "/usr/lib/systemd/system".equals(normalized)
                || "/lib/systemd/system".equals(normalized)
                || normalized.endsWith("/usr/lib/systemd/system");
    }

    static boolean isMaskSymlink(Path path) throws IOException {
        return Files.isSymbolicLink(path) && Path.of("/dev/null").equals(Files.readSymbolicLink(path));
    }

    private static void verifyDropIn(Path dropIn) throws IOException {
        String section = "";
        for (String full : logicalDropInLines(dropIn)) {
            if (full.startsWith("[") && full.endsWith("]")) {
                section = full;
            } else if ("[Service]".equals(section)) {
                verifyServiceDirective(full);
            }
        }
    }

    static List<String> logicalDropInLines(Path dropIn) throws IOException {
        List<String> logical = new ArrayList<>();
        StringBuilder continued = new StringBuilder();
        for (String rawLine : Files.readAllLines(dropIn, StandardCharsets.UTF_8)) {
            String line = stripCarriageReturn(rawLine);
            String trimmed = line.trim();
            if (isSystemdComment(trimmed) || (continued.isEmpty() && trimmed.isEmpty())) {
                continue;
            }
            if (line.endsWith("\\")) {
                continued.append(line, 0, line.length() - 1).append(' ');
            } else {
                logical.add((continued.toString() + line).trim());
                continued.setLength(0);
            }
        }
        if (!continued.isEmpty()) {
            throw new IOException("Unterminated systemd drop-in continuation");
        }
        return logical;
    }

    private static boolean isSystemdComment(String trimmed) {
        return trimmed.startsWith("#") || trimmed.startsWith(";");
    }

    private static void verifyServiceDirective(String directive) throws IOException {
        int eq = directive.indexOf('=');
        if (eq <= 0) {
            throw new IOException("Malformed systemd drop-in directive");
        }
        String key = directive.substring(0, eq).trim();
        if (UNVERIFIABLE_SERVICE_KEYS.contains(key)) {
            throw new IOException("Systemd drop-in alters service launch");
        }
        if ("Environment".equals(key)) {
            verifySystemdEnvironment(directive.substring(eq + 1).trim());
        }
    }

    private static void verifySystemdEnvironment(String value) throws IOException {
        if (value.isEmpty()) {
            throw new IOException("Systemd drop-in resets Environment");
        }
        int index = skipWhitespace(value, 0);
        while (index < value.length()) {
            ParsedToken parsed = readEnvironmentToken(value, index);
            verifyEnvironmentAssignment(parsed.token());
            index = skipWhitespace(value, parsed.nextIndex());
        }
    }

    static int skipWhitespace(String value, int start) {
        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    static ParsedToken readEnvironmentToken(String value, int start) throws IOException {
        char first = value.charAt(start);
        if (first == '"' || first == '\'') {
            return readQuotedEnvironmentToken(value, start + 1, first);
        }
        int end = start;
        while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
            end++;
        }
        return new ParsedToken(value.substring(start, end), end);
    }

    private static ParsedToken readQuotedEnvironmentToken(String value, int start, char quote) throws IOException {
        int index = start;
        while (index < value.length() && value.charAt(index) != quote) {
            if (value.charAt(index) == '\\') {
                throw new IOException("Escaped systemd Environment entry cannot be verified");
            }
            index++;
        }
        if (index >= value.length()) {
            throw new IOException("Unterminated systemd Environment quote");
        }
        return new ParsedToken(value.substring(start, index), index + 1);
    }

    private static void verifyEnvironmentAssignment(String token) throws IOException {
        int eq = token.indexOf('=');
        if (eq <= 0) {
            throw new IOException("Malformed systemd Environment assignment");
        }
        if (LAUNCH_ENV_VARS.contains(token.substring(0, eq))) {
            throw new IOException("Systemd drop-in overrides launch variable");
        }
    }

    static boolean isLaunchVariable(String key) {
        return LAUNCH_ENV_VARS.contains(key);
    }

    static boolean isIdentifier(String key) {
        return IDENTIFIER.matcher(key).matches();
    }

    private static void requireNoSetenv(Path catalinaDir) throws IOException {
        if (catalinaDir != null && !Files.notExists(catalinaDir.resolve("bin").resolve("setenv.sh"),
                LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Tomcat setenv.sh cannot be statically verified");
        }
    }

    private static void parseEnvFile(Path envFile, Map<String, String> vars) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(envFile, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            throw new IOException("Environment file is not a regular file");
        }
        for (String rawLine : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String trimmed = stripCarriageReturn(rawLine).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("export ") || trimmed.startsWith("export\t")) {
                trimmed = trimmed.substring(6).trim();
            }
            int eq = trimmed.indexOf('=');
            if (eq <= 0) {
                throw new IOException("Unverifiable line in carlos-emr.env");
            }
            String key = trimmed.substring(0, eq);
            if (!IDENTIFIER.matcher(key).matches()) {
                throw new IOException("Invalid variable name in carlos-emr.env");
            }
            vars.put(key, parseShellValue(trimmed.substring(eq + 1), vars));
        }
    }

    static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    static String parseShellValue(String raw, Map<String, String> vars) throws IOException {
        if (raw.isEmpty()) {
            return "";
        }
        char first = raw.charAt(0);
        if (first == '\'') {
            int close = raw.indexOf('\'', 1);
            if (close < 0 || !isEmptyOrComment(raw.substring(close + 1))) {
                throw new IOException("Malformed single-quoted value in carlos-emr.env");
            }
            return raw.substring(1, close);
        }
        if (first == '"') {
            return parseDoubleQuoted(raw, vars);
        }
        return parseUnquoted(raw, vars);
    }

    private static String parseUnquoted(String raw, Map<String, String> vars) throws IOException {
        // Unquoted value: disallow whitespace and shell metacharacters except simple $VAR/${VAR}.
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < raw.length()) {
            char ch = raw.charAt(index);
            if (Character.isWhitespace(ch) || ch == '#') {
                throw new IOException("Unverifiable unquoted value in carlos-emr.env");
            }
            if (ch == '$') {
                index = expandVariable(raw, index, vars, out) + 1;
            } else if (SHELL_METACHARS.matcher(String.valueOf(ch)).matches()) {
                throw new IOException("Unverifiable shell syntax in carlos-emr.env");
            } else {
                out.append(ch);
                index++;
            }
        }
        return out.toString();
    }

    private static String parseDoubleQuoted(String raw, Map<String, String> vars) throws IOException {
        StringBuilder out = new StringBuilder();
        int index = 1;
        while (index < raw.length()) {
            char ch = raw.charAt(index);
            if (ch == '"') {
                if (!isEmptyOrComment(raw.substring(index + 1))) {
                    throw new IOException("Unexpected trailing text after quoted value in carlos-emr.env");
                }
                return out.toString();
            }
            if (ch == '`') {
                throw new IOException("Command substitution in carlos-emr.env cannot be verified");
            }
            if (ch == '\\') {
                index = appendEscapedDoubleQuoted(raw, index, out);
            } else if (ch == '$') {
                index = expandVariable(raw, index, vars, out) + 1;
            } else {
                out.append(ch);
                index++;
            }
        }
        throw new IOException("Unterminated double-quoted value in carlos-emr.env");
    }

    private static int appendEscapedDoubleQuoted(String raw, int slashIndex, StringBuilder out) throws IOException {
        if (slashIndex + 1 >= raw.length()) {
            throw new IOException("Line continuation in carlos-emr.env cannot be verified");
        }
        char next = raw.charAt(slashIndex + 1);
        switch (next) {
            case '"', '\\', '$', '`' -> out.append(next);
            default -> out.append('\\').append(next);
        }
        return slashIndex + 2;
    }

    private static int expandVariable(String raw, int dollarIndex, Map<String, String> vars, StringBuilder out)
            throws IOException {
        int next = dollarIndex + 1;
        if (next >= raw.length() || raw.charAt(next) == '"' || Character.isWhitespace(raw.charAt(next))) {
            out.append('$');
            return dollarIndex;
        }
        if (raw.charAt(next) == '{') {
            int close = raw.indexOf('}', next + 1);
            if (close < 0) {
                throw new IOException("Unterminated variable expansion in carlos-emr.env");
            }
            String name = raw.substring(next + 1, close);
            if (!IDENTIFIER.matcher(name).matches()) {
                throw new IOException("Unsupported variable expansion in carlos-emr.env");
            }
            out.append(resolveKnownVariable(name, vars));
            return close;
        }
        if (raw.charAt(next) == '(') {
            throw new IOException("Command substitution in carlos-emr.env cannot be verified");
        }
        int end = next;
        while (end < raw.length() && (Character.isLetterOrDigit(raw.charAt(end)) || raw.charAt(end) == '_')) {
            end++;
        }
        String name = raw.substring(next, end);
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IOException("Unsupported variable reference in carlos-emr.env");
        }
        out.append(resolveKnownVariable(name, vars));
        return end - 1;
    }

    private static String resolveKnownVariable(String name, Map<String, String> vars) throws IOException {
        if (!vars.containsKey(name)) {
            throw new IOException("Unknown variable reference in carlos-emr.env cannot be verified");
        }
        return vars.get(name);
    }

    private static boolean isEmptyOrComment(String trailing) {
        String trimmed = trailing.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static LaunchSelection applyJvmOptions(
            String options, Path selectedProperties, Path selectedHome, boolean overridePropertiesAllowed)
            throws IOException {
        if (options == null || options.isBlank()) {
            return new LaunchSelection(selectedProperties, selectedHome);
        }
        if (SHELL_METACHARS.matcher(options).find()) {
            throw new IOException("JVM options contain shell syntax that cannot be verified");
        }
        LaunchSelection current = new LaunchSelection(selectedProperties, selectedHome);
        for (String token : options.trim().split("[ \\t]+")) {
            current = applyJvmToken(token, current, overridePropertiesAllowed);
        }
        return current;
    }

    private static LaunchSelection applyJvmToken(
            String token, LaunchSelection current, boolean overridePropertiesAllowed) throws IOException {
        if (isIndirectOptionsToken(token)) {
            throw new IOException("Indirect JVM options file cannot be verified");
        }
        if (OVERRIDE_FLAG.equals(token) || HOME_FLAG.equals(token)) {
            throw new IOException("Empty JVM property override cannot be verified");
        }
        if (token.startsWith(OVERRIDE_FLAG + "=")) {
            Path candidate = requireAbsoluteNoWhitespace(token.substring(OVERRIDE_FLAG.length() + 1));
            return overridePropertiesAllowed ? new LaunchSelection(candidate, current.home()) : current;
        }
        if (token.startsWith(HOME_FLAG + "=")) {
            Path homeDir = requireAbsoluteNoWhitespace(token.substring(HOME_FLAG.length() + 1));
            return new LaunchSelection(current.properties(), homeDir.resolve(PROPERTIES_FILE_NAME));
        }
        return current;
    }

    private static boolean isIndirectOptionsToken(String token) {
        return token.startsWith("@") || token.startsWith("-XX:Flags=") || token.startsWith("-XX:VMOptionsFile=");
    }

    private static Path requireAbsoluteNoWhitespace(String raw) throws IOException {
        if (raw == null || raw.isEmpty()) {
            throw new IOException("Empty properties path");
        }
        for (int i = 0; i < raw.length(); i++) {
            if (Character.isWhitespace(raw.charAt(i))) {
                throw new IOException("Properties path contains whitespace");
            }
        }
        Path path = Path.of(raw);
        if (!path.isAbsolute()) {
            throw new IOException("Relative properties path cannot be verified");
        }
        return path;
    }

    static boolean resolvesToManaged(Path managed, Path selected) throws IOException {
        if (managed.equals(selected)) {
            return true;
        }
        return Files.exists(managed) && Files.exists(selected)
                && managed.toRealPath().equals(selected.toRealPath());
    }

    static boolean sameFile(Path first, Path second) throws IOException {
        if (first.equals(second)) {
            return true;
        }
        return Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second);
    }

    private static int configuredTimeout(Properties defaults, Properties managed, Properties home) {
        // Same order as applicationContext.xml, CarlosProperties and the Startup listener.
        Properties values = new Properties();
        values.putAll(defaults);
        values.putAll(managed);
        values.putAll(home);
        var sources = new MutablePropertySources();
        sources.addLast(new PropertiesPropertySource("carlos", values));
        return new PropertySourcesPropertyResolver(sources).getRequiredProperty(KEY, Integer.class);
    }
}
