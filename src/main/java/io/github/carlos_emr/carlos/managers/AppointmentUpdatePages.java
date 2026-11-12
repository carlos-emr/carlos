/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.Date;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** Keyset scan within a request-owned repeatable-read transaction, including consent-filtered gaps. */
final class AppointmentUpdatePages {
    private static final org.apache.logging.log4j.Logger logger = io.github.carlos_emr.carlos.utility.MiscUtils.getLogger();
    static final int RAW_BATCH = 500;
    record Position(long millis, int id) implements Comparable<Position> {
        @Override public int compareTo(Position other) {
            int time = Long.compare(millis, other.millis);
            return time == 0 ? Integer.compare(id, other.id) : time;
        }
    }
    private AppointmentUpdatePages() { }

    static <T> AppointmentUpdatePage<T> read(Date after, int afterId, int limit,
            BiFunction<Position, Integer, List<T>> fetch, Function<List<T>, List<T>> filter,
            Function<T, Date> updated, ToIntFunction<T> identity) {
        if (after == null || afterId < 1 || limit < 1
                || limit > io.github.carlos_emr.carlos.commn.dao.AbstractDao.MAX_LIST_RETURN_SIZE) {
            throw new IllegalArgumentException("Invalid schedule sync position or page size");
        }
        Position position = new Position(after.getTime(), afterId);
        int wanted = Math.addExact(limit, 1), batchSize = Math.min(RAW_BATCH, wanted);
        List<T> selected = new ArrayList<>(wanted);
        while (selected.size() < wanted) {
            List<T> raw = fetch.apply(position, batchSize);
            if (raw == null || raw.size() > batchSize) throw new IllegalStateException("Invalid schedule sync batch");
            var originals = new IdentityHashMap<T, Position>();
            for (T row : raw) {
                Position next = position(row, updated, identity);
                if (next.compareTo(position) <= 0 || originals.put(row, next) != null) {
                    throw new IllegalStateException("Schedule sync rows are not uniquely ordered");
                }
                position = next;
            }
            List<T> readable = filter.apply(new ArrayList<>(raw));
            if (readable == null) throw new IllegalStateException("Schedule consent policy returned no result");
            Position previous = null;
            for (T row : readable) {
                Position current = position(row, updated, identity);
                if (!current.equals(originals.get(row)) || previous != null && current.compareTo(previous) <= 0) {
                    throw new IllegalStateException("Schedule consent policy changed the selected records");
                }
                previous = current;
                selected.add(row);
                if (selected.size() == wanted) break;
            }
            if (raw.size() < batchSize) break;
        }
        boolean more = selected.size() > limit;
        Long nextTime = more ? updated.apply(selected.removeLast()).getTime() : null;
        Position last = selected.isEmpty() ? null : position(selected.getLast(), updated, identity);
        return new AppointmentUpdatePage<>(selected, more, last == null ? null : last.millis(), last == null ? null : last.id(), nextTime);
    }

    /**
     * One page for a caller that sends only an exclusive timestamp and a page size: the published
     * contract of the three SOAP updated-after operations, used by every client that sends no
     * schedule sync header.
     *
     * <p>Such a caller continues from the newest update timestamp it was given, and the query is
     * strictly after that timestamp. The update column holds whole seconds, so several rows often
     * share one. If the page ended inside such a group, the caller's next request would start after
     * the group and never see the rest of it. The page is therefore extended to the end of the
     * group that its last row belongs to: it can hold more rows than {@code limit}, by at most the
     * rest of that one group, and never more than the DAO hard limit.</p>
     *
     * <p>This method never refuses a page. A fault would be repeated for every retry of the same
     * request and stop the caller for good. When even the hard limit cannot hold the whole group,
     * the answer is the one 2026.08.0-beta6 gave: exactly the first {@code limit} rows. The rest of that
     * group is then missed by a timestamp-only caller, as it always was; only the timestamp/ID
     * profile can page through a group of that size.</p>
     *
     * @param after Date the caller's exclusive lower bound
     * @param limit int the requested page size, from 1 to the DAO hard limit
     * @return List&lt;T&gt; the rows in (timestamp, id) order; a new mutable list
     * @throws IllegalArgumentException for a missing bound or a page size outside the limits
     */
    static <T> List<T> legacy(Date after, int limit,
            BiFunction<Position, Integer, List<T>> fetch, Function<List<T>, List<T>> filter,
            Function<T, Date> updated, ToIntFunction<T> identity) {
        // No row has an id above Integer.MAX_VALUE, so this position means "strictly after the timestamp".
        AppointmentUpdatePage<T> page = read(after, Integer.MAX_VALUE, limit, fetch, filter, updated, identity);
        List<T> requested = new ArrayList<>(page.items());
        if (!page.hasMore() || !java.util.Objects.equals(page.updatedMillis(), page.nextUpdatedMillis())) {
            return requested;
        }
        long shared = page.updatedMillis();
        int hardLimit = io.github.carlos_emr.carlos.commn.dao.AbstractDao.MAX_LIST_RETURN_SIZE;
        List<T> complete = new ArrayList<>(requested);
        int afterId = page.afterId();
        while (complete.size() < hardLimit) {
            // One raw batch holds this many rows plus the row that shows whether the group goes on.
            int more = Math.min(hardLimit - complete.size(), RAW_BATCH - 1);
            AppointmentUpdatePage<T> rest = read(new Date(shared), afterId, more, fetch, filter, updated, identity);
            for (T row : rest.items()) {
                if (updated.apply(row).getTime() != shared) return complete;
                complete.add(row);
            }
            if (!rest.hasMore() || rest.nextUpdatedMillis() != shared) return complete;
            afterId = rest.afterId();
        }
        // Counts only: the rows are appointments and must not be described in the log.
        logger.warn("Timestamp-only schedule page could not reach the end of the rows sharing its last update "
                + "timestamp within the {}-row hard limit; returning the {} requested rows. The caller's next "
                + "request skips the rest of that group; only the timestamp-id sync profile can page through it.",
                hardLimit, requested.size());
        return requested;
    }

    /** The queries and row accessors of one updated-after operation, shared by both sync profiles. */
    record Source<T>(BiFunction<Position, Integer, List<T>> fetch, Function<List<T>, List<T>> filter,
            Function<T, Date> updated, ToIntFunction<T> identity) {
        /** Explicit timestamp/ID profile: at most {@code limit} rows and an exact continuation position. */
        AppointmentUpdatePage<T> page(Date after, int afterId, int limit) {
            return read(after, afterId, limit, fetch, filter, updated, identity);
        }

        /** Header-less timestamp-only profile; see {@link AppointmentUpdatePages#legacy}. */
        List<T> timestampOnly(Date after, int limit) {
            return legacy(after, limit, fetch, filter, updated, identity);
        }
    }

    private static <T> Position position(T row, Function<T, Date> updated, ToIntFunction<T> identity) {
        if (row == null || updated.apply(row) == null || identity.applyAsInt(row) < 1) {
            throw new IllegalStateException("Schedule sync row has no stable position");
        }
        return new Position(updated.apply(row).getTime(), identity.applyAsInt(row));
    }
}
