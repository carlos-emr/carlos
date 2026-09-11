/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the generated per-form WAF exclusions for clinician prose on the encounter forms.
 *
 * <p>{@code REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf} (per-route ctl exclusions) and
 * {@code RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf} (anchored config-time patterns for the
 * cell names a ctl target cannot carry) are generated from the form JSPs by
 * {@code scripts/waf/generate-form-prose-exclusions.py}. A hand-maintained list of that size
 * silently sends a newly added form cell back to the front-door 403, so this test re-derives the
 * table from the JSPs with the same rules the generator applies and fails when the committed file
 * and the JSPs disagree. Regenerate, do not hand-edit.
 *
 * <p>The derivation is deliberately duplicated here rather than executed: the build must not
 * depend on a Python interpreter, and a second implementation of the same simple rules is what
 * catches a generator change that quietly widens the exemption.
 *
 * @since 2026-09-11
 */
@Tag("unit")
@Tag("security")
class FormProseWafExclusionRegressionTest {

    private static final Path FORM_DIR = resolveProjectPath(Path.of("src", "main", "webapp", "WEB-INF", "jsp", "form"));
    private static final Path GENERATED = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf"));
    private static final Path GENERATED_AFTER = resolveProjectPath(
            Path.of("debian", "assets", "modsecurity", "RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf"));
    private static final Path MAIN_CONF = resolveProjectPath(Path.of("debian", "assets", "modsecurity", "main.conf"));
    private static final Path DEBIAN_RULES = resolveProjectPath(Path.of("debian", "rules"));

    private static final int FIRST_RULE_ID = 1200;
    private static final int LAST_ALLOWED_RULE_ID = 1399;
    /** The six tag families that misread prose; attack-xss stays inspected on every cell. */
    private static final String[] CONTENT_ATTACK_TAGS = {
            "attack-sqli", "attack-rce", "attack-injection-php",
            "attack-protocol", "attack-lfi", "attack-rfi"};

