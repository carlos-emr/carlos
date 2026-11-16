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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.github.carlos_emr.carlos.commn.dao.CdsImportAdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * No import transaction carries a clock: a slow but working database never fails an import. Each
 * of these used to end its transaction at a fixed time (the ledger's 120 s, the course's and the
 * program context's 30 s, the source-field pages' 15 s).
 */
@Tag("unit")
class CdsImportTransactionClockUnitTest {
    @Test
    void shouldOpenTheLedgersTransactionsWithoutAClock_forEveryServiceThatSharesThem() {
        TransactionTemplate template =
                CdsImportLedgerService.template(mock(PlatformTransactionManager.class));
        assertThat(template.getTimeout()).isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT);
        assertThat(template.getIsolationLevel())
                .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Test
    void shouldReadTheCourseWithoutAClock_forASlowButWorkingDatabase() {
        var course =
                new CdsCourseService(
                        mock(CdsImportConfigurationService.class),
                        mock(PlatformTransactionManager.class),
                        mock(CdsProgramContextService.class));
        var read = (TransactionTemplate) ReflectionTestUtils.getField(course, "read");
        assertThat(read.getTimeout()).isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT);
        assertThat(read.isReadOnly()).isTrue();
    }

    @Test
    void shouldPageSourceFieldsWithoutAClock_forASlowButWorkingDatabase() {
        var source =
                new CdsClinicalSourceService(
                        mock(CdsImportAccessService.class),
                        mock(CdsImportFieldPersister.class),
                        mock(CdsImportPatientDao.class),
                        mock(CdsImportMaintenanceService.class),
                        mock(CdsLaboratoryReadService.class),
                        mock(CdsImportAdmissionDao.class),
                        mock(CdsImportConfigurationService.class),
                        mock(CdsCorrectionLookupService.class),
                        mock(PlatformTransactionManager.class));
        var transaction = (TransactionTemplate) ReflectionTestUtils.getField(source, "transaction");
        assertThat(transaction.getTimeout()).isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT);
    }

    @Test
    void shouldReadProgramContextWithoutAClock_forASlowButWorkingDatabase() {
        Transactional declared = CdsProgramContextService.class.getAnnotation(Transactional.class);
        assertThat(declared.readOnly()).isTrue();
        assertThat(declared.timeout()).isEqualTo(TransactionDefinition.TIMEOUT_DEFAULT);
        assertThat(declared.timeoutString()).isEmpty();
    }
}
