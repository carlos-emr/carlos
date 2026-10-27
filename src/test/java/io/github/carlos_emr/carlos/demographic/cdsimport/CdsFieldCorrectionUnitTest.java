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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsCorrectionSetDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsFieldCorrectionUnitTest {
    @TempDir Path temp;
    private final CdsFieldParser fields=new CdsFieldParser();
    private final ObjectMapper json=new ObjectMapper();

    @Test void shouldApplyReviewedLogicalValue_whileOriginalBytesAndDigestRemainVerifiable() throws Exception {
        CdsRecordDto original=record();CdsFieldDto field=field(original,"Gender[1]");
        CdsRecordDto corrected=overlay(original,field," M ",entry->{ });
        assertEquals("M",fields.firstToken(corrected,"Gender",1));
        assertEquals("F",fields.readText(corrected,field,1));
        fields.verifyRecord(corrected);
        assertEquals(original.sha256(),corrected.sha256());assertNotEquals(original.effectiveSha256(),corrected.effectiveSha256());
        List<CdsRecordDto> pages=new ArrayList<>();fields.forEachPage(corrected,128,pages::add);
        assertEquals(corrected.corrections(),pages.get(0).corrections());assertEquals("M",fields.firstToken(pages.get(0),"Gender",1));
    }

    @Test void shouldMatchFullRebasedPath_whenChildDescriptorsUseSameCorrectionManifest() throws Exception {
        CdsRecordDto original=record();CdsFieldDto field=field(original,"Names[1]/LegalName[1]/LastName[1]/Part[1]");
        CdsRecordDto corrected=overlay(original,field,"Reviewed",entry->{ });
        List<CdsRecordDto> children=new ArrayList<>();fields.forEachSubrecord(corrected,"Names",children::add);
        assertEquals(1,children.size());
        assertEquals("Reviewed",fields.firstText(children.get(0),"LegalName/LastName/Part",50));
        assertEquals("FAKE-PW0000000000004241",fields.readText(corrected,field,50));
    }

    @Test void shouldRefuseWrongOriginalIdentity_insteadOfApplyingValueByPathAlone() throws Exception {
        CdsRecordDto original=record();CdsFieldDto field=field(original,"Gender[1]");
        CdsRecordDto corrected=overlay(original,field,"M",entry->entry.put("originalSha256","0".repeat(64)));
        CdsInputException failure=assertThrows(CdsInputException.class,()->fields.firstToken(corrected,"Gender",1));
        assertEquals("CDS_CORRECTION_SOURCE_IDENTITY_MISMATCH",failure.code());
    }

    @Test void shouldDetectManifestAndReplacementTampering_evenAfterManifestWasCached() throws Exception {
        CdsRecordDto original=record();CdsFieldDto field=field(original,"Gender[1]");
        CdsRecordDto corrected=overlay(original,field,"M",entry->{ });
        assertEquals("M",fields.firstToken(corrected,"Gender",1));
        Path replacement=corrected.corrections().manifestFile().getParent().resolve("replacement.txt");
        Files.writeString(replacement,"F");
        assertThrows(CdsInputException.class,()->fields.firstToken(corrected,"Gender",1));
        Files.writeString(replacement,"M");
        Files.writeString(corrected.corrections().manifestFile(),"{}");
        CdsInputException failure=assertThrows(CdsInputException.class,()->fields.firstToken(corrected,"Gender",1));
        assertEquals("CDS_CORRECTION_MANIFEST_HASH_MISMATCH",failure.code());
    }

    private CdsRecordDto record() throws Exception {
        Path source=Files.writeString(temp.resolve("source.xml"),CdsXmlParserUnitTest.fixture());List<CdsRecordDto> rows=new ArrayList<>();
        new CdsXmlParser().parse(source,temp.resolve("parsed"),CdsInputLimitsDto.defaults(),()->false,rows::add);return rows.get(0);
    }
    private CdsFieldDto field(CdsRecordDto record,String path) throws Exception {
        CdsFieldDto[] value={null};fields.forEachField(record,field->{if(field.path().equals(path))value[0]=field;});assertNotNull(value[0]);return value[0];
    }
    private CdsRecordDto overlay(CdsRecordDto record,CdsFieldDto field,String replacement,Consumer<ObjectNode> mutate) throws Exception {
        Path folder=Files.createDirectory(record.fieldsFile().getParent().resolve("corrections"));
        byte[] bytes=replacement.getBytes(StandardCharsets.UTF_8);Files.write(folder.resolve("replacement.txt"),bytes);
        ObjectNode manifest=json.createObjectNode().put("version",1).put("acceptedXmlSha256","a".repeat(64));
        ObjectNode entry=manifest.putArray("corrections").addObject().put("sourceXPath",record.sourceXPath()+"/"+field.path())
                .put("namespace",field.namespace()).put("kind",field.kind()).put("schemaType",field.schemaType()).put("whitespace",field.whitespace())
                .put("originalSha256",field.sha256()).put("originalLength",field.length()).put("replacementFile","replacement.txt")
                .put("replacementSha256",CdsPaths.hex(CdsPaths.sha256().digest(bytes))).put("replacementBytes",bytes.length);
        mutate.accept(entry);byte[] document=json.writeValueAsBytes(manifest);Path file=Files.write(folder.resolve("manifest.json"),document);
        return record.withCorrections(new CdsCorrectionSetDto(file,CdsPaths.hex(CdsPaths.sha256().digest(document)),"a".repeat(64)));
    }
}
