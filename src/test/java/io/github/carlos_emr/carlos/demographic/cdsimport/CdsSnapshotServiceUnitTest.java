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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsSnapshotService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsZipDirectory;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
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
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void shouldRejectDirectoryPayloads_whenSizeIsDeclaredOrComesFromADataDescriptor() throws Exception {
        byte[] payload = "unaccounted directory body".getBytes(StandardCharsets.UTF_8);
        for (boolean descriptor : new boolean[] {false, true}) {
            Path source = Files.createDirectory(temp.resolve("directory-" + descriptor));
            Path archive = source.resolve("patients.zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
                ZipEntry directory = new ZipEntry("hidden/");
                if (!descriptor) {
                    CRC32 crc = new CRC32(); crc.update(payload);
                    directory.setMethod(ZipEntry.STORED); directory.setSize(payload.length);
                    directory.setCompressedSize(payload.length); directory.setCrc(crc.getValue());
                }
                zip.putNextEntry(directory); zip.write(payload); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("patient.xml"));
                zip.write("<OmdCds xmlns='cds'><PatientRecord/></OmdCds>".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
                assertEquals(descriptor ? -1 : payload.length, zip.getNextEntry().getSize());
            }
            Path durable = Files.createDirectory(temp.resolve("directory-durable-" + descriptor));
            CdsInputException failed = assertThrows(CdsInputException.class,
                    () -> service.snapshot(archive, source, durable, "rejected", LIMITS, () -> false));
            assertEquals("CDS_ZIP_DIRECTORY_PAYLOAD", failed.code());
            assertFalse(Files.exists(durable.resolve("rejected")));
            assertFalse(Files.exists(durable.resolve("rejected.pending/snapshot.seal")));
        }
    }

    @Test void shouldRetainEmptyDirectorySupport_whenADataDescriptorDeclaresNoPayload() throws Exception {
        Path source = Files.createDirectory(temp.resolve("empty-directory"));
        Path archive = source.resolve("patients.zip");
        zip(archive, new String[] {"patients/", "patients/one.xml"},
                new String[] {"", "<OmdCds xmlns='cds'><PatientRecord/></OmdCds>"});
        var snapshot = service.snapshot(archive, source, Files.createDirectory(temp.resolve("empty-durable")),
                "accepted", LIMITS, () -> false);
        assertEquals(1, snapshot.files());
        assertEquals(snapshot.sha256(), service.load(snapshot.root()).sha256());
    }

    @Test void shouldProbeCompressedReads_whenEmptyDeflateBlocksProduceNoExpandedBytes() throws Exception {
        // A legal raw DEFLATE stream can consume arbitrarily many empty stored blocks
        // before emitting a single byte. A decompressed-output-only probe cannot stop it.
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        byte[] name = "empty/".getBytes(StandardCharsets.UTF_8);
        int blocks = 80000, compressedLength = blocks * 5 + 5;
        little(archive, 0x04034b50, 4); little(archive, 20, 2); little(archive, 0, 2);
        little(archive, 8, 2); little(archive, 0, 4); little(archive, 0, 4);
        little(archive, compressedLength, 4); little(archive, 0, 4);
        little(archive, name.length, 2); little(archive, 0, 2); archive.write(name);
        for (int i = 0; i < blocks; i++) archive.write(new byte[] {0, 0, 0, -1, -1});
        archive.write(new byte[] {1, 0, 0, -1, -1});
        AtomicInteger consumed = new AtomicInteger(), probes = new AtomicInteger();
        InputStream bytes = new ByteArrayInputStream(archive.toByteArray()) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                int read = super.read(target, offset, length);
                if (read > 0) consumed.addAndGet(read);
                return read;
            }
        };
        var constructor = Class.forName(CdsSnapshotService.class.getName() + "$CountingSource")
                .getDeclaredConstructor(InputStream.class, long.class, CdsZipDirectory.Check.class);
        constructor.setAccessible(true);
        InputStream controlled = (InputStream) constructor.newInstance(bytes, 1L << 20,
                (CdsZipDirectory.Check) () -> {
                    if (probes.incrementAndGet() == 3) throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                });
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(controlled, 65536))) {
            assertTrue(zip.getNextEntry().isDirectory());
            CdsInputException failed = assertThrows(CdsInputException.class, zip::read);
            assertEquals("CDS_INPUT_CANCELLED", failed.code());
        }
        assertEquals(3, probes.get());
        assertTrue(consumed.get() > 0 && consumed.get() <= 2 * 65536);
        assertTrue(consumed.get() < archive.size());
    }

    @Test void shouldRejectRedirectedAttachmentIndex_whenManifestAndMembersAreUnchanged() throws Exception {
        var snapshot = attachmentSnapshot("redirect");
        ObjectNode redirected = marker(snapshot.root(), "b.pdf");
        redirected.put("relativeName", "a.pdf");
        Files.write(index(snapshot.root(), "a.pdf"), JSON.writeValueAsBytes(redirected));
        CdsInputException direct = assertThrows(CdsInputException.class,
                () -> service.resolveReference(snapshot, "patient.xml", "a.pdf"));
        assertEquals("CDS_SNAPSHOT_NAME_INDEX_INVALID", direct.code());
        CdsInputException reload = assertThrows(CdsInputException.class, () -> service.load(snapshot.root(), true));
        assertEquals("CDS_SNAPSHOT_NAME_INDEX_INVALID", reload.code());
    }

    @Test void shouldRejectExtraAttachmentAliases_whenARealMemberHasTheSameDigestAndLength() throws Exception {
        var snapshot = attachmentSnapshot("alias");
        ObjectNode alias = marker(snapshot.root(), "b.pdf");
        alias.put("relativeName", "forged.pdf");
        Files.write(index(snapshot.root(), "forged.pdf"), JSON.writeValueAsBytes(alias));
        var loaded = service.load(snapshot.root(), true);
        assertEquals("second attachment", Files.readString(service.resolveReference(loaded, "patient.xml", "b.pdf").path()));
        CdsInputException failed = assertThrows(CdsInputException.class,
                () -> service.resolveReference(loaded, "patient.xml", "forged.pdf"));
        assertEquals("CDS_SNAPSHOT_NAME_INDEX_INVALID", failed.code());
    }

    @Test void shouldRejectUnboundedOrNonRowLocators_whenTheDerivedIndexIsChanged() throws Exception {
        var snapshot = attachmentSnapshot("locator");
        ObjectNode original = marker(snapshot.root(), "a.pdf");
        for (ObjectNode invalid : List.of(original.deepCopy().put("manifestOffset", Long.MAX_VALUE),
                original.deepCopy().put("manifestLength", 65537),
                original.deepCopy().put("manifestOffset", original.path("manifestOffset").asLong() + 1))) {
            Files.write(index(snapshot.root(), "a.pdf"), JSON.writeValueAsBytes(invalid));
            CdsInputException failed = assertThrows(CdsInputException.class,
                    () -> service.resolveReference(snapshot, "patient.xml", "a.pdf"));
            assertEquals("CDS_SNAPSHOT_NAME_INDEX_INVALID", failed.code());
        }
    }

    @Test void shouldProbeBetweenCentralDirectoryMarkers_whenCancellationArrivesAfterTheFirstMarker() throws Exception {
        Path snapshot = Files.createDirectory(temp.resolve("central"));
        Path names = Files.createDirectory(snapshot.resolve("names"));
        Path archive = snapshot.resolve("source.zip");
        String[] entries = {"one.xml", "two.txt"}, values = {"first", "second"};
        zip(archive, entries, values);
        for (int i = 0; i < entries.length; i++) {
            CRC32 crc = new CRC32(); crc.update(values[i].getBytes(StandardCharsets.UTF_8));
            ObjectNode marker = JSON.createObjectNode().put("relativeName", entries[i])
                    .put("length", values[i].length()).put("crc32", crc.getValue());
            Files.write(index(snapshot, entries[i]), JSON.writeValueAsBytes(marker));
        }
        AtomicInteger probes = new AtomicInteger();
        CdsInputException failed = assertThrows(CdsInputException.class,
                () -> CdsZipDirectory.validate(archive, names, snapshot, 10, 2, () -> {
                    probes.incrementAndGet();
                    if (marker(snapshot, "one.xml").path("zipDirectorySeen").asBoolean()) {
                        throw CdsPaths.failure("CDS_INPUT_CANCELLED");
                    }
                }));
        assertEquals("CDS_INPUT_CANCELLED", failed.code());
        assertTrue(probes.get() >= 4);
        assertFalse(marker(snapshot, "two.txt").path("zipDirectorySeen").asBoolean());
        assertFalse(Files.exists(snapshot.resolve("snapshot.seal")));
    }

    @Test void shouldNotSealOrPublish_whenCancellationAppearsDuringCentralDirectoryValidation() throws Exception {
        Path source = Files.createDirectory(temp.resolve("central-source"));
        Path archive = source.resolve("patients.zip");
        zip(archive, new String[] {"patient.xml"}, new String[] {"<OmdCds xmlns='cds'><PatientRecord/></OmdCds>"});
        Path durable = Files.createDirectory(temp.resolve("central-durable"));
        Path pending = durable.resolve("cancelled.pending");
        CdsInputException failed = assertThrows(CdsInputException.class,
                () -> service.snapshot(archive, source, durable, "cancelled", LIMITS,
                        () -> centralSeen(pending, "patient.xml")));
        assertEquals("CDS_INPUT_CANCELLED", failed.code());
        assertFalse(Files.exists(durable.resolve("cancelled")));
        assertFalse(Files.exists(pending.resolve("snapshot.seal")));
    }

    @Test void shouldProbeAgainBeforePublication_whenCancellationAppearsAfterSealing() throws Exception {
        Path source = Files.createDirectory(temp.resolve("final-source"));
        Path xml = source.resolve("patient.xml");
        Files.writeString(xml, "<OmdCds xmlns='cds'><PatientRecord/></OmdCds>");
        Path durable = Files.createDirectory(temp.resolve("final-durable"));
        CdsInputException failed = assertThrows(CdsInputException.class,
                () -> service.snapshot(xml, source, durable, "cancelled", LIMITS,
                        () -> Files.exists(durable.resolve("cancelled.pending/snapshot.seal"))));
        assertEquals("CDS_INPUT_CANCELLED", failed.code());
        assertFalse(Files.exists(durable.resolve("cancelled")));
    }

    private io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsSnapshotDto attachmentSnapshot(String name) throws Exception {
        Path source = Files.createDirectory(temp.resolve(name + "-source"));
        Files.writeString(source.resolve("patient.xml"), "<OmdCds xmlns='cds'><PatientRecord/></OmdCds>");
        Files.writeString(source.resolve("a.pdf"), "first attachment");
        Files.writeString(source.resolve("b.pdf"), "second attachment");
        return service.snapshot(source, source, Files.createDirectory(temp.resolve(name + "-durable")),
                "accepted", LIMITS, () -> false);
    }

    private static Path index(Path snapshot, String name) {
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
        return snapshot.resolve("names").resolve(CdsPaths.hex(CdsPaths.sha256().digest(normalized.getBytes(StandardCharsets.UTF_8))));
    }

    private static ObjectNode marker(Path snapshot, String name) throws java.io.IOException {
        return (ObjectNode) JSON.readTree(Files.readAllBytes(index(snapshot, name)));
    }

    private static boolean centralSeen(Path snapshot, String name) {
        try { return Files.exists(index(snapshot, name)) && marker(snapshot, name).path("zipDirectorySeen").asBoolean(); }
        catch (java.io.IOException failed) { throw new java.io.UncheckedIOException(failed); }
    }

    private static void little(OutputStream bytes, long value, int count) throws java.io.IOException {
        for (int i = 0; i < count; i++) bytes.write((int) (value >>> (8 * i)) & 255);
    }

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

    @Test void shouldDetectSupportedUnicodeXmlTransports_withoutFilenameHints() throws Exception {
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
