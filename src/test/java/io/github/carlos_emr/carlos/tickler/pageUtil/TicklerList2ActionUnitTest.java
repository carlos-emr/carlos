/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.tickler.dto.TicklerLinkDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("fast")
class TicklerList2ActionUnitTest {
    @Test
    void shouldIncludeFutureRecalls_whenFilteringPatientChart() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "123");
        CustomFilter filter = TicklerList2Action.buildFilterFromRequest(request);
        assertThat(filter.getDemographicNo()).isEqualTo("123");
        assertThat(filter.getEndDate()).isNull();
    }

    @Test
    void shouldRetainDueDateDefault_whenViewingGlobalWorklist() {
        CustomFilter filter = TicklerList2Action.buildFilterFromRequest(new MockHttpServletRequest());
        assertThat(filter.getEndDate()).isNotNull();
    }

    @Test
    void shouldRespectRequestedDateBounds_whenFilteringPatientChart() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("demographicNo", "123");
        request.setParameter("startDate", "2026-08-01");
        request.setParameter("endDate", "2026-09-01");
        CustomFilter filter = TicklerList2Action.buildFilterFromRequest(request);
        assertThat(filter.getStartDateWeb()).isEqualTo("2026-08-01");
        assertThat(filter.getEndDateWeb()).isEqualTo("2026-09-01");
    }

    @Test
    @DisplayName("should withhold the record id and mark a link restricted when the reader lacks the type's read right")
    @SuppressWarnings("unchecked")
    void shouldMarkLinkRestricted_whenTypeReadDenied() {
        TicklerListDTO dto = new TicklerListDTO();
        dto.setId(7);
        dto.setDemographicNo(1001);
        TicklerLinkDTO document = TicklerLinkDTO.fromTicklerDocs(new TicklerDocs(7, 11, TicklerDocs.DOCTYPE_DOC, "999998"));
        TicklerDocs storedLab = new TicklerDocs(7, 77, TicklerDocs.DOCTYPE_LAB, "999998");
        storedLab.setLabType("MDS");
        TicklerLinkDTO lab = TicklerLinkDTO.fromTicklerDocs(storedLab);
        dto.setLinks(List.of(document, lab));

        Map<String, Object> row = TicklerList2Action.buildTicklerRow(dto, 0, new SimpleDateFormat("yyyy-MM-dd"),
                Locale.CANADA, Map.of(), link -> !TicklerDocs.DOCTYPE_DOC.equals(link.getDocType()));

        List<Map<String, Object>> links = (List<Map<String, Object>>) row.get("links");
        assertThat(links).hasSize(2);
        assertThat(links.get(0)).containsEntry("tableName", "DOC").containsEntry("restricted", Boolean.TRUE)
                .doesNotContainKey("id").doesNotContainKey("tableId").doesNotContainKey("formName");
        assertThat(links.get(1)).containsEntry("tableName", "MDS").containsEntry("tableId", 77L)
                .containsKey("id").doesNotContainKey("restricted");
    }

    @Test
    @DisplayName("should treat every link as restricted when tickler read is denied for the patient")
    void shouldMarkLinkUnreadable_whenTicklerReadDeniedForPatient() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        // The endpoint proved the global _tickler right only; a patient-specific denial wins.
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(false);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(true);
        TicklerListDTO dto = new TicklerListDTO();
        dto.setId(7);
        dto.setDemographicNo(1001);
        TicklerLinkDTO document = TicklerLinkDTO.fromTicklerDocs(new TicklerDocs(7, 11, TicklerDocs.DOCTYPE_DOC, "999998"));
        TicklerLinkDTO untyped = new TicklerLinkDTO();
        untyped.setTableName("DOC");

        Map<String, Boolean> cache = new HashMap<>();
        assertThat(TicklerList2Action.isLinkReadable(securityInfoManager, loggedInInfo, dto, document, cache)).isFalse();
        assertThat(TicklerList2Action.isLinkReadable(securityInfoManager, loggedInInfo, dto, untyped, cache)).isFalse();
        verify(securityInfoManager, never()).hasPrivilege(any(LoggedInInfo.class), eq("_edoc"), anyString(), anyString());
    }

    @Test
    @DisplayName("should gate a link on tickler read and then on its type, once per patient")
    void shouldMarkLinkReadable_whenTicklerAndTypeReadable() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001")).thenReturn(true);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_lab", SecurityInfoManager.READ, "1001")).thenReturn(false);
        TicklerListDTO dto = new TicklerListDTO();
        dto.setId(7);
        dto.setDemographicNo(1001);
        TicklerLinkDTO document = TicklerLinkDTO.fromTicklerDocs(new TicklerDocs(7, 11, TicklerDocs.DOCTYPE_DOC, "999998"));
        TicklerLinkDTO lab = TicklerLinkDTO.fromTicklerDocs(new TicklerDocs(7, 77, TicklerDocs.DOCTYPE_LAB, "999998"));

        Map<String, Boolean> cache = new HashMap<>();
        assertThat(TicklerList2Action.isLinkReadable(securityInfoManager, loggedInInfo, dto, document, cache)).isTrue();
        assertThat(TicklerList2Action.isLinkReadable(securityInfoManager, loggedInInfo, dto, document, cache)).isTrue();
        assertThat(TicklerList2Action.isLinkReadable(securityInfoManager, loggedInInfo, dto, lab, cache)).isFalse();
        verify(securityInfoManager, org.mockito.Mockito.times(1)).hasPrivilege(loggedInInfo, "_tickler", SecurityInfoManager.READ, "1001");
        verify(securityInfoManager, org.mockito.Mockito.times(1)).hasPrivilege(loggedInInfo, "_edoc", SecurityInfoManager.READ, "1001");
    }

    @Test
    @DisplayName("should drop a link whose item has moved to another patient, and check each item once per page")
    void shouldDropLink_whenItemNoLongerThePatients() {
        io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService service =
                mock(io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        TicklerListDTO dto = new TicklerListDTO();
        dto.setId(7);
        dto.setDemographicNo(1001);
        TicklerLinkDTO moved = TicklerLinkDTO.fromTicklerDocs(new TicklerDocs(7, 11, TicklerDocs.DOCTYPE_DOC, "999998"));
        TicklerDocs storedLab = new TicklerDocs(7, 77, TicklerDocs.DOCTYPE_LAB, "999998");
        storedLab.setLabType("MDS");
        TicklerLinkDTO lab = TicklerLinkDTO.fromTicklerDocs(storedLab);
        dto.setLinks(List.of(moved, lab));
        when(service.belongsToPatient(loggedInInfo, io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.DOC, 11, null, 1001)).thenReturn(false);
        when(service.belongsToPatient(loggedInInfo, io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.LAB, 77, "MDS", 1001)).thenReturn(true);

        Map<String, Boolean> cache = new HashMap<>();
        List<TicklerLinkDTO> owned = TicklerList2Action.ownedLinks(service, loggedInInfo, dto, cache);
        TicklerList2Action.ownedLinks(service, loggedInInfo, dto, cache);

        assertThat(owned).containsExactly(lab);
        verify(service, org.mockito.Mockito.times(1)).belongsToPatient(loggedInInfo,
                io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType.DOC, 11, null, 1001);
    }
}
