/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.conversion.summary;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.prevention.PreventionDateService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.sql.Date;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PreventionsSummaryPrecisionUnitTest {
    @Test
    void shouldRefuseDateOnlySummary_whenHistoryHasPartialTiming() {
        PreventionManager manager = mock(PreventionManager.class);
        PartialDateDao dates = mock(PartialDateDao.class);
        LoggedInInfo actor = new LoggedInInfo();
        Prevention prevention = new Prevention();
        ReflectionTestUtils.setField(prevention, "id", 41);
        prevention.setPreventionDate(Date.valueOf("2019-01-01"));
        when(manager.getPreventionsByDemographicNo(actor, 7)).thenReturn(List.of(prevention));
        when(dates.getFormat(4, 41, 7)).thenReturn("YYYY");
        var summary = new PreventionsSummary(manager, new PreventionDateService(dates));
        WebApplicationException error = catchThrowableOfType(
                () -> summary.getSummary(actor, 7, "preventions"), WebApplicationException.class);
        assertThat(error).isNotNull();
        assertThat(error.getResponse().getStatus()).isEqualTo(409);
        assertThat(error.getResponse().getEntity().toString()).contains("/preventions/active/precision");
    }
}
