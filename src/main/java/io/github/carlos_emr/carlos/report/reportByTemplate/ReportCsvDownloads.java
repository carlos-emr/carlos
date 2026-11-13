/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionBindingEvent;
import jakarta.servlet.http.HttpSessionBindingListener;

/**
 * Keeps the CSV of a Report by Template result on the server, so its CSV and XLSX downloads are built from
 * what the query returned rather than from a copy the browser posts back.
 *
 * <p>When a report runs, {@link #offer} takes each query's CSV off the request and keeps it for the user's
 * HTTP session under a random token; the result page carries only that token and which queries can be
 * downloaded. {@code GenerateOutFiles2Action} asks {@link #find} for one query's CSV by that token, for the same
 * session and the same signed-in provider, and writes the file from it.</p>
 *
 * <p>Bounds, because every kept result is heap:</p>
 * <ul>
 *   <li>Each session keeps the CSV of at most {@value #MAX_RESULTS_PER_SESSION} report runs that offered a
 *       download, and no more than {@value #MAX_RESULTS_PER_SESSION} times the CSV limit
 *       ({@code report_by_template.max_csv_mb}) in all.</li>
 *   <li>All sessions together keep no more than an eighth of the JVM's maximum heap (256 MiB with the packaged
 *       2 GiB heap): past that, the oldest results of any session are dropped, and that user is asked to run
 *       the report again.</li>
 *   <li>Older results go first, by when their report ran. The newest result is always kept whole, so one
 *       sequence can exceed a bound by itself; its size is still bounded by the report's own limits.</li>
 *   <li>A query whose CSV is over the CSV limit is held back, as the report itself holds it back; its size is
 *       counted without encoding it.</li>
 *   <li>Results expire {@value #LIFETIME_MINUTES} minutes after the report ran. Every keep and every download
 *       drops the expired results of all sessions, and a session's results are released when it ends.</li>
 * </ul>
 *
 * <p>Results are never written to disk: the session value is not {@link java.io.Serializable}, so a session
 * manager that persists sessions leaves it out (CARLOS also turns Tomcat's session persistence off).</p>
 */
public final class ReportCsvDownloads {

    /** Request attribute naming the result's token for {@code resultReport.jsp}; absent when nothing was kept. */
    public static final String TOKEN_ATTRIBUTE = "csvDownloadToken";
    /** Request attribute holding a {@code List<Boolean>}: whether each query (by index) can be downloaded. */
    public static final String DOWNLOADS_ATTRIBUTE = "csvDownloads";
    /** Report runs whose CSV one session keeps. */
    public static final int MAX_RESULTS_PER_SESSION = 3;
    /** Minutes a result stays downloadable after its report ran. */
    public static final int LIFETIME_MINUTES = 30;
    /** All sessions' kept results together may use the JVM's maximum heap divided by this. */
    static final int HEAP_SHARE = 8;

    static final String SESSION_ATTRIBUTE = "carlos.reportByTemplate.csvDownloads";
    private static final long LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(LIFETIME_MINUTES);
    /** Used when the JVM reports no maximum heap: the share of the packaged 2 GiB heap. */
    private static final long UNBOUNDED_HEAP_BUDGET = 256L * 1024 * 1024;
    /**
     * 256 random bits, written as 64 lowercase hex digits: nothing in it (such as a {@code --} that URL-safe
     * Base64 can produce) can look like an attack to the web firewall's rules.
     */
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 2 * TOKEN_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();
    /**
     * Guards only the creation of a session's results. It is never held while a store's lock is, and no store
     * lock is held while the session is called, so a session ending on another thread cannot deadlock a report.
     */
    private static final Object CREATE_LOCK = new Object();
    /** Every session's kept results in this JVM. */
    private static final Store PROCESS = new Store(heapBudget(Runtime.getRuntime().maxMemory()));

    private ReportCsvDownloads() {
    }

