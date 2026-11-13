/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Keeps the CSV of a Report by Template result on the server, so its CSV and XLSX downloads are built from
 * what the query returned rather than from a copy the browser posts back.
 *
 * <p>When a report runs, {@link #offer} takes each query's CSV off the request and keeps it in the user's
 * HTTP session under a random token; the result page carries only that token and which queries can be
 * downloaded. {@code GenerateOutFiles2Action} asks {@link #find} for one query's CSV by that token, for the same
 * session and the same signed-in provider, and builds the file from it.</p>
 *
 * <p>Bounds, because every kept result is heap: each session keeps the CSV of at most
 * {@value #MAX_RESULTS_PER_SESSION} report runs, and no more than {@value #MAX_RESULTS_PER_SESSION} times the
 * CSV limit ({@code report_by_template.max_csv_mb}) in all, dropping its oldest results first. The newest result
 * is always kept whole, so one sequence can exceed that by itself; its size is still bounded by the report's
 * own limits. A query whose CSV is over the CSV limit is held back, as the report itself holds it back. Results
 * expire {@value #LIFETIME_MINUTES} minutes after the report ran and go with the session when it ends. They
 * are never written to disk: the session value is not {@link java.io.Serializable}, so a session manager that
 * persists or replicates sessions leaves it out (CARLOS also turns Tomcat's session persistence off).</p>
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

    static final String SESSION_ATTRIBUTE = "carlos.reportByTemplate.csvDownloads";
    private static final long LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(LIFETIME_MINUTES);
    /**
     * 256 random bits, written as 64 lowercase hex digits: nothing in it (such as a {@code --} that URL-safe
     * Base64 can produce) can look like an attack to the web firewall's rules.
     */
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LENGTH = 2 * TOKEN_BYTES;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Guards only the creation of a session's store; each store guards its own contents. */
    private static final Object CREATE_LOCK = new Object();

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
        Kept kept = keep(session, providerNo, csvs, maxCsvBytes, System.nanoTime());
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
     *     token is missing, unknown, expired or another provider's, or the query has no CSV
     */
    public static Optional<String> find(HttpSession session, String providerNo, String token, String index) {
        return find(session, providerNo, token, index, System.nanoTime());
    }

    static Optional<String> find(HttpSession session, String providerNo, String token, String index, long now) {
        int query = queryIndex(index);
        if (session == null || providerNo == null || token == null || token.length() != TOKEN_LENGTH || query < 0) {
            return Optional.empty();
        }
        Results results = results(session, false);
        if (results == null) {
            return Optional.empty();
        }
        Result result = results.get(token, now);
        if (result == null || !providerNo.equals(result.providerNo) || query >= result.csvs.length
                || result.csvs[query] == null) {
            return Optional.empty();
        }
        return Optional.of(new String(result.csvs[query], StandardCharsets.UTF_8));
    }

    /**
     * Keeps one report run's CSVs and returns its token. Empty and null CSVs offer no download; a CSV over
     * {@code maxCsvBytes} in UTF-8 is held back. Nothing is kept, and no token issued, when no query has a CSV.
     */
    static Kept keep(HttpSession session, String providerNo, List<String> csvs, int maxCsvBytes, long now) {
        byte[][] kept = new byte[csvs.size()][];
        List<Boolean> downloadable = new ArrayList<>();
        List<Boolean> withheld = new ArrayList<>();
        long bytes = 0;
        for (int query = 0; query < csvs.size(); query++) {
            String csv = csvs.get(query);
            byte[] utf8 = csv == null || csv.isEmpty() ? null : csv.getBytes(StandardCharsets.UTF_8);
            boolean over = utf8 != null && utf8.length > maxCsvBytes;
            if (utf8 != null && !over) {
                kept[query] = utf8;
                bytes += utf8.length;
            }
            downloadable.add(kept[query] != null);
            withheld.add(over);
        }
        String token = null;
        if (downloadable.contains(Boolean.TRUE)) {
            token = newToken();
            long budget = (long) MAX_RESULTS_PER_SESSION * maxCsvBytes;
            results(session, true).add(token, new Result(providerNo, now, kept, bytes), budget, now);
        }
        return new Kept(token, Collections.unmodifiableList(downloadable), Collections.unmodifiableList(withheld));
    }

    /** What {@link #keep} did with one report run: its token (null when nothing was kept) and each query's fate. */
    record Kept(String token, List<Boolean> downloadable, List<Boolean> withheld) {
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

    private static Results results(HttpSession session, boolean create) {
        if (session.getAttribute(SESSION_ATTRIBUTE) instanceof Results existing) {
            return existing;
        }
        if (!create) {
            return null;
        }
        synchronized (CREATE_LOCK) {
            if (session.getAttribute(SESSION_ATTRIBUTE) instanceof Results existing) {
                return existing;
            }
            Results created = new Results();
            session.setAttribute(SESSION_ATTRIBUTE, created);
            return created;
        }
    }

    /** One report run's kept CSVs, by query index (null where a query offers no download). */
    private static final class Result {
        private final String providerNo;
        private final long keptAt;
        private final byte[][] csvs;
        private final long bytes;

        Result(String providerNo, long keptAt, byte[][] csvs, long bytes) {
            this.providerNo = providerNo;
            this.keptAt = keptAt;
            this.csvs = csvs;
            this.bytes = bytes;
        }
    }

    /**
     * A session's kept results, oldest first. Deliberately not Serializable: session persistence and
     * replication leave it out, so report data never reaches disk or another node.
     */
    private static final class Results {
        private final Map<String, Result> byToken = new LinkedHashMap<>();
        private long bytes;

        synchronized void add(String token, Result result, long budget, long now) {
            dropExpired(now);
            Iterator<Result> oldest = byToken.values().iterator();
            while (oldest.hasNext() && (byToken.size() >= MAX_RESULTS_PER_SESSION || bytes + result.bytes > budget)) {
                bytes -= oldest.next().bytes;
                oldest.remove();
            }
            byToken.put(token, result);
            bytes += result.bytes;
        }

        synchronized Result get(String token, long now) {
            dropExpired(now);
            return byToken.get(token);
        }

        private void dropExpired(long now) {
            Iterator<Result> each = byToken.values().iterator();
            while (each.hasNext()) {
                Result result = each.next();
                if (now - result.keptAt >= LIFETIME_NANOS) {
                    bytes -= result.bytes;
                    each.remove();
                }
            }
        }
    }
}
