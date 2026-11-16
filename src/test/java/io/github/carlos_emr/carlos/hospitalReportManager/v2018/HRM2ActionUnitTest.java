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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMCategory;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
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

    // ---- patient access (a report filed to a patient the provider is locked out of) ----------

    private static final int PATIENT = 4242;

    private HRMDocument report(int id, String patientName) {
        HRMDocument document = mock(HRMDocument.class);
        when(document.getId()).thenReturn(id);
        when(document.getFormattedName()).thenReturn(patientName);
        when(document.getHcn()).thenReturn("FAKE-HCN-" + id);
        when(document.getDob()).thenReturn("1980-01-02");
        return document;
    }

    private void fileReportTo(int reportId, Integer patient) {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(reportId);
        link.setDemographicNo(patient);
        when(hrmDocumentToDemographicDao.findByHrmDocumentId(Integer.toString(reportId))).thenReturn(List.of(link));
        when(hrmDocumentToDemographicDao.findByHrmDocumentId(reportId)).thenReturn(List.of(link));
    }

    /** The read-only evaluation a list uses, and the stateful one a change uses, both answer yes. */
    private void permitPatient() {
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, PATIENT)).thenReturn(true);
        for (String object : new String[] {"_demographic", "_hrm"}) {
            when(securityInfoManager.hasPrivilegeReadOnly(loggedInInfo, object, "r", Integer.toString(PATIENT))).thenReturn(true);
            when(securityInfoManager.hasPrivilege(loggedInInfo, object, "r", Integer.toString(PATIENT))).thenReturn(true);
        }
    }

    private String listReports(HRMDocument... documents) throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "r", null)).thenReturn(true);
        when(hrmDocumentDao.query(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), any(), any()))
                .thenReturn(List.of(documents));
        request.setParameter("start", "0");
        request.setParameter("length", "10");

        new HRM2Action().execute();

        return response.getContentAsString();
    }

    @Test
    @DisplayName("should list a report filed to a patient the provider may read")
    void shouldListReport_whenPatientIsReadable() throws Exception {
        fileReportTo(7, PATIENT);
        permitPatient();

        String json = listReports(report(7, "FAKE-Readable, Patient"));

        assertThat(json).contains("FAKE-Readable, Patient").contains("\"demographic_no\":\"" + PATIENT + "\"");
    }

    @Test
    @DisplayName("should leave a report filed to a locked patient out of the list, with its name and health number")
    void shouldLeaveReportOut_whenPatientIsLocked() throws Exception {
        fileReportTo(7, PATIENT);
        permitPatient();
        when(securityInfoManager.isAllowedAccessToPatientRecord(loggedInInfo, PATIENT)).thenReturn(false);
        when(hrmDocumentToDemographicDao.findByHrmDocumentId("8")).thenReturn(List.of());

        String json = listReports(report(7, "FAKE-Locked, Patient"), report(8, "FAKE-Unfiled, Person"));

        assertThat(json).doesNotContain("FAKE-Locked").doesNotContain("FAKE-HCN-7").doesNotContain("\"id\":\"7\"")
                .contains("FAKE-Unfiled, Person").contains("\"id\":\"8\"");
        // A list is filtered read-only: the stateful check, which marks the session, is not used.
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), eq(Integer.toString(PATIENT)));
    }

    @Test
    @DisplayName("should list a report filed to no patient on the module right alone")
    void shouldListReport_whenFiledToNoPatient() throws Exception {
        when(hrmDocumentToDemographicDao.findByHrmDocumentId("8")).thenReturn(List.of());

        String json = listReports(report(8, "FAKE-Unfiled, Person"));

        assertThat(json).contains("FAKE-Unfiled, Person");
        verify(securityInfoManager, never()).isAllowedAccessToPatientRecord(any(LoggedInInfo.class), anyInt());
    }

    @Test
    @DisplayName("should not change the category of a report filed to a locked patient")
    void shouldNotSaveCategory_whenPatientIsLocked() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "w", null)).thenReturn(true);
        when(hrmCategoryDao.find(3)).thenReturn(mock(HRMCategory.class));
        fileReportTo(7, PATIENT);
        request.setParameter("method", "saveCategory");
        request.setParameter("hrmDocumentId", "7");
        request.setParameter("categoryId", "3");

        new HRM2Action().execute();

        verify(hrmDocumentDao, never()).find(anyInt());
        verify(hrmDocumentDao, never()).merge(any(HRMDocument.class));
        assertThat(response.getContentAsString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("should change the category of a report filed to a patient the provider may read")
    void shouldSaveCategory_whenPatientIsReadable() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_hrm", "w", null)).thenReturn(true);
        when(hrmCategoryDao.find(3)).thenReturn(mock(HRMCategory.class));
        fileReportTo(7, PATIENT);
        permitPatient();
        HRMDocument document = mock(HRMDocument.class);
        when(hrmDocumentDao.find(7)).thenReturn(document);
        request.setParameter("method", "saveCategory");
        request.setParameter("hrmDocumentId", "7");
        request.setParameter("categoryId", "3");

        new HRM2Action().execute();

        verify(document).setHrmCategoryId(3);
        verify(hrmDocumentDao).merge(document);
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
