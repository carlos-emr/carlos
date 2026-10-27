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
package io.github.carlos_emr.carlos.demographic.cdsimport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/** Transport must remain controllable even when SAX emits no clinical content callbacks. */
@Tag("unit")
class CdsXmlTransportControlUnitTest {
    @TempDir Path temp;

    @BeforeEach
    void warmSchemaOutsideTheMeasuredDeadline() throws Exception {
        Path source = Files.writeString(temp.resolve("warm.xml"), CdsXmlParserUnitTest.fixture());
        new CdsXmlParser()
                .parse(
                        source,
                        temp.resolve("warm-spool"),
                        limits(60000),
                        () -> false,
                        record -> {});
    }

    @Test
    void shouldSealValidSourceWithoutExcessiveProbes_whenManyShortCommentsOrInstructionsFollowRoot()
            throws Exception {
        for (String kind : new String[] {"comment", "pi"}) {
            Path source = source(kind);
            Path spool = temp.resolve("success-" + kind);
            AtomicInteger probes = new AtomicInteger();
            var result =
                    new CdsXmlParser()
                            .parse(
                                    source,
                                    spool,
                                    limits(60000),
                                    () -> {
                                        probes.incrementAndGet();
                                        return false;
                                    },
                                    record -> {});
            assertEquals(Files.size(source), result.sourceBytes());
            assertEquals(4, result.records());
            assertTrue(Files.exists(spool.resolve("parse.seal")));
            assertTrue(
                    probes.get() < 20,
                    "Durable probes must remain throttled, not run per read/byte/comment");
        }
    }

    @Test
    void shouldStopBeforeEof_whenCancellationArrivesInsideManyBoundedCommentsOrInstructions()
            throws Exception {
        for (String kind : new String[] {"comment", "pi"}) {
            var input = new ControlledSource(kind, "cancel");
            CdsInputException failure =
                    assertThrows(
                            CdsInputException.class,
                            () ->
                                    input.parse(
                                            limits(60000),
                                            () -> input.consumed.get() >= input.cut));
            assertEquals("CDS_INPUT_CANCELLED", failure.code());
            input.assertStoppedInSuffix();
        }
    }

    @Test
    void shouldStopBeforeEof_whenDeadlineExpiresInsideManyBoundedCommentsOrInstructions()
            throws Exception {
        for (String kind : new String[] {"comment", "pi"}) {
            var input = new ControlledSource(kind, "deadline");
            CdsInputException failure =
                    assertThrows(
                            CdsInputException.class, () -> input.parse(limits(500), () -> false));
            assertEquals("CDS_INPUT_DEADLINE", failure.code());
            input.assertStoppedInSuffix();
        }
    }

    @Test
    void shouldStopBeforeEofAndPreserveInterrupt_whenInterruptionArrivesInBoundedMarkupSuffix()
            throws Exception {
        for (String kind : new String[] {"comment", "pi"}) {
            var input = new ControlledSource(kind, "interrupt");
            try {
                CdsInputException failure =
                        assertThrows(
                                CdsInputException.class,
                                () -> input.parse(limits(60000), () -> false));
                assertEquals("CDS_INPUT_CANCELLED", failure.code());
                assertTrue(Thread.currentThread().isInterrupted());
                input.assertStoppedInSuffix();
            } finally {
                Thread.interrupted();
            }
        }
    }

