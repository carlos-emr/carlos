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

    static <T> List<T> legacy(AppointmentUpdatePage<T> page) {
        if (page.hasMore() && java.util.Objects.equals(page.updatedMillis(), page.nextUpdatedMillis())) {
            throw new ScheduleSyncBoundaryException();
        }
        return new ArrayList<>(page.items());
    }

    private static <T> Position position(T row, Function<T, Date> updated, ToIntFunction<T> identity) {
        if (row == null || updated.apply(row) == null || identity.applyAsInt(row) < 1) {
            throw new IllegalStateException("Schedule sync row has no stable position");
        }
        return new Position(updated.apply(row).getTime(), identity.applyAsInt(row));
    }
}
