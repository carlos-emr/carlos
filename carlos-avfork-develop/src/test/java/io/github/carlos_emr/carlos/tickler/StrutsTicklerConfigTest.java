/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.tickler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "8 gated tickler JSPs return 404 when hit directly" promise of
 * PR #1670. The only way a relocated JSP could become publicly reachable again
 * is if the Struts config gained a {@code <result>} that forwards a public
 * action to a {@code /tickler/*.jsp} path outside {@code /WEB-INF/jsp/}. This
 * test parses the Struts configs that reference tickler JSPs and asserts no
 * such mapping exists.
 *
 * <p>Mirrors {@code StrutsMessengerConfigTest} which guards the equivalent
 * invariant for the messenger JSP-gating PR (#1629).
 *
 * @since 2026-04-13
 */
@DisplayName("struts tickler config Tests")
@Tag("unit")
@Tag("fast")
@Tag("tickler")
class StrutsTicklerConfigTest {

    private static final String SCHEDULING_CONFIG =
            "src/main/webapp/WEB-INF/classes/struts-scheduling.xml";

    private static final String[] CONFIG_PATHS = {SCHEDULING_CONFIG};

    /**
     * All privilege-sensitive tickler JSPs moved by PR #1670. Every Struts
     * {@code <result>} that references any of these JSPs must sit under
     * {@code /WEB-INF/jsp/}.
     */
    private static final String[] GATED_JSPS = {
            "index.jsp",
            "ticklerAdd.jsp",
            "ticklerDemoMain.jsp",
            "ticklerEdit.jsp",
            "ticklerEditSuccess.jsp",
            "ticklerMain.jsp",
            "ticklerSuggestedText.jsp"
    };

    @Test
    @DisplayName("should not route any privilege-sensitive result to a JSP outside /WEB-INF/jsp/tickler/")
    void shouldForbidPublicTicklerJspResults() throws Exception {
        List<String> offenders = collectResultPaths().stream()
                .filter(path -> path.endsWith(".jsp"))
                .filter(path -> !path.startsWith("/WEB-INF/"))
                // Only flag results inside /tickler/ — other modules' shared
                // error/close pages at the webapp root are out of scope.
                .filter(path -> path.startsWith("/tickler/"))
                .toList();

        assertThat(offenders)
                .as("tickler Struts config must not expose any privilege-sensitive "
                        + "/tickler/*.jsp outside /WEB-INF/jsp/; a result that did "
                        + "would reintroduce the direct-JSP access PR #1670 closed")
                .isEmpty();
    }

    @Test
    @DisplayName("should keep every gated tickler JSP under /WEB-INF/jsp/")
    void shouldKeepTicklerJspsBehindWebInf() throws Exception {
        List<String> paths = collectResultPaths();
        for (String jsp : GATED_JSPS) {
            List<String> refs = paths.stream().filter(p -> p.endsWith("/" + jsp)).toList();
            assertThat(refs)
                    .as("every struts reference to %s must live under /WEB-INF/jsp/", jsp)
                    .isNotEmpty()
                    .allMatch(p -> p.startsWith("/WEB-INF/jsp/"));
        }
    }

    /**
     * Guards against accidental deletion of a gate action mapping: if any of
     * the 6 new {@code tickler/View*} actions vanished from the Struts config
     * the JSP it protects would serve a 404 even though the file is still on
     * disk — breaking every caller that now routes through the gate.
     */
    @Test
    @DisplayName("should declare every tickler view-gate action in struts-scheduling.xml")
    void shouldDeclareEveryTicklerGateAction() throws Exception {
        List<String> actionNames = collectActionNames(SCHEDULING_CONFIG);
        String[] required = {
                "tickler/ViewIndex",
                "tickler/ViewTicklerMain",
                "tickler/ViewTicklerDemoMain",
                "tickler/ViewTicklerEdit",
                "tickler/ViewTicklerSuggestedText",
                "tickler/ViewAddTickler"
        };
        for (String name : required) {
            assertThat(actionNames)
                    .as("struts-scheduling.xml must declare <action name=\"%s\">", name)
                    .contains(name);
        }
    }

    private List<String> collectActionNames(String configPath) throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        Document doc;
        try (InputStream in = new FileInputStream(configPath)) {
            doc = db.parse(in);
        }
        NodeList actions = doc.getElementsByTagName("action");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element e) {
                String name = e.getAttribute("name");
                if (name != null && !name.isEmpty()) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        // Defense-in-depth XML hardening — inputs are trusted local config
        // files, but pinning secure-processing + disabling external entities
        // keeps the test robust across JAXP implementations.
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }

    private List<String> collectResultPaths() throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();

        List<String> out = new ArrayList<>();
        for (String configPath : CONFIG_PATHS) {
            Document doc;
            try (InputStream in = new FileInputStream(configPath)) {
                doc = db.parse(in);
            }

            NodeList results = doc.getElementsByTagName("result");
            for (int i = 0; i < results.getLength(); i++) {
                Node n = results.item(i);
                String text = n.getTextContent();
                if (text != null) {
                    out.add(text.trim());
                }
                // Some results use a path attribute via <param name="location">.
                if (n instanceof Element e) {
                    NodeList params = e.getElementsByTagName("param");
                    for (int j = 0; j < params.getLength(); j++) {
                        Element p = (Element) params.item(j);
                        if ("location".equals(p.getAttribute("name"))) {
                            out.add(p.getTextContent().trim());
                        }
                    }
                }
            }
        }
        return out;
    }
}