    // Mirrors of the generator's patterns. Keep them identical.
    private static final Pattern TAG = Pattern.compile(
            "<(textarea|input|form)\\b((?:<%.*?%>|<carlos:encode\\b[^>]*/>|[^>])*)>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.DOTALL);
    private static final Pattern DYNAMIC = Pattern.compile("<%|\\$\\{");
    private static final Pattern LITERAL_TARGET = Pattern.compile("^[A-Za-z0-9_.\\-]+$");
    private static final Pattern PAREN_NAME = Pattern.compile("^[A-Za-z0-9_.\\-]+\\([A-Za-z0-9_.\\-]+\\)$");
    private static final Pattern FORM_CLASS_ASSIGN = Pattern.compile(
            "^\\s*String\\s+formClass\\s*=\\s*\"([^\"]+)\"", Pattern.MULTILINE);
    private static final Pattern INCLUDE = Pattern.compile(
            "<jsp:include\\s+page\\s*=\\s*[\"']([^\"']+)[\"']|<%@\\s*include\\s+file\\s*=\\s*[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTE = Pattern.compile("/form/[A-Za-z0-9_]+");
    private static final Pattern PROSE_INPUT_NAME = Pattern.compile(
            "comment|note|observ|remark|plan|reason|detail|finding|history|hx|other|desc|explain|"
                    + "concern|summary|text|assess|impression|recommend|complaint|diagnos|problem|allerg|"
                    + "medic|social|family|advice|counsel|consider", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_PROSE_INPUT_NAME = Pattern.compile(
            "date|time|dob|phone|fax|postal|hin\\b|_no$|no$|id$|num$|code|weight|height|\\bbp\\b|"
                    + "dose|units?$|qty|quantity|score|total|count", Pattern.CASE_INSENSITIVE);

    /** What one JSP contributes: its save route, form_class, prose cell names, and includes. */
    private static final class PageInfo {
        String route;
        String formClass;
        boolean hasForm;
        final List<String> names = new ArrayList<>();
        /** Anchored regex targets for the cells a ctl action cannot name (see anchoredPattern). */
        final List<String> patterns = new ArrayList<>();
        final List<String> includes = new ArrayList<>();
    }

    @Test
    @DisplayName("the generated form exclusions should match what the form JSPs contain")
    void shouldMatchFormJsps_forGeneratedExclusions() throws IOException {
        Map<String, PageInfo> infos = analyseAll();
        Map<List<String>, List<String>> groups = group(infos);

        String expected = render(groups);
        String actual = Files.readAllLines(GENERATED, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .collect(Collectors.joining("\n"));

        assertThat(groups).as("at least the well-known forms are covered")
                .containsKeys(List.of("/carlos/form/formname", "Rourke2020"),
                        List.of("/carlos/form/formname", "DischargeSummary"),
                        List.of("/carlos/form/BCAR2020", ""));
        assertThat(actual)
                .as("REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf is stale: run "
                        + "python3 scripts/waf/generate-form-prose-exclusions.py")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("the generated AFTER-CRS patterns should match the row-indexed and map-backed cells in the form JSPs")
    void shouldMatchFormJsps_forGeneratedAnchoredPatterns() throws IOException {
        Map<String, PageInfo> infos = analyseAll();
        List<String> patterns = anchoredPatterns(infos);

        String expected = renderAfter(patterns);
        String actual = Files.readAllLines(GENERATED_AFTER, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .collect(Collectors.joining("\n"));

        assertThat(patterns).as("the map-backed Vascular Tracker cells are present")
                .contains("^value[(]subjective[)]$", "^value[(]plan[)]$");
        assertThat(patterns).as("generic row-indexed names are NOT globally exempted (they collide with other routes)")
                .noneMatch(pattern -> pattern.contains("[0-9]+"));
        assertThat(actual)
                .as("RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf is stale: run "
                        + "python3 scripts/waf/generate-form-prose-exclusions.py")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("every generated AFTER-CRS target should be anchored at both ends and carry only the six prose families")
    void shouldStayAnchoredAndBounded_forGeneratedAnchoredPatterns() throws IOException {
        String file = Files.readString(GENERATED_AFTER, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(file).contains("GENERATED FILE").contains("generate-form-prose-exclusions.py");
        Pattern line = Pattern.compile("^SecRuleUpdateTargetByTag\\s+\"([^\"]+)\"\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);
        Matcher m = line.matcher(file);
        int count = 0;
        while (m.find()) {
            count++;
            assertThat(m.group(1)).as("only the six prose families: %s", m.group(0))
                    .isIn(List.of(CONTENT_ATTACK_TAGS));
            String target = m.group(2);
            assertThat(target).as("a negated, anchored ARGS regex and nothing broader: %s", m.group(0))
                    .startsWith("!ARGS:/^").endsWith("$/");
            String regex = target.substring("!ARGS:/".length(), target.length() - 1);
            // Only bracket classes, digits-plus and literal word characters: no alternation,
            // no dot, no unanchored quantifier that could widen a pattern to another name.
            assertThat(regex).matches("\\^(?:[A-Za-z0-9_-]|\\[[()]\\])+\\$");
        }
        assertThat(count).isGreaterThan(30);
        // Every directive in the file is one of those lines: no SecRule, no request-wide removal.
        Stream.of(file.split("\n"))
                .filter(l -> !l.isBlank() && !l.startsWith("#"))
                .forEach(l -> assertThat(l).startsWith("SecRuleUpdateTargetByTag "));
        assertThat(file).doesNotContain("attack-xss");
    }

    @Test
    @DisplayName("the generated file should say it is generated and stay within its id range")
    void shouldStayGeneratedAndBounded_forFormExclusions() throws IOException {
        String file = Files.readString(GENERATED, StandardCharsets.UTF_8);
        assertThat(file).contains("GENERATED FILE").contains("generate-form-prose-exclusions.py");
        Matcher ids = Pattern.compile("\"id:(\\d+),phase:[12],pass,nolog,chain\"").matcher(file);
        int count = 0;
        while (ids.find()) {
            int id = Integer.parseInt(ids.group(1));
            assertThat(id).isBetween(FIRST_RULE_ID, LAST_ALLOWED_RULE_ID);
            count++;
        }
        assertThat(count).isGreaterThan(30);
        // Per-argument only, like every other prose exclusion in this deployment.
        assertThat(file)
                .doesNotContain("ctl:ruleRemoveById=")
                .doesNotContain("ctl:ruleRemoveByTag=")
                .doesNotContain(";ARGS,")
                .doesNotContain(";ARGS\"")
                .doesNotContain("ARGS:/")
                // The XSS layer stays on every form cell; legacy form views render stored text raw.
                .doesNotContain("attack-xss");
    }

    @Test
    @DisplayName("the generated file should be installed and included by the package")
    void shouldBeWiredIntoThePackage_forFormExclusions() throws IOException {
        String mainConf = Files.readString(MAIN_CONF, StandardCharsets.UTF_8);
        String rules = Files.readString(DEBIAN_RULES, StandardCharsets.UTF_8);
        int before = mainConf.indexOf("REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf");
        int forms = mainConf.indexOf("Include /etc/carlos-emr/modsecurity/REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf");
        int crs = mainConf.indexOf("Include /usr/share/modsecurity-crs/rules/*.conf");
        int after = mainConf.indexOf("Include /etc/carlos-emr/modsecurity/RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf");
        assertThat(forms).as("main.conf includes the form exclusions").isGreaterThan(before);
        assertThat(crs).as("form exclusions load before the CRS rules").isGreaterThan(forms);
        assertThat(after).as("the anchored form patterns load after the CRS rules they update").isGreaterThan(crs);
        assertThat(rules).contains("REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf")
                .contains("RESPONSE-998-FORM-PROSE-EXCLUSIONS-AFTER-CRS.conf");
    }

    // ---- derivation (mirror of the generator) -----------------------------------------------

    private static Map<String, PageInfo> analyseAll() throws IOException {
        Map<String, PageInfo> infos = new TreeMap<>();
        try (Stream<Path> files = Files.list(FORM_DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jsp")).sorted().collect(Collectors.toList())) {
                infos.put(file.getFileName().toString(), analyse(file));
            }
        }
        return infos;
    }

    private static PageInfo analyse(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
        PageInfo info = new PageInfo();
        Matcher inc = INCLUDE.matcher(text);
        while (inc.find()) {
            String page = inc.group(1) != null ? inc.group(1) : inc.group(2);
            info.includes.add(Path.of(page).getFileName().toString());
        }
        Matcher tag = TAG.matcher(text);
        while (tag.find()) {
            String kind = tag.group(1).toLowerCase();
            Map<String, String> attrs = attrs(tag.group(2));
            if (kind.equals("form")) {
                info.hasForm = true;
                Matcher route = ROUTE.matcher(attrs.getOrDefault("action", ""));
                if (route.find() && info.route == null) {
                    info.route = "/carlos" + route.group();
                }
                continue;
            }
            String name = attrs.get("name");
            if (name == null) {
                continue;
            }
            if (kind.equals("input")) {
                String type = attrs.getOrDefault("type", "").toLowerCase();
                if (type.equals("hidden") && name.equals("form_class")) {
                    String value = attrs.getOrDefault("value", "");
                    if (DYNAMIC.matcher(value).find()) {
                        Matcher assign = FORM_CLASS_ASSIGN.matcher(text);
                        info.formClass = assign.find() ? assign.group(1) : null;
                    } else {
                        info.formClass = value;
                    }
                    continue;
                }
                if (!type.equals("text") || !PROSE_INPUT_NAME.matcher(name).find()
                        || NOT_PROSE_INPUT_NAME.matcher(name).find()) {
                    continue;
                }
            }
            if (DYNAMIC.matcher(name).find() || !LITERAL_TARGET.matcher(name).matches()) {
                String pattern = anchoredPattern(name);
                if (pattern != null && !info.patterns.contains(pattern)) {
                    info.patterns.add(pattern);
                }
                continue;
            }
            if (!info.names.contains(name)) {
                info.names.add(name);
            }
        }
        return info;
    }

    /** Mirror of the generator's regex_literal: bracket every non-word character. */
    private static String regexLiteral(String s) {
        StringBuilder out = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                out.append(ch);
            } else {
                out.append('[').append(ch).append(']');
            }
        }
        return out.toString();
    }

    /**
     * Mirror of the generator's anchored_pattern: only a parenthesised map-backed name
     * (value(subjective)) is expressed here, spelled literally with each metacharacter bracketed.
     * A row-indexed name (comment_&lt;n&gt;) is left as a residual because its prefix is generic
     * and a global pattern would collide with other routes (rx/prescribe.jsp posts comment_&lt;rand&gt;).
     */
    private static String anchoredPattern(String name) {
        if (PAREN_NAME.matcher(name).matches()) {
            return "^" + regexLiteral(name) + "$";
        }
        return null;
    }

    /** The anchored patterns of every page that resolves to a save route, sorted like the generator. */
    private static List<String> anchoredPatterns(Map<String, PageInfo> infos) {
        Map<String, String> includers = includers(infos);
        java.util.TreeSet<String> patterns = new java.util.TreeSet<>();
        for (Map.Entry<String, PageInfo> e : infos.entrySet()) {
            if (e.getValue().patterns.isEmpty()) {
                continue;
            }
            if (resolve(e.getKey(), infos, includers, 0)[0] != null) {
                patterns.addAll(e.getValue().patterns);
            }
        }
        return new ArrayList<>(patterns);
    }

    private static String renderAfter(List<String> patterns) {
        StringBuilder out = new StringBuilder();
        for (String pattern : patterns) {
            for (String tag : CONTENT_ATTACK_TAGS) {
                out.append("SecRuleUpdateTargetByTag \"").append(tag).append('"')
                        .append(" ".repeat(22 - tag.length()))
                        .append("\"!ARGS:/").append(pattern).append("/\"\n");
            }
        }
        return out.toString().stripTrailing();
    }

    private static Map<String, String> attrs(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(raw);
        while (m.find()) {
            out.put(m.group(1).toLowerCase(), m.group(3) != null ? m.group(3) : m.group(4));
        }
        return out;
    }

    private static String[] resolve(String file, Map<String, PageInfo> infos, Map<String, String> includers, int depth) {
        PageInfo info = infos.get(file);
        if (info.route != null) {
            String formClass = info.formClass;
            for (String page : info.includes) {
                if (formClass == null && infos.containsKey(page)) {
                    formClass = infos.get(page).formClass;
                }
            }
            return new String[] {info.route, formClass};
        }
        if (!info.hasForm && includers.containsKey(file) && depth < 5) {
            return resolve(includers.get(file), infos, includers, depth + 1);
        }
        return new String[] {null, null};
    }

    /** Groups keyed by [route, formClass-or-empty], sorted like the generator sorts them. */
    private static Map<String, String> includers(Map<String, PageInfo> infos) {
        Map<String, String> includers = new LinkedHashMap<>();
        for (Map.Entry<String, PageInfo> e : infos.entrySet()) {
            for (String page : e.getValue().includes) {
                if (infos.containsKey(page) && infos.get(page).route == null) {
                    includers.put(page, e.getKey());
                }
            }
        }
        return includers;
    }

    private static Map<List<String>, List<String>> group(Map<String, PageInfo> infos) {
        Map<String, String> includers = includers(infos);
        Map<List<String>, List<String>> groups = new TreeMap<>((a, b) -> {
            int byRoute = a.get(0).compareTo(b.get(0));
            return byRoute != 0 ? byRoute : a.get(1).compareTo(b.get(1));
        });
        for (Map.Entry<String, PageInfo> e : infos.entrySet()) {
            if (e.getValue().names.isEmpty()) {
                continue;
            }
            String[] key = resolve(e.getKey(), infos, includers, 0);
            if (key[0] == null) {
                continue;
            }
            List<String> names = groups.computeIfAbsent(
                    List.of(key[0], key[1] == null ? "" : key[1]), k -> new ArrayList<>());
            for (String n : e.getValue().names) {
                if (!names.contains(n)) {
                    names.add(n);
                }
            }
        }
        return groups;
    }

    private static String render(Map<List<String>, List<String>> groups) {
        StringBuilder out = new StringBuilder();
        int ruleId = FIRST_RULE_ID;
        for (Map.Entry<List<String>, List<String>> e : groups.entrySet()) {
            String route = e.getKey().get(0);
            String formClass = e.getKey().get(1);
            boolean keyed = !formClass.isEmpty();
            int phase = keyed ? 2 : 1;
            out.append("SecRule REQUEST_URI \"@rx ^").append(route).append("(?:[;?]|$)\" \\\n")
                    .append("    \"id:").append(ruleId).append(",phase:").append(phase).append(",pass,nolog,chain\"\n")
                    .append("    SecRule REQUEST_METHOD \"@streq POST\" \\\n");
            String indent;
            if (keyed) {
                out.append("        \"chain\"\n")
                        .append("        SecRule ARGS:form_class \"@streq ").append(formClass).append("\" \\\n")
                        .append("            \"t:none,\\\n");
                indent = "            ";
            } else {
                out.append("        \"t:none,\\\n");
                indent = "        ";
            }
            List<String> ctl = new ArrayList<>();
            for (String name : e.getValue()) {
                for (String tag : CONTENT_ATTACK_TAGS) {
                    ctl.add(indent + "ctl:ruleRemoveTargetByTag=" + tag + ";ARGS:" + name);
                }
            }
            for (int i = 0; i < ctl.size(); i++) {
                out.append(ctl.get(i)).append(i < ctl.size() - 1 ? ",\\\n" : "\"\n");
            }
            ruleId++;
        }
        return out.toString().stripTrailing();
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve " + relativePath);
    }
}
