package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

@Tag("unit")
class DbConnectionFilterUnitTest extends CarlosUnitTestBase {

    @Test
    @Tag("delete")
    void shouldReleaseLegacyJdbcQueryResources_whenReleasingThreadResources() {
        try (MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class)) {
            DbConnectionFilter.releaseAllThreadDbResources();

            legacyJdbcQuery.verify(LegacyJdbcQuery::releaseThreadResources);
        }
    }

    @Test
    @Tag("delete")
    void shouldContinueThreadCleanup_whenLegacyJdbcQueryCleanupFails() {
        try (MockedStatic<LegacyJdbcQuery> legacyJdbcQuery = mockStatic(LegacyJdbcQuery.class);
             MockedStatic<OscarTrackingBasicDataSource> trackingDataSource =
                     mockStatic(OscarTrackingBasicDataSource.class)) {
            legacyJdbcQuery.when(LegacyJdbcQuery::releaseThreadResources)
                    .thenThrow(new IllegalStateException("cleanup failure"));

            DbConnectionFilter.releaseAllThreadDbResources();

            legacyJdbcQuery.verify(LegacyJdbcQuery::releaseThreadResources);
            trackingDataSource.verify(OscarTrackingBasicDataSource::releaseThreadConnections);
        }
    }

    @Test
    @DisplayName("should rethrow a client abort unwrapped and without an ERROR")
    void shouldRethrowUnwrapped_whenClientAborts() {
        // #4438: a closed browser tab is not a server error, and ResponseSanitizationFilter logs it once.
        ClientAbortException abort = new ClientAbortException("Broken pipe");
        FilterChain chain = (req, res) -> {
            throw abort;
        };

        try (MockedStatic<LegacyJdbcQuery> ignored = mockStatic(LegacyJdbcQuery.class);
             LogCapture capture = LogCapture.forLogger(DbConnectionFilter.class)) {
            assertThatThrownBy(() -> new DbConnectionFilter().doFilter(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                    .isSameAs(abort);
            assertThat(capture.events()).noneMatch(event -> event.getLevel() == Level.ERROR);
        }
    }

    @Test
    @DisplayName("should still log and wrap an unexpected failure")
    void shouldLogAndWrap_whenChainFailsUnexpectedly() {
        IllegalStateException failure = new IllegalStateException("boom");
        FilterChain chain = (req, res) -> {
            throw failure;
        };

        try (MockedStatic<LegacyJdbcQuery> ignored = mockStatic(LegacyJdbcQuery.class);
             LogCapture capture = LogCapture.forLogger(DbConnectionFilter.class)) {
            assertThatThrownBy(() -> new DbConnectionFilter().doFilter(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), chain))
                    .isInstanceOf(ServletException.class)
                    .hasCause(failure);
            assertThat(capture.events()).anyMatch(event -> event.getLevel() == Level.ERROR);
        }
    }
}
