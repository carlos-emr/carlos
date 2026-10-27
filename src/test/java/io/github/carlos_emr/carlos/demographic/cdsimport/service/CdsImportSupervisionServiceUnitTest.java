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

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** A real bounded worker pool proves fatal reviews cannot enter an automatic retry loop. */
@Tag("unit")
class CdsImportSupervisionServiceUnitTest {
    private CdsImportReviewService reviews;
    private CdsImportService service;
    private ThreadPoolExecutor worker;

    @BeforeEach void setUp() throws Exception {
        reviews=mock(CdsImportReviewService.class);
        var ledger=mock(CdsImportLedgerService.class);
        var configuration=mock(CdsImportConfigurationService.class);
        var cleanup=mock(CdsImportCleanupService.class);
        when(configuration.enabled()).thenReturn(true);
        when(cleanup.housekeeping()).thenReturn(Map.of("state", "COMPLETE", "fullPassComplete", true));
        when(ledger.pending("", 16)).thenReturn(List.of());
        when(ledger.summary("run")).thenReturn(Map.of());
        when(reviews.summary("run")).thenReturn(Map.of());
        service=new CdsImportService(ledger, mock(CdsImportJobService.class), mock(CdsImportExecutionService.class),
                mock(CdsImportResultService.class), mock(CdsImportAccessService.class), configuration, cleanup,
                reviews, mock(CdsImportFailureEvidenceService.class));
        var field=CdsImportService.class.getDeclaredField("worker");field.setAccessible(true);
        worker=(ThreadPoolExecutor)field.get(service);
    }
    @AfterEach void close() {service.close();}

    @Test void fatalReviewStaysSuspendedUntilHealthySupervisorPersistsIt() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        verify(reviews, times(1)).process("failed");
        call("dispatch", new Class<?>[]{});
        verify(reviews).superviseFatal("failed");
        // The real DAO excludes suspended rows. A different accepted review may proceed after supervision.
        when(reviews.pending(16)).thenReturn(List.of("other"));
        call("dispatch", new Class<?>[]{});barrier();
        verify(reviews).process("other");verify(reviews, times(1)).process("failed");
    }
    @Test void unavailableFatalPersistenceKeepsWorkerSuspended() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        doThrow(new IOException("synthetic unavailable metadata")).doNothing().when(reviews).superviseFatal("failed");
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("dispatch", new Class<?>[]{});
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        verify(reviews, times(1)).process("failed");
        call("dispatch", new Class<?>[]{});verify(reviews, times(2)).superviseFatal("failed");
    }
    @Test void fatalSupervisorBecomesVisibleAndStopsAutomaticWork() throws Exception {
        when(reviews.process("failed")).thenThrow(new AssertionError("synthetic private failure"));
        doThrow(new AssertionError("synthetic supervisor resource failure")).when(reviews).superviseFatal("failed");
        call("scheduleReview", new Class<?>[]{String.class}, "failed");barrier();
        call("dispatch", new Class<?>[]{});call("dispatch", new Class<?>[]{});
        verify(reviews, times(1)).superviseFatal("failed");
        var summary=(Map<?, ?>)call("operatorSummary", new Class<?>[]{String.class}, "run");
        assertThat(summary.get("workerAlert")).isEqualTo("IMPORT_SUPERVISOR_FATAL");
    }
    private void barrier() throws Exception {worker.submit(()->{}).get(5, TimeUnit.SECONDS);}
    private Object call(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method=CdsImportService.class.getDeclaredMethod(name, parameterTypes);method.setAccessible(true);
        return method.invoke(service, arguments);
    }
}
