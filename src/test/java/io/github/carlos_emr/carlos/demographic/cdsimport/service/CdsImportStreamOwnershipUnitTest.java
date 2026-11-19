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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;

@Tag("unit")
class CdsImportStreamOwnershipUnitTest {
    @TempDir Path temporary;

    @Test
    void shouldCloseEarlierSpoolFilesWithoutSeal_whenLaterOutputCannotOpen() throws Exception {
        Path source = Files.writeString(temporary.resolve("source.xml"), "<unused/>");
        Path spool = temporary.resolve("spool");
        var fields = mock(OutputStream.class);
        var values = mock(OutputStream.class);
        try (var files = mockStatic(CdsPaths.class, CALLS_REAL_METHODS)) {
            files.when(() -> CdsPaths.newPrivateFile(any(Path.class), any(Path.class)))
                    .thenAnswer(
                            call -> {
                                return switch (call.<Path>getArgument(0).getFileName().toString()) {
                                    case "fields.jsonl" -> fields;
                                    case "values.bin" -> values;
                                    default ->
                                            throw new IOException("synthetic later output failure");
                                };
                            });
            assertThatThrownBy(
                            () ->
                                    new CdsXmlParser()
                                            .parse(
                                                    source,
                                                    spool,
                                                    CdsInputLimitsDto.defaults(),
                                                    () -> false,
                                                    record -> {}))
                    .hasMessageContaining("CDS_XML_READ_FAILED");
        }
        verify(fields, atLeastOnce()).close();
        verify(values, atLeastOnce()).close();
        assertThat(Files.exists(spool.resolve("parse.seal"))).isFalse();
    }

    @Test
    void shouldCloseAllSpoolFilesWithoutSeal_whenValidatorConstructionFailsWithAnError()
            throws Exception {
        Path source = Files.writeString(temporary.resolve("source.xml"), "<unused/>");
        Path spool = temporary.resolve("spool");
        var fields = mock(OutputStream.class);
        var values = mock(OutputStream.class);
        var manifest = mock(OutputStream.class);
        var diagnostics = mock(OutputStream.class);
        var failure = new AssertionError("synthetic validator initialization failure");
        try (var files = mockStatic(CdsPaths.class, CALLS_REAL_METHODS);
                var schema = mockStatic(CdsSchemaValidator.class)) {
            files.when(() -> CdsPaths.newPrivateFile(any(Path.class), any(Path.class)))
                    .thenAnswer(
                            call -> {
                                return switch (call.<Path>getArgument(0).getFileName().toString()) {
                                    case "fields.jsonl" -> fields;
                                    case "values.bin" -> values;
                                    case "records.jsonl" -> manifest;
                                    case "validation-errors.jsonl" -> diagnostics;
                                    default -> throw new AssertionError("unexpected output");
                                };
                            });
            schema.when(CdsSchemaValidator::newValidator).thenThrow(failure);
            assertThatThrownBy(
                            () ->
                                    new CdsXmlParser()
                                            .parse(
                                                    source,
                                                    spool,
                                                    CdsInputLimitsDto.defaults(),
                                                    () -> false,
                                                    record -> {}))
                    .isSameAs(failure);
        }
        for (var output : new OutputStream[] {fields, values, manifest, diagnostics})
            verify(output, atLeastOnce()).close();
        assertThat(Files.exists(spool.resolve("parse.seal"))).isFalse();
    }

    @Test
    void shouldCloseNewDirectoryAndRoot_whenClosingPredecessorFails() throws Exception {
        var root = directory();
        var first = directory();
        var second = directory();
        when(root.newDirectoryStream(Path.of("a"), LinkOption.NOFOLLOW_LINKS)).thenReturn(first);
        when(first.newDirectoryStream(Path.of("b"), LinkOption.NOFOLLOW_LINKS)).thenReturn(second);
        var failure = new IOException("synthetic predecessor close failure");
        doThrow(failure).when(first).close();
        assertThatThrownBy(
                        () ->
                                new CdsSnapshotService.SecureSourceInputStream(
                                        root, Path.of("a/b"), Path.of("patient.xml")))
                .isSameAs(failure);
        verify(first).close();
        verify(second).close();
        verify(root).close();
        verify(second, never()).newByteChannel(any(), any());
    }

    @Test
    void shouldCloseAlreadyOpenedSource_whenDirectoryCleanupAbortsTheReturn() throws Exception {
        var root = directory();
        var child = directory();
        var channel = mock(SeekableByteChannel.class);
        when(root.newDirectoryStream(Path.of("source"), LinkOption.NOFOLLOW_LINKS))
                .thenReturn(child);
        when(child.newByteChannel(eq(Path.of("patient.xml")), any())).thenReturn(channel);
        var childFailure = new IOException("synthetic child close failure");
        var rootFailure = new IOException("synthetic root close failure");
        var channelFailure = new IOException("synthetic channel close failure");
        doThrow(childFailure).when(child).close();
        doThrow(rootFailure).when(root).close();
        doThrow(channelFailure).when(channel).close();
        assertThatThrownBy(
                        () ->
                                new CdsSnapshotService.SecureSourceInputStream(
                                        root, Path.of("source"), Path.of("patient.xml")))
                .isSameAs(childFailure)
                .satisfies(
                        failure ->
                                assertThat(failure.getSuppressed())
                                        .containsExactly(rootFailure, channelFailure));
        verify(child).close();
        verify(root).close();
        verify(channel).close();
    }

