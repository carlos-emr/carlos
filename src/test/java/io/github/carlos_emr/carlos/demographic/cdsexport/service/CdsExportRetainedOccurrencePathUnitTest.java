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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class CdsExportRetainedOccurrencePathUnitTest {
    @Test void originalSecondAndLaterPatientOrdinalsRemainValidInsideTheirVerifiedOwnerScope()throws Exception{
        for (var category:List.of(CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS, CdsCategory.MEDICATIONS_AND_TREATMENTS, CdsCategory.IMMUNIZATIONS))for (int ordinal:List.of(1, 2, 999)){
            String root="/OmdCds[1]/PatientRecord["+ordinal+"]/"+category.wireName()+"[3]"; assertThat(CdsExportRetainedOccurrencePath.root(field(root+"/Value[1]", category), category)).isEqualTo(root);
        }
    }
    @Test void aFieldCannotBorrowAnotherCategorysOccurrenceRoot(){assertThatThrownBy(()-> CdsExportRetainedOccurrencePath.root(field("/OmdCds[1]/PatientRecord[2]/AllergiesAndAdverseReactions[1]/Value[1]", CdsCategory.IMMUNIZATIONS), CdsCategory.IMMUNIZATIONS)).hasMessage("CDS_EXPORT_RETAINED_OCCURRENCE_PATH_INVALID"); }
    @Test void malformedRootOrdinalsAndAmbiguousPrefixesRemainInvalid(){for (String root:List.of("/OmdCds[2]/PatientRecord[1]/Immunizations[1]", "/OmdCds[1]/PatientRecord[0]/Immunizations[1]", "/OmdCds[1]/PatientRecord[01]/Immunizations[1]", "/OmdCds[1]/PatientRecord[1]/Immunizations[0]", "/OmdCds[1]/PatientRecord[1]/Immunizations[1]Extra"))assertThatThrownBy(()-> CdsExportRetainedOccurrencePath.root(field(root+"/Value[1]", CdsCategory.IMMUNIZATIONS), CdsCategory.IMMUNIZATIONS)).hasMessage("CDS_EXPORT_RETAINED_OCCURRENCE_PATH_INVALID"); }
    private static CdsExportRetainedFieldDto field(String path, CdsCategory category){return new CdsExportRetainedFieldDto("a".repeat(64), "11111111-1111-4111-8111-111111111111", "b".repeat(64), "c".repeat(64), category, path, 1, "cds", "{http://www.w3.org/2001/XMLSchema}string", null, "Exact", "Exact", null); }
}
