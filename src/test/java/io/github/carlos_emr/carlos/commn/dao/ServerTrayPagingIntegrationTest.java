// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BillingTrayFee;
import io.github.carlos_emr.carlos.billings.ca.bc.data.SupServiceCodeAssocDAO;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The physical billing_trayfees table is supplied by the BC migration set. */
@Tag("integration") @Tag("dao") @Tag("bc")
class ServerTrayPagingIntegrationTest extends CarlosTestBase {
    @Autowired private SupServiceCodeAssocDAO trayFees;
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager entityManager;

    @Test
    void shouldBoundTrayAssociationsAndRetainEveryRow_whenTraversingServerPages() {
        Set<String> expected = new HashSet<>();
        for (int i = 0; i < 121; i++) {
            var row = new BillingTrayFee("0", "0");
            entityManager.persist(row); expected.add(row.getId().toString());
        }
        entityManager.flush();
        Set<String> actual = new HashSet<>();
        int page = 0;
        QueryPage<Map<String, String>> result;
        do {
            result = trayFees.getServiceCodeAssociationsPage(page++);
            assertThat(result.rows()).hasSizeLessThanOrEqualTo(50);
            for (var row : result.rows()) assertThat(actual.add(row.get("id"))).isTrue();
        } while (result.isNext());
        assertThat(actual).containsAll(expected);
        assertThat(actual).hasSize(Math.toIntExact(result.total()));
    }
}
