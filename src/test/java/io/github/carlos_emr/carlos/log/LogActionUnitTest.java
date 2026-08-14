package io.github.carlos_emr.carlos.log;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import static org.mockito.ArgumentMatchers.argThat;
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

    @Test
    @Tag("create")
    void shouldPersistFullyAttributedAuditSynchronously() {
        OscarLogDao oscarLogDao = mock(OscarLogDao.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        Security security = mock(Security.class);
        when(security.getSecurityNo()).thenReturn(17);
        when(loggedInInfo.getLoggedInSecurity()).thenReturn(security);
        when(loggedInInfo.getLoggedInProvider()).thenReturn(mock(Provider.class));
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(loggedInInfo.getIp()).thenReturn("127.0.0.1");
        LogAction.setOscarLogDaoForTesting(oscarLogDao);

        LogAction.addLogSynchronous(
                loggedInInfo, "integrityFailure", "archive", "archiveId=888", "123", "fixed reason");

        verify(oscarLogDao).persist(argThat((OscarLog log) ->
                Integer.valueOf(17).equals(log.getSecurityId())
                        && "999998".equals(log.getProviderNo())
                        && "integrityFailure".equals(log.getAction())
                        && "archive".equals(log.getContent())
                        && "archiveId=888".equals(log.getContentId())
                        && "127.0.0.1".equals(log.getIp())
                        && Integer.valueOf(123).equals(log.getDemographicId())
                        && "fixed reason".equals(log.getData())));
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
