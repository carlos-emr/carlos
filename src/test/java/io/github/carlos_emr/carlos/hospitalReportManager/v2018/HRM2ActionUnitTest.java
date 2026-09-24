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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.hospitalReportManager.v2018;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Regression coverage for the retired provider confidentiality statement operations on
 * {@link HRM2Action}. The {@code getConfidentialityStatement}/{@code saveConfidentialityStatement}
 * method names must not fall through into report listing or another operation; they are rejected
 * with {@code HTTP 410 Gone} without touching any HRM DAO.
 */
@DisplayName("HRM2Action")
@Tag("unit")
@Tag("hrm")
class HRM2ActionUnitTest extends CarlosUnitTestBase {

    private static final Path STRUTS_DOCUMENT_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-document.xml");
    private static final String RETIRED_STATEMENT_ROUTE = "hospitalReportManager/Statement";

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private HRMDocumentDao hrmDocumentDao;
    private HRMCategoryDao hrmCategoryDao;
    private HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void registerActionDependencies() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        securityInfoManager = mock(SecurityInfoManager.class);
        hrmDocumentDao = mock(HRMDocumentDao.class);
        hrmCategoryDao = mock(HRMCategoryDao.class);
        hrmDocumentToDemographicDao = mock(HRMDocumentToDemographicDao.class);
        loggedInInfo = mock(LoggedInInfo.class);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(HRMDocumentDao.class, hrmDocumentDao);
        registerMock(HRMCategoryDao.class, hrmCategoryDao);
        registerMock(HRMDocumentToDemographicDao.class, hrmDocumentToDemographicDao);

        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
    }

    @AfterEach
    void closeStaticMocks() {
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (servletActionContextMock != null) {
            servletActionContextMock.close();
        }
    }

    @Test
    @DisplayName("should reject with 410 Gone when method is getConfidentialityStatement")
    void shouldRejectWith410Gone_whenMethodIsGetConfidentialityStatement() throws Exception {
        request.setParameter("method", "getConfidentialityStatement");

        String result = new HRM2Action().execute();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(410);
        verifyNoInteractions(hrmDocumentDao, hrmCategoryDao, hrmDocumentToDemographicDao);
    }

    @Test
    @DisplayName("should reject with 410 Gone when method is saveConfidentialityStatement")
    void shouldRejectWith410Gone_whenMethodIsSaveConfidentialityStatement() throws Exception {
        request.setParameter("method", "saveConfidentialityStatement");
        request.setParameter("value", "attempted update");

        String result = new HRM2Action().execute();

        assertThat(result).isNull();
        assertThat(response.getStatus()).isEqualTo(410);
        verifyNoInteractions(hrmDocumentDao, hrmCategoryDao, hrmDocumentToDemographicDao);
    }

    @Test
    @DisplayName("should not register a route for the retired statement action")
    void shouldNotRegisterARoute_forTheRetiredStatementAction() throws Exception {
        Optional<Element> action = findAction(parse(STRUTS_DOCUMENT_XML), RETIRED_STATEMENT_ROUTE);

        assertThat(action)
                .as("%s must no longer be mapped in struts-document.xml", RETIRED_STATEMENT_ROUTE)
                .isEmpty();
    }

    private Optional<Element> findAction(Document document, String actionName) {
        NodeList actions = document.getElementsByTagName("action");
        for (int i = 0; i < actions.getLength(); i++) {
            if (actions.item(i) instanceof Element element && actionName.equals(element.getAttribute("name"))) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private Document parse(Path configPath) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = new FileInputStream(configPath.toFile())) {
            return db.parse(in);
        }
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws ParserConfigurationException {
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
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return db;
    }
}