    /**
     * Keeps the CSV of the report a reporter has just put on the request and replaces it with a token.
     *
     * <p>Reads {@code csv} (or {@code csv-0} to {@code csv-N} for a sequence, as {@code reportobject} and
     * {@code sequenceLength} say) and removes those attributes, so the result page cannot carry the CSV. Sets
     * {@link #DOWNLOADS_ATTRIBUTE}, {@link #TOKEN_ATTRIBUTE} when any query can be downloaded, and the held-back
     * note's attributes for a query over the CSV limit.</p>
     *
     * @param request the report request, after its reporter succeeded
     * @param providerNo the signed-in provider who ran the report; only that provider can download the result
     */
    public static void offer(HttpServletRequest request, String providerNo) {
        boolean sequence = request.getAttribute("reportobject") instanceof ReportObject report && report.isSequence();
        List<String> names = new ArrayList<>();
        if (sequence) {
            int length = request.getAttribute("sequenceLength") instanceof Integer count ? count : 0;
            for (int query = 0; query < length; query++) {
                names.add("csv-" + query);
            }
        } else {
            names.add("csv");
        }
        List<String> csvs = new ArrayList<>();
        for (String name : names) {
            csvs.add(request.getAttribute(name) instanceof String csv ? csv : null);
            request.removeAttribute(name);
        }
        int maxCsvBytes = ReportResultBudget.Limits.configured().maxCsvBytes();
        HttpSession session = request.getSession();
        Kept kept = keep(PROCESS, session, providerNo, csvs, maxCsvBytes, System.nanoTime());
        request.setAttribute(DOWNLOADS_ATTRIBUTE, kept.downloadable());
        if (kept.token() != null) {
            request.setAttribute(TOKEN_ATTRIBUTE, kept.token());
        }
        for (int query = 0; query < csvs.size(); query++) {
            if (kept.withheld().get(query)) {
                markWithheld(request, sequence ? "csvWithheld-" + query : "csvWithheld", maxCsvBytes);
            }
        }
    }

    /**
     * Puts the held-back note on a query whose CSV is over the limit (#5849, D260): {@code flag} is
     * {@code csvWithheld}, or {@code csvWithheld-N} for query N of a sequence. While an administrator can still
     * raise the limit, {@code csvWithheldSetting} names the setting, and the page says so after the note.
     */
    static void markWithheld(HttpServletRequest request, String flag, int maxCsvBytes) {
        request.setAttribute(flag, Boolean.TRUE);
        request.setAttribute("csvWithheldMb", maxCsvBytes / (1024 * 1024));
        String setting = ReportResultBudget.raisableCsvSetting(maxCsvBytes);
        if (setting != null) {
            request.setAttribute("csvWithheldSetting", setting);
        }
    }

    /**
     * One query's CSV, for the session and provider that ran its report.
     *
     * @param session the requesting session; null finds nothing
     * @param providerNo the signed-in provider
     * @param token the result's token from the page
     * @param index the query's index, counting from 0; null means the first query
     * @return the CSV exactly as the report produced it, or empty when this session holds no such result: the
     *     token is missing, unknown, expired, dropped or another provider's, or the query has no CSV
     */
    public static Optional<Csv> find(HttpSession session, String providerNo, String token, String index) {
        return find(session, providerNo, token, index, System.nanoTime());
    }

    static Optional<Csv> find(HttpSession session, String providerNo, String token, String index, long now) {
        int query = queryIndex(index);
        if (session == null || providerNo == null || token == null || token.length() != TOKEN_LENGTH || query < 0) {
            return Optional.empty();
        }
        SessionResults results = results(session, null);
        if (results == null) {
            return Optional.empty();
        }
        Result result = results.store.get(results, token, now);
        if (result == null || !providerNo.equals(result.providerNo) || query >= result.csvs.length
                || result.csvs[query] == null) {
            return Optional.empty();
        }
        return Optional.of(new Csv(result.csvs[query]));
    }

