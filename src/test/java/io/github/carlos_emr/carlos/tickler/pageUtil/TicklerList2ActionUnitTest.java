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

import static org.assertj.core.api.Assertions.assertThat;

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
                .doesNotContainKey("tableId").doesNotContainKey("formName");
        assertThat(links.get(1)).containsEntry("tableName", "MDS").containsEntry("tableId", 77L)
                .doesNotContainKey("restricted");
    }
}
