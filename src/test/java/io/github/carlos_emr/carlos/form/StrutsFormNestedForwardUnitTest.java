/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * A Struts result that dispatches (the default result type) to another <em>action</em> path nests a
 * second FORWARD inside the first. {@code CsrfGuardScriptInjectionFilter} is FORWARD-mapped and wraps
 * the response twice, and the client gets HTTP 200 with an empty body: the Select Forms panel failed
 * that way (#3377). Issue #3394 named the sibling results in {@code struts-form.xml}; this keeps the
 * module free of them. Use a {@code chain} or {@code redirect} result, or let the action redirect itself.
 *
 * <p>A location counts as an action when Struts would route it: it names a declared action exactly or
 * matches a wildcard one (such as {@code form/*}), and {@code struts.action.excludePattern} does not hand
 * it to a servlet first, as it does for {@code /form/createpdf}.
 *
 * @since 2026-10-22
 */
@Tag("unit")
@Tag("form")
@DisplayName("struts-form.xml has no result that dispatches to another action")
class StrutsFormNestedForwardUnitTest {

    private static final Path CONFIG_DIR = Paths.get("src", "main", "webapp", "WEB-INF", "classes");

    @Test
    @DisplayName("should send no form result to another Struts action through the dispatcher")
    void shouldNotDispatchToAnotherAction_forAnyFormResult() throws Exception {
        Set<String> exactActions = new HashSet<>();
        List<Pattern> wildcardActions = new ArrayList<>();
        try (Stream<Path> configs = Files.list(CONFIG_DIR)) {
            for (Path config : configs
                    .filter(p -> p.getFileName().toString().matches("struts(?:-[a-z]+)?\\.xml")).toList()) {
                NodeList actions = parse(config).getElementsByTagName("action");
                for (int i = 0; i < actions.getLength(); i++) {
                    String name = "/" + ((Element) actions.item(i)).getAttribute("name");
                    if (name.contains("*")) {
                        wildcardActions.add(wildcardPattern(name));
                    } else {
                        exactActions.add(name);
                    }
                }
            }
        }
        List<Pattern> excluded = excludePatterns();
        assertThat(isActionPath("/form/forwardname", exactActions, wildcardActions, excluded))
                .as("an exactly declared action is an action path").isTrue();
        assertThat(isActionPath("/form/formAnyView", exactActions, wildcardActions, excluded))
                .as("a path the form/* wildcard serves is an action path").isTrue();
        assertThat(isActionPath("/form/createpdf", exactActions, wildcardActions, excluded))
                .as("the exclude pattern hands /form/createpdf to FrmPDFServlet, not to form/*").isFalse();

        List<String> nested = new ArrayList<>();
        NodeList actions = parse(CONFIG_DIR.resolve("struts-form.xml")).getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            NodeList results = action.getElementsByTagName("result");
            for (int j = 0; j < results.getLength(); j++) {
                Element result = (Element) results.item(j);
                String type = result.getAttribute("type");
                if (!type.isEmpty() && !"dispatcher".equals(type)) {
                    continue;
                }
                String location = result.getTextContent().trim();
                String path = location.split("\\?", 2)[0];
                if (isActionPath(path, exactActions, wildcardActions, excluded)) {
                    nested.add(action.getAttribute("name") + " -> " + result.getAttribute("name") + ": " + location);
                }
            }
        }

        assertThat(nested)
                .as("results that dispatch to another action nest a FORWARD and can answer with an empty body")
                .isEmpty();
    }

    /** Whether Struts, not a servlet or the container, would serve a FORWARD to {@code path}. */
    private static boolean isActionPath(String path, Set<String> exactActions, List<Pattern> wildcardActions,
            List<Pattern> excluded) {
        if (excluded.stream().anyMatch(p -> p.matcher(path).matches())) {
            return false;
        }
        return exactActions.contains(path) || wildcardActions.stream().anyMatch(p -> p.matcher(path).matches());
    }

    /**
     * Struts' wildcard syntax: {@code *} matches within one path segment, {@code **} across segments,
     * and a backslash escapes the next character.
     */
    private static Pattern wildcardPattern(String actionName) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < actionName.length(); i++) {
            char c = actionName.charAt(i);
            if (c == '\\' && i + 1 < actionName.length()) {
                regex.append(Pattern.quote(String.valueOf(actionName.charAt(++i))));
            } else if (c == '*' && i + 1 < actionName.length() && actionName.charAt(i + 1) == '*') {
                regex.append(".*");
                i++;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    /** {@code struts.action.excludePattern} from struts.xml, split on commas as Struts splits it. */
    private static List<Pattern> excludePatterns() throws Exception {
        NodeList constants = parse(CONFIG_DIR.resolve("struts.xml")).getElementsByTagName("constant");
        for (int i = 0; i < constants.getLength(); i++) {
            Element constant = (Element) constants.item(i);
            if ("struts.action.excludePattern".equals(constant.getAttribute("name"))) {
                List<Pattern> patterns = new ArrayList<>();
                for (String regex : constant.getAttribute("value").split(",")) {
                    if (!regex.isBlank()) {
                        patterns.add(Pattern.compile(regex.trim()));
                    }
                }
                return patterns;
            }
        }
        throw new AssertionError("struts.xml declares no struts.action.excludePattern");
    }

    private static Document parse(Path config) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        // The modules declare the Struts DOCTYPE; it is allowed but never resolved.
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        try (InputStream in = Files.newInputStream(config)) {
            return db.parse(in);
        }
    }
}
