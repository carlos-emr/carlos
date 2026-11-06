/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn;

import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicSearchResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("demographic")
class DemographicSearchResultTransformerUnitTest extends CarlosUnitTestBase {
    private Object[] tuple() {
        return new Object[]{17, "FAKE-last", "FAKE-first", "FAKE-chart", 'F', "900001", "NR", "AC",
                "FAKE-phone", "1980", "01", "02", "FAKE-provider-last", "FAKE-provider-first", "FAKE-HIN", null};
    }

    @Test
    void shouldPreserveIsoBirthDateAndProjectedNames_whenTransformingNativeTuple() {
        try (var ignored = mockConstruction(DemographicMerged.class)) {
            var transformer = new DemographicSearchResultTransformer();
            var result = (DemographicSearchResult) transformer.transformTuple(tuple(), new String[0]);
            assertThat(result.getDemographicNo()).isEqualTo(17);
            assertThat(result.getLastName()).isEqualTo("FAKE-last");
            assertThat(result.getFirstName()).isEqualTo("FAKE-first");
            assertThat(new java.text.SimpleDateFormat("yyyy-MM-dd").format(result.getDob())).isEqualTo("1980-01-02");
            var bad = tuple(); bad[9] = "not-a-year";
            assertThat(((DemographicSearchResult) transformer.transformTuple(bad, new String[0])).getDob()).isNull();
        }
    }

    @Test
    void shouldUseHeadRecordWithoutProviderDereference_whenPatientWasMerged() {
        try (var ignored = mockConstruction(DemographicMerged.class, (mock, context) -> when(mock.getHead(17)).thenReturn(42))) {
            var dao = mock(DemographicDao.class);
            var head = new Demographic();
            head.setDemographicNo(42); head.setLastName("FAKE-head"); head.setFirstName("FAKE-first");
            head.setYearOfBirth("1980"); head.setMonthOfBirth("01"); head.setDateOfBirth("02");
            when(dao.getDemographicById(42)).thenReturn(head);
            var transformer = new DemographicSearchResultTransformer(); transformer.setDemographicDao(dao);
            var values = tuple(); values[15] = 42;
            var result = (DemographicSearchResult) transformer.transformTuple(values, new String[0]);
            assertThat(result.getDemographicNo()).isEqualTo(42);
            assertThat(result.getLastName()).isEqualTo("FAKE-head");
            verify(dao).getDemographicById(42);
        }
    }
}
