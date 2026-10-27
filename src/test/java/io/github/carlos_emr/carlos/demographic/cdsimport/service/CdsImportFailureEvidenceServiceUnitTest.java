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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportFailureEvidenceServiceUnitTest {
    @TempDir Path root;
    @Test
    void shouldRetainOperationalCodesWithoutSensitiveMessagesOrFilenames_whenFailureEvidenceIsPublished() throws Exception {
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(root);
        var files=mock(CdsImportFileService.class);var service=new CdsImportFailureEvidenceService(jobs,files);
        String sentinel="SENSITIVE_SOURCE_PASSWORD_AND_SQL";
        var cause=new SQLException(sentinel,"22001",1406);
        cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("actual.FrameworkClass","persist",sentinel,45)});
        String location=service.write("run",9,new IllegalStateException(sentinel,cause));
        assertThat(location).isNotNull();String text=Files.readString(Path.of(location));
        assertThat(text).contains("IllegalStateException","SQLException","22001","1406","actual.FrameworkClass#persist:45").doesNotContain(sentinel);
        assertThat(Files.getPosixFilePermissions(Path.of(location))).isEqualTo(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        verify(files).reconcilePublication(eq("run"),eq(9L),eq(location),matches("[0-9a-f]{64}"),eq(Files.size(Path.of(location))));
    }
    @Test
    void shouldBoundCauseAndFrameCounts_whenExceptionChainsAreLarge() throws Exception {
        Throwable failure=new IllegalStateException("sentinel");
        for(int i=0;i<100;i++) failure=new IllegalStateException("sentinel",failure);
        for(Throwable cursor=failure;cursor!=null;cursor=cursor.getCause()) cursor.setStackTrace(IntStream.range(0,1000)
                .mapToObj(i->new StackTraceElement("Framework","method","SENSITIVE_FILENAME",i)).toArray(StackTraceElement[]::new));
        byte[] bytes=CdsImportFailureEvidenceService.document(1,failure);var document=new ObjectMapper().readTree(bytes);
        assertThat(bytes.length).isLessThanOrEqualTo(65536);assertThat(document.path("causes").size()).isEqualTo(16);
        int frames=0;for(var cause:document.path("causes")) frames+=cause.path("frames").size();assertThat(frames).isEqualTo(32);
        assertThat(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).doesNotContain("sentinel","SENSITIVE_FILENAME");
    }
    @Test
    void shouldReportEvidenceUnavailableWithoutChangingPrimaryFailure_whenPrivateStorageCannotBeUsed() throws Exception {
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(root);
        Files.writeString(root.resolve("diagnostics"),"occupied");
        assertThat(new CdsImportFailureEvidenceService(jobs,mock(CdsImportFileService.class)).write("run",1,new IllegalStateException("private"))).isNull();
        assertThat(Files.readString(root.resolve("diagnostics"))).isEqualTo("occupied");
    }
    @Test
    void shouldWithholdUnregisteredEvidence_whenDurableRegistryPublicationFails() throws Exception {
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(root);
        var files=mock(CdsImportFileService.class);
        doThrow(new CdsImportException("OWNER_FENCED")).when(files).reconcilePublication(anyString(),anyLong(),anyString(),anyString(),anyLong());
        assertThat(new CdsImportFailureEvidenceService(jobs,files).write("run",1,new IllegalStateException("private"))).isNull();
    }
    @Test
    void shouldProjectOnlyVerifiedMetadataAndRefuseTampering_whenEvidenceIsReadForAnAuthorizedDiagnostic() throws Exception {
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(root);
        var files=mock(CdsImportFileService.class);var service=new CdsImportFailureEvidenceService(jobs,files);
        String location=service.write("run",1,new IllegalStateException("private"));Path path=Path.of(location);
        byte[] bytes=Files.readAllBytes(path);String digest=io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.hex(
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.sha256().digest(bytes));
        when(files.findOwnedPublishedLocation("run",location,65536)).thenReturn(
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto("file",location,digest,bytes.length,"PUBLISHED",1));
        assertThat(service.read("run",location)).containsEntry("version",1).doesNotContainKey("protectedPath");
        Files.writeString(path,"tampered");
        assertThatThrownBy(() -> service.read("run",location)).hasMessage("IMPORT_FAILURE_EVIDENCE_INVALID");
    }

    @Test
    void shouldRejectUnexpectedFieldsEvenWithMatchingRegisteredHash_whenEvidenceEnvelopeContainsRawData() throws Exception {
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(root);
        var files=mock(CdsImportFileService.class);var service=new CdsImportFailureEvidenceService(jobs,files);
        String location=service.write("run",1,new IllegalStateException("private"));Path path=Path.of(location);
        byte[] bytes="{\"version\":1,\"ownerEpoch\":1,\"causes\":[],\"rawMessage\":\"private\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path,bytes);String digest=io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.hex(
                io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths.sha256().digest(bytes));
        when(files.findOwnedPublishedLocation("run",location,65536)).thenReturn(
                new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto("file",location,digest,bytes.length,"PUBLISHED",1));
        assertThatThrownBy(() -> service.read("run",location)).hasMessage("IMPORT_FAILURE_EVIDENCE_INVALID");
    }
}
