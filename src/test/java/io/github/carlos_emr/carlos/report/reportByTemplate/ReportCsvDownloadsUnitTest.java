/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The server-side store of Report by Template CSV results: the result page carries only a token, and the
 * CSV and XLSX downloads are built from what the server kept for that session and provider.
 */
@DisplayName("Report by Template CSV downloads kept on the server")
@Tag("unit")
@Tag("report")
class ReportCsvDownloadsUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER = "999998";
    private static final int LIMIT = 10;
    private static final long START = 1_000_000_000L;

    /** A store of this test's own, so other tests' results never count against its bounds. */
    private final ReportCsvDownloads.Store store = new ReportCsvDownloads.Store(Long.MAX_VALUE);
    private final MockHttpSession session = new MockHttpSession();

    private ReportCsvDownloads.Kept keep(String... csvs) {
        return keep(START, csvs);
    }

    private ReportCsvDownloads.Kept keep(long now, String... csvs) {
        return ReportCsvDownloads.keep(store, session, PROVIDER, Arrays.asList(csvs), LIMIT, now);
    }

    private String find(String token, String index) {
        return text(ReportCsvDownloads.find(session, PROVIDER, token, index, START));
    }

    private static String text(Optional<ReportCsvDownloads.Csv> csv) {
        return csv.map(ReportCsvDownloads.Csv::text).orElse(null);
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        @Test
        @DisplayName("should return each kept query's CSV to the session and provider that ran the report")
        void shouldReturnKeptCsv_forTheSessionAndProviderThatRanTheReport() {
            ReportCsvDownloads.Kept kept = keep("a,b\r\n", "c\r\n");

            assertThat(kept.token()).isNotNull();
            assertThat(kept.downloadable()).containsExactly(true, true);
            assertThat(find(kept.token(), "0")).isEqualTo("a,b\r\n");
            assertThat(find(kept.token(), "1")).isEqualTo("c\r\n");
            // A request that names no query asks for the first (a single-query report).
            assertThat(find(kept.token(), null)).isEqualTo("a,b\r\n");
        }

        @Test
        @DisplayName("should find nothing for another session holding the same token")
        void shouldFindNothing_forAnotherSession() {
            String token = keep("a\r\n").token();

            MockHttpSession other = new MockHttpSession();
            assertThat(ReportCsvDownloads.find(other, PROVIDER, token, "0", START)).isEmpty();
            assertThat(ReportCsvDownloads.find(null, PROVIDER, token, "0", START)).isEmpty();
        }

        @Test
        @DisplayName("should find nothing for another provider signed in to the same session")
        void shouldFindNothing_forAnotherProvider() {
            String token = keep("a\r\n").token();

            assertThat(ReportCsvDownloads.find(session, "100", token, "0", START)).isEmpty();
            assertThat(ReportCsvDownloads.find(session, null, token, "0", START)).isEmpty();
        }

        @Test
        @DisplayName("should find nothing for a missing, stale, altered or oversized token")
        void shouldFindNothing_forUnknownTokens() {
            String token = keep("a\r\n").token();
            char last = token.charAt(token.length() - 1);
            String altered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

            assertThat(find(null, "0")).isNull();
            assertThat(find("", "0")).isNull();
            assertThat(find("stale", "0")).isNull();
            assertThat(find(altered, "0")).isNull();
            assertThat(find(token + token, "0")).isNull();
            assertThat(find("x".repeat(100_000), "0")).isNull();
        }

        @Test
        @DisplayName("should find nothing for a query index that is out of range or not a number")
        void shouldFindNothing_forBadQueryIndex() {
            String token = keep("a\r\n").token();

            for (String index : new String[] {"1", "-1", "x", "", " 0", "99999999999999999999"}) {
                assertThat(find(token, index)).as(index).isNull();
            }
        }

        @Test
        @DisplayName("should expire a result thirty minutes after the report ran")
        void shouldExpireResult_afterThirtyMinutes() {
            String token = keep("a\r\n").token();
            long lifetime = TimeUnit.MINUTES.toNanos(30);

            assertThat(text(ReportCsvDownloads.find(session, PROVIDER, token, "0", START + lifetime - 1))).isEqualTo("a\r\n");
            assertThat(ReportCsvDownloads.find(session, PROVIDER, token, "0", START + lifetime)).isEmpty();
            // Expired results are dropped, not merely hidden.
            assertThat(ReportCsvDownloads.find(session, PROVIDER, token, "0", START)).isEmpty();
        }
    }

    @Nested
    @DisplayName("memory bounds")
    class Bounds {

        @Test
        @DisplayName("should keep only the three most recent results of a session")
        void shouldKeepOnlyTheThreeMostRecentResults_perSession() {
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tokens.add(keep(START + i, "r" + i + "\r\n").token());
            }

            assertThat(find(tokens.get(0), "0")).isNull();
            assertThat(find(tokens.get(1), "0")).isEqualTo("r1\r\n");
            assertThat(find(tokens.get(2), "0")).isEqualTo("r2\r\n");
            assertThat(find(tokens.get(3), "0")).isEqualTo("r3\r\n");
        }

        @Test
        @DisplayName("should drop older results first to keep a session within three times the CSV limit")
        void shouldDropOlderResults_whenTheSessionWouldHoldMoreThanThreeTimesTheLimit() {
            String first = keep(START, "0123456789").token();
            String second = keep(START + 1, "0123456789").token();
            // A sequence of two full queries: 40 bytes would exceed 3 x 10, so the oldest result goes.
            String sequence = keep(START + 2, "0123456789", "0123456789").token();

            assertThat(find(first, "0")).isNull();
            assertThat(find(second, "0")).isEqualTo("0123456789");
            assertThat(find(sequence, "1")).isEqualTo("0123456789");
        }

        @Test
        @DisplayName("should keep the newest result whole even when it alone is over the session budget")
        void shouldKeepNewestResultWhole_whenItAloneExceedsTheSessionBudget() {
            String older = keep(START, "a").token();
            String sequence = keep(START + 1, "0123456789", "0123456789", "0123456789", "0123456789").token();

            assertThat(find(older, "0")).isNull();
            for (int query = 0; query < 4; query++) {
                assertThat(find(sequence, Integer.toString(query))).isEqualTo("0123456789");
            }
        }

        @Test
        @DisplayName("should hold back only a query over the CSV limit, measured in UTF-8 bytes")
        void shouldHoldBackOnlyTheQuery_overTheLimit() {
            // Ten characters but twenty UTF-8 bytes.
            ReportCsvDownloads.Kept kept = keep("é".repeat(10), "0123456789", "", null);

            assertThat(kept.withheld()).containsExactly(true, false, false, false);
            assertThat(kept.downloadable()).containsExactly(false, true, false, false);
            assertThat(find(kept.token(), "0")).isNull();
            assertThat(find(kept.token(), "1")).isEqualTo("0123456789");
            assertThat(find(kept.token(), "2")).isNull();
        }

        @Test
        @DisplayName("should keep nothing and issue no token when no query has a CSV")
        void shouldKeepNothing_whenNoQueryHasCsv() {
            ReportCsvDownloads.Kept kept = keep("", null);

            assertThat(kept.token()).isNull();
            assertThat(kept.downloadable()).containsExactly(false, false);
            assertThat(session.getAttribute(ReportCsvDownloads.SESSION_ATTRIBUTE)).isNull();
        }

        @Test
        @DisplayName("should keep results in memory only, in a session value that is never serialized")
        void shouldNeverOfferResultsForSerialization_whenStoredInTheSession() {
            keep("a\r\n");

            Object stored = session.getAttribute(ReportCsvDownloads.SESSION_ATTRIBUTE);
            assertThat(stored).isNotNull().isNotInstanceOf(Serializable.class);
        }
    }

    @Nested
    @DisplayName("bounds across sessions")
    class ProcessBounds {

        private final ReportCsvDownloads.Store small = new ReportCsvDownloads.Store(25);

        private String keepIn(MockHttpSession owner, long now, String... csvs) {
            return ReportCsvDownloads.keep(small, owner, PROVIDER, Arrays.asList(csvs), LIMIT, now).token();
        }

        private String findIn(MockHttpSession owner, String token, long now) {
            return text(ReportCsvDownloads.find(owner, PROVIDER, token, "0", now));
        }

        @Test
        @DisplayName("should drop the oldest result of any session when all sessions would hold more than the store allows")
        void shouldDropTheOldestResultOfAnySession_whenTheStoreIsFull() {
            MockHttpSession first = new MockHttpSession();
            MockHttpSession second = new MockHttpSession();
            String oldest = keepIn(first, START, "0123456789");
            String older = keepIn(second, START + 1, "0123456789");

            String newest = keepIn(second, START + 2, "0123456789");

            assertThat(findIn(first, oldest, START + 2)).isNull();
            assertThat(findIn(second, older, START + 2)).isEqualTo("0123456789");
            assertThat(findIn(second, newest, START + 2)).isEqualTo("0123456789");
            assertThat(small.bytes()).isEqualTo(20);
        }

        @Test
        @DisplayName("should keep the newest result whole even when it alone is larger than the store")
        void shouldKeepTheNewestResultWhole_whenItAloneExceedsTheStore() {
            MockHttpSession first = new MockHttpSession();
            String older = keepIn(first, START, "0123456789");

            String sequence = keepIn(new MockHttpSession(), START + 1, "0123456789", "0123456789", "0123456789");

            assertThat(findIn(first, older, START + 1)).isNull();
            assertThat(small.bytes()).isEqualTo(30);
            assertThat(sequence).isNotNull();
        }

        @Test
        @DisplayName("should drop every session's expired results whenever any session keeps or finds one")
        void shouldDropExpiredResultsOfEverySession_onAnyKeepOrFind() {
            MockHttpSession idle = new MockHttpSession();
            keepIn(idle, START, "0123456789");
            long lifetime = TimeUnit.MINUTES.toNanos(ReportCsvDownloads.LIFETIME_MINUTES);

            MockHttpSession active = new MockHttpSession();
            String fresh = keepIn(active, START + lifetime - 1, "abc");
            assertThat(small.bytes()).isEqualTo(13);

            assertThat(findIn(active, fresh, START + lifetime)).isEqualTo("abc");
            assertThat(small.bytes()).as("the idle session's result expired without that session doing anything")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("should release a session's results as soon as the session ends")
        void shouldReleaseResults_whenTheSessionEnds() {
            MockHttpSession ending = new MockHttpSession();
            String token = keepIn(ending, START, "0123456789");
            keepIn(new MockHttpSession(), START, "abc");

            ending.invalidate();

            assertThat(small.bytes()).isEqualTo(3);
            assertThat(token).isNotNull();
        }

        @Test
        @DisplayName("should drop the result whose report ran first, not the one kept first")
        void shouldDropTheResultWhoseReportRanFirst_whenResultsArriveOutOfOrder() {
            MockHttpSession owner = new MockHttpSession();
            ReportCsvDownloads.Store roomy = new ReportCsvDownloads.Store(Long.MAX_VALUE);
            List<String> tokens = new ArrayList<>();
            long[] ranAt = {START + 20, START + 30, START + 10, START + 40};
            for (int run = 0; run < ranAt.length; run++) {
                tokens.add(ReportCsvDownloads.keep(roomy, owner, PROVIDER, List.of("r" + run), LIMIT, ranAt[run]).token());
            }

            // Run 2 was kept third but ran first, so it is the one dropped for the fourth.
            assertThat(text(ReportCsvDownloads.find(owner, PROVIDER, tokens.get(2), "0", START + 40))).isNull();
            assertThat(text(ReportCsvDownloads.find(owner, PROVIDER, tokens.get(0), "0", START + 40))).isEqualTo("r0");
            assertThat(text(ReportCsvDownloads.find(owner, PROVIDER, tokens.get(1), "0", START + 40))).isEqualTo("r1");
            assertThat(text(ReportCsvDownloads.find(owner, PROVIDER, tokens.get(3), "0", START + 40))).isEqualTo("r3");
        }

        @Test
        @DisplayName("should give all sessions an eighth of the maximum heap, or 256 MiB when the JVM names none")
        void shouldShareAnEighthOfTheHeap_acrossAllSessions() {
            assertThat(ReportCsvDownloads.heapBudget(2L * 1024 * 1024 * 1024)).isEqualTo(256L * 1024 * 1024);
            assertThat(ReportCsvDownloads.heapBudget(Long.MAX_VALUE)).isEqualTo(256L * 1024 * 1024);
        }
    }

    @Test
    @DisplayName("should count UTF-8 bytes exactly as String.getBytes encodes them, and stop once over the limit")
    void shouldCountUtf8Bytes_asGetBytesEncodesThem() {
        for (String csv : new String[] {"", "abc", "é", "１２", "\uD83D\uDE00", "a\uD83Db", "\uDE00", "x\uD83D", "Zoë,\"q\"\r\n"}) {
            assertThat(ReportCsvDownloads.utf8Length(csv, Long.MAX_VALUE)).as(csv)
                    .isEqualTo(csv.getBytes(StandardCharsets.UTF_8).length);
        }
        assertThat(ReportCsvDownloads.utf8Length("é".repeat(5), 10)).isEqualTo(10);
        assertThat(ReportCsvDownloads.utf8Length("é".repeat(6), 10)).isEqualTo(-1);
        assertThat(ReportCsvDownloads.utf8Length("a".repeat(11), 10)).isEqualTo(-1);
    }

    @Test
    @DisplayName("should issue a distinct 256-bit hex token for every result")
    void shouldIssueUnguessableTokens_forEveryResult() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String token = ReportCsvDownloads.keep(store, new MockHttpSession(), PROVIDER, List.of("a"), LIMIT, START).token();
            assertThat(token).matches("[0-9a-f]{64}");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1_000);
    }

    @Nested
    @DisplayName("offering a report's downloads")
    class Offer {

        private final MockHttpServletRequest request = new MockHttpServletRequest();

        private ReportObject report(boolean sequence) {
            ReportObject report = mock(ReportObject.class);
            when(report.isSequence()).thenReturn(sequence);
            return report;
        }

        @Test
        @DisplayName("should publish only the token and take the CSV off the request")
        void shouldPublishTokenAndRemoveCsv_forSingleQueryReport() {
            request.setAttribute("reportobject", report(false));
            request.setAttribute("csv", "name\r\nsynthetic\r\n");

            ReportCsvDownloads.offer(request, PROVIDER);

            String token = (String) request.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE);
            assertThat(token).matches("[0-9a-f]{64}");
            assertThat(request.getAttribute(ReportCsvDownloads.DOWNLOADS_ATTRIBUTE)).isEqualTo(List.of(true));
            assertThat(request.getAttribute("csv")).isNull();
            assertThat(text(ReportCsvDownloads.find(request.getSession(false), PROVIDER, token, "0")))
                    .isEqualTo("name\r\nsynthetic\r\n");
        }

        @Test
        @DisplayName("should offer a download for each query of a sequence that has a CSV")
        void shouldOfferEachQueryWithCsv_forSequencedReport() {
            request.setAttribute("reportobject", report(true));
            request.setAttribute("sequenceLength", 3);
            request.setAttribute("csv-0", "first\r\n");
            request.setAttribute("csv-1", "");
            request.setAttribute("csv-2", "third\r\n");

            ReportCsvDownloads.offer(request, PROVIDER);

            String token = (String) request.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE);
            assertThat(request.getAttribute(ReportCsvDownloads.DOWNLOADS_ATTRIBUTE)).isEqualTo(List.of(true, false, true));
            assertThat(request.getAttribute("csv-0")).isNull();
            assertThat(request.getAttribute("csv-2")).isNull();
            assertThat(text(ReportCsvDownloads.find(request.getSession(false), PROVIDER, token, "2"))).isEqualTo("third\r\n");
            assertThat(request.getAttribute("csvWithheld-0")).isNull();
        }

        @Test
        @DisplayName("should hold back a query over the configured CSV limit with the same note as the report")
        void shouldHoldBackQueryOverTheConfiguredLimit_withTheHeldBackNote() {
            request.setAttribute("reportobject", report(true));
            request.setAttribute("sequenceLength", 2);
            // Reporters other than SQLReporter do not apply the CSV limit themselves.
            request.setAttribute("csv-0", "a".repeat(SQLReporter.MAX_CSV_EXPORT_LENGTH + 1));
            request.setAttribute("csv-1", "small\r\n");

            ReportCsvDownloads.offer(request, PROVIDER);

            assertThat(request.getAttribute(ReportCsvDownloads.DOWNLOADS_ATTRIBUTE)).isEqualTo(List.of(false, true));
            assertThat(request.getAttribute("csvWithheld-0")).isEqualTo(Boolean.TRUE);
            assertThat(request.getAttribute("csvWithheldMb")).isEqualTo(5);
            // The default is below the setting's maximum, so the note says an administrator can raise it.
            assertThat(request.getAttribute("csvWithheldSetting")).isEqualTo("report_by_template.max_csv_mb");
        }

        @Test
        @DisplayName("should publish no token when the report returned no CSV")
        void shouldPublishNoToken_whenTheReportReturnedNoCsv() {
            request.setAttribute("reportobject", report(false));
            request.setAttribute("csv", "");

            ReportCsvDownloads.offer(request, PROVIDER);

            assertThat(request.getAttribute(ReportCsvDownloads.TOKEN_ATTRIBUTE)).isNull();
            assertThat(request.getAttribute(ReportCsvDownloads.DOWNLOADS_ATTRIBUTE)).isEqualTo(List.of(false));
        }
    }

    @Test
    @DisplayName("should keep the exact UTF-8 bytes of the CSV the report produced")
    void shouldKeepExactCsvText_forUnicodeAndLineBreaks() {
        String csv = "\"\t=header\",multiline\r\n\"Zoë,\"\"text\"\"\nnext\",１２\r\n";
        String token = ReportCsvDownloads.keep(store, session, PROVIDER, List.of(csv),
                csv.getBytes(StandardCharsets.UTF_8).length, START).token();

        assertThat(find(token, "0")).isEqualTo(csv);
    }
}
