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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsImportValueVerificationUnitTest {
    @TempDir Path temp;

    @Test void shouldReuseOnePhysicalBlobProofWithinEpoch_andRecheckBeforeResumeWrites() throws Exception {
        assumeTrue(Files.getFileStore(temp).supportsFileAttributeView("posix"));
        Path source=Files.writeString(temp.resolve("source.xml"),CdsXmlParserUnitTest.fixture());
        List<CdsRecordDto> records=new ArrayList<>();
        var parsed=new CdsXmlParser().parse(source,temp.resolve("parsed"),CdsInputLimitsDto.defaults(),()->false,records::add);
        var ledger=mock(CdsImportLedgerService.class);var files=mock(CdsImportFileService.class);
        var configuration=mock(CdsImportConfigurationService.class);when(configuration.limits()).thenReturn(CdsInputLimitsDto.defaults());
        var jobs=mock(CdsImportJobService.class);when(jobs.root("run")).thenReturn(temp);
        when(ledger.status("run")).thenReturn(status(1));
        Set<String> verified=new HashSet<>();
        when(files.verifiedFor(anyString(),anyLong(),anyString(),anyString(),anyLong())).thenAnswer(call->
                verified.contains(call.getArgument(1)+":"+call.getArgument(2)));
        doAnswer(call->{verified.add(call.getArgument(1)+":"+call.getArgument(2));return null;})
                .when(files).publishedVerified(anyString(),anyLong(),anyString(),anyString(),anyLong());
        var service=new CdsImportPlanningService(ledger,configuration,new CdsSnapshotService(),new CdsXmlParser(),new CdsFieldParser(),
                mock(CdsPatientIdentityResolver.class),mock(CdsImportNativeService.class),mock(CdsCourseService.class),files,jobs,mock(CdsPreflightIdentityService.class));
        var work=new CdsImportWorkDto(records.get(0),null,"source.xml",parsed.sourceSha256(),temp.resolve("parsed/parse-result.json"),null,null);
        service.publishValues("run",1,work);
        // If a field-page publication rereads the blob, this second invocation cannot succeed.
        Files.setPosixFilePermissions(parsed.valuesFile(),PosixFilePermissions.fromString("---------"));
        try {service.publishValues("run",1,work);} finally {Files.setPosixFilePermissions(parsed.valuesFile(),PosixFilePermissions.fromString("rw-------"));}
        verify(files,times(1)).publishedVerified("run",1,parsed.valuesFile().toString(),parsed.valuesSha256(),parsed.valuesBytes());
        byte[] bytes=Files.readAllBytes(parsed.valuesFile());bytes[0]^=1;Files.write(parsed.valuesFile(),bytes);
        when(ledger.status("run")).thenReturn(status(2));
        CdsImportException changed=assertThrows(CdsImportException.class,()->service.publishValues("run",2,work));
        assertEquals("VALUE_SPOOL_HASH_MISMATCH",changed.getCode());
        verify(files,never()).publishedVerified(eq("run"),eq(2L),anyString(),anyString(),anyLong());
    }

    private static CdsImportJobDto status(long epoch) {
        return new CdsImportJobDto("run","operator","1","source","omd-5.2","RUNNING","a".repeat(64),"b".repeat(64),
                "c".repeat(64),"d".repeat(64),1,1,epoch,false,Instant.EPOCH,Instant.EPOCH,0,null,null);
    }
}
