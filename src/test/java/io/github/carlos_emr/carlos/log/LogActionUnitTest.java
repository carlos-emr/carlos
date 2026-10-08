package io.github.carlos_emr.carlos.log;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class LogActionUnitTest {

    @AfterEach
    void tearDown() {
        LogAction.setOscarLogDaoForTesting(null);
        LogAction.resetExecutorServiceForTesting();
        Thread.interrupted();
    }

    @Test
    @Tag("create")
    void shouldPersistSynchronously_whenExecutorRejectsAuditTask() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());

        LogAction.addLog("999998", "view", "document", "123", "127.0.0.1", null, "data");

        verify(oscarLogDao).persist(argThat((OscarLog log) ->
                "999998".equals(log.getProviderNo())
                        && "view".equals(log.getAction())
                        && "document".equals(log.getContent())
                        && "123".equals(log.getContentId())));
    }

    private static final String NOTE_TEXT_WITH_PHI = "Jane Roe reports chest pain\nIssues\nHypertension\n";

    /**
     * The unparsable value must reach neither a message nor an attached exception (a
     * {@code NumberFormatException} message is the input itself); the rejected-executor warning of the
     * synchronous test path is unrelated and carries no input.
     */
    private static void assertThatNothingEchoesThePhi(LogCapture capture) {
        assertThat(capture.events()).anyMatch(event -> event.getLevel() == Level.ERROR);
        assertThat(capture.events()).noneMatch(event -> event.getLevel() == Level.ERROR && event.getThrown() != null);
        assertThat(capture.events()).noneMatch(event -> event.getMessage().getFormattedMessage().contains("Jane Roe")
                || event.getMessage().getFormattedMessage().contains("chest pain")
                || (event.getThrown() != null && String.valueOf(event.getThrown().getMessage()).contains("chest pain")));
    }

    @Test
    @Tag("create")
    void shouldSaveRowWithoutPatientAndNotLogInput_whenDemographicNoIsNotNumeric() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());

        try (LogCapture capture = LogCapture.forLogger(LogAction.class)) {
            LogAction.addLog("999998", "read", "note", "501", "127.0.0.1", NOTE_TEXT_WITH_PHI);

            verify(oscarLogDao).persist(argThat((OscarLog log) ->
                    log.getDemographicId() == null && "501".equals(log.getContentId())));
            assertThatNothingEchoesThePhi(capture);
        }
    }

    @Test
    @Tag("create")
    void shouldSaveRowWithoutPatientAndNotLogInput_whenLoggedInInfoDemographicNoIsNotNumeric() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());
        LoggedInInfo info = mock(LoggedInInfo.class);

        try (LogCapture capture = LogCapture.forLogger(LogAction.class)) {
            LogAction.addLog(info, "read", "note", "501", NOTE_TEXT_WITH_PHI, "data");

            verify(oscarLogDao).persist(argThat((OscarLog log) ->
                    log.getDemographicId() == null && "data".equals(log.getData())));
            assertThatNothingEchoesThePhi(capture);
        }
    }

    @Test
    @Tag("create")
    void shouldRecordPatientAndData_whenDemographicNoIsNumericWithWhitespace() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());

        try (LogCapture capture = LogCapture.forLogger(LogAction.class)) {
            LogAction.addLog("999998", "read", "note", "501", "127.0.0.1", " 101 ", "note body");

            verify(oscarLogDao).persist(argThat((OscarLog log) ->
                    Integer.valueOf(101).equals(log.getDemographicId()) && "note body".equals(log.getData())));
            assertThat(capture.events()).noneMatch(event -> event.getLevel() == Level.ERROR);
        }
    }

    @Test
    @Tag("create")
    void shouldLeavePatientUnset_whenDemographicNoIsBlank() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());

        try (LogCapture capture = LogCapture.forLogger(LogAction.class)) {
            LogAction.addLog("999998", "read", "note", "501", "127.0.0.1", "  ", "data");

            verify(oscarLogDao).persist(argThat((OscarLog log) -> log.getDemographicId() == null));
            assertThat(capture.events()).noneMatch(event -> event.getLevel() == Level.ERROR);
        }
    }

    @Test
    @Tag("create")
    void shouldPropagatePersistenceFailure_fromStrictAudit() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        RuntimeException failure = new IllegalStateException("synthetic persistence failure");
        doThrow(failure).when(oscarLogDao).persist(any());
        LogAction.setOscarLogDaoForTesting(oscarLogDao);

        assertThatThrownBy(() -> LogAction.addLogStrict(loggedInInfo(), "read", "LabEmbeddedDocument", "456",
                "123", "segment=1,group=0,disposition=inline")).isSameAs(failure);

        verify(oscarLogDao).persist(argThat((OscarLog log) ->
                "999998".equals(log.getProviderNo())
                        && "read".equals(log.getAction())
                        && "LabEmbeddedDocument".equals(log.getContent())
                        && "456".equals(log.getContentId())
                        && Integer.valueOf(123).equals(log.getDemographicId())
                        && "segment=1,group=0,disposition=inline".equals(log.getData())));
    }

    @Test
    @Tag("create")
    void shouldSwallowPersistenceFailure_fromBestEffortAuditFallback() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        doThrow(new IllegalStateException("synthetic persistence failure")).when(oscarLogDao).persist(any());
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        // Saturated executor: the best-effort addLog falls back to a synchronous persist.
        LogAction.setExecutorServiceForTesting(new RejectingExecutorService());

        assertThatCode(() -> LogAction.addLog(loggedInInfo(), "read", "LabEmbeddedDocument", "456", "123", "data"))
                .doesNotThrowAnyException();

        verify(oscarLogDao).persist(any());
    }

    private static LoggedInInfo loggedInInfo() {
        LoggedInInfo info = mock(LoggedInInfo.class);
        Provider provider = mock(Provider.class);
        when(info.getLoggedInProvider()).thenReturn(provider);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(info.getIp()).thenReturn("127.0.0.1");
        return info;
    }

    @Test
    @Tag("delete")
    void shouldShutdownExecutor_whenAlreadyTerminated() {
        ControllableExecutorService executor = new ControllableExecutorService(true, List.of());
        LogAction.setExecutorServiceForTesting(executor);

        LogAction.shutdownExecutorService();

        org.assertj.core.api.Assertions.assertThat(executor.shutdownCalled).isTrue();
        org.assertj.core.api.Assertions.assertThat(executor.shutdownNowCalled).isFalse();
    }

    @Test
    @Tag("delete")
    void shouldCallShutdownNow_whenAwaitTerminationTimesOut() {
        Runnable droppedTask = () -> { };
        ControllableExecutorService executor = new ControllableExecutorService(false, List.of(droppedTask));
        LogAction.setExecutorServiceForTesting(executor);

        LogAction.shutdownExecutorService();

        org.assertj.core.api.Assertions.assertThat(executor.shutdownCalled).isTrue();
        org.assertj.core.api.Assertions.assertThat(executor.shutdownNowCalled).isTrue();
    }

    @Test
    @Tag("delete")
    void shouldCallShutdownNowAndRestoreInterrupt_whenAwaitTerminationIsInterrupted() {
        ControllableExecutorService executor = new ControllableExecutorService(false, List.of(), true);
        LogAction.setExecutorServiceForTesting(executor);

        LogAction.shutdownExecutorService();

        org.assertj.core.api.Assertions.assertThat(executor.shutdownNowCalled).isTrue();
        org.assertj.core.api.Assertions.assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private static final class RejectingExecutorService extends ControllableExecutorService {
        private RejectingExecutorService() {
            super(true, List.of());
        }

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("closed");
        }
    }

    private static class ControllableExecutorService extends AbstractExecutorService {
        private final boolean terminatedAfterShutdown;
        private final List<Runnable> droppedTasks;
        private final boolean interruptDuringAwait;
        private boolean shutdownCalled;
        private boolean shutdownNowCalled;

        private ControllableExecutorService(boolean terminatedAfterShutdown, List<Runnable> droppedTasks) {
            this(terminatedAfterShutdown, droppedTasks, false);
        }

        private ControllableExecutorService(boolean terminatedAfterShutdown, List<Runnable> droppedTasks,
                                           boolean interruptDuringAwait) {
            this.terminatedAfterShutdown = terminatedAfterShutdown;
            this.droppedTasks = droppedTasks;
            this.interruptDuringAwait = interruptDuringAwait;
        }

        @Override
        public void shutdown() {
            shutdownCalled = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdownNowCalled = true;
            return droppedTasks;
        }

        @Override
        public boolean isShutdown() {
            return shutdownCalled;
        }

        @Override
        public boolean isTerminated() {
            return shutdownCalled && terminatedAfterShutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            if (interruptDuringAwait) {
                throw new InterruptedException("interrupted");
            }
            return terminatedAfterShutdown;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
