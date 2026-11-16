// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto;
import io.github.carlos_emr.carlos.consultation.dto.ConsultationListRow;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Tag("integration") @Tag("dao") @Transactional
class ConsultationListCorrectionsIntegrationTest extends CarlosTestBase {
    @Autowired private ConsultationRequestDao dao;

    private ConsultationRequest save(String team, String status, String site) {
        var request = new ConsultationRequest();
        request.setSendTo(team); request.setStatus(status); request.setSiteName(site);
        dao.persist(request);
        return request;
    }

    private ConsultationListFilterDto filter(String team, boolean completed, String direction, int offset, int limit) {
        return new ConsultationListFilterDto(team, completed, null, null, "10", direction, "0", offset, limit, null, null);
    }

    @Test
    void shouldKeepUnsetStatus_inBothCompletionModes() {
        String team = "null" + Long.toString(System.nanoTime(), 36);
        var unset = save(team, null, "A");
        var complete = save(team, "4", "B");
        assertThat(dao.getConsultationListRows(filter(team, false, "0", 0, 10)))
                .extracting(ConsultationListRow::id).containsExactly(unset.getId());
        assertThat(dao.getConsultationListRows(filter(team, true, "0", 0, 10)))
                .extracting(ConsultationListRow::id).containsExactly(unset.getId(), complete.getId());
    }

    @Test
    void shouldKeepSiteOrderAndStableTies_acrossPageBoundaries() {
        String team = "sort" + Long.toString(System.nanoTime(), 36);
        var z = save(team, "1", "Z"); var a = save(team, "1", "A"); var a2 = save(team, "1", "A");
        assertThat(dao.getConsultationListRows(filter(team, true, "0", 0, 2)))
                .extracting(ConsultationListRow::id).containsExactly(a.getId(), a2.getId());
        assertThat(dao.getConsultationListRows(filter(team, true, "0", 2, 2)))
                .extracting(ConsultationListRow::id).containsExactly(z.getId());
        assertThat(dao.getConsultationListRows(filter(team, true, "1", 0, 3)))
                .extracting(ConsultationListRow::id).containsExactly(z.getId(), a.getId(), a2.getId());
    }
}
