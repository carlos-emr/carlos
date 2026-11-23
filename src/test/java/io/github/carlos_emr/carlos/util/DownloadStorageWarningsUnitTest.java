/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.util;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.Mockito.*;

@Tag("unit")
class DownloadStorageWarningsUnitTest {
    @TempDir Path directory;

    @Test
    void shouldSuppressMissingBasenameDiagnostics_whenTheDirectoryIsAccessible() {
        var logger = mock(Logger.class);
        new DownloadStorageWarnings(logger, () -> 0).report(directory.toFile());
        DownloadStorageWarnings.reportIfUnavailable(directory.toFile());
        verifyNoInteractions(logger);
    }

    @Test
    void shouldBoundWarnings_withoutLoggingDirectoryNames() {
        var logger = mock(Logger.class);
        var now = new AtomicLong();
        var warnings = new DownloadStorageWarnings(logger, now::get);
        var unavailable = directory.resolve("FAKE private directory").toFile();
        warnings.report(unavailable);
        warnings.report(unavailable);
        now.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(59));
        warnings.report(unavailable);
        verify(logger).warn("The configured download directory is unavailable to the service. Check storage permissions and service filesystem restrictions.");
        now.set(java.util.concurrent.TimeUnit.MINUTES.toNanos(1));
        warnings.report(unavailable);
        verify(logger, times(2)).warn("The configured download directory is unavailable to the service. Check storage permissions and service filesystem restrictions.");
        verifyNoMoreInteractions(logger);
    }

    @Test
    void shouldWarnForUnreadableOrUnsearchableDirectories() {
        for (boolean readable : new boolean[]{false, true}) {
            var file = mock(File.class);
            when(file.isDirectory()).thenReturn(true);
            when(file.canRead()).thenReturn(readable);
            when(file.canExecute()).thenReturn(false);
            var logger = mock(Logger.class);
            new DownloadStorageWarnings(logger, () -> 0).report(file);
            verify(logger).warn(anyString());
        }
    }
}
