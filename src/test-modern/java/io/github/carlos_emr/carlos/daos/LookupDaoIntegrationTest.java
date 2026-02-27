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
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link LookupDao} positional-parameter HQL queries.
 *
 * <p>Note: {@code LookupTableDefValue} is mapped with {@code mutable="false"} and
 * {@code generator class="native"} in its HBM, which conflicts with String-typed IDs.
 * Test data is therefore seeded via native SQL instead of ORM saves.</p>
 *
 * @since 2026-02-12
 * @see LookupDao
 */
@DisplayName("LookupDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lookup")
@Transactional
public class LookupDaoIntegrationTest extends CarlosTestBase {

    private static final String INSERT_LOOKUP_TABLE =
        "INSERT INTO app_lookuptable (tableId, table_name, description, activeyn, readonly, istree, treecode_length, moduleid) VALUES (:tid, :tname, :desc, :active, :ro, :tree, :tcl, :mid)";

    private static final String INSERT_FIELD =
        "INSERT INTO app_lookuptable_fields (tableid, fieldname, fielddesc, fieldtype, edityn, lookuptable, fieldsql, fieldindex, autoyn, uniqueyn, genericidx, fieldlength) VALUES (:tid, :fname, :fdesc, :ftype, :edit, :lt, :fsql, :fidx, :auto, :uniq, :gidx, :flen)";

    @Autowired
    private LookupDao lookupDao;

    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /**
     * Generate a short unique tableId that fits within column width constraints.
     * Format: prefix + 4-digit sequence (e.g. "LA0001"), max 6 chars.
     */
    private String nextTableId(String prefix) {
        return prefix + String.format("%04d", SEQ.incrementAndGet());
    }

    private void insertLookupTableDef(String tableId, String tableName) {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_LOOKUP_TABLE)
                .setParameter("tid", tableId)
                .setParameter("tname", tableName)
                .setParameter("desc", "Lookup " + tableId)
                .setParameter("active", true)
                .setParameter("ro", false)
                .setParameter("tree", false)
                .setParameter("tcl", 0)
                .setParameter("mid", "T")
                .executeUpdate();
            return null;
        });
    }

    private void insertField(String tableId, String fieldName, int fieldIndex, int genericIdx) {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_FIELD)
                .setParameter("tid", tableId)
                .setParameter("fname", fieldName)
                .setParameter("fdesc", fieldName + " desc")
                .setParameter("ftype", "S")
                .setParameter("edit", true)
                .setParameter("lt", "")
                .setParameter("fsql", fieldName)
                .setParameter("fidx", fieldIndex)
                .setParameter("auto", false)
                .setParameter("uniq", false)
                .setParameter("gidx", genericIdx)
                .setParameter("flen", 20)
                .executeUpdate();
            return null;
        });
    }

    @Test
    @Tag("query")
    @DisplayName("should bind tableId in GetLookupTableDef")
    void shouldBindTableId_inGetLookupTableDef() {
        String unwantedId = nextTableId("LA");
        String wantedId = nextTableId("LB");
        insertLookupTableDef(unwantedId, "lkp_a");
        insertLookupTableDef(wantedId, "lkp_b");
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
    void shouldBindAndOrder_inLoadFieldDefList() {
        String tableId = nextTableId("FA");
        insertLookupTableDef(tableId, "table_field_test");

        insertField(tableId, "field_three", 3, 3);
        insertField(tableId, "field_one", 1, 1);
        insertField(tableId, "field_two", 2, 2);

        String otherTable = nextTableId("FB");
        insertLookupTableDef(otherTable, "table_other");
        insertField(otherTable, "other_field", 1, 1);

        hibernateTemplate.flush();

        @SuppressWarnings("unchecked")
        List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

        assertThat(result).hasSize(3);
        assertThat(result)
            .extracting(FieldDefValue::getTableId)
            .containsOnly(tableId);
        assertThat(result)
            .extracting(FieldDefValue::getFieldIndex)
            .containsExactly(1, 2, 3);
    }

    @Test
    @Tag("query")
    @DisplayName("should return null when tableId does not exist")
    void shouldReturnNull_whenTableIdDoesNotExist() {
        LookupTableDefValue result = lookupDao.GetLookupTableDef("NONEXISTENT");

        assertThat(result).isNull();
    }

    /**
     * Tests for {@code inOrg} LIKE-based query ({@code like %?0}).
     *
     * <p>{@code LstOrgcd} uses {@code generator class="native"} with a String ID,
     * so test data is seeded via native SQL (same pattern as LookupTableDefValue).</p>
     *
     * <p><b>Known issue:</b> The production HQL {@code like %?0} embeds the {@code %}
     * wildcard outside the parameter value. This is non-standard HQL and may fail
     * during Hibernate 6 migration. The test documents current behavior.</p>
     */
    @Nested
    @DisplayName("inOrg (LIKE ?0 — org hierarchy check)")
    class InOrg {

        private static final String INSERT_ORG =
            "INSERT INTO lst_orgcd (code, description, activeyn, orderbyindex, codetree, fullCode, codeCsv) VALUES (:code, :desc, :active, :idx, :tree, :full, :csv)";

        private void insertOrgCode(String code, String description, String fullcode, String codecsv) {
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(INSERT_ORG)
                    .setParameter("code", code)
                    .setParameter("desc", description)
                    .setParameter("active", 1)
                    .setParameter("idx", 0)
                    .setParameter("tree", code)
                    .setParameter("full", fullcode)
                    .setParameter("csv", codecsv)
                    .executeUpdate();
                return null;
            });
        }

        @Test
        @Tag("query")
        @DisplayName("should exercise inOrg LIKE parameter binding")
        void shouldExerciseInOrg_withLikeParameterBinding() {
            // Given — org hierarchy: PARENT > CHILD
            insertOrgCode("PARENT", "Parent Org", "PARENT", "PARENT,");
            insertOrgCode("CHILD", "Child Org", "PARENTCHILD", "PARENT,CHILD,");
            hibernateTemplate.flush();

            // When/Then — The production HQL `like %?0` has a syntax issue:
            // the % wildcard is outside the parameter value, which is non-standard.
            // Hibernate's HQL parser rejects the bare `%` token before the parameter.
            // Hibernate 5's ExceptionConverterImpl.convert() wraps QueryException as
            // IllegalArgumentException (JPA convention) rather than HibernateQueryException.
            assertThatThrownBy(() -> lookupDao.inOrg("PARENT", "CHILD"))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
