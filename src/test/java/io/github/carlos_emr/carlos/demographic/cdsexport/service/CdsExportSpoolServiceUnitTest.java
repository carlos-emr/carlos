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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The protected spool keeps a roster's packaging records on encrypted storage: order, sort,
 * bounded memory, erasure and a loud storage failure. Synthetic keys and values only.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportSpoolServiceUnitTest {
    @TempDir Path root;
    private CdsExportStagingService store;

    @BeforeEach
    void prepare() throws Exception {
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        store = store(0);
    }

    private CdsExportStagingService store(long reserve) {
        return new CdsExportStagingService(
                root,
                UUID.randomUUID().toString(),
                1,
                new SecretKeySpec(new byte[32], "AES"),
                reserve,
                () -> {});
    }

    private long files() throws IOException {
        try (var listed = Files.list(root)) {
            return listed.count();
        }
    }

    private static byte[] value(int index) {
        return ("value-" + index).getBytes(StandardCharsets.UTF_8);
    }

    private static List<String> keys(CdsExportSpoolService spool) throws IOException {
        var keys = new ArrayList<String>();
        try (var cursor = spool.open()) {
            CdsExportSpoolService.Entry entry;
            while ((entry = cursor.next()) != null) keys.add(entry.key());
        }
        return keys;
    }

    @Test
    void shouldReturnEveryRecordInAppendOrder_afterSpillingThroughSeveralLevels()
            throws Exception {
        int count = 3000;
        // 256 bytes of batch and a fan-in of 3: about one segment per three records, and six or
        // seven levels of combined segments.
        try (var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 256, 3)) {
            for (int i = 0; i < count; i++) spool.append("k" + i, value(i));
            assertThat(spool.records()).isEqualTo(count);
            int index = 0;
            try (var cursor = spool.open()) {
                CdsExportSpoolService.Entry entry;
                while ((entry = cursor.next()) != null) {
                    assertThat(entry.key()).isEqualTo("k" + index);
                    assertThat(entry.value()).isEqualTo(value(index));
                    index++;
                }
            }
            assertThat(index).isEqualTo(count);
            // The records went to protected storage ...
            assertThat(spool.segmentsWritten()).isGreaterThan(count / 4);
            // ... and what stays in memory does not grow with them: fewer than the fan-in per
            // level, for a number of levels that is the logarithm of the segment count.
            assertThat(spool.segmentsHeld()).isLessThanOrEqualTo(2 * 8 + 1);
            assertThat(files()).isEqualTo(spool.segmentsHeld());
        }
        assertThat(files()).isZero();
    }

    @Test
    void shouldHoldNoMoreDescriptors_whenTheRecordCountGrowsTenfold() throws Exception {
        int[] held = new int[2];
        int[] counts = {400, 4000};
        for (int round = 0; round < 2; round++)
            try (var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 64, 4)) {
                int most = 0;
                for (int i = 0; i < counts[round]; i++) {
                    spool.append("k" + i, value(i));
                    most = Math.max(most, spool.segmentsHeld());
                }
                held[round] = most;
                assertThat(spool.segmentsWritten()).isGreaterThanOrEqualTo(counts[round] - 1L);
            }
        // Ten times the records: two more levels of at most three descriptors each (and a
        // fuller top level), not ten times the descriptors.
        assertThat(held[0]).isLessThanOrEqualTo(3 * 5);
        assertThat(held[1]).isLessThanOrEqualTo(held[0] + 8);
    }

    @Test
    void shouldSortByKeyThroughAnExternalMerge_keepingEqualKeysInAppendOrder() throws Exception {
        var random = new Random(42);
        var expected = new ArrayList<String[]>();
        try (var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 512, 3)) {
            for (int i = 0; i < 2500; i++) {
                // 300 distinct keys, so most keys repeat; non-ASCII keys order as Strings do.
                String key = (random.nextInt(4) == 0 ? "É" : "k") + random.nextInt(300);
                spool.append(key, value(i));
                expected.add(new String[] {key, "value-" + i});
            }
            expected.sort(Comparator.comparing(pair -> pair[0]));
            try (var sorted = spool.sorted("TEST_SORT");
                    var cursor = sorted.open()) {
                assertThat(sorted.records()).isEqualTo(2500);
                for (String[] pair : expected) {
                    var entry = cursor.next();
                    assertThat(entry.key()).isEqualTo(pair[0]);
                    assertThat(new String(entry.value(), StandardCharsets.UTF_8))
                            .isEqualTo(pair[1]);
                }
                assertThat(cursor.next()).isNull();
            }
            // The source spool is untouched by the sort and its intermediates are erased.
            assertThat(keys(spool)).hasSize(2500);
            assertThat(files()).isEqualTo(spool.segmentsHeld());
        }
        assertThat(files()).isZero();
    }

    @Test
    void shouldReadAnEmptySpool_andSortIt() throws Exception {
        try (var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {});
                var sorted = spool.sorted("TEST_SORT")) {
            assertThat(keys(spool)).isEmpty();
            assertThat(keys(sorted)).isEmpty();
        }
        assertThat(files()).isZero();
    }

    @Test
    void shouldAcceptARecordLargerThanOneBatch_withoutRefusingItsSize() throws Exception {
        byte[] large = new byte[300_000];
        new Random(3).nextBytes(large);
        try (var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 1024, 3)) {
            spool.append("small", value(1));
            spool.append("large", large);
            spool.append("after", value(2));
            try (var cursor = spool.open()) {
                assertThat(cursor.next().key()).isEqualTo("small");
                assertThat(cursor.next().value()).isEqualTo(large);
                assertThat(cursor.next().key()).isEqualTo("after");
                assertThat(cursor.next()).isNull();
            }
        }
    }

    @Test
    void shouldStopWithTheStoresReserveCode_andLeaveNothingTruncatedBehind() throws Exception {
        // A store whose free space is below its reserve: the genuine storage failure.
        var full = store(Long.MAX_VALUE);
        var spool = new CdsExportSpoolService(full, "TEST_SPOOL", () -> {}, 64, 3);
        spool.append("first", value(1));
        assertThatThrownBy(() -> spool.append("second", new byte[200]))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_DISK_RESERVE");
        // A failed spool answers nothing more: no partial read can stand in for the records.
        assertThatThrownBy(spool::open).hasMessage("CDS_EXPORT_SPOOL_SCOPE_INVALID");
        assertThatThrownBy(() -> spool.append("third", value(3)))
                .hasMessage("CDS_EXPORT_SPOOL_SCOPE_INVALID");
        spool.close();
        assertThat(files()).isZero();
    }

    @Test
    void shouldRefuseAnAlteredSegment_insteadOfReturningItsRecords() throws Exception {
        var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 4096, 3);
        for (int i = 0; i < 20; i++) spool.append("k" + i, value(i));
        assertThat(keys(spool)).hasSize(20);
        Path segment;
        try (var listed = Files.list(root)) {
            segment = listed.findFirst().orElseThrow();
        }
        byte[] bytes = Files.readAllBytes(segment);
        bytes[bytes.length - 40] ^= 1;
        Files.write(segment, bytes);
        assertThatThrownBy(() -> keys(spool))
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
        // A member that no longer authenticates is not erased by the spool either: it stays for
        // the attempt's own purge, as every unverifiable staging member does.
        assertThatThrownBy(spool::close)
                .isInstanceOf(IOException.class)
                .hasMessage("CDS_EXPORT_STAGED_AUTHENTICATION_FAILED");
        assertThat(files()).isEqualTo(1);
    }

    @Test
    void shouldRefuseUseAfterClose_andEraseItsSegments() throws Exception {
        var spool = new CdsExportSpoolService(store, "TEST_SPOOL", () -> {}, 64, 3);
        for (int i = 0; i < 50; i++) spool.append("k" + i, value(i));
        assertThat(keys(spool)).hasSize(50);
        assertThat(files()).isPositive();
        spool.close();
        spool.close();
        assertThat(files()).isZero();
        assertThatThrownBy(() -> spool.append("late", value(0)))
                .hasMessage("CDS_EXPORT_SPOOL_SCOPE_INVALID");
        assertThat(spool).hasToString("CdsExportSpoolService[protected]");
    }
}
