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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsInputException;
import io.github.carlos_emr.carlos.demographic.cdsimport.validator.CdsSourceFieldCapacityValidator;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsSourceFieldCapacityUnitTest {
    @Test void shouldAcceptExactNativeColumnCapacities_inUnicodeCodePoints() {
        CdsRecordDto record=record("x".repeat(2047));
        assertDoesNotThrow(()->CdsSourceFieldCapacityValidator.validate(record, field("", "😀".repeat(250), "😀".repeat(4608))));
        assertDoesNotThrow(()->CdsSourceFieldCapacityValidator.validate(record("x".repeat(2048)), field("", "cds", "")));
    }

    @Test void shouldRefuseOversizedPathNamespaceOrType_beforeAnyClinicalTransaction() {
        assertCode("SOURCE_FIELD_PATH_CAPACITY_EXCEEDED",record("x".repeat(2048)),field("child", "cds", "type"));
        assertCode("SOURCE_FIELD_NAMESPACE_CAPACITY_EXCEEDED",record("/root"),field("leaf", "x".repeat(251), "type"));
        assertCode("SOURCE_FIELD_TYPE_CAPACITY_EXCEEDED",record("/root"),field("leaf", "cds", "x".repeat(4609)));
    }

    private static void assertCode(String code, CdsRecordDto record, CdsFieldDto field) {
        CdsInputException failure=assertThrows(CdsInputException.class,()->CdsSourceFieldCapacityValidator.validate(record,field));
        assertEquals(code,failure.code()); assertTrue(failure.sourceXPath().startsWith(record.sourceXPath()));
    }
    private static CdsRecordDto record(String path) {
        return new CdsRecordDto("Demographics",1,path,Path.of("fields.jsonl"),0,0,Path.of("values.bin"),"a".repeat(64),0);
    }
    private static CdsFieldDto field(String path,String namespace,String type) {
        return new CdsFieldDto(path,namespace,"TEXT",0,0,"a".repeat(64),type,"preserve");
    }
}
