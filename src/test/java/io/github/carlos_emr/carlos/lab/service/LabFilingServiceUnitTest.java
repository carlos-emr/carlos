/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.service;

import java.util.List;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class LabFilingServiceUnitTest extends CarlosUnitTestBase {
    private final ProviderLabRoutingDao routes = mock(ProviderLabRoutingDao.class);
    private final PatientLabRoutingDao patients = mock(PatientLabRoutingDao.class);
    private final PropertyDao properties = mock(PropertyDao.class);
    private final ProviderDao providers = mock(ProviderDao.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private LabFilingService service;

    @BeforeEach
    void fixture() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:labFilingUnit");
        service = spy(new LabFilingService(routes, patients, properties, providers, security, new DataSourceTransactionManager(ds)));
        // Deliberately nonnumeric version order: 90 is older than 30, 10 is newer.
        doReturn(List.of(90)).when(service).olderVersions(30);
        Provider actor = new Provider(); actor.setFirstName("Reviewer"); actor.setLastName("Test");
        when(info.getLoggedInProvider()).thenReturn(actor);
        when(info.getLoggedInProviderNo()).thenReturn("100001");
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(true);
        when(patients.findByLabNoAndLabType(30, "HL7")).thenReturn(List.of(new PatientLabRouting(30, "HL7", 321)));
        when(security.hasPrivilege(info, "_lab", "w", "321")).thenReturn(true);
        Provider target = new Provider(); target.setFirstName("Target"); target.setLastName("Test");
        when(providers.getProvider("100002")).thenReturn(target);
        when(properties.isActiveBooleanProperty(Property.PROPERTY_KEY.hl7_allow_others_file_for_you, "100002")).thenReturn(true);
        when(routes.findRoutingForUpdate(30, "HL7", "100002")).thenReturn(List.of(row("N", "")));
        when(routes.transitionNewRoutingRows(anyInt(), eq("HL7"), eq("100002"), eq('F'))).thenReturn(1);
    }

    private ProviderLabRoutingModel row(String status, String comment) {
        var row = new ProviderLabRoutingModel(); row.setStatus(status); row.setComment(comment); return row;
    }
    private int file(List<String> targets) {
        return service.fileForProviders(info, 30, "HL7", targets, "Extra <plain> text", true);
    }
    private void noWrites() {
        verify(routes, never()).transitionNewRoutingRows(anyInt(), anyString(), anyString(), anyChar());
        verify(routes, never()).persist(any()); verify(routes, never()).merge(any());
        logActionMock.verifyNoInteractions();
    }

    @Test
    void shouldRejectOptedOutTarget_whenClientNamesAnotherProvider() {
        when(properties.isActiveBooleanProperty(Property.PROPERTY_KEY.hl7_allow_others_file_for_you, "100002")).thenReturn(false);
        assertThatThrownBy(() -> file(List.of("100002"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldRejectWholeSelection_whenLaterTargetIsArbitrary() {
        assertThatThrownBy(() -> file(List.of("100002", "999999"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldRejectUnroutedTarget_whenOptedIn() {
        when(routes.findRoutingForUpdate(30, "HL7", "100002")).thenReturn(List.of());
        assertThatThrownBy(() -> file(List.of("100002"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldRejectSelfFiling_whenTargetMatchesSessionIdentity() {
        assertThatThrownBy(() -> file(List.of("100001"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldRejectFiling_whenWritePrivilegeMissing() {
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(false);
        assertThatThrownBy(() -> file(List.of("100002"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldRejectPatientSpecificDenial_whenGlobalWriteIsAllowed() {
        when(security.hasPrivilege(info, "_lab", "w", "321")).thenReturn(false);
        assertThatThrownBy(() -> file(List.of("100002"))).isInstanceOf(SecurityException.class); noWrites();
    }
    @Test
    void shouldCheckOlderPatientBeforeAnyWrite_whenVersionsHaveDifferentPatientRoutes() {
        when(patients.findByLabNoAndLabType(90, "HL7")).thenReturn(List.of(new PatientLabRouting(90, "HL7", 654)));
        when(security.hasPrivilege(info, "_lab", "w", "654")).thenReturn(false);
        assertThatThrownBy(() -> file(List.of("100002"))).isInstanceOf(SecurityException.class); noWrites();
        verify(security).hasPrivilege(info, "_lab", "w", "321");
    }
    @Test
    void shouldRetainGlobalAuthorizationForUnmatchedLabs_whenNoPatientIsLinked() {
        when(patients.findByLabNoAndLabType(30, "HL7")).thenReturn(List.of(new PatientLabRouting(30, "HL7", 0)));
        assertThat(file(List.of("100002"))).isEqualTo(1);
        verify(security, never()).hasPrivilege(info, "_lab", "w", "0");
    }
    @Test
    void shouldPreserveAcknowledgedAndFiledRows_whenOlderVersionAlreadyHandled() {
        var acknowledged = row("A", "Clinical acknowledgement"); var filed = row("F", "Earlier filing");
        when(routes.findRoutingForUpdate(90, "HL7", "100002")).thenReturn(List.of(acknowledged, filed));
        assertThat(file(List.of("100002"))).isEqualTo(1);
        verify(routes, never()).transitionNewRoutingRows(eq(90), anyString(), anyString(), anyChar());
        assertThat(acknowledged.getStatus()).isEqualTo("A"); assertThat(filed.getComment()).isEqualTo("Earlier filing");
    }
    @Test
    void shouldFileByVersionOrderAndKeepComments_whenFilingReviewedResult() {
        var older = row("N", "Existing clinical comment");
        when(routes.findRoutingForUpdate(90, "HL7", "100002")).thenReturn(List.of(older));
        assertThat(file(List.of("100002", "100002"))).isEqualTo(2);
        var order = inOrder(routes); order.verify(routes).lockRoutingReport(30); order.verify(routes).lockRoutingReport(90);
        verify(routes, never()).lockRoutingReport(10);
        assertThat(older.getStatus()).isEqualTo("F");
        assertThat(older.getComment()).startsWith("Existing clinical comment\nFiled by ")
                .contains("Test, Reviewer", "on behalf of Test, Target", "Extra <plain> text");
        logActionMock.verify(() -> LogAction.addLogStrict(info, "lab.fileOnBehalf", "90", null, null, "filedFor=100002"));
        logActionMock.verify(() -> LogAction.addLogStrict(info, "lab.fileOnBehalf", "30", null, null, "filedFor=100002"));
        verify(routes, never()).persist(any());
    }
    @Test
    void shouldLeaveAbsentOlderRouteAbsent_whenOnlyReviewedResultIsRouted() {
        assertThat(file(List.of("100002"))).isEqualTo(1);
        verify(routes, never()).transitionNewRoutingRows(eq(90), anyString(), anyString(), anyChar());
        verify(routes, never()).persist(any());
    }
    @Test
    void shouldRetainLiteralWhitespace_whenExistingCommentContainsOnlyWhitespace() {
        var existing = row("N", " \t ");
        when(routes.findRoutingForUpdate(30, "HL7", "100002")).thenReturn(List.of(existing));
        assertThat(file(List.of("100002"))).isEqualTo(1);
        assertThat(existing.getComment()).startsWith(" \t \nFiled by ");
    }
    @Test
    void shouldRefuseNonHl7Selection_whenDocumentIsPosted() {
        assertThatThrownBy(() -> service.fileForProviders(info, 30, "DOC", List.of("100002"), null, true))
                .isInstanceOf(IllegalArgumentException.class); noWrites();
    }
    @Test
    void shouldReturnZeroWithoutAudit_whenSelectionAlreadyFiled() {
        when(routes.findRoutingForUpdate(30, "HL7", "100002")).thenReturn(List.of(row("F", "Filed")));
        assertThat(file(List.of("100002"))).isZero(); noWrites();
    }
}