    /**
     * Keeps one report run's CSVs in {@code store} and returns its token. Empty and null CSVs offer no
     * download; a CSV over {@code maxCsvBytes} in UTF-8 is held back. Nothing is kept, and no token issued, when
     * no query has a CSV.
     */
    static Kept keep(Store store, HttpSession session, String providerNo, List<String> csvs, int maxCsvBytes, long now) {
        byte[][] kept = new byte[csvs.size()][];
        List<Boolean> downloadable = new ArrayList<>();
        List<Boolean> withheld = new ArrayList<>();
        long bytes = 0;
        for (int query = 0; query < csvs.size(); query++) {
            String csv = csvs.get(query);
            boolean present = csv != null && !csv.isEmpty();
            boolean over = present && utf8Length(csv, maxCsvBytes) < 0;
            if (present && !over) {
                kept[query] = csv.getBytes(StandardCharsets.UTF_8);
                bytes += kept[query].length;
            }
            downloadable.add(kept[query] != null);
            withheld.add(over);
        }
        String token = null;
        if (downloadable.contains(Boolean.TRUE)) {
            token = newToken();
            long sessionBudget = (long) MAX_RESULTS_PER_SESSION * maxCsvBytes;
            SessionResults results = results(session, store);
            results.store.add(results, new Result(token, providerNo, now, kept, bytes), sessionBudget, now);
        }
        return new Kept(token, Collections.unmodifiableList(downloadable), Collections.unmodifiableList(withheld));
    }

    /** What {@link #keep} did with one report run: its token (null when nothing was kept) and each query's fate. */
    record Kept(String token, List<Boolean> downloadable, List<Boolean> withheld) {
    }

