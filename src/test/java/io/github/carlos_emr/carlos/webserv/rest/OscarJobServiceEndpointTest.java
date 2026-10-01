/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.managers.OscarJobManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobTypeResponse;

/**
 * CXF local-transport endpoint tests for {@link OscarJobService} using CXF local transport.
 *
 * <p>These tests verify path routing, JSON serialization, and HTTP status codes
 * for job management REST endpoints. All dependencies are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("OscarJobService REST endpoint tests")
class OscarJobServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private OscarJobManager mockOscarJobManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        OscarJobService service = new OscarJobService();
        injectDependency(service, "oscarJobManager", mockOscarJobManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), any(), any(), any()))
            .thenReturn(true);
    }

    private OscarJobType createTestJobType(Integer id, String name, String className) {
        OscarJobType jobType = new OscarJobType();
        jobType.setId(id);
        jobType.setName(name);
        jobType.setClassName(className);
        jobType.setEnabled(true);
        return jobType;
    }

    private OscarJob createTestJob(Integer id, String name, OscarJobType jobType) {
        OscarJob job = new OscarJob();
        job.setId(id);
        job.setName(name);
        job.setEnabled(true);
        job.setOscarJobType(jobType);
        return job;
    }

    /** Tests for GET /jobs/types/current endpoint. */
    @Nested
    @DisplayName("GET /jobs/types/current")
    class GetCurrentlyAvailableJobTypes {

        @Test
        @DisplayName("should return 200 with job types when types exist")
        void shouldReturn200WithJobTypes_whenTypesExist() {
            OscarJobType testType = createTestJobType(1, "TestJob", "com.example.TestJob");
            when(mockOscarJobManager.getCurrentlyAvaliableJobTypes())
                .thenReturn(List.of(testType));

            Response response = request().path("/jobs/types/current").get();

            assertThat(response.getStatus()).isEqualTo(200);
            OscarJobTypeResponse body = response.readEntity(OscarJobTypeResponse.class);
            assertThat(body.getTypes()).hasSize(1);
        }

        @Test
        @DisplayName("should return 200 with empty list when no types available")
        void shouldReturn200WithEmptyList_whenNoTypesAvailable() {
            when(mockOscarJobManager.getCurrentlyAvaliableJobTypes())
                .thenReturn(Collections.emptyList());

            Response response = request().path("/jobs/types/current").get();

            assertThat(response.getStatus()).isEqualTo(200);
            OscarJobTypeResponse body = response.readEntity(OscarJobTypeResponse.class);
            assertThat(body.getTypes()).isEmpty();
        }
    }

    /** Tests for GET /jobs/all endpoint. */
    @Nested
    @DisplayName("GET /jobs/all")
    class GetAllJobs {

        @Test
        @DisplayName("should return 200 with jobs list")
        void shouldReturn200WithJobs_whenJobsExist() {
            OscarJobType jobType = createTestJobType(1, "TestType", "com.example.TestJob");
            OscarJob testJob = createTestJob(10, "My Job", jobType);
            when(mockOscarJobManager.getAllJobs(any(LoggedInInfo.class)))
                .thenReturn(List.of(testJob));

            Response response = request().path("/jobs/all").get();

            assertThat(response.getStatus()).isEqualTo(200);
            OscarJobResponse body = response.readEntity(OscarJobResponse.class);
            assertThat(body.getJobs()).hasSize(1);
        }

        @Test
        @DisplayName("should return 200 with empty list when no jobs")
        void shouldReturn200WithEmptyList_whenNoJobs() {
            when(mockOscarJobManager.getAllJobs(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/jobs/all").get();

            assertThat(response.getStatus()).isEqualTo(200);
            OscarJobResponse body = response.readEntity(OscarJobResponse.class);
            assertThat(body.getJobs()).isEmpty();
        }
    }

    /** Tests for GET /jobs/job/{jobId} endpoint. */
    @Nested
    @DisplayName("GET /jobs/job/{jobId}")
    class GetJob {

        @Test
        @DisplayName("should return 200 with job details when valid ID")
        void shouldReturn200WithJob_whenValidIdProvided() {
            OscarJobType jobType = createTestJobType(1, "TestType", "com.example.TestJob");
            OscarJob testJob = createTestJob(5, "Specific Job", jobType);
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(5)))
                .thenReturn(testJob);

            Response response = request().path("/jobs/job/5").get();

            assertThat(response.getStatus()).isEqualTo(200);
            OscarJobResponse body = response.readEntity(OscarJobResponse.class);
            assertThat(body.getJobs()).hasSize(1);
        }

        @Test
        @DisplayName("should serialise jobs as an array carrying the stored cron expression")
        void shouldSerialiseJobsAsArray_withStoredCronExpression() {
            // Jobs Management (admin/jobs.jsp) reads the schedule from jobs[0]; reading it off
            // "jobs" itself was always undefined and the dialog then saved every-minute.
            OscarJobType jobType = createTestJobType(1, "TestType", "com.example.TestJob");
            OscarJob testJob = createTestJob(5, "Specific Job", jobType);
            testJob.setCronExpression("0 15,45 3 * * *");
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(5)))
                .thenReturn(testJob);

            JsonNode json = responseJson(request().path("/jobs/job/5").get());

            assertThat(json.get("jobs").isArray()).isTrue();
            assertThat(json.get("jobs").get(0).get("cronExpression").asText()).isEqualTo("0 15,45 3 * * *");
        }
    }

    /** Tests for POST /jobs/saveCrontabExpression endpoint. */
    @Nested
    @DisplayName("POST /jobs/saveCrontabExpression")
    class SaveCrontabExpression {

        private Form everyPartExcept(String part, String... values) {
            Form form = new Form().param("scheduleJobId", "5");
            for (String name : List.of("minute", "hour", "day", "month", "weekday")) {
                form.param(name + "_chooser", name.equals(part) ? "1" : "0");
            }
            for (String value : values) {
                form.param(part, value);
            }
            return form;
        }

        private Response post(Form form) {
            return request().path("/jobs/saveCrontabExpression")
                .type(MediaType.APPLICATION_FORM_URLENCODED)
                .post(form);
        }

        @Test
        @DisplayName("should store the chosen minutes as the job cron expression")
        void shouldStoreCronExpression_withChosenMinutes() {
            OscarJob job = createTestJob(5, "Specific Job", createTestJobType(1, "TestType", "com.example.TestJob"));
            // Disabled, so OscarJobUtils.scheduleJob never reaches the task scheduler.
            job.setEnabled(false);
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(5))).thenReturn(job);

            Response response = post(everyPartExcept("minute", "15", "45"));

            assertThat(response.getStatus()).isEqualTo(200);
            ArgumentCaptor<OscarJob> saved = ArgumentCaptor.forClass(OscarJob.class);
            verify(mockOscarJobManager).updateJob(any(LoggedInInfo.class), saved.capture());
            assertThat(saved.getValue().getCronExpression()).isEqualTo("0 15,45 * * * *");
        }

        @Test
        @DisplayName("should refuse a chosen part with no values without saving or failing")
        void shouldRefuseWithoutSaving_whenChosenPartHasNoValues() {
            Response response = post(everyPartExcept("minute"));

            assertThat(response.getStatus()).isLessThan(500);
            verify(mockOscarJobManager, never()).updateJob(any(LoggedInInfo.class), any(OscarJob.class));
        }
    }
}
