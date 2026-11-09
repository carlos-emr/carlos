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
    @Test void shouldFaultLegacyTimestampPaging_whenTheNextPermittedRowSharesTheBoundaryTime() {
        var page = read(rows(3),new Date(0),Integer.MAX_VALUE,2,Function.identity(),new AtomicInteger());
        assertThatThrownBy(() -> AppointmentUpdatePages.legacy(page)).isInstanceOf(ScheduleSyncBoundaryException.class);
        var complete = read(rows(2),new Date(0),Integer.MAX_VALUE,2,Function.identity(),new AtomicInteger());
        assertThat(AppointmentUpdatePages.legacy(complete)).hasSize(2);
    }
    @Test void shouldRetainLegacyPages_whenTheNextPermittedTimestampIsLater() {
        var source = rows(3); source.getLast().setUpdateDateTime(new Date(2000));
        var page = read(source,new Date(0),Integer.MAX_VALUE,2,Function.identity(),new AtomicInteger());
        assertThat(AppointmentUpdatePages.legacy(page)).extracting(Appointment::getId).containsExactly(1,2);
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
