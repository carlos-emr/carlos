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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSnapshotService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsSnapshotServiceUnitTest {
    @TempDir Path temp;
    private final CdsSnapshotService service=new CdsSnapshotService();
    private static final CdsInputLimitsDto LIMITS=CdsInputLimitsDto.defaults();

    @Test void shouldCaptureOnlyReferencedAttachments_whenInputIsBareXml() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source"));
        Path reports=Files.createDirectory(source.resolve("reports"));
        Files.writeString(reports.resolve("résumé.pdf"),"protected attachment");
        Files.writeString(source.resolve("unrelated.xml"),"<private/> ");
        Path xml=source.resolve("patient.xml");
        String original="<OmdCds xmlns='cds'><PatientRecord><Reports><FilePath>reports\\résumé.pdf</FilePath></Reports></PatientRecord></OmdCds>";
        Files.writeString(xml,original);
        Path durable=Files.createDirectory(temp.resolve("durable"));
        var snapshot=service.snapshot(xml,source,durable,"accepted",LIMITS,()->false);
        assertEquals(2,snapshot.files()); assertEquals(1,snapshot.patientXmlFiles());
        assertEquals("protected attachment",Files.readString(service.resolveReference(snapshot,"patient.xml","reports\\résumé.pdf").path()));
        assertEquals(original,Files.readString(xml));
        assertEquals(snapshot.sha256(),service.load(snapshot.root()).sha256());
        List<CdsSnapshotFileDto> rows=new ArrayList<>(); service.forEachFile(snapshot,rows::add);
        assertTrue(rows.stream().noneMatch(row->row.relativeName().equals("unrelated.xml")));
        assertTrue(rows.stream().allMatch(row->row.path().getFileName().toString().matches("[0-9]{12}\\.bin")));
    }

    @Test void shouldPreserveAncillaryXmlAndRawArchive_whenZipContainsClinicalAttachments() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")); Path zip=source.resolve("patients.zip");
        zip(zip,new String[]{"patients/one.xml","patients/reports/cda.xml","audit.xml"},
                new String[]{"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>","<ClinicalDocument xmlns='urn:hl7-org:v3'/>","<audit/>"});
        byte[] original=Files.readAllBytes(zip);
        var snapshot=service.snapshot(zip,source,Files.createDirectory(temp.resolve("durable")),"zip",LIMITS,()->false);
        assertEquals(3,snapshot.files()); assertEquals(1,snapshot.patientXmlFiles());
        assertArrayEquals(original,Files.readAllBytes(snapshot.root().resolve("source-archive.bin")));
        assertEquals(snapshot.sha256(),service.load(snapshot.root()).sha256());
        assertArrayEquals(original,Files.readAllBytes(zip));
    }

    @Test void shouldRejectTraversalAndCaseOrUnicodeParentCollisions_beforePublication() throws Exception {
        String[][] names={{"../escape.xml"},{"A/one.xml","a/two.xml"},{"café/one.xml","cafe\u0301/two.xml"},{"same","same/child.xml"}};
        for(int i=0;i<names.length;i++) {
            Path source=Files.createDirectory(temp.resolve("source"+i)), zip=source.resolve("patients.zip");
            String[] bodies=new String[names[i].length]; java.util.Arrays.fill(bodies,"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>");
            zip(zip,names[i],bodies); Path durable=Files.createDirectory(temp.resolve("durable"+i));
            assertThrows(CdsInputException.class,()->service.snapshot(zip,source,durable,"zip",LIMITS,()->false));
            assertFalse(Files.exists(durable.resolve("zip"))); assertTrue(Files.exists(zip));
        }
    }

    @Test void shouldRejectUnixSymlinkMode_evenWhenZipLocalBytesLookRegular() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), zip=source.resolve("patients.zip");
        zip(zip,new String[]{"patient.xml"},new String[]{"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>"});
        byte[] archive=Files.readAllBytes(zip); int central=signature(archive,0x02014b50);
        archive[central+5]=3; int mode=0120777 << 16;
        for(int i=0;i<4;i++) archive[central+38+i]=(byte)(mode >>> (8*i));
        Files.write(zip,archive);
        CdsInputException failure=assertThrows(CdsInputException.class,()->service.snapshot(zip,source,Files.createDirectory(temp.resolve("durable")),"zip",LIMITS,()->false));
        assertEquals("CDS_UNSAFE_ZIP_MEMBER",failure.code());
    }

    @Test void shouldRejectChangedMemberOrManifest_whenLoadingPublishedSnapshot() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), xml=source.resolve("patient.xml");
        Files.writeString(xml,"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>");
        var snapshot=service.snapshot(xml,source,Files.createDirectory(temp.resolve("durable")),"accepted",LIMITS,()->false);
        List<CdsSnapshotFileDto> rows=new ArrayList<>(); service.forEachFile(snapshot,rows::add);
        Files.writeString(rows.get(0).path(),"changed");
        assertThrows(CdsInputException.class,()->service.load(snapshot.root()));
    }

    @Test void shouldLeaveOriginalAndUnsealedEvidence_whenCancelledOrByteBudgetExceeded() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), xml=source.resolve("patient.xml");
        String original="<OmdCds xmlns='cds'><PatientRecord/></OmdCds>"; Files.writeString(xml,original);
        Path durable=Files.createDirectory(temp.resolve("durable"));
        CdsInputException cancelled=assertThrows(CdsInputException.class,()->service.snapshot(xml,source,durable,"cancelled",LIMITS,()->true));
        assertEquals("CDS_INPUT_CANCELLED",cancelled.code());
        var tiny=new CdsInputLimitsDto(10,10,100,256,65536,100,1,10000,8192);
        assertThrows(CdsInputException.class,()->service.snapshot(xml,source,durable,"tiny",tiny,()->false));
        assertEquals(original,Files.readString(xml)); assertFalse(Files.exists(durable.resolve("cancelled")));
        assertFalse(Files.exists(durable.resolve("tiny")));
    }

    @Test void shouldRejectSymlinkSourceAndMissingReferencedAttachment_withoutMovingOriginal() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), xml=source.resolve("patient.xml");
        Files.writeString(xml,"<OmdCds xmlns='cds'><PatientRecord><Reports><FilePath>missing.pdf</FilePath></Reports></PatientRecord></OmdCds>");
        Path durable=Files.createDirectory(temp.resolve("durable"));
        assertThrows(Exception.class,()->service.snapshot(xml,source,durable,"missing",LIMITS,()->false));
        Path link=source.resolve("link.xml"); Files.createSymbolicLink(link,xml.getFileName());
        assertThrows(Exception.class,()->service.snapshot(link,source,durable,"link",LIMITS,()->false)); assertTrue(Files.exists(xml));
    }

    @Test void shouldDiscoverCdsMembersWithoutXmlSuffix_whileRetainingNonCdsAndBinaryAncillaryFiles() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), archive=source.resolve("patients.zip");
        String patient="<OmdCds xmlns='cds'><PatientRecord/></OmdCds>";
        zip(archive,new String[]{"Patient_First_uid_01011970","another.dat","cda.dat","audit","report.pdf","root-after-budget"},
                new String[]{patient,patient,"<ClinicalDocument xmlns='urn:hl7-org:v3'/>","<audit/>","%PDF binary"," ".repeat(100_000)+patient});
        var snapshot=service.snapshot(archive,source,Files.createDirectory(temp.resolve("durable")),"accepted",LIMITS,()->false);
        assertEquals(6,snapshot.files());assertEquals(3,snapshot.patientXmlFiles());
        List<CdsSnapshotFileDto> patients=new ArrayList<>();service.forEachXml(snapshot,patients::add);
        assertTrue(patients.stream().noneMatch(file->List.of("cda.dat","audit","report.pdf").contains(file.relativeName())));
        assertEquals(snapshot.sha256(),service.load(snapshot.root()).sha256());
    }

    @Test void shouldAcceptBareCdsInstanceWithoutSuffix_asPermittedByTheOfficialFilenameContract() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), file=source.resolve("Patient_First_uid_04031971");
        Files.writeString(file,CdsXmlParserUnitTest.fixture());
        var snapshot=service.snapshot(file,source,Files.createDirectory(temp.resolve("durable")),"accepted",LIMITS,()->false);
        assertEquals(1,snapshot.files());assertEquals(1,snapshot.patientXmlFiles());assertTrue(Files.exists(file));
    }

    @Test void shouldKeepXmlLookingUnresolvedMembersVisible_whenRootExceedsTheSniffBudget() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source")), file=source.resolve("patient-data");
        Files.writeString(file,"<!--"+"x".repeat(100_000)+"-->"+CdsXmlParserUnitTest.fixture().replaceFirst("<\\?xml[^>]*>",""));
        var snapshot=service.snapshot(source,source,Files.createDirectory(temp.resolve("durable")),"accepted",LIMITS,()->false);
        assertEquals(1,snapshot.patientXmlFiles());
        var candidates=new ArrayList<CdsSnapshotFileDto>();service.forEachXml(snapshot,candidates::add);
        Path spool=Files.createDirectory(temp.resolve("spools")).resolve("parsed");
        var invalid=assertThrows(CdsInputException.class,()->new io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser()
                .parse(candidates.get(0).path(),spool,LIMITS,()->false,record->{ }));
        assertEquals("CDS_XML_MARKUP_LIMIT",invalid.code());assertFalse(Files.exists(spool.resolve("parse.seal")));assertTrue(Files.exists(file));
    }

    @Test void shouldDetectSupportedUnicodeXmlTransportsWithoutFilenameHints() throws Exception {
        Path source=Files.createDirectory(temp.resolve("source"));
        String patient="<OmdCds xmlns='cds'><PatientRecord/></OmdCds>";
        String[] encodings={"UTF-8","UTF-16BE","UTF-16LE","UTF-32BE","UTF-32LE"};
        byte[][] marks={{(byte)239,(byte)187,(byte)191},{(byte)254,(byte)255},{(byte)255,(byte)254},{0,0,(byte)254,(byte)255},{(byte)255,(byte)254,0,0}};
        for(int i=0;i<encodings.length;i++) {
            byte[] body=patient.getBytes(java.nio.charset.Charset.forName(encodings[i]));
            Files.write(source.resolve(encodings[i]+"-plain"),body);
            byte[] withMark=new byte[marks[i].length+body.length];System.arraycopy(marks[i],0,withMark,0,marks[i].length);
            System.arraycopy(body,0,withMark,marks[i].length,body.length);Files.write(source.resolve(encodings[i]+"-marked"),withMark);
        }
        var snapshot=service.snapshot(source,source,Files.createDirectory(temp.resolve("durable")),"accepted",LIMITS,()->false);
        assertEquals(10,snapshot.patientXmlFiles());assertEquals(10,snapshot.files());
    }

    private static void zip(Path path,String[] names,String[] contents) throws Exception {
        try(OutputStream raw=Files.newOutputStream(path);ZipOutputStream zip=new ZipOutputStream(raw,StandardCharsets.UTF_8)) {
            for(int i=0;i<names.length;i++) {zip.putNextEntry(new ZipEntry(names[i]));zip.write(contents[i].getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        }
    }
    private static int signature(byte[] bytes,int expected) {
        for(int i=0;i<=bytes.length-4;i++) if(((bytes[i]&255)|((bytes[i+1]&255)<<8)|((bytes[i+2]&255)<<16)|((bytes[i+3]&255)<<24)) == expected) return i;
        throw new AssertionError("ZIP central directory missing");
    }
}
