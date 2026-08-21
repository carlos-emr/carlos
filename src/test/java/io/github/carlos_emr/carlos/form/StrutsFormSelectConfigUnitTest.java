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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Pins the Select Forms admin route so the four panel mutations (Add, Delete, Move Up,
 * Move Down) keep returning rendered markup instead of an empty body.
 *
 * <p>The Select Forms panel is loaded over AJAX into {@code #dynamic-content}, so the POST
 * response to {@code form/select} must itself carry the re-rendered panel. Issue #3377 was
 * caused by declaring the success result as a plain servlet-dispatch forward to the
 * {@code /form/setupSelect} action path. That nests a second FORWARD dispatch inside the
 * first, and {@code CsrfGuardScriptInjectionFilter} — which is mapped for FORWARD dispatch —
 * then wraps the response twice. The inner forward closes the outer capture wrapper's writer,
 * so the outer replay is discarded and the browser receives HTTP 200 with zero bytes.
 *
 * <p>A Struts {@code chain} result runs {@code FrmSetupSelect2Action} inside the same
 * invocation and forwards exactly once — to {@code formselect.jsp} — which is the same
 * single-forward shape the working {@code GET /form/setupSelect} route already uses.
 *
 * @since 2026-08-07
 */
@DisplayName("struts-form.xml Select Forms route Tests")
@Tag("unit")
@Tag("form")
class StrutsFormSelectConfigUnitTest {

    private static final String BASEDIR_PROPERTY = "basedir";
    private static final Path STRUTS_FORM_XML =
            Path.of("src", "main", "webapp", "WEB-INF", "classes", "struts-form.xml");

    @Test
    @DisplayName("form/select success result should chain to form/setupSelect")
    void shouldChainToSetupSelect_forSelectFormsMutations()
            throws IOException, ParserConfigurationException, SAXException {
        Element successResult = findResult("form/select", "success");

        assertThat(successResult.getAttribute("type"))
                .as("form/select must chain so FrmSetupSelect2Action re-renders the panel in the "
                        + "same invocation; a servlet-dispatch forward to another action path "
                        + "nests a second FORWARD and returns a zero-byte body (issue #3377)")
                .isEqualTo("chain");
        assertThat(successResult.getTextContent().trim())
                .as("form/select must chain to the Select Forms setup action")
                .isEqualTo("form/setupSelect");
    }

    @Test
    @DisplayName("form/setupSelect should render the Select Forms JSP directly")
    void shouldRenderFormSelectJsp_forSetupSelectRoute()
            throws IOException, ParserConfigurationException, SAXException {
        Element continueResult = findResult("form/setupSelect", "continue");

        assertThat(continueResult.getAttribute("type"))
                .as("form/setupSelect should keep the default dispatcher result so the chain "
                        + "terminates in exactly one forward")
                .isEmpty();
        assertThat(continueResult.getTextContent().trim())
                .isEqualTo("/WEB-INF/jsp/form/formselect.jsp");
    }

    private Element findResult(String actionName, String resultName)
            throws IOException, ParserConfigurationException, SAXException {
        Element action = findAction(parse(resolveProjectPath(STRUTS_FORM_XML)), actionName)
                .orElseThrow(() -> new AssertionError(
                        "struts-form.xml must declare action " + actionName));
        NodeList results = action.getElementsByTagName("result");
        for (int i = 0; i < results.getLength(); i++) {
            if (results.item(i) instanceof Element result
                    && resultName.equals(result.getAttribute("name"))) {
                return result;
            }
        }
        throw new AssertionError("action " + actionName
                + " must declare a result named " + resultName);
    }

    private Optional<Element> findAction(Document doc, String actionName) {
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element element
                    && actionName.equals(element.getAttribute("name"))) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private Document parse(Path absolutePath)
            throws IOException, ParserConfigurationException, SAXException {
        assertThat(absolutePath)
                .as("Struts config file not found — run tests from project root or set "
                        + "-Dbasedir=<project-root>")
                .exists();
        String xml = Files.readString(absolutePath, StandardCharsets.UTF_8);
        return newHardenedDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static Path resolveProjectPath(Path relativePath) {
        return Path.of(System.getProperty(BASEDIR_PROPERTY, System.getProperty("user.dir")))
                .toAbsolutePath()
                .resolve(relativePath)
                .normalize();
    }

    private static DocumentBuilder newHardenedDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setAttributeIfSupported(dbf, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        setAttributeIfSupported(dbf, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        // Struts config files intentionally declare the project-standard Struts 6.5 DTD.
        // External DTD loading and entity expansion remain disabled above.
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return db;
    }

    private static void setAttributeIfSupported(DocumentBuilderFactory dbf, String name, String value) {
        try {
            dbf.setAttribute(name, value);
        } catch (IllegalArgumentException ignored) {
            // Parser does not support the property; FEATURE_SECURE_PROCESSING still applies.
        }
    }
}
