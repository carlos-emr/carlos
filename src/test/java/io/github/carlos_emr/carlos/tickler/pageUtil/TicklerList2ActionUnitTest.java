/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
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
}
