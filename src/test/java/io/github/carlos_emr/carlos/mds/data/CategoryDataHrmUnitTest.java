/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.mds.data;

import io.github.carlos_emr.carlos.commn.dao.SystemPreferencesDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for matching HRM category counts.
 *
 * @since 2026-09-16
 */
@Tag("unit")
class CategoryDataHrmUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @CsvSource(value = {"N,0", "A,1", "F,1", "'',any", "NULL,0"}, nullValues = "NULL")
    void shouldUseSameReviewFilterForMatchedAndUnmatchedCounts_whenStatusSelected(String status, String signedOff) throws Exception {
        EntityManagerFactory factory = mock(EntityManagerFactory.class);
        EntityManager manager = mock(EntityManager.class);
        Query query = mock(Query.class);
        Tuple count = mock(Tuple.class);
        registerMock(EntityManagerFactory.class, factory);
        registerMock(SystemPreferencesDao.class, mock(SystemPreferencesDao.class));
        when(factory.createEntityManager()).thenReturn(manager);
        when(manager.createNativeQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(count));
        when(count.get("count")).thenReturn(3L);
        when(count.get("demographic_no", Integer.class)).thenReturn(7);
        when(count.get("first_name", String.class)).thenReturn("Synthetic");
        when(count.get("last_name", String.class)).thenReturn("Patient");
        CategoryData data = new CategoryData("", "", "", false, true, "999998", status, "all", null, null);
        assertThat(data.getHRMDocumentCountForPatient()).isEqualTo(3);
        assertThat(data.getHRMDocumentCountForUnmatched()).isEqualTo(3);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(manager, times(2)).createNativeQuery(sql.capture(), eq(Tuple.class));
        for (String statement : sql.getAllValues()) {
            assertThat(statement).contains("COUNT(DISTINCT h.id)").doesNotContain("hp.viewed");
            if ("any".equals(signedOff)) assertThat(statement).doesNotContain("hp.signedOff");
            else assertThat(statement).contains("hp.signedOff = :hrmSignedOff");
        }
        verify(query, times(2)).setParameter("hrmProviderNo", "999998");
        if ("any".equals(signedOff)) verify(query, never()).setParameter(eq("hrmSignedOff"), any());
        else verify(query, times(2)).setParameter("hrmSignedOff", Integer.parseInt(signedOff));
    }
}
