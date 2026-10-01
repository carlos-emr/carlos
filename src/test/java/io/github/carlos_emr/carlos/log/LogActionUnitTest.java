package io.github.carlos_emr.carlos.log;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

@Tag("unit")
class LogActionUnitTest {

    @AfterEach
    void tearDown() {
        LogAction.setOscarLogDaoForTesting(null);
        LogAction.resetExecutorServiceForTesting();
        Thread.interrupted();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Tag("create")
    void shouldRecordContentIdAndPatient_whenLoggingSynchronously(boolean requireAudit) {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        Security security = new Security();
        security.setSecurityNo(99);
        when(loggedInInfo.getLoggedInSecurity()).thenReturn(security);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(mock(Provider.class));
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getIp()).thenReturn("127.0.0.1");
        if (requireAudit) LogAction.addLogSynchronousOrThrow(loggedInInfo, "retire", "consent", "11", 100, "data");
        else LogAction.addLogSynchronous(loggedInInfo, "retire", "consent", "11", 100, "data");

        verify(oscarLogDao).persist(argThat((OscarLog log) ->
                "retire".equals(log.getAction())
                        && Integer.valueOf(99).equals(log.getSecurityId())
                        && "999998".equals(log.getProviderNo())
                        && "consent".equals(log.getContent())
                        && "11".equals(log.getContentId())
                        && Integer.valueOf(100).equals(log.getDemographicId())
                        && "127.0.0.1".equals(log.getIp())
                        && "data".equals(log.getData())));
    }

    @Test
    @Tag("create")
    void shouldPropagateAuditFailure_whenCallerRequiresAudit() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        IllegalStateException failure = new IllegalStateException("synthetic audit failure");
        doThrow(failure).when(oscarLogDao).persist(any(OscarLog.class));

        assertThatThrownBy(() -> LogAction.addLogSynchronousOrThrow(mock(LoggedInInfo.class),
                "change", "consent", "11", 100, "data"))
                .isSameAs(failure);
    }

    @Test
    @Tag("create")
    void shouldKeepLegacyFailureHandling_whenLoggingSynchronously() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LogAction.setOscarLogDaoForTesting(oscarLogDao);
        doThrow(new IllegalStateException("synthetic audit failure")).when(oscarLogDao).persist(any(OscarLog.class));

        assertThatCode(() -> LogAction.addLogSynchronous(mock(LoggedInInfo.class),
                "change", "consent", "11", 100, "data"))
                .doesNotThrowAnyException();
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
