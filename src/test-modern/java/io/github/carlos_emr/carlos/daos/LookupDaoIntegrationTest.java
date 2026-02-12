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
package io.github.carlos_emr.carlos.daos;

import io.github.carlos_emr.carlos.model.FieldDefValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LookupDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lookup")
@Transactional
public class LookupDaoIntegrationTest extends OpenOTestBase {

    @Autowired
    private LookupDao lookupDao;

    private LookupTableDefValue createLookupTableDef(String tableId, String tableName) {
        LookupTableDefValue value = new LookupTableDefValue();
        value.setTableId(tableId);
        value.setTableName(tableName);
        value.setDescription("Lookup " + tableId);
        value.setModuleId("T");
        value.setModuleName("TEST");
        value.setActive(true);
        value.setReadonly(false);
        value.setTree(false);
        value.setHasActive(false);
        value.setHasDisplayOrder(false);
        value.setTreeCodeLength(0);
        hibernateTemplate.save(value);
        return value;
    }

    private FieldDefValue createField(String tableId, String name, int index, int genericIdx) {
        FieldDefValue field = new FieldDefValue();
        field.setTableId(tableId);
        field.setFieldName(name);
        field.setFieldDesc(name + " desc");
        field.setFieldSQL(name);
        field.setFieldType("S");
        field.setLookupTable("");
        field.setEditable(true);
        field.setAuto(false);
        field.setUnique(false);
        field.setFieldIndex(index);
        field.setGenericIdx(genericIdx);
        field.setFieldLength(20);
        hibernateTemplate.save(field);
        return field;
    }

    @Test
    @Tag("query")
    @DisplayName("should bind tableId in GetLookupTableDef")
    void shouldBindTableIdInGetLookupTableDef() {
        createLookupTableDef("LKP_A_" + System.nanoTime(), "lkp_a");
        String wantedId = "LKP_B_" + System.nanoTime();
        createLookupTableDef(wantedId, "lkp_b");
        hibernateTemplate.flush();

        LookupTableDefValue result = lookupDao.GetLookupTableDef(wantedId);

        assertThat(result).isNotNull();
        assertThat(result.getTableId()).isEqualTo(wantedId);
        assertThat(result.getTableName()).isEqualTo("lkp_b");
    }

    @Test
    @Tag("query")
    @Tag("filter")
    @DisplayName("should bind tableId and order results by fieldIndex in LoadFieldDefList")
    void shouldBindAndOrderInLoadFieldDefList() {
        String tableId = "FLD_" + System.nanoTime();
        createLookupTableDef(tableId, "table_field_test");

        createField(tableId, "field_three", 3, 3);
        createField(tableId, "field_one", 1, 1);
        createField(tableId, "field_two", 2, 2);

        String otherTable = "FLD_OTHER_" + System.nanoTime();
        createLookupTableDef(otherTable, "table_other");
        createField(otherTable, "other_field", 1, 1);

        hibernateTemplate.flush();

        List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

        assertThat(result).hasSize(3);
        assertThat(result)
            .extracting(FieldDefValue::getTableId)
            .containsOnly(tableId);
        assertThat(result)
            .extracting(FieldDefValue::getFieldIndex)
            .containsExactly(1, 2, 3);
    }
}
