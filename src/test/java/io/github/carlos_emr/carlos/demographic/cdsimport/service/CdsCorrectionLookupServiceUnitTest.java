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

import io.github.carlos_emr.carlos.commn.dao.CdsImportReviewDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportDiagnosticDao;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@Tag("unit")
class CdsCorrectionLookupServiceUnitTest {
    @Test
    void shouldReturnNoOverlayWithoutFilesystemAccess_whenNoCorrectionExists() throws Exception {
        var reviews=mock(CdsImportReviewDao.class);
        var runs=mock(CdsImportRunDao.class);
        var patients=mock(CdsImportPatientDao.class);
        var diagnostics=mock(CdsImportDiagnosticDao.class);
        var ledger=mock(CdsImportLedgerService.class);
        var jobs=mock(CdsImportJobService.class);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        var lookup=new CdsCorrectionLookupService(reviews,runs,patients,diagnostics,ledger,jobs,manager);
        String digest="a".repeat(64);
        assertThat(lookup.find("synthetic-run","patient.xml",digest,Path.of("unused-private-folder"))).isNull();
        verify(reviews).latestCorrection("synthetic-run",CdsCorrectionLookupService.sourceKey("patient.xml",digest));
        verify(manager).commit(any());
        verifyNoInteractions(runs,patients,diagnostics,ledger,jobs);
    }
}