    @Test
    void shouldRefuseBeforeSeal_whenDistinctProcessingInstructionTargetsExceedTheNameBudget()
            throws Exception {
        Path source =
                Files.writeString(
                        temp.resolve("distinct-targets.xml"), CdsXmlParserUnitTest.fixture());
        try (var output =
                Files.newBufferedWriter(
                        source, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            for (int i = 0; i < 5000; i++) output.write("<?source_" + i + " bounded?>");
        }
        Path spool = temp.resolve("distinct-targets-spool");
        CdsInputException failure =
                assertThrows(
                        CdsInputException.class,
                        () ->
                                new CdsXmlParser()
                                        .parse(
                                                source,
                                                spool,
                                                limits(60000),
                                                () -> false,
                                                record -> {}));
        assertEquals("CDS_XML_NAME_RESOURCE_LIMIT", failure.code());
        assertFalse(Files.exists(spool.resolve("parse.seal")));
    }

    @Test
    void shouldRefuseBeforeSeal_whenAProcessingInstructionTargetExceedsTheNameLengthBudget()
            throws Exception {
        Path source =
                Files.writeString(
                        temp.resolve("long-target.xml"),
                        CdsXmlParserUnitTest.fixture() + "<?" + "s".repeat(257) + " bounded?>");
        Path spool = temp.resolve("long-target-spool");
        CdsInputException failure =
                assertThrows(
                        CdsInputException.class,
                        () ->
                                new CdsXmlParser()
                                        .parse(
                                                source,
                                                spool,
                                                limits(60000),
                                                () -> false,
                                                record -> {}));
        assertEquals("CDS_XML_NAME_RESOURCE_LIMIT", failure.code());
        assertFalse(Files.exists(spool.resolve("parse.seal")));
    }

    private Path source(String kind) throws Exception {
        Path source = temp.resolve(kind + "-" + System.nanoTime() + ".xml");
        Files.writeString(source, CdsXmlParserUnitTest.fixture(), StandardCharsets.UTF_8);
        String token =
                kind.equals("comment")
                        ? "<!-- Short source metadata -->"
                        : "<?import-source metadata?>";
        String block = token.repeat(1024);
        try (var output =
                Files.newBufferedWriter(
                        source, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            for (int i = 0; i < 64; i++) output.write(block);
        }
        return source;
    }

    private final class ControlledSource {
        private final String mode;
        private final Path source, spool;
        private final long cut =
                CdsXmlParserUnitTest.fixture().getBytes(StandardCharsets.UTF_8).length + 32768L;
        private final AtomicLong consumed = new AtomicLong();
        private final AtomicInteger records = new AtomicInteger();
        private final AtomicBoolean triggered = new AtomicBoolean();

        ControlledSource(String kind, String mode) throws Exception {
            this.mode = mode;
            source = source(kind);
            spool = temp.resolve(mode + "-" + kind);
        }

        void parse(CdsInputLimitsDto limits, BooleanSupplier cancelled) throws Exception {
            InputStream raw = Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS);
            try (InputStream controlled =
                            new FilterInputStream(raw) {
                                @Override
                                public int read() throws IOException {
                                    int value = in.read();
                                    if (value >= 0) afterRead(1);
                                    return value;
                                }

                                @Override
                                public int read(byte[] bytes, int offset, int length)
                                        throws IOException {
                                    int count = in.read(bytes, offset, Math.min(length, 4096));
                                    if (count > 0) afterRead(count);
                                    return count;
                                }

                                private void afterRead(int count) {
                                    if (consumed.addAndGet(count) < cut) return;
                                    if (mode.equals("cancel")) LockSupport.parkNanos(2_000_000L);
                                    else if (triggered.compareAndSet(false, true)) {
                                        if (mode.equals("deadline"))
                                            LockSupport.parkNanos(800_000_000L);
                                        else Thread.currentThread().interrupt();
                                    }
                                }
                            };
                    var io = mockStatic(Files.class, CALLS_REAL_METHODS)) {
                io.when(() -> Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS))
                        .thenReturn(controlled);
                new CdsXmlParser()
                        .parse(
                                source,
                                spool,
                                limits,
                                cancelled,
                                record -> records.incrementAndGet());
            }
        }

        void assertStoppedInSuffix() throws Exception {
            assertEquals(
                    4,
                    records.get(),
                    "The root's records must finish before the ignored suffix triggers control");
            assertTrue(consumed.get() >= cut);
            assertTrue(consumed.get() < Files.size(source), "Control waited until EOF");
            assertFalse(
                    Files.exists(spool.resolve("parse.seal")),
                    "Interrupted/incomplete XML must not publish a success seal");
        }
    }

    private static CdsInputLimitsDto limits(long maximumMillis) {
        var d = CdsInputLimitsDto.defaults();
        return new CdsInputLimitsDto(
                d.maxSourceBytes(),
                d.maxExpandedBytes(),
                d.maxEntries(),
                d.maxXmlDepth(),
                d.maxBoundedScalarCharacters(),
                d.maxBinaryBytes(),
                0,
                maximumMillis,
                d.maxPathCharacters(),
                d.maxCompressionRatio(),
                d.maxNamespaceAliases(),
                d.maxUniqueXmlNames(),
                d.maxXmlNameCharacters(),
                d.maxNamespaceUriCharacters());
    }
}
