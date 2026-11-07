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
package io.github.carlos_emr.carlos.demographic.cdsimport.assembler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportReviewProvenanceDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalSourceService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class CdsSourceFieldsViewModelAssemblerUnitTest {
    @Test void shouldKeepCorrectedNormalizedPreviewVisible_whenOriginalValueRequiresProtectedStreaming() throws Exception {
        var source=mock(CdsClinicalSourceService.class);var actor=mock(LoggedInInfo.class);String review="11111111-1111-4111-8111-111111111111";
        when(source.page(actor,42,"ClinicalNotes","",50)).thenReturn(List.of(new CdsImportFieldDto("a".repeat(64),"b".repeat(64),"ClinicalNotes","/PatientRecord/ClinicalNotes[1]/MyClinicalNotesContent[1]",1,"cds","text",null,null,"Corrected logical content","/private/protected-spool/Synthetic-Name.values","c".repeat(64),0,400000)));
        when(source.provenance(actor,42,"ClinicalNotes","",50)).thenReturn(List.of(new CdsImportReviewProvenanceDto(review,"web-42",Instant.parse("2026-01-01T00:00:00Z"),"CORRECT_VALUE","OVERLAY_READY","Synthetic reviewed reason",2,"/PatientRecord/ClinicalNotes[1]/MyClinicalNotesContent[1]")));
        var model=new CdsSourceFieldsViewModelAssembler(source).assemble(actor,42,"ClinicalNotes","","");
        assertThat(model.fields().get(0).externalPayload()).isTrue();assertThat(model.fields().get(0).normalizedAvailable()).isTrue();
        assertThat(model.fields().get(0).normalizedPreview()).isEqualTo("Corrected logical content");assertThat(model.reviews().get(0).state()).isEqualTo("OVERLAY_READY");
        assertThat(new ObjectMapper().writeValueAsString(model)).doesNotContain("/private","Synthetic-Name.values","payloadLocation");
    }
    @Test void shouldIdentifyShortenedPreview_withoutSplittingSupplementaryUnicodeOrLosingCompleteFieldIdentity(){
        var source=mock(CdsClinicalSourceService.class);var actor=mock(LoggedInInfo.class);String value="X".repeat(1999)+"😀tail";
        when(source.page(actor,42,"ClinicalNotes","",50)).thenReturn(List.of(new CdsImportFieldDto("a".repeat(64),"b".repeat(64),"ClinicalNotes","/PatientRecord/ClinicalNotes[1]/MyClinicalNotesContent[1]",1,"cds","text",null,value,value,null,null,0,0)));
        when(source.provenance(actor,42,"ClinicalNotes","",50)).thenReturn(List.of());
        var field=new CdsSourceFieldsViewModelAssembler(source).assemble(actor,42,"ClinicalNotes","","").fields().get(0);
        assertThat(field.truncated()).isTrue();assertThat(field.originalPreview()).isEqualTo("X".repeat(1999));assertThat(field.id()).isEqualTo("a".repeat(64));
    }
}
