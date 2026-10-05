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
package io.github.carlos_emr.carlos.commn.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.managers.CanadianVaccineCatalogueManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

@Tag("unit")
@Tag("fast")
@DisplayName("Scheduled vaccine catalogue refresh job")
class CanadianVaccineCatalogueJobUnitTest extends CarlosUnitTestBase {

    private CanadianVaccineCatalogueManager manager;
    private CanadianVaccineCatalogueJob job;
    private final Provider provider = new Provider();
    private final Security security = new Security();

    @BeforeEach
    void setUp() {
        manager = createAndRegisterMock(CanadianVaccineCatalogueManager.class);
        job = new CanadianVaccineCatalogueJob();
        job.setLoggedInProvider(provider);
        job.setLoggedInSecurity(security);
    }

    @Test
    @DisplayName("should refresh the catalogue as the job's provider when it runs")
    void shouldRefreshCatalogue_whenJobRuns() throws IOException {
        job.run();

        ArgumentCaptor<LoggedInInfo> runAs = ArgumentCaptor.forClass(LoggedInInfo.class);
        verify(manager).update(runAs.capture());
        assertThat(runAs.getValue().getLoggedInProvider()).isSameAs(provider);
        assertThat(runAs.getValue().getLoggedInSecurity()).isSameAs(security);
    }

    @Test
    @DisplayName("should log and finish when the refresh fails, so the scheduler keeps running")
    void shouldNotThrow_whenRefreshFails() throws IOException {
        doThrow(new IOException("National Vaccine Catalogue answered HTTP 503")).when(manager).update(any());

        assertThatCode(job::run).doesNotThrowAnyException();
    }
}
