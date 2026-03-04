/*
 * Copyright (c) 2026. CARLOS EMR contributors and others.
 *
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
 */
package io.github.carlos_emr.carlos.fax.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link FaxSender}.
 *
 * <p>Tests the send() workflow including status transitions, error handling,
 * transient network error detection, and audit logging via FaxClientLog.</p>
 *
 * @since 2026-02-13
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxSender Unit Tests")
class FaxSenderTest extends CarlosUnitTestBase {

    @Mock
    private FaxConfigDao faxConfigDao;

    @Mock
    private FaxJobDao faxJobDao;

    @Mock
    private FaxClientLogDao faxClientLogDao;

    @Mock
    private FaxProviderClientFactory faxProviderClientFactory;

    @Mock
    private FaxProviderClient faxProviderClient;

    private MockedStatic<OscarProperties> oscarPropertiesMock;
    private OscarProperties mockOscarProperties;

    private FaxSender faxSender;
    private AutoCloseable mockitoCloseable;

    @BeforeEach
    void setUp() {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        faxSender = new FaxSender(faxConfigDao, faxJobDao, faxClientLogDao, faxProviderClientFactory);

        // Mock OscarProperties.getInstance() static call
        mockOscarProperties = mock(OscarProperties.class);
        oscarPropertiesMock = mockStatic(OscarProperties.class);
        oscarPropertiesMock.when(OscarProperties::getInstance).thenReturn(mockOscarProperties);
        when(mockOscarProperties.getDocumentDirectory()).thenReturn("/test/documents");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (oscarPropertiesMock != null) {
            oscarPropertiesMock.close();
        }
        if (mockitoCloseable != null) {
            mockitoCloseable.close();
        }
    }

    /**
     * Creates an active FaxConfig with a fax number and sender email.
     */
    private FaxConfig createActiveFaxConfig(String faxNumber) {
        FaxConfig config = new FaxConfig();
        config.setActive(true);
        config.setFaxNumber(faxNumber);
        config.setSiteUser("testuser");
        config.setSenderEmail("test@example.com");
        return config;
    }

    /**
     * Creates a FaxJob with WAITING status and a relative filename.
     */
    private FaxJob createWaitingFaxJob(int id, String filename) {
        FaxJob job = new FaxJob();
        job.setId(id);
        job.setFile_name(filename);
        job.setFax_line("5551234567");
        job.setDestination("5559876543");
        job.setStatus(FaxJob.STATUS.WAITING);
        return job;
    }

    /**
     * Creates a FaxClientLog associated with a fax id.
     */
    private FaxClientLog createFaxClientLog(int faxId) {
        FaxClientLog log = new FaxClientLog();
        log.setFaxId(faxId);
        return log;
    }

    /**
     * Tests for the send() method workflow.
     */
    @Nested
    @DisplayName("send()")
    class SendTests {

        @Test
        @DisplayName("should throw IllegalStateException when DOCUMENT_DIR is null")
        void shouldThrowIllegalStateException_whenDocumentDirIsNull() {
            // Given
            when(mockOscarProperties.getDocumentDirectory()).thenReturn(null);

            // When / Then
            assertThatThrownBy(() -> faxSender.send())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DOCUMENT_DIR is not configured");
            verify(faxJobDao, never()).getReadyToSendFaxes(any());
        }

        @Test
        @DisplayName("should throw IllegalStateException when DOCUMENT_DIR is empty")
        void shouldThrowIllegalStateException_whenDocumentDirIsEmpty() {
            // Given
            when(mockOscarProperties.getDocumentDirectory()).thenReturn("   ");

            // When / Then
            assertThatThrownBy(() -> faxSender.send())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DOCUMENT_DIR is not configured");
            verify(faxJobDao, never()).getReadyToSendFaxes(any());
        }

