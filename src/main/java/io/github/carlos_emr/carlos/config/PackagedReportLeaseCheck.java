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
    private static final String ENV_FILE_PROPERTY = "carlos.reportLeaseCheck.envFile";
    private static final String CATALINA_BASE_PROPERTY = "carlos.reportLeaseCheck.catalinaBase";
    private static final String CATALINA_HOME_PROPERTY = "carlos.reportLeaseCheck.catalinaHome";
    private static final String SYSTEMD_DIRS_PROPERTY = "carlos.reportLeaseCheck.systemdDirs";

    private static final String DEFAULT_ENV_FILE = "/etc/carlos-emr/carlos-emr.env";
    private static final String DEFAULT_CATALINA_BASE = "/var/lib/carlos-emr/catalina";
    private static final String DEFAULT_CATALINA_HOME = "/usr/share/tomcat11";
    private static final String DEFAULT_SYSTEMD_DIRS =
            "/etc/systemd/system:/run/systemd/system:/usr/lib/systemd/system";
    private static final String SERVICE_UNIT = "carlos-emr.service";
    private static final String OVERRIDE_FLAG = "-Dcarlos_override_properties";
    private static final String HOME_FLAG = "-Duser.home";

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SHELL_METACHARS = Pattern.compile("['\"\\\\`$;|&<>()*?\\[~!]");
    private static final Set<String> UNVERIFIABLE_SERVICE_KEYS = Set.of(
            "ExecStart", "ExecStartPre", "User", "EnvironmentFile",
            "PassEnvironment", "UnsetEnvironment", "WorkingDirectory",
            "RootDirectory", "RootImage", "BindPaths", "BindReadOnlyPaths");
    private static final Set<String> LAUNCH_ENV_VARS = Set.of(
            "PROPERTIES", "CONF_DIR", "CARLOS_JAVA_OPTS", "JAVA_OPTS", "CATALINA_OPTS",
            "CATALINA_BASE", "CATALINA_HOME", "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS", "HOME");

    /**
     * Paths inspected to determine which properties file the packaged service launches with.
     *
     * @param envFile Path optional {@code carlos-emr.env} file read by the Tomcat wrapper
     * @param catalinaBase Path Tomcat {@code CATALINA_BASE} directory checked for {@code bin/setenv.sh}
     * @param catalinaHome Path Tomcat {@code CATALINA_HOME} directory checked for {@code bin/setenv.sh}
     * @param systemdDirs List of systemd unit directories checked for overrides and drop-ins
     */
    record LaunchConfig(Path envFile, Path catalinaBase, Path catalinaHome, List<Path> systemdDirs) {
        static LaunchConfig none() {
            return new LaunchConfig(null, null, null, List.of());
        }

        static LaunchConfig fromSystemProperties() {
            String dirsRaw = System.getProperty(SYSTEMD_DIRS_PROPERTY, DEFAULT_SYSTEMD_DIRS);
            List<Path> dirs = new ArrayList<>();
            for (String entry : dirsRaw.split(":")) {
                if (!entry.isEmpty()) {
                    dirs.add(Path.of(entry));
                }
            }
            String envRaw = System.getProperty(ENV_FILE_PROPERTY, DEFAULT_ENV_FILE);
            Path env = envRaw.isEmpty() ? null : Path.of(envRaw);
            return new LaunchConfig(
                    env,
                    Path.of(System.getProperty(CATALINA_BASE_PROPERTY, DEFAULT_CATALINA_BASE)),
                    Path.of(System.getProperty(CATALINA_HOME_PROPERTY, DEFAULT_CATALINA_HOME)),
                    dirs);
        }
    }

    private record LaunchSelection(Path properties, Path home) { }

    private PackagedReportLeaseCheck() {
    }

    /**
     * Exits with:
     * <ul>
     *   <li>{@code 10} when the launcher selects the managed file and publishing the candidate restores 360;</li>
     *   <li>{@code 0} when the launcher selects the managed file and its effective timeout is not 15;</li>
     *   <li>{@code 15} when the launcher selects the managed file and its effective timeout remains 15;</li>
     *   <li>{@code 20} when the launcher selects a separate file whose effective timeout is not 15;</li>
     *   <li>{@code 25} when the launcher selects a separate file whose effective timeout is 15;</li>
     *   <li>{@code 1} when launch selection or configuration cannot be verified.</li>
     * </ul>
     * Property values must never reach package logs.
     *
     * @param args String[] installed WEB-INF/classes directory, managed properties file, and optional candidate file
     */
    public static void main(String[] args) {
        System.exit(status(args, Path.of(System.getProperty("user.home"), "carlos.properties"),
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
            Path classes = Path.of(args[0]);
            Path managed = Path.of(args[1]);
            LaunchSelection selection = resolveLaunch(managed, home, launch);
            boolean selectsManaged = sameFile(managed, selection.properties());
            Properties defaults = PackagedConfigurationPreflight.readFile(classes.resolve("jdbc-defaults.properties"));
            defaults.putAll(PackagedConfigurationPreflight.readFile(classes.resolve("carlos.properties")));
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
        } catch (IOException | RuntimeException failure) {
            // Spring diagnostics may contain the referenced property's value.
            return STATUS_UNVERIFIABLE;
        }
    }

    private static LaunchSelection resolveLaunch(Path managed, Path defaultHome, LaunchConfig launch)
            throws IOException {
        verifySystemd(launch.systemdDirs());
        Map<String, String> vars = new HashMap<>();
        Path confDir = launch.envFile() != null && launch.envFile().getParent() != null
                ? launch.envFile().getParent() : Path.of("/etc/carlos-emr");
        vars.put("CONF_DIR", confDir.toString());
        vars.put("PROPERTIES", managed.toString());
        if (launch.catalinaBase() != null) {
            vars.put("CATALINA_BASE", launch.catalinaBase().toString());
        }
        if (launch.catalinaHome() != null) {
            vars.put("CATALINA_HOME", launch.catalinaHome().toString());
        }
        if (launch.envFile() != null && !Files.notExists(launch.envFile(), LinkOption.NOFOLLOW_LINKS)) {
            parseEnvFile(launch.envFile(), vars);
        }
        requireNoSetenv(launch.catalinaBase());
        requireNoSetenv(launch.catalinaHome());
        if (vars.containsKey("CATALINA_BASE")) {
            requireNoSetenv(requireAbsoluteNoWhitespace(vars.get("CATALINA_BASE")));
        }
        if (vars.containsKey("CATALINA_HOME")) {
            requireNoSetenv(requireAbsoluteNoWhitespace(vars.get("CATALINA_HOME")));
        }

        Path envProperties = requireAbsoluteNoWhitespace(vars.get("PROPERTIES"));
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
        return new LaunchSelection(selectedProperties, selectedHome);
    }

    private static void verifySystemd(List<Path> systemdDirs) throws IOException {
        for (Path dir : systemdDirs) {
            if (Files.notExists(dir, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (!isPackagedSystemdDir(dir)) {
                Path unitOverride = dir.resolve(SERVICE_UNIT);
                if (!Files.notExists(unitOverride, LinkOption.NOFOLLOW_LINKS) && !isMaskSymlink(unitOverride)) {
                    throw new IOException("Custom systemd unit override cannot be verified");
                }
            }
            Path dropInDir = dir.resolve(SERVICE_UNIT + ".d");
            if (Files.notExists(dropInDir, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            if (Files.isSymbolicLink(dropInDir) || !Files.isDirectory(dropInDir)) {
                throw new IOException("Unexpected systemd drop-in entry");
            }
            try (var entries = Files.list(dropInDir)) {
                for (Path entry : entries.toList()) {
                    if (entry.getFileName().toString().endsWith(".conf")) {
                        verifyDropIn(entry);
                    }
                }
            }
        }
    }

    private static boolean isPackagedSystemdDir(Path dir) {
        String normalized = dir.normalize().toString();
        return "/usr/lib/systemd/system".equals(normalized) || "/lib/systemd/system".equals(normalized);
    }

    private static boolean isMaskSymlink(Path path) throws IOException {
        return Files.isSymbolicLink(path) && Path.of("/dev/null").equals(Files.readSymbolicLink(path));
    }

    private static void verifyDropIn(Path dropIn) throws IOException {
        List<String> lines = Files.readAllLines(dropIn, StandardCharsets.UTF_8);
        String section = "";
        StringBuilder continued = new StringBuilder();
        for (String rawLine : lines) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            String trimmed = line.trim();
            if (continued.isEmpty() && (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";"))) {
                continue;
            }
            if (line.endsWith("\\")) {
                continued.append(line, 0, line.length() - 1).append(' ');
                continue;
            }
            String full = (continued.toString() + line).trim();
            continued.setLength(0);
            if (full.startsWith("[") && full.endsWith("]")) {
                section = full;
                continue;
            }
            if (!"[Service]".equals(section)) {
                continue;
            }
            int eq = full.indexOf('=');
            if (eq <= 0) {
                throw new IOException("Malformed systemd drop-in directive");
            }
            String key = full.substring(0, eq).trim();
            String value = full.substring(eq + 1).trim();
            if (UNVERIFIABLE_SERVICE_KEYS.contains(key)) {
                throw new IOException("Systemd drop-in alters service launch");
            }
            if ("Environment".equals(key)) {
                verifySystemdEnvironment(value);
            }
        }
        if (!continued.isEmpty()) {
            throw new IOException("Unterminated systemd drop-in continuation");
        }
    }

    private static void verifySystemdEnvironment(String value) throws IOException {
        if (value.isEmpty()) {
            throw new IOException("Systemd drop-in resets Environment");
        }
        int i = 0;
        while (i < value.length()) {
            while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
                i++;
            }
            if (i >= value.length()) {
                break;
            }
            String token;
            if (value.charAt(i) == '"' || value.charAt(i) == '\'') {
                char quote = value.charAt(i++);
                int start = i;
                while (i < value.length() && value.charAt(i) != quote) {
                    if (value.charAt(i) == '\\') {
                        throw new IOException("Escaped systemd Environment entry cannot be verified");
                    }
                    i++;
                }
                if (i >= value.length()) {
                    throw new IOException("Unterminated systemd Environment quote");
                }
                token = value.substring(start, i++);
            } else {
                int start = i;
                while (i < value.length() && !Character.isWhitespace(value.charAt(i))) {
                    i++;
                }
                token = value.substring(start, i);
            }
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new IOException("Malformed systemd Environment assignment");
            }
            String varName = token.substring(0, eq);
            if (LAUNCH_ENV_VARS.contains(varName)) {
                throw new IOException("Systemd drop-in overrides launch variable");
            }
        }
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
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            String trimmed = line.trim();
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

    private static String parseShellValue(String raw, Map<String, String> vars) throws IOException {
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
        // Unquoted value: disallow whitespace and shell metacharacters except simple $VAR/${VAR}.
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || ch == '#') {
                throw new IOException("Unverifiable unquoted value in carlos-emr.env");
            }
            if (ch == '$') {
                i = expandVariable(raw, i, vars, out);
            } else if (SHELL_METACHARS.matcher(String.valueOf(ch)).matches()) {
                throw new IOException("Unverifiable shell syntax in carlos-emr.env");
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String parseDoubleQuoted(String raw, Map<String, String> vars) throws IOException {
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"') {
                if (!isEmptyOrComment(raw.substring(i + 1))) {
                    throw new IOException("Unexpected trailing text after quoted value in carlos-emr.env");
                }
                return out.toString();
            }
            if (ch == '`') {
                throw new IOException("Command substitution in carlos-emr.env cannot be verified");
            }
            if (ch == '\\') {
                if (i + 1 >= raw.length()) {
                    throw new IOException("Line continuation in carlos-emr.env cannot be verified");
                }
                char next = raw.charAt(++i);
                if (next == '"' || next == '\\' || next == '$' || next == '`') {
                    out.append(next);
                } else {
                    out.append('\\').append(next);
                }
            } else if (ch == '$') {
                i = expandVariable(raw, i, vars, out);
            } else {
                out.append(ch);
            }
        }
        throw new IOException("Unterminated double-quoted value in carlos-emr.env");
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
            out.append(vars.getOrDefault(name, ""));
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
        out.append(vars.getOrDefault(name, ""));
        return end - 1;
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
        Path properties = selectedProperties;
        Path home = selectedHome;
        for (String token : options.trim().split("[ \\t]+")) {
            if (token.startsWith("@") || token.startsWith("-XX:Flags=")) {
                throw new IOException("Indirect JVM options file cannot be verified");
            }
            if (OVERRIDE_FLAG.equals(token) || HOME_FLAG.equals(token)) {
                throw new IOException("Empty JVM property override cannot be verified");
            }
            if (token.startsWith(OVERRIDE_FLAG + "=")) {
                Path candidate = requireAbsoluteNoWhitespace(token.substring(OVERRIDE_FLAG.length() + 1));
                if (overridePropertiesAllowed) {
                    properties = candidate;
                }
            } else if (token.startsWith(HOME_FLAG + "=")) {
                Path homeDir = requireAbsoluteNoWhitespace(token.substring(HOME_FLAG.length() + 1));
                home = homeDir.resolve("carlos.properties");
            }
        }
        return new LaunchSelection(properties, home);
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

    private static boolean sameFile(Path first, Path second) throws IOException {
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