    /**
     * The length of {@code csv} in UTF-8, as {@link String#getBytes} encodes it (a lone surrogate becomes one
     * {@code ?}), or -1 as soon as it passes {@code limit}: an oversized CSV is never encoded only to be dropped.
     */
    static long utf8Length(String csv, long limit) {
        // Every Java char encodes to at least one byte, so a longer string is over without counting.
        if (csv.length() > limit) {
            return -1;
        }
        long bytes = 0;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c < 0x80) {
                bytes += 1;
            } else if (c < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < csv.length() && Character.isLowSurrogate(csv.charAt(i + 1))) {
                bytes += 4;
                i++;
            } else if (Character.isSurrogate(c)) {
                bytes += 1;
            } else {
                bytes += 3;
            }
            if (bytes > limit) {
                return -1;
            }
        }
        return bytes;
    }

    /** All sessions' share of a JVM whose maximum heap is {@code maxHeap} bytes. */
    static long heapBudget(long maxHeap) {
        return maxHeap == Long.MAX_VALUE || maxHeap <= 0 ? UNBOUNDED_HEAP_BUDGET : maxHeap / HEAP_SHARE;
    }

    private static int queryIndex(String index) {
        if (index == null) {
            return 0;
        }
        // At most four digits: a sequence runs no more than ReportResultBudget.MAX_QUERIES queries.
        if (index.isEmpty() || index.length() > 4 || !index.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return -1;
        }
        return Integer.parseInt(index);
    }

    private static String newToken() {
        byte[] random = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    /** The session's results, created in {@code store} when {@code store} is given; null when there are none. */
    @SuppressWarnings("java:S2441") private static SessionResults results(HttpSession session, Store store) {
        try {
            if (session.getAttribute(SESSION_ATTRIBUTE) instanceof SessionResults existing) {
                return existing;
            }
            if (store == null) {
                return null;
            }
            synchronized (CREATE_LOCK) {
                if (session.getAttribute(SESSION_ATTRIBUTE) instanceof SessionResults existing) {
                    return existing;
                }
                SessionResults created = new SessionResults(store);
                session.setAttribute(SESSION_ATTRIBUTE, created); // S2441: not Serializable on purpose, see SessionResults
                return created;
            }
        } catch (IllegalStateException invalidated) {
            // The session ended while the report ran: nothing of it can be downloaded any more.
            return store == null ? null : SessionResults.closed(store);
        }
    }

    /**
     * One query's kept CSV, in the UTF-8 the report produced. It is read where it is kept, never copied: a
     * download writes these bytes as they are, and the XLSX export reads them one row at a time.
     */
    public static final class Csv {
        private final byte[] utf8;

        private Csv(byte[] utf8) {
            this.utf8 = utf8;
        }

        /** Writes the CSV, unchanged, to {@code output}; the caller owns the stream. */
        public void writeTo(OutputStream output) throws IOException {
            output.write(utf8);
        }

        /** Reads the CSV as text. */
        public Reader reader() {
            return new InputStreamReader(new ByteArrayInputStream(utf8), StandardCharsets.UTF_8);
        }

        /** The CSV as a string, for tests. */
        String text() {
            return new String(utf8, StandardCharsets.UTF_8);
        }
    }

    /** One report run's kept CSVs, by query index (null where a query offers no download). */
    private static final class Result {
        private final String token;
        private final String providerNo;
        private final long keptAt;
        private final byte[][] csvs;
        private final long bytes;
        /** Set when the result is added to a session's results. */
        private SessionResults owner;

        Result(String token, String providerNo, long keptAt, byte[][] csvs, long bytes) {
            this.token = token;
            this.providerNo = providerNo;
            this.keptAt = keptAt;
            this.csvs = csvs;
            this.bytes = bytes;
        }
    }

    /**
     * Every session's kept results in one JVM, and the heap they may use together. One lock guards all of them,
     * so a result can be dropped from any session; downloads and report runs are far too rare for it to matter.
     */
    static final class Store {
        private final long budget;
        private final List<Result> results = new ArrayList<>();
        private long bytes;

        Store(long budget) {
            this.budget = budget;
        }

        /**
         * Adds {@code result} to {@code owner}, first dropping every expired result, then the session's oldest
         * results beyond its bounds, then the oldest results of any session beyond the store's. The new result
         * itself is always kept.
         */
        synchronized void add(SessionResults owner, Result result, long sessionBudget, long now) {
            dropExpired(now);
            if (owner.closed) {
                return;
            }
            while (!owner.byToken.isEmpty() && (owner.byToken.size() >= MAX_RESULTS_PER_SESSION
                    || owner.bytes + result.bytes > sessionBudget)) {
                drop(oldest(owner.byToken.values()));
            }
            while (!results.isEmpty() && bytes + result.bytes > budget) {
                drop(oldest(results));
            }
            result.owner = owner;
            owner.byToken.put(result.token, result);
            owner.bytes += result.bytes;
            results.add(result);
            bytes += result.bytes;
        }

        /** The result {@code owner} holds under {@code token}, after dropping every expired result; else null. */
        synchronized Result get(SessionResults owner, String token, long now) {
            dropExpired(now);
            return owner.byToken.get(token);
        }

        /** Drops all of a session's results: it has ended. */
        synchronized void release(SessionResults owner) {
            owner.closed = true;
            for (Result result : new ArrayList<>(owner.byToken.values())) {
                drop(result);
            }
        }

        /** Bytes kept for all sessions, for tests. */
        synchronized long bytes() {
            return bytes;
        }

        private void dropExpired(long now) {
            for (Result result : new ArrayList<>(results)) {
                if (now - result.keptAt >= LIFETIME_NANOS) {
                    drop(result);
                }
            }
        }

        private void drop(Result result) {
            results.remove(result);
            bytes -= result.bytes;
            SessionResults owner = result.owner;
            if (owner.byToken.remove(result.token) != null) {
                owner.bytes -= result.bytes;
            }
        }

        /** The result whose report ran first; on a tie, the one kept first. */
        private static Result oldest(Collection<Result> candidates) {
            Result oldest = null;
            for (Result candidate : candidates) {
                if (oldest == null || candidate.keptAt - oldest.keptAt < 0) {
                    oldest = candidate;
                }
            }
            return oldest;
        }
    }

    /**
     * A session's kept results; its store's lock guards them. Deliberately not Serializable: session persistence
     * and replication leave it out, so report data never reaches disk or another node. When the session ends,
     * or the value is otherwise removed, its results are released at once.
     */
    private static final class SessionResults implements HttpSessionBindingListener {
        private final Store store;
        private final Map<String, Result> byToken = new LinkedHashMap<>();
        private long bytes;
        private boolean closed;

        SessionResults(Store store) {
            this.store = store;
        }

        /** Results for a session that has already ended: whatever is added to them is dropped. */
        static SessionResults closed(Store store) {
            SessionResults results = new SessionResults(store);
            results.closed = true;
            return results;
        }

        @Override
        public void valueUnbound(HttpSessionBindingEvent event) {
            store.release(this);
        }
    }
}
