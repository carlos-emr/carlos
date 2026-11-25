/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.providers.gate;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Proves the removal of the unlinked {@code provider/ViewObarRiskEdit} and
 * {@code provider/ViewObarChecklistEdit} routes is complete (commit ae9da405e99a, #6318 / #3438; part of the
 * #6336 gap tests). They served stale copies of the antenatal editors with the pre-hardening scriptlet writer.
 * The Struts mappings, the two gate classes and the two {@code provider/obar*edit_99_12.jsp} views must stay
 * gone, while the maintained {@code decision/antenatal/obar*edit_99_12} routes stay mapped to JSPs that exist.
 */
@Tag("unit")
@Tag("provider")
@DisplayName("Retired provider/ViewObar* routes")
class RetiredObarProviderRoutesUnitTest {

    private static final Path STRUTS_DIR = Path.of("src/main/webapp/WEB-INF/classes");
    private static final Path JSP_DIR = Path.of("src/main/webapp/WEB-INF/jsp");
    private static final Path JAVA_DIR =
            Path.of("src/main/java/io/github/carlos_emr/carlos/providers/gate");

    /** Action name to its {@code success} result location, for every action in one Struts file. */
    private static Map<String, String> successResults(String file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
        Document document;
        try (InputStream in = Files.newInputStream(STRUTS_DIR.resolve(file))) {
            document = builder.parse(in);
        }
        Map<String, String> actions = new HashMap<>();
        NodeList nodes = document.getElementsByTagName("action");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element action = (Element) nodes.item(i);
            String success = "";
            NodeList results = action.getElementsByTagName("result");
            for (int j = 0; j < results.getLength(); j++) {
                Element result = (Element) results.item(j);
                String name = result.getAttribute("name");
                if (name.isEmpty() || "success".equals(name)) {
                    success = result.getTextContent().trim();
                }
            }
            actions.put(action.getAttribute("name"), success);
        }
        return actions;
    }

    @Test
    @DisplayName("should not map provider/ViewObarRiskEdit or provider/ViewObarChecklistEdit in any Struts file")
    void shouldNotMapRetiredRoutes_inAnyStrutsFile() throws Exception {
        try (var files = Files.list(STRUTS_DIR)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches("struts(?:-[a-z]+)?\\.xml")).toList()) {
                Map<String, String> actions = successResults(file.getFileName().toString());
                assertThat(actions).as(file.toString())
                        .doesNotContainKeys("provider/ViewObarRiskEdit", "provider/ViewObarChecklistEdit");
            }
        }
        assertThat(successResults("struts-provider.xml")).isNotEmpty();
    }

    @Test
    @DisplayName("should have removed the two gate classes and the two stale provider JSPs")
    void shouldHaveRemovedGateClassesAndJsps_forRetiredRoutes() {
        // Removed in ae9da405e99a (#6318 / #3438).
        assertThat(JAVA_DIR.resolve("ViewObarRiskEdit2Action.java")).doesNotExist();
        assertThat(JAVA_DIR.resolve("ViewObarChecklistEdit2Action.java")).doesNotExist();
        assertThat(JSP_DIR.resolve("provider/obarriskedit_99_12.jsp")).doesNotExist();
        assertThat(JSP_DIR.resolve("provider/obarchecklistedit_99_12.jsp")).doesNotExist();

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        for (String name : new String[] {"ViewObarRiskEdit2Action", "ViewObarChecklistEdit2Action"}) {
            assertThat(loader.getResource("io/github/carlos_emr/carlos/providers/gate/" + name + ".class"))
                    .as(name).isNull();
        }
    }

    @Test
    @DisplayName("should keep the maintained decision/antenatal obar editors mapped to existing JSPs")
    void shouldKeepAntenatalObarEditors_mappedToExistingJsps() throws Exception {
        Map<String, String> clinical = successResults("struts-clinical.xml");
        for (String editor : new String[] {"obarriskedit_99_12", "obarchecklistedit_99_12"}) {
            String route = "decision/antenatal/" + editor;
            assertThat(clinical).as(route).containsKey(route);
            String target = clinical.get(route);
            assertThat(target).as(route).isEqualTo("/WEB-INF/jsp/decision/antenatal/" + editor + ".jsp");
            assertThat(Path.of("src/main/webapp" + target)).as(route).isRegularFile();
        }
    }

    private static final Pattern MARKUP_COMMENT = Pattern.compile("<!--.*?-->|<%--.*?--%>", Pattern.DOTALL);
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("(?m)^\\s*//.*$");
    private static final Pattern RETIRED_REFERENCE = Pattern.compile(
            "ViewObarRiskEdit|ViewObarChecklistEdit|provider/obarriskedit_99_12|provider/obarchecklistedit_99_12");

    /** Source text with markup comments (and, for code files, Java/JS comments) removed. */
    private static String withoutComments(String name, String text) {
        String stripped = MARKUP_COMMENT.matcher(text).replaceAll("");
        if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".jsp") || name.endsWith(".jspf")
                || name.endsWith(".tag")) {
            stripped = BLOCK_COMMENT.matcher(stripped).replaceAll("");
            stripped = LINE_COMMENT.matcher(stripped).replaceAll("");
        }
        return stripped;
    }

    @Test
    @DisplayName("should leave no live reference to the retired routes, gate classes or JSPs")
    void shouldLeaveNoLiveReference_toRetiredRoutes() throws Exception {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        for (String root : new String[] {"src/main/java", "src/main/webapp"}) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(Path.of(root))) {
                files = walk.filter(Files::isRegularFile).filter(p -> {
                    String n = p.getFileName().toString();
                    return n.endsWith(".java") || n.endsWith(".jsp") || n.endsWith(".jspf") || n.endsWith(".tag")
                            || n.endsWith(".js") || n.endsWith(".html") || n.endsWith(".xml");
                }).toList();
            }
            for (Path file : files) {
                scanned++;
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (RETIRED_REFERENCE.matcher(withoutComments(file.getFileName().toString(), text)).find()) {
                    offenders.add(file.toString());
                }
            }
        }
        assertThat(scanned).isGreaterThan(1000);
        assertThat(offenders).as("sources still linking or mapping the retired provider/ViewObar* routes").isEmpty();
    }
}
