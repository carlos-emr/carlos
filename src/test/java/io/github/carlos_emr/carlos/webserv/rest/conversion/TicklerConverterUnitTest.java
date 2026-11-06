/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDocsDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.TicklerLinkTo1;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The REST {@code ticklerLinks} rows are gated on the patient-scoped read right of each
 * attachment's type, the same gate the picker and the list JSON apply: a caller who holds
 * {@code _tickler} but not, say, {@code _edoc} must not learn document identifiers.
 *
 * @since 2026-09-26
 */
@Tag("unit")
@Tag("fast")
@Tag("tickler")
@Tag("security")
@DisplayName("TicklerConverter attachment link authorization")
class TicklerConverterUnitTest extends CarlosUnitTestBase {

    private TicklerDocsDao ticklerDocsDao;
    private SecurityInfoManager securityInfoManager;
    private TicklerAttachmentService attachmentService;
    private LoggedInInfo loggedInInfo;
    private Tickler tickler;

    @BeforeEach
    void registerCollaborators() {
        ProviderDao providerDao = mock(ProviderDao.class);
        DemographicDao demographicDao = mock(DemographicDao.class);
        ticklerDocsDao = mock(TicklerDocsDao.class);
        securityInfoManager = mock(SecurityInfoManager.class);
        registerMock(ProviderDao.class, providerDao);
        registerMock(DemographicDao.class, demographicDao);
        registerMock(TicklerDocsDao.class, ticklerDocsDao);
        registerMock(ProgramDao.class, mock(ProgramDao.class));
        registerMock(SecurityInfoManager.class, securityInfoManager);
        attachmentService = mock(TicklerAttachmentService.class);
        registerMock(TicklerAttachmentService.class, attachmentService);
        loggedInInfo = mock(LoggedInInfo.class);
        // Items are the patient's unless a test moves one.
        org.mockito.Mockito.lenient().when(attachmentService.belongsToPatient(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any()))
                .thenReturn(true);

        Demographic demographic = new Demographic();
        demographic.setLastName("Patient");
        demographic.setFirstName("Test");
        when(demographicDao.getDemographicById(1001)).thenReturn(demographic);

        tickler = new Tickler();
        tickler.setId(42);
        tickler.setDemographicNo(1001);

        TicklerDocs document = new TicklerDocs(42, 11, TicklerDocs.DOCTYPE_DOC, "999998");
        document.setId(1);
        TicklerDocs lab = new TicklerDocs(42, 77, TicklerDocs.DOCTYPE_LAB, "999998");
        lab.setId(2);
        lab.setLabType("MDS");
        when(ticklerDocsDao.findByTicklerId(42)).thenReturn(List.of(document, lab));
    }

    @Test
    @DisplayName("should serialise every attachment the caller may read")
    void shouldIncludeLinks_whenTypesReadable() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), eq(SecurityInfoManager.READ), eq("1001"))).thenReturn(true);
        TicklerConverter converter = new TicklerConverter();
        converter.setIncludeLinks(true);

        List<TicklerLinkTo1> links = converter.getAsTransferObject(loggedInInfo, tickler).getTicklerLinks();

        assertThat(links).extracting(TicklerLinkTo1::getTableName).containsExactly("DOC", "MDS");
        assertThat(links).extracting(TicklerLinkTo1::getTableId).containsExactly(11L, 77L);
    }

    @Test
    @DisplayName("should leave out attachments of a type the caller may not read for the patient")
    void shouldOmitLinks_whenTypeReadDenied() throws Exception {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(true);
        TicklerConverter converter = new TicklerConverter();
        converter.setIncludeLinks(true);

        List<TicklerLinkTo1> links = converter.getAsTransferObject(loggedInInfo, tickler).getTicklerLinks();

        assertThat(links).singleElement().extracting(TicklerLinkTo1::getTableName).isEqualTo("MDS");
    }

    @Test
    @DisplayName("should leave out every attachment when tickler read is denied for the patient")
    void shouldOmitAllLinks_whenTicklerReadDeniedForPatient() throws Exception {
        // The endpoint proved the global right only; a patient-specific denial wins over it.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(true);
        TicklerConverter converter = new TicklerConverter();
        converter.setIncludeLinks(true);

        List<TicklerLinkTo1> links = converter.getAsTransferObject(loggedInInfo, tickler).getTicklerLinks();

        assertThat(links).isEmpty();
        org.mockito.Mockito.verify(ticklerDocsDao, org.mockito.Mockito.never()).findByTicklerId(any());
    }

    @Test
    @DisplayName("should leave out an attachment whose item has moved to another patient")
    void shouldOmitLink_whenItemNoLongerThePatients() throws Exception {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), anyString(), eq(SecurityInfoManager.READ), eq("1001"))).thenReturn(true);
        when(attachmentService.belongsToPatient(loggedInInfo, DocumentType.DOC, 11, null, 1001)).thenReturn(false);
        TicklerConverter converter = new TicklerConverter();
        converter.setIncludeLinks(true);

        List<TicklerLinkTo1> links = converter.getAsTransferObject(loggedInInfo, tickler).getTicklerLinks();

        assertThat(links).singleElement().extracting(TicklerLinkTo1::getTableName).isEqualTo("MDS");
    }

    @Test
    @DisplayName("should not consult attachment rights when links are not requested")
    void shouldSkipLinks_whenNotIncluded() throws Exception {
        TicklerConverter converter = new TicklerConverter();

        List<TicklerLinkTo1> links = converter.getAsTransferObject(loggedInInfo, tickler).getTicklerLinks();

        assertThat(links).isEmpty();
        org.mockito.Mockito.verify(securityInfoManager, org.mockito.Mockito.never())
                .hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), anyString());
    }
}
