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
package io.github.carlos_emr.carlos.chartspace;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.FileInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Privilege, method and route contract for the {@link ViewChartSpace2Action} gate.
 *
 * <p>The mapping assertion also guards that the {@code encounter/chartspace} view stays
 * behind {@code /WEB-INF/} so it cannot be reached as a public JSP.</p>
 *
 * @since 2026-10-08
 */
@DisplayName("ViewChartSpace2Action gate tests")
@Tag("integration")
@Tag("chartspace")
class ViewChartSpace2ActionIntegrationTest extends CarlosWebTestBase {

    private static final String ENCOUNTER_CONFIG = "src/main/webapp/WEB-INF/classes/struts-encounter.xml";

    @BeforeEach
    void setUpGate() {
        // Deny-all default so each allow-test must grant the specific privilege it relies on.
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(false);
        mockRequest.setMethod("GET");
    }

    @Test
    @DisplayName("should return success when _eChart r is granted")
    void shouldAllow_whenEChartReadGranted() throws Exception {
        addRequestParameter("demographicNo", "2");
        allowPrivilege("_eChart", "r");

        assertThat(executeAction(new ViewChartSpace2Action())).isEqualTo(ActionSupport.SUCCESS);
        verifySecurityCheck("_eChart", "r");
    }

    @Test
    @DisplayName("should deny when _eChart r is missing")
    void shouldDeny_whenEChartReadMissing() {
        addRequestParameter("demographicNo", "2");

        assertThatThrownBy(() -> executeAction(new ViewChartSpace2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    @Test
    @DisplayName("should deny when only an unrelated privilege is granted")
    void shouldDeny_whenOnlyUnrelatedPrivilegeGranted() {
        addRequestParameter("demographicNo", "2");
        allowPrivilege("_allergy", "r");

        assertThatThrownBy(() -> executeAction(new ViewChartSpace2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    @Test
    @DisplayName("should throw SecurityException when session has no LoggedInInfo")
    void shouldDeny_whenSessionEmpty() {
        addRequestParameter("demographicNo", "2");
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", null);

        assertThatThrownBy(() -> executeAction(new ViewChartSpace2Action()))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_eChart");
    }

    @Test
    @DisplayName("should return 405 on POST")
    void shouldReturn405_onPost() throws Exception {
        addRequestParameter("demographicNo", "2");
        allowPrivilege("_eChart", "r");
        mockRequest.setMethod("POST");

        assertThat(executeAction(new ViewChartSpace2Action())).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }

    @Test
    @DisplayName("should map encounter/chartspace to the WEB-INF JSP")
    void shouldMapRoute_toWebInfJsp() throws Exception {
        Element action = findAction(parseStrutsConfig(ENCOUNTER_CONFIG), "encounter/chartspace");

        assertThat(action).as("encounter/chartspace action mapping").isNotNull();
        assertThat(action.getAttribute("class")).isEqualTo(ViewChartSpace2Action.SPRING_BEAN_NAME);
        NodeList results = action.getElementsByTagName("result");
        String success = null;
        for (int i = 0; i < results.getLength(); i++) {
            Element result = (Element) results.item(i);
            if ("success".equals(result.getAttribute("name"))) {
                success = result.getTextContent().trim();
            }
        }
        assertThat(success).isEqualTo("/WEB-INF/jsp/chartspace/chartSpace.jsp");
    }

    /**
     * Parses a Struts config without resolving the DTD or external entities.
     * Package-private so other chartspace config tests can reuse it.
     */
    static Document parseStrutsConfig(String configPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) -> new InputSource(new java.io.StringReader("")));
        try (InputStream in = new FileInputStream(configPath)) {
            return db.parse(in);
        }
    }

    /** Returns the {@code <action>} element with the given name, or null. */
    static Element findAction(Document doc, String name) {
        NodeList actions = doc.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            Element e = (Element) actions.item(i);
            if (name.equals(e.getAttribute("name"))) {
                return e;
            }
        }
        return null;
    }
}