        @Test
        @DisplayName("should skip inactive accounts without querying fax jobs")
        void shouldSkipInactiveAccounts_withoutQueryingFaxJobs() {
            // Given
            FaxConfig inactiveConfig = new FaxConfig();
            inactiveConfig.setActive(false);
            inactiveConfig.setFaxNumber("5550001111");
            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(inactiveConfig));

            // When
            faxSender.send();

            // Then - should never query for fax jobs since account is inactive
            verify(faxJobDao, never()).getReadyToSendFaxes(any());
        }

        @Test
        @DisplayName("should send fax and update status to SENT for active account")
        void shouldSendFaxAndUpdateStatus_forActiveAccount() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(1, "fax_document.pdf");
            FaxClientLog clientLog = createFaxClientLog(1);

            FaxJob sentResponse = new FaxJob();
            sentResponse.setStatus(FaxJob.STATUS.SENT);
            sentResponse.setJobId(12345L);
            sentResponse.setStatusString("Queued");

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(1)).thenReturn(clientLog);
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(waitingJob), any(Path.class))).thenReturn(sentResponse);

            // When
            faxSender.send();

            // Then
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
            assertThat(waitingJob.getJobId()).isEqualTo(12345L);
            assertThat(waitingJob.getStatusString()).isEqualTo("Queued");
            assertThat(waitingJob.getSenderEmail()).isEqualTo("test@example.com");
            verify(faxJobDao).merge(waitingJob);
            verify(faxClientLogDao).merge(clientLog);
            assertThat(clientLog.getResult()).isEqualTo("SENT");
            assertThat(clientLog.getEndTime()).isNotNull();
        }

        @Test
        @DisplayName("should mark fax as ERROR when path validation fails with IllegalArgumentException")
        void shouldMarkFaxAsError_whenPathValidationFailsWithIllegalArgument() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(2, ""); // empty filename triggers IllegalArgumentException
            FaxClientLog clientLog = createFaxClientLog(2);

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(2)).thenReturn(clientLog);

            // When
            faxSender.send();

            // Then
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(waitingJob.getStatusString()).isEqualTo("INVALID OR UNSAFE FAX FILE PATH");
            verify(faxJobDao).merge(waitingJob);
            verify(faxClientLogDao).merge(clientLog);
            assertThat(clientLog.getResult()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("should revert fax to WAITING when transient network error occurs")
        void shouldRevertFaxToWaiting_whenTransientNetworkErrorOccurs() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(3, "fax_document.pdf");
            FaxClientLog clientLog = createFaxClientLog(3);

            ConnectException connectEx = new ConnectException("Connection refused");
            FaxProviderException networkException = new FaxProviderException(
                    "Connection failed", connectEx, FaxProviderException.isTransientNetworkCause(connectEx));

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(3)).thenReturn(clientLog);
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(waitingJob), any(Path.class))).thenThrow(networkException);

            // When
            faxSender.send();

            // Then - transient error should revert to WAITING for retry
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.WAITING);
            verify(faxJobDao).merge(waitingJob);
            verify(faxClientLogDao).merge(clientLog);
            assertThat(clientLog.getResult()).isEqualTo("WAITING");
        }

        @Test
        @DisplayName("should mark fax as ERROR when permanent provider error occurs")
        void shouldMarkFaxAsError_whenPermanentProviderErrorOccurs() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(4, "fax_document.pdf");
            FaxClientLog clientLog = createFaxClientLog(4);

            FaxProviderException permanentError = new FaxProviderException("Invalid credentials");

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(4)).thenReturn(clientLog);
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(waitingJob), any(Path.class))).thenThrow(permanentError);

            // When
            faxSender.send();

            // Then
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(waitingJob.getStatusString()).isEqualTo("Invalid credentials");
            verify(faxJobDao).merge(waitingJob);
            verify(faxClientLogDao).merge(clientLog);
            assertThat(clientLog.getResult()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("should continue processing remaining faxes when one fails")
        void shouldContinueProcessing_whenOneFaxFails() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob failingJob = createWaitingFaxJob(5, ""); // will fail with path error
            FaxJob successJob = createWaitingFaxJob(6, "good_fax.pdf");
            FaxClientLog failLog = createFaxClientLog(5);
            FaxClientLog successLog = createFaxClientLog(6);

            FaxJob sentResponse = new FaxJob();
            sentResponse.setStatus(FaxJob.STATUS.SENT);
            sentResponse.setJobId(99L);

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Arrays.asList(failingJob, successJob));
            when(faxClientLogDao.findClientLogbyFaxId(5)).thenReturn(failLog);
            when(faxClientLogDao.findClientLogbyFaxId(6)).thenReturn(successLog);
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(successJob), any(Path.class))).thenReturn(sentResponse);

            // When
            faxSender.send();

            // Then - first fax should be ERROR, second should be SENT
            assertThat(failingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
            assertThat(successJob.getStatus()).isEqualTo(FaxJob.STATUS.SENT);

            // Both faxes should be merged (persisted)
            verify(faxJobDao).merge(failingJob);
            verify(faxJobDao).merge(successJob);
        }

        @Test
        @DisplayName("should handle null FaxClientLog gracefully without NPE")
        void shouldHandleNullFaxClientLog_withoutNpe() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(7, "fax_document.pdf");

            FaxJob sentResponse = new FaxJob();
            sentResponse.setStatus(FaxJob.STATUS.SENT);
            sentResponse.setJobId(777L);

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(7)).thenReturn(null); // no audit log entry
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(waitingJob), any(Path.class))).thenReturn(sentResponse);

            // When
            faxSender.send();

            // Then - fax status should still be updated even without audit log
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
            verify(faxJobDao).merge(waitingJob);
            // faxClientLogDao.merge should NOT be called since log was null
            verify(faxClientLogDao, never()).merge(any());
        }

        @Test
        @DisplayName("should set statusString from provider exception message when message is null")
        void shouldSetDefaultStatusString_whenProviderExceptionMessageIsNull() throws Exception {
            // Given
            FaxConfig config = createActiveFaxConfig("5551112222");
            FaxJob waitingJob = createWaitingFaxJob(8, "fax_document.pdf");
            FaxClientLog clientLog = createFaxClientLog(8);

            FaxProviderException nullMessageException = new FaxProviderException(null);

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(config));
            when(faxJobDao.getReadyToSendFaxes("5551112222")).thenReturn(Collections.singletonList(waitingJob));
            when(faxClientLogDao.findClientLogbyFaxId(8)).thenReturn(clientLog);
            when(faxProviderClientFactory.getClient(config)).thenReturn(faxProviderClient);
            when(faxProviderClient.sendFax(eq(config), eq(waitingJob), any(Path.class))).thenThrow(nullMessageException);

            // When
            faxSender.send();

            // Then
            assertThat(waitingJob.getStatusString()).isEqualTo("PROBLEM COMMUNICATING WITH WEB SERVICE");
            assertThat(waitingJob.getStatus()).isEqualTo(FaxJob.STATUS.ERROR);
        }
    }

    /**
     * Tests for credential decryption failure isolation (IllegalStateException catch).
     *
     * <p>When FaxConfig.getPasswd() or getFaxPasswd() throws IllegalStateException due to
     * encryption key rotation or data corruption, the affected account should be skipped
     * with a clear log message, and remaining accounts should continue processing.</p>
     *
     * <p>Note: In FaxSender.send(), getReadyToSendFaxes() is called before getClient(),
     * so the IllegalStateException occurs inside the per-fax loop when getClient() is called.
     * It bubbles up to the outer catch block since the inner catches don't handle it.</p>
     */
    @Nested
    @DisplayName("Credential Decryption Failure Isolation")
    @Tag("security")
    class CredentialDecryptionFailureTests {

        @Test
        @DisplayName("should skip account and continue with next when IllegalStateException occurs")
        void shouldSkipAccountAndContinue_whenIllegalStateExceptionOccurs() throws Exception {
            // Given: Two accounts - first throws IllegalStateException during getClient (credential decrypt)
            FaxConfig badConfig = createActiveFaxConfig("5550001111");
            FaxConfig goodConfig = createActiveFaxConfig("5550002222");

            // Need a fax job for the bad config so the inner loop runs and hits getClient()
            FaxJob badJob = createWaitingFaxJob(19, "bad_fax.pdf");
            FaxJob goodJob = createWaitingFaxJob(20, "good_fax.pdf");
            FaxClientLog goodLog = createFaxClientLog(20);

            FaxJob sentResponse = new FaxJob();
            sentResponse.setStatus(FaxJob.STATUS.SENT);
            sentResponse.setJobId(999L);

            when(faxConfigDao.findAll(null, null)).thenReturn(Arrays.asList(badConfig, goodConfig));
            // First account: has faxes queued but getClient() will fail on credential decrypt
            when(faxJobDao.getReadyToSendFaxes("5550001111")).thenReturn(Collections.singletonList(badJob));
            when(faxProviderClientFactory.getClient(any())).thenAnswer(invocation -> {
                FaxConfig config = invocation.getArgument(0);
                if ("5550001111".equals(config.getFaxNumber())) {
                    throw new IllegalStateException("Failed to decrypt faxPasswd");
                }
                return faxProviderClient;
            });
            // Second account: normal processing
            when(faxJobDao.getReadyToSendFaxes("5550002222")).thenReturn(Collections.singletonList(goodJob));
            when(faxClientLogDao.findClientLogbyFaxId(20)).thenReturn(goodLog);
            when(faxProviderClient.sendFax(eq(goodConfig), eq(goodJob), any(Path.class))).thenReturn(sentResponse);

            // When
            faxSender.send();

            // Then: Second account processed normally despite first failing
            assertThat(goodJob.getStatus()).isEqualTo(FaxJob.STATUS.SENT);
            assertThat(goodJob.getJobId()).isEqualTo(999L);
            verify(faxJobDao).merge(goodJob);
        }

        @Test
        @DisplayName("should not propagate IllegalStateException to caller")
        void shouldNotPropagateIllegalStateException() throws Exception {
            // Given: Single account with credential decryption failure
            FaxConfig badConfig = createActiveFaxConfig("5550001111");
            FaxJob badJob = createWaitingFaxJob(19, "bad_fax.pdf");

            when(faxConfigDao.findAll(null, null)).thenReturn(Collections.singletonList(badConfig));
            when(faxJobDao.getReadyToSendFaxes("5550001111")).thenReturn(Collections.singletonList(badJob));
            when(faxProviderClientFactory.getClient(any()))
                    .thenThrow(new IllegalStateException("Failed to decrypt faxPasswd"));

            // When/Then: Should not throw - exception is caught and logged
            faxSender.send();

            // Verify the fax jobs were queried (happens before getClient) but no fax was merged
            // (IllegalStateException prevents the finally block from running for that fax)
            verify(faxJobDao).getReadyToSendFaxes("5550001111");
        }
    }

    /**
     * Tests for the transient error detection pipeline: provider clients use
     * {@link FaxProviderException#isTransientNetworkCause(Throwable)} to set the transient
     * flag, and FaxSender uses {@link FaxProviderException#isTransient()} to decide retry.
     *
     * <p>These tests verify the full pipeline by constructing exceptions the way real provider
     * clients do (using the 3-arg constructor with isTransientNetworkCause), then checking
     * FaxSender's isTransientNetworkError via reflection.</p>
     */
    @Nested
    @DisplayName("isTransientNetworkError()")
    class IsTransientNetworkErrorTests {

        private boolean invokeIsTransientNetworkError(FaxProviderException e) throws Exception {
            Method method = FaxSender.class.getDeclaredMethod("isTransientNetworkError", FaxProviderException.class);
            method.setAccessible(true);
            return (boolean) method.invoke(faxSender, e);
        }

        @Test
        @DisplayName("should return true for ConnectException cause")
        void shouldReturnTrue_forConnectExceptionCause() throws Exception {
            ConnectException cause = new ConnectException("Connection refused");
            FaxProviderException ex = new FaxProviderException("fail", cause,
                    FaxProviderException.isTransientNetworkCause(cause));

            assertThat(invokeIsTransientNetworkError(ex)).isTrue();
        }

        @Test
        @DisplayName("should return true for SocketTimeoutException cause")
        void shouldReturnTrue_forSocketTimeoutExceptionCause() throws Exception {
            SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
            FaxProviderException ex = new FaxProviderException("fail", cause,
                    FaxProviderException.isTransientNetworkCause(cause));

            assertThat(invokeIsTransientNetworkError(ex)).isTrue();
        }

        @Test
        @DisplayName("should return true for UnknownHostException cause")
        void shouldReturnTrue_forUnknownHostExceptionCause() throws Exception {
            UnknownHostException cause = new UnknownHostException("fax.example.com");
            FaxProviderException ex = new FaxProviderException("fail", cause,
                    FaxProviderException.isTransientNetworkCause(cause));

            assertThat(invokeIsTransientNetworkError(ex)).isTrue();
        }

        @Test
        @DisplayName("should return true for NoRouteToHostException cause")
        void shouldReturnTrue_forNoRouteToHostExceptionCause() throws Exception {
            NoRouteToHostException cause = new NoRouteToHostException("No route");
            FaxProviderException ex = new FaxProviderException("fail", cause,
                    FaxProviderException.isTransientNetworkCause(cause));

            assertThat(invokeIsTransientNetworkError(ex)).isTrue();
        }

        @Test
        @DisplayName("should return true for nested transient error in cause chain")
        void shouldReturnTrue_forNestedTransientErrorInCauseChain() throws Exception {
            ConnectException connectEx = new ConnectException("Connection refused");
            RuntimeException wrapper = new RuntimeException("Wrapped", connectEx);
            FaxProviderException ex = new FaxProviderException("fail", wrapper,
                    FaxProviderException.isTransientNetworkCause(wrapper));

            assertThat(invokeIsTransientNetworkError(ex)).isTrue();
        }

        @Test
        @DisplayName("should return false for non-network exception cause")
        void shouldReturnFalse_forNonNetworkExceptionCause() throws Exception {
            IllegalStateException cause = new IllegalStateException("bad state");
            FaxProviderException ex = new FaxProviderException("fail", cause,
                    FaxProviderException.isTransientNetworkCause(cause));

            assertThat(invokeIsTransientNetworkError(ex)).isFalse();
        }

        @Test
        @DisplayName("should return false when no cause is present")
        void shouldReturnFalse_whenNoCauseIsPresent() throws Exception {
            FaxProviderException ex = new FaxProviderException("fail");

            assertThat(invokeIsTransientNetworkError(ex)).isFalse();
        }
    }

    /**
     * Tests for the resolveFilePath() private method, which validates and resolves
     * fax document file paths using PathValidationUtils.
     *
     * <p>These tests verify path traversal prevention, null/empty input handling,
     * and proper resolution of relative paths under a document directory. Uses
     * real temp directories and PathValidationUtils (no mocking) to ensure
     * security validation works end-to-end.</p>
     */
    @Nested
    @DisplayName("resolveFilePath()")
    @Tag("security")
    class ResolveFilePathTests {

        @TempDir
        Path tempDocumentDir;

        private Method resolveFilePathMethod;

        @BeforeEach
        void setUpReflection() throws Exception {
            resolveFilePathMethod = FaxSender.class.getDeclaredMethod(
                    "resolveFilePath", String.class, String.class);
            resolveFilePathMethod.setAccessible(true);
        }

        /**
         * Invokes resolveFilePath via reflection, unwrapping InvocationTargetException.
         */
        private Path invokeResolveFilePath(String filename, String documentDir) throws Throwable {
            try {
                return (Path) resolveFilePathMethod.invoke(faxSender, filename, documentDir);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when filename is null")
        void shouldThrowIllegalArgumentException_whenFilenameIsNull() {
            // Given
            String nullFilename = null;
            String documentDir = tempDocumentDir.toString();

            // When/Then
            assertThatThrownBy(() -> invokeResolveFilePath(nullFilename, documentDir))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when filename is empty")
        void shouldThrowIllegalArgumentException_whenFilenameIsEmpty() {
            // Given
            String emptyFilename = "   ";
            String documentDir = tempDocumentDir.toString();

            // When/Then
            assertThatThrownBy(() -> invokeResolveFilePath(emptyFilename, documentDir))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("null or empty");
        }

        @Test
        @DisplayName("should neutralize path traversal by stripping directory components")
        void shouldNeutralizePathTraversal_whenDirectoryComponentsPresent() throws Throwable {
            // Given: A path traversal attack filename - PathValidationUtils.sanitizeFileName
            // strips directory components via FilenameUtils.getName(), neutralizing the attack
            // by resolving only the base filename "passwd" safely under the document dir
            String traversalFilename = "../../../etc/passwd";
            String documentDir = tempDocumentDir.toString();

            // When: resolveFilePath sanitizes the path
            Path resolved = invokeResolveFilePath(traversalFilename, documentDir);

            // Then: Path traversal is neutralized - result is safely under document dir
            assertThat(resolved).isNotNull();
            assertThat(resolved.getFileName().toString()).isEqualTo("passwd");
            assertThat(resolved.toFile().getCanonicalPath())
                    .startsWith(tempDocumentDir.toFile().getCanonicalPath());
        }

        @Test
        @DisplayName("should throw SecurityException when hidden file path attempted")
        void shouldThrowSecurityException_whenHiddenFilePathAttempted() {
            // Given: A hidden file (starts with .) - PathValidationUtils rejects these
            String hiddenFilename = ".hidden_config";
            String documentDir = tempDocumentDir.toString();

            // When/Then: PathValidationUtils should reject hidden files
            assertThatThrownBy(() -> invokeResolveFilePath(hiddenFilename, documentDir))
                    .isInstanceOf(SecurityException.class);
        }

        @Test
        @DisplayName("should resolve valid relative path when under document dir")
        void shouldResolveValidRelativePath_whenUnderDocumentDir() throws Throwable {
            // Given: Create an actual file in the temp document directory
            String validFilename = "fax_document.pdf";
            Files.createFile(tempDocumentDir.resolve(validFilename));
            String documentDir = tempDocumentDir.toString();

            // When
            Path resolved = invokeResolveFilePath(validFilename, documentDir);

            // Then: Should resolve to a path under the document directory
            assertThat(resolved).isNotNull();
            assertThat(resolved.toString()).contains(validFilename);
            assertThat(resolved.toFile().getCanonicalPath())
                    .startsWith(tempDocumentDir.toFile().getCanonicalPath());
        }

        @Test
        @DisplayName("should throw SecurityException when absolute path outside document dir")
        void shouldThrowSecurityException_whenAbsolutePathOutsideDocumentDir() {
            // Given: An absolute path that is outside the document directory
            String absolutePath = "/etc/shadow";
            String documentDir = tempDocumentDir.toString();

            // When/Then: Should reject absolute paths outside document dir
            // (and /etc/shadow is not in any allowed temp directory either)
            assertThatThrownBy(() -> invokeResolveFilePath(absolutePath, documentDir))
                    .isInstanceOf(SecurityException.class);
        }
    }
}
