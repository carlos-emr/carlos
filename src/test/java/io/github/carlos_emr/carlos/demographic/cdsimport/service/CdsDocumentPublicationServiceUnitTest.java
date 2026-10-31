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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsDocumentPublicationServiceUnitTest {
    @TempDir Path directory;
    private final String unit="a".repeat(64);

    @Test void shouldPublishExclusiveDurableBytes_whenPayloadIsVerified() throws Exception {
        byte[] content="synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CdsImportFileService files=mock(CdsImportFileService.class);
        var service=service(files);
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            var result=service.publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content));
            assertThat(Files.readAllBytes(Path.of(result.location()))).isEqualTo(content);
            assertThat(Files.getPosixFilePermissions(Path.of(result.location())))
                    .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
            verify(files).publishedVerified("run",1,result.location(),sha(content),content.length);
            assertThat(result.filename()).doesNotContain("synthetic");
            try(var children=Files.walk(directory)) { assertThat(children.filter(Files::isRegularFile).count()).isEqualTo(1); }
        }
    }

    @Test void shouldStopPublicationAndExistingVerification_whenScopedOwnerIsLost() throws Exception {
        byte[] content="synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var files=mock(CdsImportFileService.class); var fields=mock(CdsFieldParser.class);
        var service=service(files,fields);
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            doThrow(new CdsImportException("OWNER_FENCED")).when(fields).checkpoint();
            assertThatThrownBy(()->service.publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class).hasMessageContaining("OWNER_FENCED");
            verifyNoInteractions(files);
            reset(fields);
            var published=service.publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content));
            doThrow(new CdsImportException("OWNER_FENCED")).when(fields).checkpoint();
            assertThatThrownBy(()->service.publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class).hasMessageContaining("OWNER_FENCED");
            assertThat(Files.readAllBytes(Path.of(published.location()))).isEqualTo(content);
            verify(files,times(1)).publishedVerified("run",1,published.location(),sha(content),content.length);
        }
    }

    @Test void shouldRetireOnlyUnpublishedPartial_whenActualCopyExhaustsAttemptBudgetOutsideTransaction() throws Exception {
        byte[] content=new byte[3*65536];java.util.Arrays.fill(content,(byte)65);
        var files=mock(CdsImportFileService.class);var fields=new CdsFieldParser();
        var clock=new java.util.concurrent.atomic.AtomicLong();var reads=new java.util.concurrent.atomic.AtomicInteger();
        var owner=mock(CdsImportCoordinatorService.Owner.class);
        when(owner.control("run")).thenReturn(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportControlDto(true,true,1,false,true));
        Runnable budget=()->{if(clock.get()>=100_000_000)throw new CdsImportException("IMPORT_ATTEMPT_TIMEOUT");};
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS);
                var control=new CdsImportExecutionControl("run",1,owner,"IMPORT_PREPARATION_TIMEOUT",10000,clock::get,budget,fields,mock(CdsImportMaintenanceService.Owner.class))) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(()->service(files,fields).publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new java.io.FilterInputStream(new ByteArrayInputStream(content)) {
                        @Override public int read(byte[] bytes,int offset,int length) throws java.io.IOException {
                            int read=super.read(bytes,offset,Math.min(length,65536));
                            if(read>0 && reads.incrementAndGet()==2) {
                                try(var walk=Files.walk(directory)) {
                                    var partial=walk.filter(path->path.getFileName().toString().equals("payload.partial")).findFirst().orElseThrow();
                                    assertThat(Files.size(partial)).isEqualTo(65536);
                                    assertThat(Files.getPosixFilePermissions(partial))
                                            .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                                }
                                clock.set(100_000_000);
                            }
                            return read;
                        }
                    })).hasMessage("IMPORT_ATTEMPT_TIMEOUT");
            assertThat(reads).hasValue(2);
            try(var walk=Files.walk(directory)){assertThat(walk.filter(Files::isRegularFile).count()).isZero();}
            verifyNoInteractions(files);verify(owner,times(1)).control("run");
        }
    }

    @Test void shouldBoundExistingFileVerification_whenFileGrowsAfterItsInitialLengthCheck() throws Exception {
        byte[] content="synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var files=mock(CdsImportFileService.class); var fields=mock(CdsFieldParser.class);
        Path existing=directory.resolve("cds-"+unit+"-embedded-"+sha(content)+".txt"); Files.write(existing,content);
        doAnswer(invocation->{
            Files.write(existing,new byte[65536],java.nio.file.StandardOpenOption.APPEND);
            return null;
        }).when(fields).checkpoint();
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(()->service(files,fields).publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class).hasMessageContaining("DOCUMENT_PUBLICATION_CONFLICT");
            verifyNoInteractions(files);
            verify(fields,times(2)).checkpoint();
        }
    }

    @Test void shouldRefuseAndPreserveExistingBytes_whenSameLocationHasAnotherGeneration() throws Exception {
        byte[] content="expected".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path existing=directory.resolve("cds-"+unit+"-embedded-"+sha(content)+".txt"); Files.writeString(existing,"foreign!");
        CdsImportFileService files=mock(CdsImportFileService.class);
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(()->service(files).publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class);
            assertThat(Files.readString(existing)).isEqualTo("foreign!");
            verifyNoInteractions(files);
        }
    }

    @Test void shouldRemoveOnlyOwnedPartial_whenCopyLengthDoesNotMatch() throws Exception {
        byte[] content="expected".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CdsImportFileService files=mock(CdsImportFileService.class);
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(()->service(files).publish("run",1,unit,"embedded","txt",sha(content),content.length-1,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class);
            try(var children=Files.walk(directory)) { assertThat(children.filter(Files::isRegularFile).count()).isZero(); }
            verifyNoInteractions(files);
        }
    }

    @Test void shouldRejectSymbolicLink_whenAnExistingPathPointsOutsideOwnedStorage() throws Exception {
        Path outside=Files.createTempFile("cds-publication-fixture-",".txt");
        try {
            Files.writeString(outside,"protected fixture");
            Files.createSymbolicLink(directory.resolve("cds-"+unit+"-embedded-"+"a".repeat(64)+".txt"),outside);
            try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
                paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
                assertThatThrownBy(()->service(mock(CdsImportFileService.class)).publish("run",1,unit,"embedded","txt",
                        "a".repeat(64),17,()->new ByteArrayInputStream(new byte[0]))).isInstanceOf(SecurityException.class);
                assertThat(Files.readString(outside)).isEqualTo("protected fixture");
            }
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void shouldRetainExactPrivateOwnership_whenDatabasePublicationAcknowledgementIsLost() throws Exception {
        byte[] content="synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        CdsImportFileService files=mock(CdsImportFileService.class);
        doThrow(new CdsImportException("PUBLICATION_ACKNOWLEDGEMENT_LOST")).when(files)
                .publishedVerified(eq("run"),eq(1L),anyString(),eq(sha(content)),eq((long)content.length));
        try (MockedStatic<PathValidationUtils> paths=mockStatic(PathValidationUtils.class,CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(()->service(files).publish("run",1,unit,"embedded","txt",sha(content),content.length,
                    ()->new ByteArrayInputStream(content))).isInstanceOf(CdsImportException.class);
            Path target=directory.resolve("cds-"+unit+"-embedded-"+sha(content)+".txt");
            try(var children=Files.walk(directory.resolve(".cds-pending/run"))) {
                var regular=children.filter(Files::isRegularFile).toList();
                assertThat(regular).hasSize(2);
                Path pending=regular.stream().filter(path->path.getFileName().toString().equals("payload.partial")).findFirst().orElseThrow();
                Path journal=regular.stream().filter(path->path.getFileName().toString().equals("owner.json")).findFirst().orElseThrow();
                assertThat(Files.isSameFile(target,pending)).isTrue();
                assertThat(Files.getPosixFilePermissions(target))
                        .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----"));
                assertThat(Files.getPosixFilePermissions(journal))
                        .isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                var owner=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(journal));
                assertThat(owner.path("runId").asText()).isEqualTo("run");
                assertThat(owner.path("digest").asText()).isEqualTo(sha(content));
                assertThat(owner.path("finalFilename").asText()).isEqualTo(target.getFileName().toString());
            }
        }
    }

    @Test void shouldPreserveCopyFailure_whenRetiringItsPrivateOperationAlsoFails() throws Exception {
        byte[] content = "synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var files = mock(CdsImportFileService.class);
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            Throwable failure = catchThrowable(() -> service(files).publish("run", 1, unit, "embedded", "txt",
                    sha(content), content.length, () -> {
                        addUnexpectedPrivateEntry();
                        return new ByteArrayInputStream(content) {
                            @Override public void close() throws java.io.IOException {
                                throw new java.io.IOException("COPY_CLOSE_FAILED");
                            }
                        };
                    }));
            assertThat(failure).isInstanceOf(java.io.IOException.class).hasMessage("COPY_CLOSE_FAILED");
            assertThat(failure.getSuppressed()).hasSize(1);
            assertThat(failure.getSuppressed()[0]).isInstanceOf(CdsImportException.class)
                    .hasMessage("DOCUMENT_PUBLICATION_CLEANUP_FAILED");
            verifyNoInteractions(files);
            try (var tree = Files.walk(directory)) {
                assertThat(tree.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).toList())
                        .containsExactly("unexpected-owned-fixture");
            }
        }
    }

    @Test void shouldReportRetirementFailure_whenPublicationRegistrationWasAcknowledged() throws Exception {
        byte[] content = "synthetic report".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var files = mock(CdsImportFileService.class);
        try (MockedStatic<PathValidationUtils> paths = mockStatic(PathValidationUtils.class, CALLS_REAL_METHODS)) {
            paths.when(PathValidationUtils::getRequiredDocumentDirectory).thenReturn(directory.toFile());
            assertThatThrownBy(() -> service(files).publish("run", 1, unit, "embedded", "txt",
                    sha(content), content.length, () -> {
                        addUnexpectedPrivateEntry();
                        return new ByteArrayInputStream(content);
                    })).isInstanceOf(CdsImportException.class).hasMessage("DOCUMENT_PUBLICATION_CLEANUP_FAILED");
            Path destination = directory.resolve("cds-" + unit + "-embedded-" + sha(content) + ".txt");
            assertThat(Files.readAllBytes(destination)).isEqualTo(content);
            verify(files).publishedVerified("run", 1, destination.toString(), sha(content), content.length);
        }
    }

    private void addUnexpectedPrivateEntry() throws java.io.IOException {
        try (var operations = Files.list(directory.resolve(".cds-pending/run"))) {
            Path operation = operations.findFirst().orElseThrow();
            Files.writeString(operation.resolve("unexpected-owned-fixture"), "fixture");
        }
    }

    private static String sha(byte[] content) { return CdsPaths.hex(CdsPaths.sha256().digest(content)); }
    private static CdsDocumentPublicationService service(CdsImportFileService files) {
        return service(files,new CdsFieldParser());
    }
    private static CdsDocumentPublicationService service(CdsImportFileService files,CdsFieldParser fields) {
        Properties properties=new Properties(); properties.setProperty("cds.import.minimumFreeDiskBytes","1");
        return new CdsDocumentPublicationService(files,new CdsImportConfigurationService(properties),fields);
    }
}
