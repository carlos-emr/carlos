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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.commn.model.OscarJobType;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobExecutingManager;
import io.github.carlos_emr.carlos.commn.jobs.OscarJobUtils;
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

    @ParameterizedTest
    @ValueSource(strings = {"cancelJob", "disableJob"})
    void shouldStopTrackingTheTimer_whenAnExplicitActionCancelsIt(String action) {
        int jobId = 3043;
        OscarJobType type = createTestJobType(1, "TestType", "com.example.TestJob");
        OscarJob job = createTestJob(jobId, "Scheduled job", type);
        when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(jobId))).thenReturn(job);

        try (var timer = new ScheduledThreadPoolExecutor(1)) {
            var future = timer.<Object>schedule(() -> null, 1, TimeUnit.HOURS);
            var futures = OscarJobExecutingManager.getFutures();
            futures.put(jobId, future);
            Form form = new Form().param("jobId", Integer.toString(jobId));
            try (Response response = request().path("/jobs/" + action)
                    .type(MediaType.APPLICATION_FORM_URLENCODED).post(form)) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(future.isCancelled()).isTrue();
                assertThat(futures).doesNotContainKey(jobId);
            } finally {
                futures.remove(jobId, future);
                timer.shutdownNow();
            }
        }
    }

    @Test
    void shouldReturnTheCreatedJob_whenItHasNoScheduleYet() {
        int jobId = 3046;
        var type = createTestJobType(1, "TestType", "com.example.TestJob");
        var stored = createTestJob(jobId, "Scheduled job", type);
        when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(jobId))).thenReturn(stored);
        doAnswer(call -> {
            call.<OscarJob>getArgument(1).setId(jobId);
            return null;
        }).when(mockOscarJobManager).saveJob(any(LoggedInInfo.class), any(OscarJob.class));

        try (Response response = request().path("/jobs/saveJob")
                .type(MediaType.APPLICATION_FORM_URLENCODED).post(saveForm(0, true))) {
            assertThat(response.getStatus()).isEqualTo(200);
            JsonNode json = response.readEntity(JsonNode.class);
            assertThat(json.path("jobs").get(0).path("id").asInt()).isEqualTo(jobId);
            assertThat(json.has("scheduleActivation")).isFalse();
        }
    }

    @Test
    void shouldEnableAndReturnAnExistingJobThroughTheSharedScheduler() throws Exception {
        int jobId = 3047;
        var type = createTestJobType(1, "TestType", "com.example.TestJob");
        var stored = createTestJob(jobId, "Scheduled job", type);
        stored.setEnabled(false);
        when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(jobId))).thenReturn(stored);
        var form = new Form().param("jobId", Integer.toString(jobId));

        try (var scheduler = mockStatic(OscarJobUtils.class);
                Response response = request().path("/jobs/enableJob")
                        .type(MediaType.APPLICATION_FORM_URLENCODED).post(form)) {
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(stored.isEnabled()).isTrue();
            scheduler.verify(() -> OscarJobUtils.scheduleJob(stored));
        }
    }

    @Test
    void shouldLeaveNoTimer_whenAnEnabledSaveOverlapsADisableFromAnotherRestService() throws Exception {
        int jobId = 3045;
        var storedEnabled = new AtomicBoolean();
        var type = createTestJobType(1, "TestType", "com.example.TestJob");
        when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(jobId))).thenAnswer(call -> {
            var stored = createTestJob(jobId, "Scheduled job", type);
            stored.setEnabled(storedEnabled.get());
            stored.setCronExpression("0 15 * * * *");
            return stored;
        });
        doAnswer(call -> {
            OscarJob job = call.getArgument(1);
            storedEnabled.set(job.isEnabled());
            return null;
        }).when(mockOscarJobManager).updateJob(any(LoggedInInfo.class), any(OscarJob.class));

        OscarJobService primary = authenticatedService();
        OscarJobService secondary = authenticatedService();
        ScheduledFuture<Object> timer = mock();
        when(timer.cancel(false)).thenReturn(true);
        var reachedScheduler = new CountDownLatch(1);
        var disableAcknowledged = new CountDownLatch(1);
        var scheduledJobs = OscarJobExecutingManager.getFutures();
        try (var requests = Executors.newFixedThreadPool(2)) {
            var save = requests.submit(() -> {
                try (var scheduler = mockStatic(OscarJobUtils.class)) {
                    scheduler.when(() -> OscarJobUtils.scheduleJob(any(OscarJob.class))).thenAnswer(call -> {
                        reachedScheduler.countDown();
                        disableAcknowledged.await(2, TimeUnit.SECONDS);
                        scheduledJobs.put(jobId, timer);
                        return true;
                    });
                    return primary.saveJob(saveForm(jobId, true).asMap());
                }
            });
            assertThat(reachedScheduler.await(5, TimeUnit.SECONDS)).isTrue();
            var disable = requests.submit(() -> {
                var response = secondary.saveJob(saveForm(jobId, false).asMap());
                disableAcknowledged.countDown();
                return response;
            });
            assertThat(save.get(8, TimeUnit.SECONDS).getScheduleActivation())
                    .isEqualTo(OscarJobResponse.ScheduleActivation.ACTIVE);
            assertThat(disable.get(8, TimeUnit.SECONDS).getScheduleActivation())
                    .isEqualTo(OscarJobResponse.ScheduleActivation.DISABLED);
            assertThat(storedEnabled).isFalse();
            assertThat(scheduledJobs).doesNotContainKey(jobId);
            verify(timer).cancel(false);
        } finally {
            scheduledJobs.remove(jobId, timer);
        }
    }

    private OscarJobService authenticatedService() {
        var principal = new LoggedInInfo();
        OscarJobService service = new OscarJobService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return principal;
            }
        };
        injectDependency(service, "oscarJobManager", mockOscarJobManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    private static Form saveForm(int jobId, boolean enabled) {
        Form form = new Form().param("job.id", Integer.toString(jobId)).param("job.name", "Scheduled job")
                .param("job.description", "Synthetic job details").param("job.oscarJobTypeId", "1")
                .param("job.provider", "999998");
        if (enabled) {
            form.param("job.enabled", "on");
        }
        return form;
    }

    @Nested
    @DisplayName("POST /jobs/saveJob")
    class SaveJob {

        private static final int JOB_ID = 3042;
        private static final String CRON = "0 15 * * * *";

        private OscarJob persistedEdit(boolean enabled) {
            OscarJobType type = createTestJobType(1, "TestType", "com.example.TestJob");
            OscarJob before = createTestJob(JOB_ID, "Previous job", type);
            before.setCronExpression(CRON);
            OscarJob stored = createTestJob(JOB_ID, "Edited job", type);
            stored.setCronExpression(CRON);
            stored.setEnabled(enabled);
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(JOB_ID))).thenReturn(before, stored);
            return stored;
        }

        private Response post(boolean enabled) {
            Form form = new Form().param("job.id", String.valueOf(JOB_ID))
                .param("job.name", "Edited job").param("job.description", "Synthetic job details")
                .param("job.oscarJobTypeId", "1").param("job.provider", "999998");
            if (enabled) {
                form.param("job.enabled", "on");
            }
            return request().path("/jobs/saveJob").type(MediaType.APPLICATION_FORM_URLENCODED).post(form);
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void shouldReturnNotFound_whenTheJobIsMissingDuringSave(boolean foundBeforeUpdate) {
            var type = createTestJobType(1, "TestType", "com.example.TestJob");
            var before = createTestJob(JOB_ID, "Previous job", type);
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(JOB_ID)))
                    .thenReturn(foundBeforeUpdate ? before : null, (OscarJob) null);

            try (var scheduler = mockStatic(OscarJobUtils.class); Response response = post(true)) {
                assertThat(response.getStatus()).isEqualTo(404);
                scheduler.verifyNoInteractions();
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void shouldClearTheScheduledTask_whenTheSavedEditDisablesTheJob(boolean alreadyCancelled) {
            persistedEdit(false);
            try (var timer = new ScheduledThreadPoolExecutor(1)) {
                var future = timer.<Object>schedule(() -> null, 1, TimeUnit.HOURS);
                if (alreadyCancelled) {
                    future.cancel(false);
                }
                var futures = OscarJobExecutingManager.getFutures();
                futures.put(JOB_ID, future);
                try (Response response = post(false)) {
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(future.isCancelled()).isTrue();
                    assertThat(futures).doesNotContainKey(JOB_ID);
                    assertThat(response.readEntity(JsonNode.class).path("scheduleActivation").asText()).isEqualTo("DISABLED");
                } finally {
                    futures.remove(JOB_ID, future);
                    timer.shutdownNow();
                }
            }
        }

        @Test
        void shouldReportFailureAndKeepTrackingTheTask_whenCancellationIsRefused() {
            persistedEdit(false);
            ScheduledFuture<Object> future = mock();
            var futures = OscarJobExecutingManager.getFutures();
            futures.put(JOB_ID, future);
            try (Response response = post(false)) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.readEntity(JsonNode.class).path("scheduleActivation").asText()).isEqualTo("FAILED");
                assertThat(futures).containsEntry(JOB_ID, future);
                verify(future).cancel(false);
            } finally {
                futures.remove(JOB_ID, future);
            }
        }

        @ParameterizedTest
        @NullAndEmptySource
        void shouldConfirmASavedJobWithNoSchedule_withoutTryingToActivateIt(String cron) {
            OscarJob stored = persistedEdit(true);
            stored.setCronExpression(cron);
            try (var scheduler = mockStatic(OscarJobUtils.class); Response response = post(true)) {
                assertThat(response.getStatus()).isEqualTo(200);
                assertThat(response.readEntity(JsonNode.class).has("scheduleActivation")).isFalse();
                scheduler.verifyNoInteractions();
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void shouldReturnTheSavedJobWithFailedActivation_whenTheStoredTypeIsMissing(boolean hasCron) {
            OscarJob stored = persistedEdit(true);
            stored.setOscarJobType(null);
            stored.setOscarJobTypeId(1);
            if (!hasCron) {
                stored.setCronExpression(null);
            }

            try (var scheduler = mockStatic(OscarJobUtils.class); Response response = post(true)) {
                assertThat(response.getStatus()).isEqualTo(200);
                JsonNode json = response.readEntity(JsonNode.class);
                assertThat(json.path("jobs").get(0).path("id").asInt()).isEqualTo(JOB_ID);
                assertThat(json.path("jobs").get(0).path("oscarJobTypeId").asInt()).isEqualTo(1);
                assertThat(json.path("scheduleActivation").asText()).isEqualTo("FAILED");
                verify(mockOscarJobManager).updateJob(any(LoggedInInfo.class), any(OscarJob.class));
                scheduler.verify(() -> OscarJobUtils.cancelJob(JOB_ID, false));
            }

            when(mockOscarJobManager.getAllJobs(any(LoggedInInfo.class))).thenReturn(List.of(stored));
            try (Response readback = request().path("/jobs/all").get()) {
                assertThat(readback.getStatus()).isEqualTo(200);
                var listed = readback.readEntity(OscarJobResponse.class).getJobs();
                assertThat(listed).singleElement().satisfies(job -> {
                    assertThat(job.getId()).isEqualTo(JOB_ID);
                    assertThat(job.getNextPlannedExecutionDate()).isNull();
                });
            }
        }

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void shouldConfirmActivationOnlyWhenThePersistedJobWasScheduled(boolean activated) throws Exception {
            OscarJob stored = persistedEdit(true);
            try (var scheduler = mockStatic(OscarJobUtils.class)) {
                scheduler.when(() -> OscarJobUtils.scheduleJob(stored)).thenReturn(activated);
                try (Response response = post(true)) {
                    assertThat(response.getStatus()).isEqualTo(200);
                    assertThat(response.readEntity(JsonNode.class).path("scheduleActivation").asText())
                        .isEqualTo(activated ? "ACTIVE" : "FAILED");
                    scheduler.verify(() -> OscarJobUtils.scheduleJob(stored));
                }
            }
        }
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

        @ParameterizedTest
        @ValueSource(booleans = {true, false})
        void shouldReportTheNextRunOnlyWhenAnExistingJobWithATypeIsEnabled(boolean enabled) {
            OscarJobType jobType = createTestJobType(1, "TestType", "com.example.TestJob");
            OscarJob job = createTestJob(10, "Scheduled job", jobType);
            job.setEnabled(enabled);
            job.setCronExpression("0 */5 * * * *");
            when(mockOscarJobManager.getAllJobs(any(LoggedInInfo.class))).thenReturn(List.of(job));

            try (Response response = request().path("/jobs/all").get()) {
                assertThat(response.getStatus()).isEqualTo(200);
                var listed = response.readEntity(OscarJobResponse.class).getJobs();
                assertThat(listed).singleElement()
                        .satisfies(item -> assertThat(item.getNextPlannedExecutionDate() != null).isEqualTo(enabled));
            }
        }

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
        void shouldReturnNotFoundWithoutUpdating_whenTheScheduleJobIsMissing() {
            try (var scheduler = mockStatic(OscarJobUtils.class);
                    Response response = post(everyPartExcept("minute", "15"))) {
                assertThat(response.getStatus()).isEqualTo(404);
                verify(mockOscarJobManager, never()).updateJob(any(LoggedInInfo.class), any(OscarJob.class));
                scheduler.verifyNoInteractions();
            }
        }

        @Test
        @DisplayName("should store the chosen minutes as the job cron expression")
        void shouldStoreCronExpression_withChosenMinutes() {
            OscarJob job = createTestJob(5, "Specific Job", createTestJobType(1, "TestType", "com.example.TestJob"));
            // A disabled job keeps the saved expression for a later enable.
            job.setEnabled(false);
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(5))).thenReturn(job);

            Response response = post(everyPartExcept("minute", "15", "45"));

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.readEntity(JsonNode.class).get("scheduleActivation").asText()).isEqualTo("DISABLED");
            ArgumentCaptor<OscarJob> saved = ArgumentCaptor.forClass(OscarJob.class);
            verify(mockOscarJobManager).updateJob(any(LoggedInInfo.class), saved.capture());
            assertThat(saved.getValue().getCronExpression()).isEqualTo("0 15,45 * * * *");
        }

        @ParameterizedTest
        @ValueSource(strings = {"active", "declined", "rejected"})
        void shouldReportWhetherTheStoredScheduleWasActivated_whenTheJobIsEnabled(String outcome) {
            OscarJob job = createTestJob(5, "Specific Job", createTestJobType(1, "TestType", "com.example.TestJob"));
            when(mockOscarJobManager.getJob(any(LoggedInInfo.class), eq(5))).thenReturn(job);

            try (var scheduler = mockStatic(OscarJobUtils.class)) {
                var schedule = scheduler.when(() -> OscarJobUtils.scheduleJob(job));
                if ("rejected".equals(outcome)) {
                    schedule.thenThrow(new RejectedExecutionException("FAKE scheduler refusal"));
                } else {
                    schedule.thenReturn("active".equals(outcome));
                }
                Response response = post(everyPartExcept("minute", "15"));

                assertThat(response.getStatus()).isEqualTo(200);
                JsonNode json = response.readEntity(JsonNode.class);
                assertThat(json.get("jobs").get(0).get("cronExpression").asText()).isEqualTo("0 15 * * * *");
                assertThat(json.get("scheduleActivation").asText()).isEqualTo("active".equals(outcome) ? "ACTIVE" : "FAILED");
                verify(mockOscarJobManager).updateJob(any(LoggedInInfo.class), eq(job));
                scheduler.verify(() -> OscarJobUtils.scheduleJob(job));
            }
        }

        @Test
        @DisplayName("should refuse a request that omits a chooser instead of widening it to every")
        void shouldRefuseWithoutSaving_whenChooserIsMissing() {
            Form form = new Form().param("scheduleJobId", "5")
                .param("minute_chooser", "1").param("minute", "15")
                .param("hour_chooser", "0").param("day_chooser", "0").param("month_chooser", "0");

            Response response = post(form);

            // The service returns null for a refused schedule, which JAX-RS answers as 204.
            assertThat(response.getStatus()).isEqualTo(204);
            verify(mockOscarJobManager, never()).updateJob(any(LoggedInInfo.class), any(OscarJob.class));
        }

        @Test
        @DisplayName("should refuse a chosen part with no values without saving or failing")
        void shouldRefuseWithoutSaving_whenChosenPartHasNoValues() {
            Response response = post(everyPartExcept("minute"));

            // The service returns null for a refused schedule, which JAX-RS answers as 204.
            assertThat(response.getStatus()).isEqualTo(204);
            verify(mockOscarJobManager, never()).updateJob(any(LoggedInInfo.class), any(OscarJob.class));
        }
    }
}
