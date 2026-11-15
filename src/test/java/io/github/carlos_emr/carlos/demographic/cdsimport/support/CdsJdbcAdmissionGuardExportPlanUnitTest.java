/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Every statement the CDS export's patient table plans send must be admissible by the CDS
 * maintenance guard. A node that runs CDS imports has the guard enabled; an export statement the
 * guard cannot parse failed the whole export job on such a node (installed census, finding D1:
 * the appointment status plan's {@code binary right(...)}).
 */
@Tag("unit")
class CdsJdbcAdmissionGuardExportPlanUnitTest {

    @BeforeEach
    void readOnlyTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    /** The SQL text every plan's count and first page send, by plan. */
    private static Map<String, List<String>> statements() {
        var sent = new ArrayList<String>();
        var em = mock(EntityManager.class);
        when(em.createNativeQuery(anyString(), any(Class.class)))
                .thenAnswer(
                        call -> {
                            sent.add(call.getArgument(0));
                            return mock(Query.class, RETURNS_SELF);
                        });
        when(em.createNativeQuery(anyString()))
                .thenAnswer(
                        call -> {
                            sent.add(call.getArgument(0));
                            return mock(Query.class, RETURNS_SELF);
                        });
        var byPlan = new LinkedHashMap<String, List<String>>();
        for (var plan : CdsExportPatientTableReadSupport.Plan.values()) {
            sent.clear();
            // The mocked result is empty, so each call stops at its own result check after the
            // statement was built and sent; only the sent text matters here.
            try {
                CdsExportPatientTableReadSupport.count(em, plan, 27);
            } catch (RuntimeException ignored) {
                // expected: no rows from the mock
            }
            try {
                CdsExportPatientTableReadSupport.page(em, plan, 27, null, 10);
            } catch (RuntimeException ignored) {
                // expected: no rows from the mock
            }
            if (!sent.isEmpty()) byPlan.put(plan.name(), List.copyOf(sent));
        }
        return byPlan;
    }

    @Test
    void shouldAdmitEveryExportPlanStatement_whenTheMaintenanceGuardIsEnabled() {
        var refused = new TreeSet<String>();
        var statements = statements();
        assertThat(statements).isNotEmpty().containsKey("APPOINTMENT_STATUSES");
        for (var entry : statements.entrySet())
            for (String sql : entry.getValue()) {
                try {
                    CdsJdbcAdmissionGuard.clinical(sql);
                } catch (java.sql.SQLException refusal) {
                    refused.add(entry.getKey());
                }
            }
        assertThat(refused).as("plans whose SQL the maintenance guard refuses").isEmpty();
    }
}
