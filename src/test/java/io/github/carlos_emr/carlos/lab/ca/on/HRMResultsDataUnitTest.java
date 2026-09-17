/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for HRM result query filters.
 *
 * @since 2026-09-16
 */
@Tag("unit")
class HRMResultsDataUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource(value = {"N,0", "A,1", "F,1", "'',2", "NULL,0"}, nullValues = "NULL")
    void shouldFilterBySignOffIndependentlyOfViewedState_whenReviewStatusSelected(String status, int signedOff) {
        HRMDocumentToProviderDao providers = mock(HRMDocumentToProviderDao.class);
        registerMock(HRMDocumentToProviderDao.class, providers);
        registerMock(HRMDocumentDao.class, mock(HRMDocumentDao.class));
        registerMock(HRMDocumentToDemographicDao.class, mock(HRMDocumentToDemographicDao.class));
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        assertThat(new HRMResultsData().populateHRMdocumentsResultsData(
                mock(LoggedInInfo.class), "999998", status, null, null, false, 0, 100)).isEmpty();
        verify(providers).findByProviderNoLimit(eq("999998"), eq(java.util.List.of()), eq(false),
                isNull(), isNull(), eq(2), eq(signedOff), eq(false), eq(0), eq(100));
    }
}
