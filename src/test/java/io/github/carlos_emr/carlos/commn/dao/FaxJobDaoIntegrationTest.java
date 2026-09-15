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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link FaxJobDao} outbound-queue queries and the
 * provider-job-id duplicate-prevention lookup.
 *
 * <p>Runs against the H2 test schema generated from the {@link FaxJob} entity. All fax
 * numbers are fictitious test numbers (416555xxxx / 905555xxxx) and no credentials are
 * involved.</p>
 *
 * @since 2026-08-21
 * @see FaxJobDao
 */
@DisplayName("FaxJob Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("fax")
@Transactional
public class FaxJobDaoIntegrationTest extends CarlosTestBase {

    private static final String FAX_LINE = "4165550100";

    @Autowired
    private FaxJobDao faxJobDao;

    @Test
    @Tag("query")
    @DisplayName("should return only WAITING jobs without a job id when getting ready-to-send faxes")
    void shouldReturnOnlyWaitingJobsWithoutJobId_whenGettingReadyToSendFaxes() {
        // Given: a WAITING job on the line without a provider job id (the only ready one),
        // a WAITING job already handed to the provider (jobId set), a SENT job, and a
        // WAITING job on a different line
        FaxJob ready = persistFaxJob(FaxJob.STATUS.WAITING, FAX_LINE, null);
        persistFaxJob(FaxJob.STATUS.WAITING, FAX_LINE, 101L);
        persistFaxJob(FaxJob.STATUS.SENT, FAX_LINE, null);
        persistFaxJob(FaxJob.STATUS.WAITING, "9055550111", null);

        // When
        List<FaxJob> result = faxJobDao.getReadyToSendFaxes(FAX_LINE);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(ready.getId());
        assertThat(result.get(0).getStatus()).isEqualTo(FaxJob.STATUS.WAITING);
        assertThat(result.get(0).getJobId()).isNull();
    }

    @Test
    @Tag("query")
    @DisplayName("should return SENT and WAITING jobs with a job id when getting in-progress faxes")
    void shouldReturnSentAndWaitingJobsWithJobId_whenGettingInprogressFaxes() {
        // Given: in-progress = (SENT or WAITING) with a provider job id
        FaxJob sentWithJobId = persistFaxJob(FaxJob.STATUS.SENT, FAX_LINE, 201L);
        FaxJob waitingWithJobId = persistFaxJob(FaxJob.STATUS.WAITING, FAX_LINE, 202L);
        persistFaxJob(FaxJob.STATUS.WAITING, FAX_LINE, null);
        persistFaxJob(FaxJob.STATUS.RECEIVED, FAX_LINE, 203L);
        persistFaxJob(FaxJob.STATUS.ERROR, FAX_LINE, 204L);

        // When
        List<FaxJob> result = faxJobDao.getInprogressFaxesByJobId();

        // Then
        assertThat(result)
                .extracting(FaxJob::getId)
                .containsExactlyInAnyOrder(sentWithJobId.getId(), waitingWithJobId.getId());
    }

    @Test
    @Tag("create")
    @DisplayName("should persist a job id beyond int range for the SRFax FaxDetailsID")
    void shouldPersistJobIdBeyondIntRange_forSrfaxFaxDetailsId() {
        // Given: SRFax FaxDetailsID values exceed Integer.MAX_VALUE - the column must be BIGINT
        long largeJobId = 3_000_000_000L;
        FaxJob job = persistFaxJob(FaxJob.STATUS.SENT, FAX_LINE, largeJobId);

        // When: query round-trips the value through the database column
        List<FaxJob> found = faxJobDao.findByProviderJobId(largeJobId);

        // Then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(job.getId());
        assertThat(found.get(0).getJobId()).isEqualTo(largeJobId);
    }

    @Test
    @Tag("query")
    @DisplayName("should find rows by provider job id and return empty for an unknown id")
    void shouldFindRows_byProviderJobId() {
        // Given: two rows sharing a provider job id (e.g. an ERROR retry plus the final
        // RECEIVED import) and an unrelated row
        FaxJob errorRow = persistFaxJob(FaxJob.STATUS.ERROR, FAX_LINE, 777L);
        FaxJob receivedRow = persistFaxJob(FaxJob.STATUS.RECEIVED, FAX_LINE, 777L);
        persistFaxJob(FaxJob.STATUS.RECEIVED, FAX_LINE, 778L);

        // When / Then: both rows for the shared id
        assertThat(faxJobDao.findByProviderJobId(777L))
                .extracting(FaxJob::getId)
                .containsExactlyInAnyOrder(errorRow.getId(), receivedRow.getId());

        // When / Then: unknown id yields an empty list, never null
        assertThat(faxJobDao.findByProviderJobId(999_999L)).isEmpty();
    }

    // -- helper methods --

    private FaxJob persistFaxJob(FaxJob.STATUS status, String faxLine, Long jobId) {
        FaxJob job = new FaxJob();
        job.setStatus(status);
        job.setFax_line(faxLine);
        job.setJobId(jobId);
        job.setUser("-1");
        job.setDestination("9055550122");
        job.setFile_name("test-fax.pdf");
        job.setStamp(new Date());
        faxJobDao.persist(job);
        assertThat(job.getId()).isPositive();
        return job;
    }
}
