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
package io.github.carlos_emr.carlos.messenger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the "11 gated JSPs return 404 when hit directly" promise of the
 * messenger JSP-gating PR. The only way a relocated JSP could become publicly
 * reachable again is if the Struts config gained a {@code <result>} that
 * forwards a public action to a {@code /messenger/*.jsp} path outside
 * {@code /WEB-INF/jsp/}. This test parses the messenger Struts config and
 * asserts that no such mapping exists.
 *
 * @since 2026-04-13
 */
@DisplayName("struts-messenger.xml config Tests")
@Tag("unit")
@Tag("fast")
@Tag("messenger")
class StrutsMessengerConfigTest {

    private static final String CONFIG_PATH =
            "src/main/webapp/WEB-INF/classes/struts-messenger.xml";

    /**
     * Utility JSPs intentionally left public in PR #1629: pure presentational
     * helpers with no DB access, no forms, no privilege-sensitive UI. Gating
     * them would add ceremony with zero security delta.
     */
    private static final java.util.Set<String> ALLOWED_PUBLIC_UTILITY_JSPS = java.util.Set.of(
            "/messenger/attachmentFrameset.jsp",
            "/messenger/selfCloseAndRefreshOpener.jsp",
            "/messenger/Transfer/error.jsp"
    );

    @Test
    @DisplayName("should not route any privilege-sensitive result to a JSP outside /WEB-INF/jsp/messenger/")
    void shouldForbidPublicMessengerJspResults() throws Exception {
        List<String> offenders = collectResultPaths().stream()
                .filter(path -> path.endsWith(".jsp"))
                .filter(path -> !path.startsWith("/WEB-INF/"))
                // Only flag results inside /messenger/ — other modules' shared
                // error pages at the webapp root are out of scope.
                .filter(path -> path.startsWith("/messenger/"))
                .filter(path -> !ALLOWED_PUBLIC_UTILITY_JSPS.contains(path))
                .toList();

        assertThat(offenders)
                .as("struts-messenger.xml must not expose any privilege-sensitive "
                        + "/messenger/*.jsp outside /WEB-INF/jsp/; a result that did "
                        + "would reintroduce the direct-JSP access this PR closed")
                .isEmpty();
    }

    @Test
    @DisplayName("should keep every gated messenger JSP under /WEB-INF/jsp/")
    void shouldKeepMessengerJspsBehindWebInf() throws Exception {
        // Subset of the 11 JSPs the PR relocated — the results that reference
        // these files must all sit under /WEB-INF/jsp/messenger/.
        String[] gated = {
                "CreateMessage.jsp", "DisplayMessages.jsp", "ViewMessage.jsp",
                "SentMessage.jsp", "ViewAttachment.jsp", "generatePreviewPDF.jsp",
                "DisplayDemographicMessages.jsp"
        };

        List<String> paths = collectResultPaths();
        for (String jsp : gated) {
            List<String> refs = paths.stream().filter(p -> p.endsWith("/" + jsp)).toList();
            assertThat(refs)
                    .as("every struts-messenger.xml reference to %s must be under /WEB-INF/jsp/", jsp)
                    .allMatch(p -> p.startsWith("/WEB-INF/jsp/"));
        }
    }

    private List<String> collectResultPaths() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));

        Document doc;
        try (InputStream in = new FileInputStream(CONFIG_PATH)) {
            doc = db.parse(in);
        }

        NodeList results = doc.getElementsByTagName("result");
        List<String> out = new ArrayList<>(results.getLength());
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
        return out;
    }
}