    @Test
    void shouldCloseEveryDirectory_whenSourceOpeningFailsWithAnError() throws Exception {
        var root = directory();
        var child = directory();
        when(root.newDirectoryStream(Path.of("source"), LinkOption.NOFOLLOW_LINKS))
                .thenReturn(child);
        var failure = new AssertionError("synthetic open failure");
        when(child.newByteChannel(eq(Path.of("patient.xml")), any())).thenThrow(failure);
        assertThatThrownBy(
                        () ->
                                new CdsSnapshotService.SecureSourceInputStream(
                                        root, Path.of("source"), Path.of("patient.xml")))
                .isSameAs(failure);
        verify(child).close();
        verify(root).close();
    }

    @Test
    void shouldTransferOnlyTheSourceChannel_whenDirectoryCleanupSucceeds() throws Exception {
        var root = directory();
        var child = directory();
        var channel = mock(SeekableByteChannel.class);
        when(root.newDirectoryStream(Path.of("source"), LinkOption.NOFOLLOW_LINKS))
                .thenReturn(child);
        when(child.newByteChannel(eq(Path.of("patient.xml")), any())).thenReturn(channel);
        when(channel.read(any(ByteBuffer.class)))
                .thenAnswer(
                        call -> {
                            call.<ByteBuffer>getArgument(0).put((byte) 42);
                            return 1;
                        });
        var input =
                new CdsSnapshotService.SecureSourceInputStream(
                        root, Path.of("source"), Path.of("patient.xml"));
        verify(child).close();
        verify(root).close();
        verify(channel, never()).close();
        try (input) {
            assertThat(input.read()).isEqualTo(42);
        }
        verify(channel).close();
    }

    @Test
    void shouldCloseUnsupportedRootDirectory_whenSecureOpenIsUnavailable() throws Exception {
        DirectoryStream<Path> root = plainDirectory();
        assertThatThrownBy(
                        () ->
                                new CdsSnapshotService.SecureSourceInputStream(
                                        root, Path.of("source"), Path.of("patient.xml")))
                .hasMessageContaining("CDS_SECURE_SOURCE_OPEN_UNSUPPORTED");
        verify(root).close();
    }

    @Test
    void shouldCloseSliceChannel_whenItsRangeIsInvalid() throws Exception {
        var channel = mock(FileChannel.class);
        when(channel.size()).thenReturn(10L);
        assertThatThrownBy(() -> new CdsFieldParser.SliceInputStream(channel, 11, 0))
                .hasMessageContaining("CDS_SPOOL_RANGE_INVALID");
        verify(channel).close();
    }

    @Test
    void shouldCloseSliceChannelAndKeepSecondaryFailure_whenInitializationThrowsAnError()
            throws Exception {
        var channel = mock(FileChannel.class);
        var failure = new AssertionError("synthetic size failure");
        var closeFailure = new IOException("synthetic close failure");
        when(channel.size()).thenThrow(failure);
        doThrow(closeFailure).when(channel).close();
        assertThatThrownBy(() -> new CdsFieldParser.SliceInputStream(channel, 0, 1))
                .isSameAs(failure)
                .satisfies(
                        thrown -> assertThat(thrown.getSuppressed()).containsExactly(closeFailure));
        verify(channel).close();
    }

    @Test
    void shouldCloseSliceChannel_whenPositioningFails() throws Exception {
        var channel = mock(FileChannel.class);
        when(channel.size()).thenReturn(10L);
        var failure = new IOException("synthetic seek failure");
        when(channel.position(2)).thenThrow(failure);
        assertThatThrownBy(() -> new CdsFieldParser.SliceInputStream(channel, 2, 3))
                .isSameAs(failure);
        verify(channel).close();
    }

    @Test
    void shouldRespectSliceLengthAndCloseOwner_whenReadingSucceeds() throws Exception {
        var channel = mock(FileChannel.class);
        when(channel.size()).thenReturn(10L);
        when(channel.read(any(ByteBuffer.class)))
                .thenAnswer(
                        call -> {
                            var buffer = call.<ByteBuffer>getArgument(0);
                            assertThat(buffer.remaining()).isEqualTo(3);
                            buffer.put(new byte[] {1, 2, 3});
                            return 3;
                        });
        try (var input = new CdsFieldParser.SliceInputStream(channel, 2, 3)) {
            assertThat(input.read(new byte[10])).isEqualTo(3);
            assertThat(input.read()).isEqualTo(-1);
        }
        verify(channel).position(2);
        verify(channel, times(1)).read(any(ByteBuffer.class));
        verify(channel).close();
    }

    @SuppressWarnings("unchecked")
    private static SecureDirectoryStream<Path> directory() {
        return mock(SecureDirectoryStream.class);
    }

    @SuppressWarnings("unchecked")
    private static DirectoryStream<Path> plainDirectory() {
        return mock(DirectoryStream.class);
    }
}
