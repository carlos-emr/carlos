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
 * <p>{@code REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf} is generated from the form JSPs by
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
    private static final Path MAIN_CONF = resolveProjectPath(Path.of("debian", "assets", "modsecurity", "main.conf"));
    private static final Path DEBIAN_RULES = resolveProjectPath(Path.of("debian", "rules"));

    private static final int FIRST_RULE_ID = 1200;
    private static final int LAST_ALLOWED_RULE_ID = 1399;
    /** The six tag families that misread prose; attack-xss stays inspected on every cell. */
    private static final String[] CONTENT_ATTACK_TAGS = {
            "attack-sqli", "attack-rce", "attack-injection-php",
            "attack-protocol", "attack-lfi", "attack-rfi"};

    // Mirrors of the generator's patterns and its tag scanner. Keep them identical.
    private static final Pattern TAG_START = Pattern.compile("<(textarea|input|form)\\b", Pattern.CASE_INSENSITIVE);
    // A field only inside a comment is not submittable, so it must not be exempted: a JSP
    // <%-- --%> is stripped before render and a control in an HTML <!-- --> is never sent.
    // JSP comments go first, so a stray "<!--" inside one is not read as an HTML opener.
    private static final Pattern JSP_COMMENT = Pattern.compile("<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final String ENCODE_TAG = "<carlos:encode";
    private static final Pattern ATTR = Pattern.compile(
            "([a-zA-Z_:-]++)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", Pattern.DOTALL);
    private static final Pattern DYNAMIC = Pattern.compile("<%|\\$\\{");
    private static final Pattern LITERAL_TARGET = Pattern.compile("^[A-Za-z0-9_.\\-]+$");
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
        assertThat(forms).as("main.conf includes the form exclusions").isGreaterThan(before);
        assertThat(crs).as("form exclusions load before the CRS rules").isGreaterThan(forms);
        assertThat(rules).contains("REQUEST-901-FORM-PROSE-EXCLUSIONS-BEFORE-CRS.conf");
    }

    // ---- derivation (mirror of the generator) -----------------------------------------------

    private static Map<String, PageInfo> analyseAll() throws IOException {
        Map<String, PageInfo> infos = new TreeMap<>();
        // Walk the subdirectories (pharmaForms/) as the generator does, keyed by the path
        // under the form directory, so a nested page cannot hide from the mirror.
        try (Stream<Path> files = Files.walk(FORM_DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".jsp")).sorted().toList()) {
                String key = FORM_DIR.relativize(file).toString().replace('\\', '/');
                infos.put(key, analyse(file, key));
            }
        }
        return infos;
    }

    private static PageInfo analyse(Path file, String pageKey) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
        text = HTML_COMMENT.matcher(JSP_COMMENT.matcher(text).replaceAll(" ")).replaceAll(" ");
        PageInfo info = new PageInfo();
        Matcher inc = INCLUDE.matcher(text);
        while (inc.find()) {
            info.includes.add(includeKey(pageKey, inc.group(inc.group(1) != null ? 1 : 2)));
        }
        for (String[] tag : findTags(text)) {
            String kind = tag[0];
            Map<String, String> attrs = attrs(tag[1]);
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
                continue;
            }
            if (!info.names.contains(name)) {
                info.names.add(name);
            }
        }
        return info;
    }

    /**
     * (kind, attribute text) of every textarea/input/form start tag, in document order. A
     * scriptlet or an encoder tag inside the attribute list is stepped over whole, so the '>'
     * that ends the tag is the first one outside them; any other '<' is an ordinary character.
     * A tag with no closing '>' is skipped and the scan resumes after its name. A hand-written
     * scan, like the generator's: the alternation a single regex needs backtracks super-linearly.
     */
    private static List<String[]> findTags(String text) {
        List<String[]> found = new ArrayList<>();
        int n = text.length();
        int i = 0;
        Matcher m = TAG_START.matcher(text);
        while (m.find(i)) {
            int j = m.end();
            int end = -1;
            while (j < n) {
                char c = text.charAt(j);
                if (c == '>') {
                    end = j;
                    break;
                }
                if (text.startsWith("<%", j)) {
                    int k = text.indexOf("%>", j + 2);
                    if (k < 0) {
                        break;
                    }
                    j = k + 2;
                } else {
                    int afterEncoder = text.regionMatches(true, j, ENCODE_TAG, 0, ENCODE_TAG.length())
                            ? skipEncoderTag(text, j) : -1;
                    j = afterEncoder > 0 ? afterEncoder : j + 1;
                }
            }
            if (end < 0) {
                i = m.end();
                continue;
            }
            found.add(new String[] {m.group(1).toLowerCase(), text.substring(m.end(), end)});
            i = end + 1;
        }
        return found;
    }

    /**
     * Index just past the "/>" of the encoder tag opening at start, or -1 when the tag does not
     * close that way: its body may hold a scriptlet, but no other '<' and no '>' before "/>".
     */
    private static int skipEncoderTag(String text, int start) {
        int j = start + ENCODE_TAG.length();
        int n = text.length();
        if (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
            return -1;
        }
        while (j < n) {
            if (text.startsWith("<%", j)) {
                int k = text.indexOf("%>", j + 2);
                if (k < 0) {
                    return -1;
                }
                j = k + 2;
            } else if (text.charAt(j) == '>') {
                return text.charAt(j - 1) == '/' ? j + 1 : -1;
            } else if (text.charAt(j) == '<') {
                return -1;
            } else {
                j++;
            }
        }
        return -1;
    }

    /**
     * The key an included page is analysed under: its path under the form directory, resolved
     * against the including page's own directory (an absolute /WEB-INF/jsp/form/... target from
     * that root), so a nested page and its includer agree.
     */
    private static String includeKey(String pageKey, String target) {
        String prefix = "/WEB-INF/jsp/form/";
        if (target.startsWith(prefix)) {
            return Path.of(target.substring(prefix.length())).normalize().toString().replace('\\', '/');
        }
        if (target.startsWith("/")) {
            return Path.of(target).getFileName().toString();
        }
        Path dir = Path.of(pageKey).getParent();
        Path resolved = dir == null ? Path.of(target) : dir.resolve(target);
        return resolved.normalize().toString().replace('\\', '/');
    }

    private static Map<String, String> attrs(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        Matcher m = ATTR.matcher(raw);
        while (m.find()) {
            out.put(m.group(1).toLowerCase(), m.group(m.group(3) != null ? 3 : 4));
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
    private static Map<List<String>, List<String>> group(Map<String, PageInfo> infos) {
        Map<String, String> includers = new LinkedHashMap<>();
        for (Map.Entry<String, PageInfo> e : infos.entrySet()) {
            for (String page : e.getValue().includes) {
                if (infos.containsKey(page) && infos.get(page).route == null) {
                    includers.put(page, e.getKey());
                }
            }
        }
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
