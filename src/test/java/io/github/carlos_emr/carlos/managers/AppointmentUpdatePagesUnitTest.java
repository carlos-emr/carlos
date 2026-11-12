/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AppointmentUpdatePagesUnitTest {
    private static List<Appointment> rows(int count) {
        var result = new ArrayList<Appointment>();
        for (int id=1;id<=count;id++) {
            var row = new Appointment(); row.setId(id); row.setUpdateDateTime(new Date(1000)); result.add(row);
        }
        return result;
    }
    private static AppointmentUpdatePage<Appointment> read(List<Appointment> source, Date after, int afterId,
            int limit, Function<List<Appointment>,List<Appointment>> policy, AtomicInteger queries) {
        return AppointmentUpdatePages.read(after,afterId,limit,(position,batch) -> {
            queries.incrementAndGet(); assertThat(batch).isBetween(1,500);
            return source.stream().filter(row -> row.getUpdateDateTime().getTime()>position.millis()
                    || row.getUpdateDateTime().getTime()==position.millis() && row.getId()>position.id()).limit(batch).toList();
        },policy,Appointment::getUpdateDateTime,Appointment::getId);
    }
    @Test void shouldReturnEveryRowExactlyOnce_whenManyPagesShareOneTimestamp() {
        var source = rows(1203); var seen = new ArrayList<Integer>(); var queries = new AtomicInteger();
        Date after = new Date(0); int afterId = Integer.MAX_VALUE;
        for (;;) {
            var page = read(source,after,afterId,7,Function.identity(),queries);
            assertThat(page.items()).hasSizeLessThanOrEqualTo(7);
            page.items().forEach(row -> seen.add(row.getId()));
            if (!page.hasMore()) break;
            after = new Date(page.updatedMillis()); afterId = page.afterId();
        }
        assertThat(seen).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,1203).boxed().toList());
        assertThat(queries.get()).isGreaterThan(100);
    }
    @Test void shouldContinuePastEntirelyBlockedRawBatches_andPositionOnTheLastReturnedRow() {
        var queries = new AtomicInteger();
        var page = read(rows(1201),new Date(0),Integer.MAX_VALUE,1,
                batch -> batch.stream().filter(row -> row.getId()>=1200).toList(),queries);
        assertThat(page.items()).extracting(Appointment::getId).containsExactly(1200);
        assertThat(page.hasMore()).isTrue(); assertThat(page.afterId()).isEqualTo(1200);
        assertThat(queries.get()).isGreaterThan(500);
    }
    @Test void shouldReturnATruthfulEmptyEnd_onlyAfterScanningAllBlockedRecords() {
        var queries = new AtomicInteger();
        var page = read(rows(1200),new Date(0),Integer.MAX_VALUE,1,batch -> List.of(),queries);
        assertThat(page.items()).isEmpty(); assertThat(page.hasMore()).isFalse();
        assertThat(page.afterId()).isNull(); assertThat(queries.get()).isEqualTo(601);
    }
    // ---- Header-less timestamp-only paging: never refused, extended to the end of a shared timestamp. ----

    /** Rows {@code 1..count}; {@code seconds[i]} is the whole update second of row {@code i+1}. */
    private static List<Appointment> rowsAt(int... seconds) {
        var result = new ArrayList<Appointment>();
        for (int index=0;index<seconds.length;index++) {
            var row = new Appointment(); row.setId(index+1); row.setUpdateDateTime(new Date(seconds[index]*1000L)); result.add(row);
        }
        return result;
    }
    private static List<Appointment> legacy(List<Appointment> source, Date after, int limit,
            Function<List<Appointment>,List<Appointment>> policy, AtomicInteger queries) {
        return AppointmentUpdatePages.legacy(after,limit,(position,batch) -> {
            queries.incrementAndGet(); assertThat(batch).isBetween(1,500);
            return source.stream().filter(row -> row.getUpdateDateTime().getTime()>position.millis()
                    || row.getUpdateDateTime().getTime()==position.millis() && row.getId()>position.id()).limit(batch).toList();
        },policy,Appointment::getUpdateDateTime,Appointment::getId);
    }
    /** An unmodified client: its next exclusive bound is the newest timestamp it has seen; it stops at an empty page. */
    private static List<Integer> synchronize(List<Appointment> source, int pageSize,
            Function<List<Appointment>,List<Appointment>> policy, List<Integer> pageSizes) {
        var seen = new ArrayList<Integer>(); Date after = new Date(0);
        for (int requests=0;requests<10_000;requests++) {
            var page = legacy(source,after,pageSize,policy,new AtomicInteger());
            if (page.isEmpty()) return seen;
            pageSizes.add(page.size());
            Date newest = page.getLast().getUpdateDateTime();
            assertThat(newest).as("a non-empty page must move the bound forward").isAfter(after);
            page.forEach(row -> seen.add(row.getId())); after = newest;
        }
        throw new AssertionError("The client never reached an empty page");
    }
    @Test void shouldExtendATimestampOnlyPage_toTheEndOfTheGroupSharingItsLastTimestamp() {
        // The requested page of two would end inside the three rows sharing second 1.
        assertThat(legacy(rows(3),new Date(0),2,Function.identity(),new AtomicInteger()))
                .extracting(Appointment::getId).containsExactly(1,2,3);
        // Only the group of the last requested row is completed; later timestamps stay for the next request.
        assertThat(legacy(rowsAt(1,2,2,2,3,3),new Date(0),2,Function.identity(),new AtomicInteger()))
                .extracting(Appointment::getId).containsExactly(1,2,3,4);
        assertThat(legacy(rows(2),new Date(0),2,Function.identity(),new AtomicInteger())).hasSize(2);
    }
    @Test void shouldRetainTheRequestedPage_whenTheNextPermittedTimestampIsLater() {
        var source = rows(3); source.getLast().setUpdateDateTime(new Date(2000));
        var queries = new AtomicInteger();
        assertThat(legacy(source,new Date(0),2,Function.identity(),queries)).extracting(Appointment::getId).containsExactly(1,2);
        // No shared boundary: the page costs exactly what it cost before.
        assertThat(queries.get()).isEqualTo(1);
    }
    @Test void shouldLoseNoRow_acrossConsecutiveTimestampOnlyPages() {
        // Groups of 1, 4, 1, 2, 1, 700 and 1 rows; every page size splits at least one of them.
        var seconds = new ArrayList<Integer>(List.of(1,2,2,2,2,3,4,4,5));
        for (int row=0;row<700;row++) seconds.add(6);
        seconds.add(7);
        var source = rowsAt(seconds.stream().mapToInt(Integer::intValue).toArray());
        var everyId = java.util.stream.IntStream.rangeClosed(1,source.size()).boxed().toList();
        for (int pageSize : List.of(1,2,3,5,7,499,500,501,5000)) {
            var pageSizes = new ArrayList<Integer>();
            assertThat(synchronize(source,pageSize,Function.identity(),pageSizes)).as("page size %d",pageSize)
                    .containsExactlyElementsOf(everyId);
            assertThat(pageSizes).as("page size %d",pageSize).allMatch(size -> size<=5000);
        }
        var pageSizes = new ArrayList<Integer>();
        synchronize(source,2,Function.identity(),pageSizes);
        // 1,2 + rest of second 2; 6,7 + rest of second 4; 9,10 + the other 699 rows of second 6; the last row.
        assertThat(pageSizes).containsExactly(5,3,701,1);
    }
    @Test void shouldCompleteTheSharedTimestamp_withPermittedRowsOnly() {
        // Rows 2 to 9 share one second; the consent policy hides the even ones.
        var source = rowsAt(1,2,2,2,2,2,2,2,2,3);
        Function<List<Appointment>,List<Appointment>> oddOnly = batch -> batch.stream().filter(row -> row.getId()%2==1).toList();
        assertThat(legacy(source,new Date(0),2,oddOnly,new AtomicInteger())).extracting(Appointment::getId).containsExactly(1,3,5,7,9);
        assertThat(synchronize(source,2,oddOnly,new ArrayList<>())).containsExactly(1,3,5,7,9);
        // A boundary whose remaining rows of the same second are all hidden is not a shared boundary.
        Function<List<Appointment>,List<Appointment>> firstThree = batch -> batch.stream().filter(row -> row.getId()<=3).toList();
        assertThat(legacy(source,new Date(0),3,firstThree,new AtomicInteger())).extracting(Appointment::getId).containsExactly(1,2,3);
    }
    @Test void shouldFillTheHardLimitExactly_whenTheSharedTimestampEndsThere() {
        var source = rows(5000); var later = new Appointment(); later.setId(5001); later.setUpdateDateTime(new Date(2000)); source.add(later);
        var queries = new AtomicInteger();
        assertThat(legacy(source,new Date(0),2,Function.identity(),queries)).hasSize(5000)
                .extracting(Appointment::getId).last().isEqualTo(5000);
        assertThat(queries.get()).isLessThanOrEqualTo(12);
    }
    @Test void shouldReturnTheRequestedRowsWithoutAFault_whenTheHardLimitCannotHoldTheSharedTimestamp() {
        // 5,001 rows written in one second cannot fit any response; the pre-2026.08 answer is the requested page.
        var source = rows(5001); var later = new Appointment(); later.setId(5002); later.setUpdateDateTime(new Date(2000)); source.add(later);
        try (var log = io.github.carlos_emr.carlos.test.logging.LogCapture.forLogger(AppointmentUpdatePages.class)) {
            assertThat(legacy(source,new Date(0),7,Function.identity(),new AtomicInteger()))
                    .extracting(Appointment::getId).containsExactly(1,2,3,4,5,6,7);
            assertThat(legacy(source,new Date(0),5000,Function.identity(),new AtomicInteger())).hasSize(5000);
            assertThat(log.messages()).hasSize(2).allSatisfy(message -> assertThat(message).contains("timestamp-id"));
        }
        // The client is not stopped: it moves past the oversized second exactly as it did before 2026.08.
        var pageSizes = new ArrayList<Integer>();
        assertThat(synchronize(source,7,Function.identity(),pageSizes)).containsExactly(1,2,3,4,5,6,7,5002);
        assertThat(pageSizes).containsExactly(7,1);
    }
    @Test void shouldRejectAMissingBoundOrAnOutOfRangePageSize_forTimestampOnlyPages() {
        for (int limit : List.of(0,-1,5001)) {
            assertThatThrownBy(() -> legacy(rows(3),new Date(0),limit,Function.identity(),new AtomicInteger()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> legacy(rows(3),null,2,Function.identity(),new AtomicInteger()))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void shouldPropagateAFailureWhileCompletingTheSharedTimestamp_insteadOfReturningAnIncompletePage() {
        var failure = new IllegalStateException("controlled query failure"); var queries = new AtomicInteger();
        assertThatThrownBy(() -> AppointmentUpdatePages.legacy(new Date(0),2,(position,batch) -> {
            if (queries.incrementAndGet()>1) throw failure;
            return rows(3).stream().limit(batch).toList();
        },Function.identity(),Appointment::getUpdateDateTime,Appointment::getId)).isSameAs(failure);
    }
    @Test void shouldKeepThePublishedHardLimit_withoutRequestingAnOversizedSentinelQuery() {
        var queries = new AtomicInteger();
        var page = read(rows(5001),new Date(0),Integer.MAX_VALUE,5000,Function.identity(),queries);
        assertThat(page.items()).hasSize(5000); assertThat(page.hasMore()).isTrue();
        assertThat(page.afterId()).isEqualTo(5000); assertThat(queries.get()).isEqualTo(11);
    }
    @Test void shouldPropagateALaterPolicyFailure_insteadOfReturningAnIncompleteSuccessfulPage() {
        var failure = new IllegalStateException("controlled policy failure"); var policies = new AtomicInteger();
        assertThatThrownBy(() -> read(rows(8),new Date(0),Integer.MAX_VALUE,1,batch -> {
            if (policies.incrementAndGet()>1) throw failure; return List.of(batch.getFirst());
        },new AtomicInteger())).isSameAs(failure);
    }
    @Test void shouldRejectNonprogressingBackendRows_insteadOfLoopingOrDroppingDuplicates() {
        var row = rows(1).getFirst();
        assertThatThrownBy(() -> AppointmentUpdatePages.read(new Date(1000),1,2,
                (position,batch) -> List.of(row),Function.identity(),Appointment::getUpdateDateTime,Appointment::getId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not uniquely ordered");
    }
    @Test void shouldRejectPolicyReplacementAndReordering_insteadOfTrustingAChangedCursor() {
        var source = rows(3);
        assertThatThrownBy(() -> read(source,new Date(0),Integer.MAX_VALUE,2,batch -> {
            java.util.Collections.reverse(batch); return batch;
        },new AtomicInteger())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> read(source,new Date(0),Integer.MAX_VALUE,2,batch -> rows(1),new AtomicInteger()))
                .isInstanceOf(IllegalStateException.class);
    }
}
