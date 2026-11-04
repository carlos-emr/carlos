/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.gate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.mds.data.MDSResultsData;
import io.github.carlos_emr.carlos.mds.gate.ViewSegmentDisplay2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Patient locks on the legacy Ontario CML and MDS report views. Global {@code _lab} read must not
 * open a report, or any version the JSP renders beside it, whose actual source-typed routing owner
 * is locked for the caller; caller-supplied patient, provider and version parameters are ignored.
 */
@Tag("unit")
@Tag("lab")
@Tag("security")
class LabSegmentDisplayPatientLockUnitTest extends CarlosUnitTestBase {

    /** Each gate with the routing source and the version lookup its JSP uses. */
    enum Gate {
        CML("CML"), MDS("MDS");

        final String labType;

        Gate(String labType) {
            this.labType = labType;
        }
    }

    private SecurityInfoManager security;
    private PatientLabRoutingDao routings;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedConstruction<MDSResultsData> versionLookups;
    private String versionChain;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        routings = mock(PatientLabRoutingDao.class);
        login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(eq(login), eq("_lab"), eq("r"), isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        when(routings.findByLabNoAndLabType(anyInt(), anyString())).thenReturn(List.of());
        registerMock(SecurityInfoManager.class, security);
        registerMock(PatientLabRoutingDao.class, routings);
        // MDSResultsData field initializers, in case a construction reaches them.
        registerMock(ConsultDocsDao.class, mock(ConsultDocsDao.class));
        registerMock(ConsultResponseDocDao.class, mock(ConsultResponseDocDao.class));
        registerMock(LabPatientPhysicianInfoDao.class, mock(LabPatientPhysicianInfoDao.class));
        registerMock(EFormDocsDao.class, mock(EFormDocsDao.class));

        request = new MockHttpServletRequest("GET", "/lab/segment");
        request.setParameter("segmentID", "77");
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);

