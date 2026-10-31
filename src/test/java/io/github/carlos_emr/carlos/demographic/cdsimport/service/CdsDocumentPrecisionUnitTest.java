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
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.documentManager.dto.DocumentListItemDTO;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DocumentConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;
import java.util.Date;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@org.junit.jupiter.api.parallel.Isolated("Scoped legacy EDoc DAO cache")
class CdsDocumentPrecisionUnitTest {
    @Test
    void shouldValidateAndRetainImmutableSourcePrecision_whenItsPublicSetterIsUsed() {
        Document document=new Document();
        assertThatThrownBy(()->document.setCdsContentPrecision("unknown"))
                .hasMessage("CDS_CONTENT_PRECISION_INVALID");
        document.setCdsContentPrecision("UNKNOWN");
        assertThatThrownBy(()->document.setCdsContentPrecision(null)).hasMessage("CDS_CONTENT_PRECISION_IMMUTABLE");
        assertThatThrownBy(()->document.setCdsContentPrecision("FULL_DATE")).hasMessage("CDS_CONTENT_PRECISION_IMMUTABLE");
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(document,"rememberCdsContentPrecision");
        // A merge can copy an omitted raw field before any dirty update callback runs.
        org.springframework.test.util.ReflectionTestUtils.setField(document,"cdsContentPrecision",null);
        assertThat(document.getCdsContentPrecision()).isEqualTo("UNKNOWN");
        Document metadata=new Document(); metadata.setContentdatetime(new Date());
        metadata.preserveImportedContentFrom(document);
        assertThat(metadata.getCdsContentPrecision()).isEqualTo("UNKNOWN");
        assertThat(metadata.getContentdatetime()).isNull();
    }

    @Test
    void shouldExposeAnUnknownClockWithExplicitSourcePrecision_whenRestAndListDtosAreBuilt() throws Exception {
        Document document=new Document(123); document.setCdsContentPrecision("MONTH"); document.setContentdatetime(null);
        DocumentTo1 transfer=new DocumentConverter().getAsTransferObject(null,document);
        assertThat(transfer.getContentDateTime()).isNull(); assertThat(transfer.getCdsContentPrecision()).isEqualTo("MONTH");
        assertThat(new DocumentConverter().getAsDomainObject(null,transfer).getCdsContentPrecision()).isEqualTo("MONTH");
        assertThat(DocumentListItemDTO.fromEntity(document).getCdsContentPrecision()).isEqualTo("MONTH");
        var json=new ObjectMapper();
        assertThat(json.readTree(json.writeValueAsString(transfer)).path("cdsContentPrecision").asText()).isEqualTo("MONTH");
        assertThat(json.readValue("{\"cdsContentPrecision\":\"YEAR\"}",DocumentTo1.class).getCdsContentPrecision()).isNull();
    }

    @Test
    void shouldCarrySourcePrecisionThroughEdocConversion_withoutInventingAContentTime() {
        EDoc value=new EDoc(); value.setCdsContentPrecision("FULL_DATE"); value.setContentDateTime(null);
        Document document=value.getDocument();
        assertThat(document.getCdsContentPrecision()).isEqualTo("FULL_DATE"); assertThat(document.getContentdatetime()).isNull();
        assertThat(value.getContentDateTimeDisplay()).contains("Date without a time");
        assertThat(value.getContentDateTimeDisplay("yyyy-MM-dd")).doesNotContain("1970","2026");
    }

    @Test
    void shouldPreserveTheImportedSourceTimestamp_whenLegacyMetadataCarriesAnUploadTimeDefault() {
        Document original=new Document(123); original.setCdsContentPrecision("UNKNOWN");
        Document metadata=new Document(123); metadata.setContentdatetime(new Date());
        metadata.preserveImportedContentFrom(original);
        assertThat(metadata.getContentdatetime()).isNull(); assertThat(metadata.getCdsContentPrecision()).isEqualTo("UNKNOWN");
        Document ordinary=new Document(456); Date explicit=new Date(123456); ordinary.setContentdatetime(explicit);
        ordinary.preserveImportedContentFrom(new Document(456)); assertThat(ordinary.getContentdatetime()).isEqualTo(explicit);
    }

    @Test
    void shouldRetainUnknownTimeDuringEdocMetadataSave_whenAnExistingFileNameIsPresent() {
        Document original=new Document(123); original.setCdsContentPrecision("UNKNOWN");
        EDoc metadata=new EDoc(); metadata.setDocId("123"); metadata.setFileName("existing.txt");
        metadata.setContentDateTime(new Date());
        DocumentDao dao=mock(DocumentDao.class); when(dao.find((Object)Integer.valueOf(123))).thenReturn(original);
        Object previous=org.springframework.test.util.ReflectionTestUtils.getField(EDocUtil.class,"documentDao");
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(EDocUtil.class,"documentDao",dao);
            EDocUtil.editDocumentSQL(metadata,false);
        } finally {org.springframework.test.util.ReflectionTestUtils.setField(EDocUtil.class,"documentDao",previous);}
        assertThat(original.getContentdatetime()).isNull(); assertThat(original.getCdsContentPrecision()).isEqualTo("UNKNOWN");
        verify(dao).merge(original);
    }
}