        versionChain = "77";
        versionLookups = mockConstruction(MDSResultsData.class, (lookup, context) -> {
            when(lookup.getCmlVersionChain(anyString()))
                    .thenAnswer(call -> new MDSResultsData.CmlVersionChain(versionChain, true));
            when(lookup.getMatchingLabs(anyString())).thenAnswer(call -> versionChain);
        });
    }

    @AfterEach
    void tearDown() {
        versionLookups.close();
        sessions.close();
        servlet.close();
    }

    private String execute(Gate gate) throws Exception {
        return switch (gate) {
            case CML -> new ViewCMLDisplay2Action().execute();
            case MDS -> new ViewSegmentDisplay2Action().execute();
        };
    }

    private void owner(int labNo, String labType, Integer patient) {
        when(routings.findByLabNoAndLabType(labNo, labType))
                .thenReturn(List.of(new PatientLabRouting(labNo, labType, patient)));
    }

    private void locked(int patient) {
        when(security.isAllowedAccessToPatientRecord(login, patient)).thenReturn(false);
    }

    private void assertNothingRendered() {
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(response.getContentType()).isNull();
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should forward to the report when its actual owner is unlocked")
    void shouldForwardToReport_whenActualOwnerIsUnlocked(Gate gate) throws Exception {
        owner(77, gate.labType, 123);

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(routings).findByLabNoAndLabType(77, gate.labType);
        verify(security).isAllowedAccessToPatientRecord(login, 123);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should refuse before any version lookup or rendering when the actual owner is locked")
    void shouldRefuseBeforeVersionLookup_whenActualOwnerIsLocked(Gate gate) {
        owner(77, gate.labType, 123);
        locked(123);

        assertThatThrownBy(() -> execute(gate)).isInstanceOf(SecurityException.class);

        assertThat(versionLookups.constructed()).isEmpty();
        assertNothingRendered();
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should ignore forged patient and provider parameters when the actual owner is locked")
    void shouldIgnoreForgedPatientParameters_whenActualOwnerIsLocked(Gate gate) {
        owner(77, gate.labType, 123);
        locked(123);
        request.setParameter("demographicNo", "456");
        request.setParameter("demographicId", "456");
        request.setParameter("providerNo", "someone-else");

        assertThatThrownBy(() -> execute(gate)).isInstanceOf(SecurityException.class);

        verify(security, never()).isAllowedAccessToPatientRecord(login, 456);
        assertNothingRendered();
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should refuse the whole report when another rendered version belongs to a locked patient")
    void shouldRefuseWholeReport_whenAnotherVersionOwnerIsLocked(Gate gate) {
        owner(77, gate.labType, 456);
        owner(88, gate.labType, 123);
        locked(123);
        versionChain = "77,88";

        assertThatThrownBy(() -> execute(gate)).isInstanceOf(SecurityException.class);

        verify(security).isAllowedAccessToPatientRecord(login, 456);
        verify(security).isAllowedAccessToPatientRecord(login, 123);
        assertNothingRendered();
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should resolve versions from the selected report with the lookup its JSP uses")
    void shouldResolveVersionsFromSelectedReport_whenJspRendersTheChain(Gate gate) throws Exception {
        owner(77, gate.labType, 123);
        owner(88, gate.labType, 456);
        versionChain = "88,77,88";

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        assertThat(versionLookups.constructed()).hasSize(1);
        MDSResultsData lookup = versionLookups.constructed().get(0);
        if (gate == Gate.CML) {
            verify(lookup).getCmlVersionChain("77");
        } else {
            verify(lookup).getMatchingLabs("77");
        }
        verify(routings, times(1)).findByLabNoAndLabType(77, gate.labType);
        verify(routings, times(1)).findByLabNoAndLabType(88, gate.labType);
        verify(security).isAllowedAccessToPatientRecord(login, 456);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should ignore a client multiID because neither JSP renders it")
    void shouldIgnoreClientVersionList_whenServerChainIsAuthorized(Gate gate) throws Exception {
        owner(77, gate.labType, 123);
        owner(99, gate.labType, 999);
        locked(999);
        request.setParameter("multiID", "77,99");

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(routings, never()).findByLabNoAndLabType(99, gate.labType);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should keep an unassigned report behind the global _lab read privilege")
    void shouldKeepUnassignedReportBehindGlobalPrivilege_whenRoutedToNoPatient(Gate gate) throws Exception {
        when(routings.findByLabNoAndLabType(77, gate.labType)).thenReturn(List.of(
                new PatientLabRouting(77, gate.labType, 0), new PatientLabRouting(77, gate.labType, null)));

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should use the report's own source routing when another source reuses the lab number")
    void shouldUseSourceTypedRouting_whenAnotherSourceSharesTheLabNumber(Gate gate) throws Exception {
        owner(77, "HL7", 123);
        owner(77, gate.labType, 456);
        locked(123);

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(routings, never()).findByLabNoAndLabType(77, "HL7");
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should skip an empty legacy version chain and still check the selected report")
    void shouldCheckOnlySelectedReport_whenVersionChainIsEmpty(Gate gate) throws Exception {
        owner(77, gate.labType, 123);
        versionChain = "";

        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);

        verify(routings, times(1)).findByLabNoAndLabType(anyInt(), anyString());
        verify(security).isAllowedAccessToPatientRecord(login, 123);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should answer 400 when a resolved version is not a lab identity")
    void shouldAnswerBadRequest_whenResolvedVersionIsNotALabIdentity(Gate gate) throws Exception {
        owner(77, gate.labType, 123);
        versionChain = "77,not-a-lab";

        assertThat(execute(gate)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    static Stream<Arguments> invalidSegmentIds() {
        List<Arguments> cases = new ArrayList<>();
        for (Gate gate : Gate.values()) {
            for (String id : new String[] {null, "", "0", "-1", "1x", "2147483648", "999999999999999999999"}) {
                cases.add(Arguments.of(gate, id));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("invalidSegmentIds")
    @DisplayName("should answer 400 without routing or version lookups when segmentID is invalid")
    void shouldAnswerBadRequest_whenSegmentIdIsInvalid(Gate gate, String segmentId) throws Exception {
        request.removeParameter("segmentID");
        if (segmentId != null) {
            request.setParameter("segmentID", segmentId);
        }

        assertThat(execute(gate)).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(routings);
        assertThat(versionLookups.constructed()).isEmpty();
        verify(security, never()).isAllowedAccessToPatientRecord(any(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should deny before routing or identity parsing when global _lab read is missing")
    void shouldDenyBeforeRoutingLookup_whenGlobalLabReadIsMissing(Gate gate) {
        request.setParameter("segmentID", "bad");
        when(security.hasPrivilege(login, "_lab", "r", null)).thenReturn(false);

        assertThatThrownBy(() -> execute(gate))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_lab)");

        verifyNoInteractions(routings);
        assertThat(versionLookups.constructed()).isEmpty();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(Gate.class)
    @DisplayName("should fail closed when the routing lookup cannot resolve ownership")
    void shouldRefuse_whenOwnershipLookupIsUnresolved(Gate gate) {
        when(routings.findByLabNoAndLabType(77, gate.labType)).thenReturn(null);

        assertThatThrownBy(() -> execute(gate)).isInstanceOf(SecurityException.class);

        assertThat(versionLookups.constructed()).isEmpty();
        assertNothingRendered();
    }

    private static String jspSource(String relative) throws Exception {
        return Files.readString(Path.of(System.getProperty("user.dir"), "src/main/webapp/WEB-INF/jsp", relative));
    }

    @Test
    @DisplayName("should pin the JSP version sources the gates authorize")
    void shouldRenderOnlyServerResolvedVersions_forBothSegmentJsps() throws Exception {
        String cml = jspSource("lab/CA/ON/CMLDisplay.jsp");
        String mds = jspSource("oscarMDS/SegmentDisplay.jsp");

        // CMLLabTest.populateLab takes its chain from MDSResultsData.getCmlVersionChain(segmentID).
        assertThat(cml).contains("lab.populateLab(segmentID);").contains("lab.multiLabId.split(\",\")");
        assertThat(mds).contains("clrd.getMatchingLabs(request.getParameter(\"segmentID\"), \"MDS\")");
        assertThat(cml).doesNotContain("getParameter(\"multiID\")");
        assertThat(mds).doesNotContain("getParameter(\"multiID\")");
    }
}
