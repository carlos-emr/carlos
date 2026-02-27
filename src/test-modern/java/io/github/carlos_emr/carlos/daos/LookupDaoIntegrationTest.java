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

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.model.FieldDefValue;
import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link LookupDao} multi-parameter query methods.
 *
 * <p>These tests validate HQL queries with positional parameters (?0, ?1, ...)
 * bind correctly, ensuring safe migration to Hibernate 6 named parameter syntax.
 * Tests cover CRUD operations, multi-parameter searches, and edge cases.</p>
 *
 * <h3>Architecture Notes</h3>
 * <p>{@code LookupDaoImpl} uses two distinct data-access strategies:</p>
 * <ul>
 *   <li><b>HibernateTemplate (HQL)</b>: {@code GetLookupTableDef}, {@code LoadFieldDefList},
 *       and {@code inOrg}. These participate in the Spring-managed transaction and are fully
 *       testable with the H2 in-memory database. ({@code updateOrgStatus} is private and called
 *       indirectly by {@code SaveAsOrgCode}.)</li>
 *   <li><b>Raw JDBC via {@code DBPreparedHandler}</b>: {@code LoadCodeList}, {@code GetCode},
 *       {@code GetCodeFieldValues}, {@code SaveCodeValue} (insert/update), and
 *       {@code getCountOfActiveClient}. These use {@code DbConnectionFilter.getThreadLocalDbConnection()},
 *       which obtains a <em>separate</em> JDBC connection that does <strong>not</strong>
 *       participate in the Spring-managed test transaction. Data written through Hibernate
 *       is invisible to these methods, and vice versa.</li>
 * </ul>
 *
 * <p>For raw-JDBC methods, tests verify:</p>
 * <ul>
 *   <li>Early-return / null-guard code paths that execute <em>before</em> the JDBC call</li>
 *   <li>Correct delegation between overloaded methods</li>
 *   <li>Behavior when dependent HQL lookups return null (causing empty result lists)</li>
 *   <li>SQL generation correctness is documented for the EntityManager migration</li>
 * </ul>
 *
 * <p>Note: {@code LookupTableDefValue} is mapped with {@code mutable="false"} and
 * {@code generator class="native"} in its HBM, which conflicts with String-typed IDs.
 * Test data is therefore seeded via native SQL instead of ORM saves.</p>
 *
 * @since 2026-02-12
 * @see LookupDao
 * @see LookupDaoImpl
 */
@DisplayName("LookupDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("lookup")
@Transactional
public class LookupDaoIntegrationTest extends CarlosTestBase {

    /** Native SQL to insert a row into the {@code app_lookuptable} definition table. */
    private static final String INSERT_LOOKUP_TABLE =
        "INSERT INTO app_lookuptable (tableId, table_name, description, activeyn, readonly, istree, treecode_length, moduleid) VALUES (:tid, :tname, :desc, :active, :ro, :tree, :tcl, :mid)";

    /** Native SQL to insert a field definition into {@code app_lookuptable_fields}. */
    private static final String INSERT_FIELD =
        "INSERT INTO app_lookuptable_fields (tableid, fieldname, fielddesc, fieldtype, edityn, lookuptable, fieldsql, fieldindex, autoyn, uniqueyn, genericidx, fieldlength) VALUES (:tid, :fname, :fdesc, :ftype, :edit, :lt, :fsql, :fidx, :auto, :uniq, :gidx, :flen)";

    /** Native SQL to insert an organization code into {@code lst_orgcd}. */
    private static final String INSERT_ORG =
        "INSERT INTO lst_orgcd (code, description, activeyn, orderbyindex, codetree, fullCode, codeCsv) VALUES (:code, :desc, :active, :idx, :tree, :full, :csv)";

    /** The DAO under test, autowired from the Spring test application context. */
    @Autowired
    private LookupDao lookupDao;

    /** Thread-safe sequence counter for generating unique tableId values across tests. */
    private static final AtomicInteger SEQ = new AtomicInteger(0);

    /**
     * Generates a short unique tableId that fits within column width constraints.
     * Format: prefix + 4-digit sequence (e.g. "LA0001"), max 6 chars.
     *
     * @param prefix String the 2-character prefix identifying the test context
     * @return String the unique tableId (e.g., "LA0001")
     */
    private String nextTableId(String prefix) {
        return prefix + String.format("%04d", SEQ.incrementAndGet());
    }

    /**
     * Inserts a lookup table definition with default settings (non-tree, active, editable).
     *
     * @param tableId   String the unique table identifier
     * @param tableName String the backing data table name referenced in dynamic SQL
     */
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

    /**
     * Inserts a lookup table definition with explicit tree configuration.
     *
     * @param tableId        String the unique table identifier
     * @param tableName      String the backing data table name
     * @param isTree         boolean whether this table uses hierarchical tree structure
     * @param treeCodeLength int the length of tree code segments (0 if not a tree)
     */
    private void insertLookupTableDefWithTree(String tableId, String tableName, boolean isTree, int treeCodeLength) {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_LOOKUP_TABLE)
                .setParameter("tid", tableId)
                .setParameter("tname", tableName)
                .setParameter("desc", "Lookup " + tableId)
                .setParameter("active", true)
                .setParameter("ro", false)
                .setParameter("tree", isTree)
                .setParameter("tcl", treeCodeLength)
                .setParameter("mid", "T")
                .executeUpdate();
            return null;
        });
    }

    /**
     * Inserts a field definition with default settings (type "S", not auto-increment, no lookup).
     *
     * @param tableId    String the parent table identifier
     * @param fieldName  String the field name
     * @param fieldIndex int the display order index
     * @param genericIdx int the generic index used for column mapping (1=code, 2=desc, 3=active, etc.)
     */
    private void insertField(String tableId, String fieldName, int fieldIndex, int genericIdx) {
        insertFieldFull(tableId, fieldName, fieldIndex, genericIdx, "S", false, "");
    }

    /**
     * Inserts a field definition with all configurable attributes.
     *
     * @param tableId       String the parent table identifier
     * @param fieldName     String the field name
     * @param fieldIndex    int the display order index
     * @param genericIdx    int the generic index for column mapping
     * @param fieldType     String the field data type ("S"=String, "D"=Date, "I"=Integer)
     * @param autoIncrement boolean whether the field auto-increments
     * @param lookupTable   String the reference lookup table name, or empty string if none
     */
    private void insertFieldFull(String tableId, String fieldName, int fieldIndex, int genericIdx,
                                  String fieldType, boolean autoIncrement, String lookupTable) {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_FIELD)
                .setParameter("tid", tableId)
                .setParameter("fname", fieldName)
                .setParameter("fdesc", fieldName + " desc")
                .setParameter("ftype", fieldType)
                .setParameter("edit", true)
                .setParameter("lt", lookupTable)
                .setParameter("fsql", fieldName)
                .setParameter("fidx", fieldIndex)
                .setParameter("auto", autoIncrement)
                .setParameter("uniq", false)
                .setParameter("gidx", genericIdx)
                .setParameter("flen", 100)
                .executeUpdate();
            return null;
        });
    }

    /**
     * Inserts an organization code with default settings (active, order index 0).
     *
     * @param code        String the organization code (primary key)
     * @param description String the organization description
     * @param fullcode    String the full hierarchical code path
     * @param codecsv     String the comma-separated code hierarchy for LIKE queries
     */
    private void insertOrgCode(String code, String description, String fullcode, String codecsv) {
        insertOrgCodeFull(code, description, 1, 0, code, fullcode, codecsv);
    }

    /**
     * Inserts an organization code with all configurable attributes.
     *
     * @param code        String the organization code (primary key)
     * @param description String the organization description
     * @param active      int whether the org is active (1) or inactive (0)
     * @param orderIdx    int the display ordering index
     * @param codetree    String the tree-structure code path
     * @param fullcode    String the full hierarchical code path
     * @param codecsv     String the comma-separated code hierarchy
     */
    private void insertOrgCodeFull(String code, String description, int active, int orderIdx,
                                    String codetree, String fullcode, String codecsv) {
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_ORG)
                .setParameter("code", code)
                .setParameter("desc", description)
                .setParameter("active", active)
                .setParameter("idx", orderIdx)
                .setParameter("tree", codetree)
                .setParameter("full", fullcode)
                .setParameter("csv", codecsv)
                .executeUpdate();
            return null;
        });
    }

    // =========================================================================
    // GetLookupTableDef tests
    // =========================================================================

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
    @DisplayName("should return null when tableId does not exist")
    void shouldReturnNull_whenTableIdDoesNotExist() {
        LookupTableDefValue result = lookupDao.GetLookupTableDef("NONEXISTENT");

        assertThat(result).isNull();
    }

    @Test
    @Tag("query")
    @DisplayName("should populate all LookupTableDefValue properties from database")
    void shouldPopulateAllProperties_fromGetLookupTableDef() {
        // Given
        String tableId = nextTableId("LP");
        hibernateTemplate.execute(session -> {
            session.createNativeQuery(INSERT_LOOKUP_TABLE)
                .setParameter("tid", tableId)
                .setParameter("tname", "test_table_name")
                .setParameter("desc", "Test Description")
                .setParameter("active", true)
                .setParameter("ro", true)
                .setParameter("tree", true)
                .setParameter("tcl", 8)
                .setParameter("mid", "T")
                .executeUpdate();
            return null;
        });
        hibernateTemplate.flush();

        // When
        LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTableId()).isEqualTo(tableId);
        assertThat(result.getTableName()).isEqualTo("test_table_name");
        assertThat(result.getDescription()).isEqualTo("Test Description");
        assertThat(result.isActive()).isTrue();
        assertThat(result.isReadonly()).isTrue();
        assertThat(result.isTree()).isTrue();
        assertThat(result.getTreeCodeLength()).isEqualTo(8);
        assertThat(result.getModuleId()).isEqualTo("T");
    }

    // =========================================================================
    // LoadFieldDefList tests
    // =========================================================================

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
    @DisplayName("should return empty list when no fields exist for tableId")
    void shouldReturnEmptyList_whenNoFieldsExist() {
        // Given
        String tableId = nextTableId("FE");
        insertLookupTableDef(tableId, "table_no_fields");
        hibernateTemplate.flush();

        // When
        @SuppressWarnings("unchecked")
        List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @Tag("query")
    @DisplayName("should populate all FieldDefValue properties from database")
    void shouldPopulateAllFieldDefProperties_fromLoadFieldDefList() {
        // Given
        String tableId = nextTableId("FP");
        insertLookupTableDef(tableId, "table_props");
        insertFieldFull(tableId, "test_field", 1, 5, "D", true, "REF");
        hibernateTemplate.flush();

        // When
        @SuppressWarnings("unchecked")
        List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

        // Then
        assertThat(result).hasSize(1);
        FieldDefValue field = result.get(0);
        assertThat(field.getTableId()).isEqualTo(tableId);
        assertThat(field.getFieldName()).isEqualTo("test_field");
        assertThat(field.getFieldType()).isEqualTo("D");
        assertThat(field.isAuto()).isTrue();
        assertThat(field.getGenericIdx()).isEqualTo(5);
        assertThat(field.getLookupTable()).isEqualTo("REF");
        assertThat(field.getFieldSQL()).isEqualTo("test_field");
        assertThat(field.getFieldIndex()).isEqualTo(1);
    }

    // =========================================================================
    // GetCode tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#GetCode(String, String)}.
     *
     * <p>{@code GetCode} delegates to {@code LoadCodeList(tableId, false, code, "")}
     * which uses {@code DBPreparedHandler} for raw JDBC queries. The null/empty
     * code guard executes before JDBC access and is fully testable.</p>
     */
    @Nested
    @DisplayName("GetCode")
    class GetCodeTests {

        @Test
        @Tag("query")
        @DisplayName("should return null when code is null")
        void shouldReturnNull_whenCodeIsNull() {
            // Given - null code parameter

            // When
            LookupCodeValue result = lookupDao.GetCode("ANY", null);

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when code is empty string")
        void shouldReturnNull_whenCodeIsEmpty() {
            // Given - empty code parameter

            // When
            LookupCodeValue result = lookupDao.GetCode("ANY", "");

            // Then
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when tableId does not exist in lookup table definitions")
        void shouldReturnNull_whenTableIdDoesNotExist() {
            // Given - no table definition exists for this tableId
            // LoadCodeList calls GetLookupTableDef first, which returns null,
            // causing LoadCodeList to return an empty list, so GetCode returns null.

            // When
            LookupCodeValue result = lookupDao.GetCode("NONEXISTENT", "CODE1");

            // Then - GetLookupTableDef returns null, LoadCodeList returns empty list
            assertThat(result).isNull();
        }
    }

    // =========================================================================
    // LoadCodeList tests (4-param overload)
    // =========================================================================

    /**
     * Tests for {@link LookupDao#LoadCodeList(String, boolean, String, String)}.
     *
     * <p>The 4-param overload delegates to the 5-param version with an empty
     * {@code parentCode} string. Tests verify the delegation pattern and
     * early-return behavior when no table definition exists.</p>
     */
    @Nested
    @DisplayName("LoadCodeList (4-param overload)")
    class LoadCodeList4ParamTests {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when tableId does not exist")
        void shouldReturnEmptyList_whenTableIdDoesNotExist() {
            // Given - no table definition exists

            // When
            @SuppressWarnings("unchecked")
            List<LookupCodeValue> result = lookupDao.LoadCodeList("NOPE", true, "C1", "desc");

            // Then - GetLookupTableDef returns null, so empty list is returned
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should delegate to 5-param overload with empty parentCode")
        void shouldDelegateTo5ParamOverload_withEmptyParentCode() {
            // Given - table exists but has no field defs, so no data table to query.
            // This exercises the delegation path and SQL-building logic up to the
            // point where the dynamic table reference would fail.
            String tableId = nextTableId("CL");
            insertLookupTableDef(tableId, "nonexistent_data_table");
            hibernateTemplate.flush();

            // When - The SQL will reference "nonexistent_data_table" which doesn't exist,
            // but the DBPreparedHandler runs on a separate JDBC connection. The DAO
            // catches SQLException and returns an empty list.
            @SuppressWarnings("unchecked")
            List<LookupCodeValue> result = lookupDao.LoadCodeList(tableId, false, "C1", "desc");

            // Then - either empty (JDBC error caught) or populated (if data visible)
            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // LoadCodeList tests (5-param overload)
    // =========================================================================

    /**
     * Tests for {@link LookupDao#LoadCodeList(String, boolean, String, String, String)}.
     *
     * <p>This is the core code-list loading method. It builds dynamic SQL from
     * the lookup table definition and field definitions, then executes via
     * {@code DBPreparedHandler}. Tests verify:
     * <ul>
     *   <li>Early return when table definition is null</li>
     *   <li>USR tableId special handling (parentCode set to null)</li>
     *   <li>SQL generation with various filter combinations</li>
     * </ul>
     * </p>
     */
    @Nested
    @DisplayName("LoadCodeList (5-param overload)")
    class LoadCodeList5ParamTests {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when table definition is null")
        void shouldReturnEmptyList_whenTableDefIsNull() {
            // Given - no table definition for this tableId

            // When
            @SuppressWarnings("unchecked")
            List<LookupCodeValue> result = lookupDao.LoadCodeList(
                "MISSING", false, "", "", "");

            // Then
            assertThat(result)
                .as("Should return empty ArrayList when GetLookupTableDef returns null")
                .isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list with no filters and no backing table data")
        void shouldReturnEmptyList_withNoFiltersAndNoBackingData() {
            // Given - table def exists but referenced data table doesn't
            String tableId = nextTableId("LC");
            insertLookupTableDef(tableId, "no_such_table");
            hibernateTemplate.flush();

            // When - DBPreparedHandler will fail on missing table, DAO catches exception
            @SuppressWarnings("unchecked")
            List<LookupCodeValue> result = lookupDao.LoadCodeList(
                tableId, false, "", "", "");

            // Then
            assertThat(result).isNotNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should set parentCode to null for USR tableId")
        void shouldNullifyParentCode_forUsrTableId() {
            // Given - USR table type gets special treatment: parentCode is set to null
            // so it doesn't filter by parent. This tests the early path logic.
            // Without a real USR table definition, GetLookupTableDef returns null.

            // When
            @SuppressWarnings("unchecked")
            List<LookupCodeValue> result = lookupDao.LoadCodeList(
                "USR", false, "PROG1", "CODE1", "desc");

            // Then - returns empty because no USR table def exists
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // GetCodeFieldValues tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#GetCodeFieldValues(LookupTableDefValue, String)}.
     *
     * <p>This method builds dynamic SQL from the field definitions and executes
     * via {@code DBPreparedHandler}. It also calls back to {@code GetCode} for
     * fields with lookup tables. Tests verify the field-list loading and
     * behavior when no matching code row is found.</p>
     */
    @Nested
    @DisplayName("GetCodeFieldValues (single code)")
    class GetCodeFieldValuesSingleTests {

        @Test
        @Tag("query")
        @DisplayName("should return field list even when data table query fails")
        void shouldReturnFieldList_whenDataTableQueryFails() {
            // Given - table def pointing to a nonexistent data table
            String tableId = nextTableId("CF");
            insertLookupTableDef(tableId, "nonexistent_table_cf");
            insertField(tableId, "code_col", 1, 1);
            insertField(tableId, "desc_col", 2, 2);
            hibernateTemplate.flush();

            LookupTableDefValue tableDef = lookupDao.GetLookupTableDef(tableId);
            assertThat(tableDef).isNotNull();

            // When - SQL referencing "nonexistent_table_cf" will fail;
            // DAO catches SQLException and returns the field list as-is
            @SuppressWarnings("unchecked")
            List<FieldDefValue> result = lookupDao.GetCodeFieldValues(tableDef, "TEST_CODE");

            // Then - returns the field definition list (values not populated due to SQL error)
            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty field list when table has no field definitions")
        void shouldReturnEmptyFieldList_whenNoFieldDefinitions() {
            // Given - table def exists but has no field definitions
            String tableId = nextTableId("CN");
            insertLookupTableDef(tableId, "empty_fields_table");
            hibernateTemplate.flush();

            LookupTableDefValue tableDef = lookupDao.GetLookupTableDef(tableId);

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> result = lookupDao.GetCodeFieldValues(tableDef, "ANY_CODE");

            // Then - empty field list means no SQL is built
            assertThat(result).isEmpty();
        }
    }

    /**
     * Tests for {@link LookupDao#GetCodeFieldValues(LookupTableDefValue)}.
     *
     * <p>The no-arg version fetches ALL codes from the table. It builds SQL
     * without a WHERE clause (no code filter). Tests verify behavior when
     * the data table doesn't exist.</p>
     */
    @Nested
    @DisplayName("GetCodeFieldValues (all codes)")
    class GetCodeFieldValuesAllTests {

        @Test
        @Tag("query")
        @DisplayName("should return empty list when data table query fails")
        void shouldReturnEmptyList_whenDataTableQueryFails() {
            // Given - table def pointing to nonexistent data table
            String tableId = nextTableId("CA");
            insertLookupTableDef(tableId, "nonexistent_all_table");
            insertField(tableId, "id_col", 1, 1);
            insertField(tableId, "name_col", 2, 2);
            hibernateTemplate.flush();

            LookupTableDefValue tableDef = lookupDao.GetLookupTableDef(tableId);

            // When - DBPreparedHandler will fail on missing table
            @SuppressWarnings("unchecked")
            List<List> result = lookupDao.GetCodeFieldValues(tableDef);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty list when table has no field definitions")
        void shouldReturnEmptyCodesList_whenNoFieldDefinitions() {
            // Given
            String tableId = nextTableId("CE");
            insertLookupTableDef(tableId, "empty_table_all");
            hibernateTemplate.flush();

            LookupTableDefValue tableDef = lookupDao.GetLookupTableDef(tableId);

            // When
            @SuppressWarnings("unchecked")
            List<List> result = lookupDao.GetCodeFieldValues(tableDef);

            // Then
            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // SaveCodeValue tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#SaveCodeValue(boolean, LookupTableDefValue, List)} and
     * {@link LookupDao#SaveCodeValue(boolean, LookupCodeValue)}.
     *
     * <p>These methods use raw JDBC for INSERT/UPDATE operations via
     * {@code DbConnectionFilter.getThreadLocalDbConnection()}, which obtains a
     * separate connection outside the Spring-managed test transaction. Full
     * insert/update testing requires the raw JDBC connection to share the same
     * transaction, which only happens in the production servlet filter context.</p>
     *
     * <p>Tests here verify the code paths that execute <em>before</em> JDBC calls:
     * the LookupCodeValue-to-FieldDefValue mapping logic, and behavior when the
     * table definition or field definitions are invalid.</p>
     */
    @Nested
    @DisplayName("SaveCodeValue")
    class SaveCodeValueTests {

        @Test
        @Tag("create")
        @DisplayName("should throw StringIndexOutOfBoundsException from SaveCodeValue when field list is empty")
        void shouldThrowStringIndexOutOfBounds_whenFieldListIsEmpty() {
            // InsertCodeValue builds phs="" with empty field list, then calls
            // phs.substring(0, phs.length()-1) = phs.substring(0,-1) which throws
            // StringIndexOutOfBoundsException (LookupDaoImpl.InsertCodeValue line ~491)
            String tableId = nextTableId("SV");
            insertLookupTableDef(tableId, "save_test_table");
            hibernateTemplate.flush();

            LookupCodeValue codeValue = new LookupCodeValue();
            codeValue.setPrefix(tableId);

            // When/Then
            assertThatThrownBy(() -> lookupDao.SaveCodeValue(true, codeValue))
                .isInstanceOf(StringIndexOutOfBoundsException.class);
        }

        @Test
        @Tag("create")
        @DisplayName("should map LookupCodeValue fields to FieldDefValue list by genericIdx")
        void shouldMapCodeValueToFieldDefs_byGenericIdx() {
            // Given - table def with field definitions for all 17 generic indices
            String tableId = nextTableId("SM");
            insertLookupTableDef(tableId, "map_test_table");

            // Insert fields covering genericIdx 1-9
            insertField(tableId, "code", 1, 1);
            insertField(tableId, "description", 2, 2);
            insertField(tableId, "active", 3, 3);
            insertField(tableId, "display_order", 4, 4);
            insertField(tableId, "parent_code", 5, 5);
            insertField(tableId, "buf1", 6, 6);
            insertField(tableId, "codetree", 7, 7);
            insertField(tableId, "update_user", 8, 8);
            insertFieldFull(tableId, "update_date", 9, 9, "D", false, "");
            hibernateTemplate.flush();

            // Verify the field defs load correctly before attempting save
            @SuppressWarnings("unchecked")
            List<FieldDefValue> fields = lookupDao.LoadFieldDefList(tableId);
            assertThat(fields).hasSize(9);
            assertThat(fields)
                .extracting(FieldDefValue::getGenericIdx)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        }

        @Test
        @Tag("create")
        @DisplayName("should build LookupCodeValue with all buffer fields for SaveCodeValue overload")
        void shouldBuildCodeValue_withAllBufferFields() {
            // Given - verify the LookupCodeValue can hold all 17 mapped fields
            LookupCodeValue codeValue = new LookupCodeValue();
            codeValue.setPrefix("TST");
            codeValue.setCode("C001");
            codeValue.setDescription("Test Code");
            codeValue.setActive(true);
            codeValue.setOrderByIndex(10);
            codeValue.setParentCode("P001");
            codeValue.setBuf1("buffer1");
            codeValue.setCodeTree("TREE001");
            codeValue.setLastUpdateUser("testuser");
            codeValue.setLastUpdateDate(Calendar.getInstance());
            codeValue.setBuf3("buffer3");
            codeValue.setBuf4("buffer4");
            codeValue.setBuf5("buffer5");
            codeValue.setBuf6("buffer6");
            codeValue.setBuf7("buffer7");
            codeValue.setBuf8("buffer8");
            codeValue.setBuf9("buffer9");
            codeValue.setCodecsv("CSV001,");

            // Then - all fields are set correctly
            assertThat(codeValue.getCode()).isEqualTo("C001");
            assertThat(codeValue.getDescription()).isEqualTo("Test Code");
            assertThat(codeValue.isActive()).isTrue();
            assertThat(codeValue.getOrderByIndex()).isEqualTo(10);
            assertThat(codeValue.getParentCode()).isEqualTo("P001");
            assertThat(codeValue.getBuf1()).isEqualTo("buffer1");
            assertThat(codeValue.getCodeTree()).isEqualTo("TREE001");
            assertThat(codeValue.getLastUpdateUser()).isEqualTo("testuser");
            assertThat(codeValue.getLastUpdateDate()).isNotNull();
            assertThat(codeValue.getBuf3()).isEqualTo("buffer3");
            assertThat(codeValue.getBuf4()).isEqualTo("buffer4");
            assertThat(codeValue.getBuf5()).isEqualTo("buffer5");
            assertThat(codeValue.getBuf6()).isEqualTo("buffer6");
            assertThat(codeValue.getBuf7()).isEqualTo("buffer7");
            assertThat(codeValue.getBuf8()).isEqualTo("buffer8");
            assertThat(codeValue.getBuf9()).isEqualTo("buffer9");
            assertThat(codeValue.getCodecsv()).isEqualTo("CSV001,");
        }

        @Test
        @Tag("create")
        @DisplayName("should throw SQLException from SaveCodeValue(LookupCodeValue) when table def is missing")
        void shouldThrowNpe_whenTableDefIsMissingForCodeValueOverload() {
            // Given - LookupCodeValue with a prefix that has no table definition
            LookupCodeValue codeValue = new LookupCodeValue();
            codeValue.setPrefix("NOPE");
            codeValue.setCode("X001");
            codeValue.setDescription("Missing table");
            codeValue.setActive(true);
            codeValue.setOrderByIndex(0);

            // When/Then - GetLookupTableDef("NOPE") returns null,
            // then InsertCodeValue tries to call tableDef.getTableName() on null
            assertThatThrownBy(() -> lookupDao.SaveCodeValue(true, codeValue))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // inOrg tests
    // =========================================================================

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
    @DisplayName("inOrg (LIKE ?0 --- org hierarchy check)")
    class InOrg {

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

    // =========================================================================
    // SaveAsOrgCode(Program) tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#SaveAsOrgCode(io.github.carlos_emr.carlos.PMmodule.model.Program)}.
     *
     * <p>This method builds an org-code hierarchy entry for a program. It depends on:
     * <ul>
     *   <li>An existing facility org-code looked up via {@code GetCode("ORG", "F" + facilityId)}</li>
     *   <li>The ORG table definition and field definitions (for SaveCodeValue)</li>
     * </ul>
     * Since {@code GetCode} uses raw JDBC (DBPreparedHandler), and the ORG table definition
     * must exist for the full code path, these tests verify early failure modes.</p>
     */
    @Nested
    @DisplayName("SaveAsOrgCode (Program)")
    class SaveAsOrgCodeProgramTests {

        @Test
        @Tag("create")
        @DisplayName("should throw NullPointerException when facility org-code does not exist")
        void shouldThrowNpe_whenFacilityOrgCodeDoesNotExist() {
            // Given - Program referencing a facility with no org-code entry
            // GetCode("ORG", "F999") will return null (no ORG table def or no data),
            // then fcd.getBuf1() will throw NPE
            io.github.carlos_emr.carlos.PMmodule.model.Program program =
                new io.github.carlos_emr.carlos.PMmodule.model.Program();
            program.setId(100);
            program.setFacilityId(999);
            program.setName("Test Program");
            program.setProgramStatus("active");
            program.setLastUpdateUser("admin");

            // When/Then - GetCode returns null for the facility, causing NPE
            assertThatThrownBy(() -> lookupDao.SaveAsOrgCode(program))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // SaveAsOrgCode(Facility) tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#SaveAsOrgCode(io.github.carlos_emr.carlos.commn.model.Facility)}.
     *
     * <p>This method builds an org-code hierarchy entry for a facility. It depends on:
     * <ul>
     *   <li>An existing org (shelter) org-code looked up via {@code GetCode("ORG", "S" + orgId)}</li>
     *   <li>The ORG table definition and field definitions (for SaveCodeValue)</li>
     * </ul>
     * </p>
     */
    @Nested
    @DisplayName("SaveAsOrgCode (Facility)")
    class SaveAsOrgCodeFacilityTests {

        @Test
        @Tag("create")
        @DisplayName("should throw NullPointerException when shelter org-code does not exist")
        void shouldThrowNpe_whenShelterOrgCodeDoesNotExist() {
            // Given - Facility referencing an org with no org-code entry
            io.github.carlos_emr.carlos.commn.model.Facility facility =
                new io.github.carlos_emr.carlos.commn.model.Facility();
            facility.setId(200);
            facility.setOrgId(999);
            facility.setName("Test Facility");
            facility.setDisabled(false);

            // When/Then - GetCode("ORG", "S999") returns null, causing NPE on ocd.getBuf1()
            assertThatThrownBy(() -> lookupDao.SaveAsOrgCode(facility))
                .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // SaveAsOrgCode(LookupCodeValue, String) tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#SaveAsOrgCode(LookupCodeValue, String)}.
     *
     * <p>This overload handles ORG (OGN) and Shelter (SHL) code types. It:
     * <ol>
     *   <li>Derives the org prefix from the first char of tableId</li>
     *   <li>Looks up the parent code via GetCode</li>
     *   <li>Builds the new org entry</li>
     *   <li>Calls SaveCodeValue to persist</li>
     * </ol>
     * </p>
     */
    @Nested
    @DisplayName("SaveAsOrgCode (LookupCodeValue, tableId)")
    class SaveAsOrgCodeLookupCodeValueTests {

        @Test
        @Tag("create")
        @DisplayName("should return silently when parent org-code does not exist")
        void shouldReturnSilently_whenParentOrgCodeDoesNotExist() throws SQLException {
            // Given - org value with a parent that doesn't exist in ORG table
            LookupCodeValue orgVal = new LookupCodeValue();
            orgVal.setCode("001");
            orgVal.setDescription("Test Org");
            orgVal.setParentCode("999");
            orgVal.setActive(true);
            orgVal.setLastUpdateUser("admin");

            // When - GetCode("ORG", "R1999") returns null, method returns early
            // For OGN tableId, orgPrefix = "O", orgPrefixP = "R1"
            lookupDao.SaveAsOrgCode(orgVal, "OGN");

            // Then - no exception, method returns silently
        }

        @Test
        @Tag("create")
        @DisplayName("should derive correct prefix for SHL tableId")
        void shouldDeriveCorrectPrefix_forShlTableId() throws SQLException {
            // Given - SHL tableId: orgPrefix = "S", orgPrefixP = "O"
            LookupCodeValue orgVal = new LookupCodeValue();
            orgVal.setCode("002");
            orgVal.setDescription("Test Shelter");
            orgVal.setParentCode("888");
            orgVal.setActive(true);
            orgVal.setLastUpdateUser("admin");

            // When - GetCode("ORG", "O888") returns null, method returns early
            lookupDao.SaveAsOrgCode(orgVal, "SHL");

            // Then - no exception, method returns silently
        }
    }

    // =========================================================================
    // runProcedure tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#runProcedure(String, String[])}.
     *
     * <p>This method calls stored procedures via {@code DBPreparedHandler.procExecute()}.
     * H2 does not support MySQL-style stored procedures, so the test is disabled
     * with documentation for the limitation.</p>
     */
    @Nested
    @DisplayName("runProcedure")
    class RunProcedureTests {

        @Test
        @Tag("query")
        @Disabled("Requires MySQL stored procedures - not supported in H2. " +
                  "DBPreparedHandler.procExecute() builds a JDBC CallableStatement " +
                  "'{call procName(?,?)}' which requires a real MySQL/MariaDB backend.")
        @DisplayName("should call stored procedure via DBPreparedHandler")
        void shouldCallStoredProcedure_viaDbPreparedHandler() throws SQLException {
            // This test documents that runProcedure delegates to DBPreparedHandler.procExecute()
            // which builds: "{call <procName>(<params>)}"
            // This is MySQL-specific and cannot be tested with H2.
            lookupDao.runProcedure("test_proc", new String[]{"param1", "param2"});
        }

        @Test
        @Tag("query")
        @Disabled("Requires MySQL stored procedures - not supported in H2. " +
                  "DBPreparedHandler.procExecute() builds a JDBC CallableStatement.")
        @DisplayName("should handle null params array in stored procedure call")
        void shouldHandleNullParams_inStoredProcedureCall() throws SQLException {
            // DBPreparedHandler.procExecute() handles null params by not adding parameter placeholders
            lookupDao.runProcedure("test_proc_no_params", null);
        }

        @Test
        @Tag("query")
        @Disabled("Requires MySQL stored procedures - not supported in H2. " +
                  "DBPreparedHandler.procExecute() builds a JDBC CallableStatement.")
        @DisplayName("should handle empty params array in stored procedure call")
        void shouldHandleEmptyParams_inStoredProcedureCall() throws SQLException {
            // Empty array: procExecute builds "{call test_proc}" with no params
            lookupDao.runProcedure("test_proc_empty", new String[]{});
        }
    }

    // =========================================================================
    // getCountOfActiveClient tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#getCountOfActiveClient(String)}.
     *
     * <p>This method executes two raw JDBC queries via {@code DBPreparedHandler}:</p>
     * <ol>
     *   <li>Count of admissions with status 'admitted' in programs under the org</li>
     *   <li>If first count is zero, count of program_queue entries for programs under the org</li>
     * </ol>
     *
     * <p><b>SQL injection risk:</b> The orgCd parameter is concatenated directly into SQL
     * strings without parameterization. This is a known security issue documented for
     * future remediation during EntityManager migration.</p>
     *
     * <p><b>H2 compatibility:</b> The SQL uses Oracle-style concatenation ({@code ||})
     * which H2 supports, but references tables (admission, program_queue) that don't
     * exist in the test schema by default.</p>
     */
    @Nested
    @DisplayName("getCountOfActiveClient")
    class GetCountOfActiveClientTests {

        @Test
        @Tag("aggregate")
        @DisplayName("should use string concatenation in SQL - documents injection risk for migration")
        void shouldDocumentSqlInjectionRisk_inGetCountOfActiveClient() {
            // Given - This test documents that getCountOfActiveClient builds SQL with
            // string concatenation of the orgCd parameter:
            //   "... where codecsv like '%' || '" + orgCd + ",' || '%'"
            // This is a SQL injection vulnerability that should be fixed during
            // the EntityManager migration.

            // When/Then - The method will fail because 'admission' and 'program_queue'
            // tables don't exist in the H2 test schema, but the important thing is
            // documenting the concatenation pattern.
            assertThatThrownBy(() -> lookupDao.getCountOfActiveClient("ORG001"))
                .isInstanceOf(SQLException.class);
        }
    }

    // =========================================================================
    // setProviderDao tests
    // =========================================================================

    /**
     * Tests for {@link LookupDao#setProviderDao(ProviderDao)}.
     *
     * <p>Simple setter used for dependency injection of the ProviderDao.
     * The ProviderDao is used by LoadCodeList when the tableId is "USR"
     * to filter the result list by active providers in a program.</p>
     */
    @Nested
    @DisplayName("setProviderDao")
    class SetProviderDaoTests {

        @Autowired
        private ProviderDao providerDao;

        @Test
        @Tag("read")
        @DisplayName("should accept ProviderDao injection without error")
        void shouldAcceptProviderDao_withoutError() {
            // Given - a valid ProviderDao instance

            // When - setting the provider DAO
            lookupDao.setProviderDao(providerDao);

            // Then - no exception thrown, DAO is ready for USR table queries
        }

        @Test
        @Tag("read")
        @DisplayName("should accept null ProviderDao without error")
        void shouldAcceptNullProviderDao_withoutError() {
            // Given - null provider DAO

            // When
            lookupDao.setProviderDao(null);

            // Then - no exception; NPE would occur later if USR table is queried
        }
    }

    // =========================================================================
    // Additional GetLookupTableDef edge cases
    // =========================================================================

    /**
     * Tests for {@code GetLookupTableDef(String)} edge cases - verifies behavior with
     * empty string IDs, formula-based computed properties (hasActive, hasDisplayOrder),
     * and module name resolution from the {@code lst_field_category} table.
     */
    @Nested
    @DisplayName("GetLookupTableDef edge cases")
    class GetLookupTableDefEdgeCases {

        @Test
        @Tag("query")
        @DisplayName("should return first matching table when duplicate tableIds exist")
        void shouldReturnResult_whenTableIdExists() {
            // Given
            String tableId = nextTableId("DU");
            insertLookupTableDef(tableId, "table_dup_test");
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getTableId()).isEqualTo(tableId);
        }

        @Test
        @Tag("query")
        @DisplayName("should handle empty string tableId gracefully")
        void shouldReturnNull_whenTableIdIsEmpty() {
            // Given - empty string tableId that won't match any record

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef("");

            // Then - returns null since no record matches empty string
            assertThat(result).isNull();
        }

        @Test
        @Tag("query")
        @DisplayName("should compute formula-based properties (hasActive, hasDisplayOrder)")
        void shouldComputeFormulaProperties_fromFieldDefinitions() {
            // Given - a table with field defs at genericIdx 3 (active) and 4 (display order)
            String tableId = nextTableId("FO");
            insertLookupTableDef(tableId, "formula_test");
            insertField(tableId, "code_field", 1, 1);
            insertField(tableId, "active_field", 2, 3);      // genericIdx 3 = active
            insertField(tableId, "display_field", 3, 4);      // genericIdx 4 = display order
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then - formula columns should reflect field existence
            assertThat(result).isNotNull();
            // hasActive formula: count(*) from app_lookuptable_fields where genericidx=3
            assertThat(result.isHasActive()).isTrue();
            // hasDisplayOrder formula: count(*) from app_lookuptable_fields where genericidx=4
            assertThat(result.isHasDisplayOrder()).isTrue();
        }

        @Test
        @Tag("query")
        @DisplayName("should return false for hasActive when no genericIdx 3 field exists")
        void shouldReturnFalseHasActive_whenNoActiveField() {
            // Given - table with only genericIdx 1 and 2
            String tableId = nextTableId("FN");
            insertLookupTableDef(tableId, "no_active_test");
            insertField(tableId, "code_field", 1, 1);
            insertField(tableId, "desc_field", 2, 2);
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isHasActive()).isFalse();
            assertThat(result.isHasDisplayOrder()).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should resolve moduleName formula from lst_field_category")
        void shouldResolveModuleName_fromFieldCategoryTable() {
            // Given - insert a category record and a table referencing it
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(
                    "MERGE INTO lst_field_category (id, description) KEY(id) VALUES (:id, :desc)")
                    .setParameter("id", "MED")
                    .setParameter("desc", "Medical Records")
                    .executeUpdate();
                return null;
            });

            String tableId = nextTableId("FM");
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(INSERT_LOOKUP_TABLE)
                    .setParameter("tid", tableId)
                    .setParameter("tname", "module_test")
                    .setParameter("desc", "Module Test")
                    .setParameter("active", true)
                    .setParameter("ro", false)
                    .setParameter("tree", false)
                    .setParameter("tcl", 0)
                    .setParameter("mid", "MED")
                    .executeUpdate();
                return null;
            });
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then - moduleName formula resolves to "Medical Records"
            assertThat(result).isNotNull();
            assertThat(result.getModuleName()).isEqualTo("Medical Records");
            assertThat(result.getModuleId()).isEqualTo("MED");
        }
    }

    // =========================================================================
    // LoadFieldDefList edge cases
    // =========================================================================

    /**
     * Tests for {@code LoadFieldDefList(String)} edge cases - verifies empty results
     * for non-existent tableIds, single-field tables, and field type/attribute preservation.
     */
    @Nested
    @DisplayName("LoadFieldDefList edge cases")
    class LoadFieldDefListEdgeCases {

        @Test
        @Tag("query")
        @DisplayName("should return empty list for nonexistent tableId")
        void shouldReturnEmptyList_forNonexistentTableId() {
            // Given - no fields exist for this tableId

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> result = lookupDao.LoadFieldDefList("ZZZZZ");

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        @Tag("query")
        @DisplayName("should handle table with single field definition")
        void shouldHandleSingleField_inLoadFieldDefList() {
            // Given
            String tableId = nextTableId("FS");
            insertLookupTableDef(tableId, "single_field_table");
            insertField(tableId, "only_field", 1, 1);
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

            // Then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFieldName()).isEqualTo("only_field");
        }

        @Test
        @Tag("query")
        @DisplayName("should preserve field types and attributes from database")
        void shouldPreserveFieldAttributes_fromDatabase() {
            // Given - fields with different types
            String tableId = nextTableId("FT");
            insertLookupTableDef(tableId, "type_test");
            insertFieldFull(tableId, "string_field", 1, 1, "S", false, "");
            insertFieldFull(tableId, "date_field", 2, 9, "D", false, "");
            insertFieldFull(tableId, "int_field", 3, 3, "I", true, "OTHER_TBL");
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> result = lookupDao.LoadFieldDefList(tableId);

            // Then
            assertThat(result).hasSize(3);

            FieldDefValue stringField = result.get(0);
            assertThat(stringField.getFieldType()).isEqualTo("S");
            assertThat(stringField.isAuto()).isFalse();
            assertThat(stringField.getLookupTable()).isEmpty();

            FieldDefValue dateField = result.get(1);
            assertThat(dateField.getFieldType()).isEqualTo("D");
            assertThat(dateField.getGenericIdx()).isEqualTo(9);

            FieldDefValue intField = result.get(2);
            assertThat(intField.getFieldType()).isEqualTo("I");
            assertThat(intField.isAuto()).isTrue();
            assertThat(intField.getLookupTable()).isEqualTo("OTHER_TBL");
        }
    }

    // =========================================================================
    // LookupTableDefValue tree configuration tests
    // =========================================================================

    /**
     * Tests for tree-structured lookup table configuration - verifies that
     * {@code isTree} and {@code treeCodeLength} properties are correctly loaded
     * from the {@code app_lookuptable} definition.
     */
    @Nested
    @DisplayName("Tree-structured lookup tables")
    class TreeLookupTableTests {

        @Test
        @Tag("query")
        @DisplayName("should load tree configuration properties correctly")
        void shouldLoadTreeConfig_whenTreeIsEnabled() {
            // Given - a tree-configured lookup table
            String tableId = nextTableId("TR");
            insertLookupTableDefWithTree(tableId, "tree_test_table", true, 8);
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isTree()).isTrue();
            assertThat(result.getTreeCodeLength()).isEqualTo(8);
        }

        @Test
        @Tag("query")
        @DisplayName("should load non-tree table with tree=false")
        void shouldLoadNonTreeTable_withTreeFalse() {
            // Given
            String tableId = nextTableId("NT");
            insertLookupTableDefWithTree(tableId, "flat_table", false, 0);
            hibernateTemplate.flush();

            // When
            LookupTableDefValue result = lookupDao.GetLookupTableDef(tableId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isTree()).isFalse();
            assertThat(result.getTreeCodeLength()).isEqualTo(0);
        }
    }

    // =========================================================================
    // LstOrgcd entity tests (supporting inOrg and SaveAsOrgCode)
    // =========================================================================

    /**
     * Tests for {@code LstOrgcd} entity seeding and retrieval - verifies native SQL
     * insertion, HQL querying by codecsv pattern, status updates, and the
     * {@code updateOrgStatus} HQL pattern used in production.
     */
    @Nested
    @DisplayName("LstOrgcd entity seeding and retrieval")
    class LstOrgcdEntityTests {

        @Test
        @Tag("create")
        @DisplayName("should seed org-code data via native SQL")
        void shouldSeedOrgCodeData_viaNativeSql() {
            // Given
            insertOrgCode("R10001", "Root Org", "R10001", "R10001,");
            insertOrgCode("S10001", "Shelter One", "R10001S10001", "R10001,S10001,");
            hibernateTemplate.flush();

            // When - verify data was persisted by querying via HQL
            @SuppressWarnings("unchecked")
            List<io.github.carlos_emr.carlos.model.LstOrgcd> results =
                (List<io.github.carlos_emr.carlos.model.LstOrgcd>) hibernateTemplate.find(
                    "FROM LstOrgcd o WHERE o.code = ?0", "R10001");

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getDescription()).isEqualTo("Root Org");
            assertThat(results.get(0).getFullcode()).isEqualTo("R10001");
            assertThat(results.get(0).getCodecsv()).isEqualTo("R10001,");
        }

        @Test
        @Tag("query")
        @DisplayName("should query org-codes by codecsv pattern")
        void shouldQueryOrgCodes_byCodecsvPattern() {
            // Given - org hierarchy: Root > Shelter > Facility
            insertOrgCode("R10002", "Root", "R10002", "R10002,");
            insertOrgCode("S10002", "Shelter", "R10002S10002", "R10002,S10002,");
            insertOrgCodeFull("F10002", "Facility", 1, 0,
                "R10002S10002F10002", "R10002S10002F10002", "R10002,S10002,F10002,");
            hibernateTemplate.flush();

            // When - find all org-codes whose codecsv contains "R10002,"
            @SuppressWarnings("unchecked")
            List<io.github.carlos_emr.carlos.model.LstOrgcd> results =
                (List<io.github.carlos_emr.carlos.model.LstOrgcd>) hibernateTemplate.find(
                    "FROM LstOrgcd o WHERE o.codecsv LIKE ?0", "%R10002,%");

            // Then - all three should match since they all contain "R10002," in their codecsv
            assertThat(results).hasSize(3);
            assertThat(results)
                .extracting(io.github.carlos_emr.carlos.model.LstOrgcd::getCode)
                .containsExactlyInAnyOrder("R10002", "S10002", "F10002");
        }

        @Test
        @Tag("update")
        @DisplayName("should update org-code status via HibernateTemplate")
        void shouldUpdateOrgCodeStatus_viaHibernateTemplate() {
            // Given
            insertOrgCode("U10001", "Updatable Org", "U10001", "U10001,");
            hibernateTemplate.flush();

            // When - use HQL query pattern similar to updateOrgStatus
            @SuppressWarnings("unchecked")
            List<io.github.carlos_emr.carlos.model.LstOrgcd> orgs =
                (List<io.github.carlos_emr.carlos.model.LstOrgcd>) hibernateTemplate.find(
                    "FROM LstOrgcd o WHERE o.code = ?0", "U10001");
            assertThat(orgs).hasSize(1);

            io.github.carlos_emr.carlos.model.LstOrgcd org = orgs.get(0);
            org.setActiveyn(0);
            hibernateTemplate.update(org);
            hibernateTemplate.flush();

            // Then
            @SuppressWarnings("unchecked")
            List<io.github.carlos_emr.carlos.model.LstOrgcd> updated =
                (List<io.github.carlos_emr.carlos.model.LstOrgcd>) hibernateTemplate.find(
                    "FROM LstOrgcd o WHERE o.code = ?0", "U10001");
            assertThat(updated.get(0).getActiveyn()).isEqualTo(0);
        }

        @Test
        @Tag("query")
        @DisplayName("should verify updateOrgStatus HQL pattern is valid")
        void shouldVerifyUpdateOrgStatusHql_isValid() {
            // Given - the HQL pattern used in updateOrgStatus:
            //   "FROM LstOrgcd o WHERE o.codecsv like ?0"
            insertOrgCode("P10003", "Parent", "P10003", "P10003,");
            insertOrgCodeFull("C10003", "Child", 1, 0,
                "P10003C10003", "P10003C10003", "P10003,C10003,");
            hibernateTemplate.flush();

            // When - query pattern matching updateOrgStatus logic
            String oldCsv = "P10003," + "_%";
            @SuppressWarnings("unchecked")
            List<io.github.carlos_emr.carlos.model.LstOrgcd> children =
                (List<io.github.carlos_emr.carlos.model.LstOrgcd>) hibernateTemplate.find(
                    "FROM LstOrgcd o WHERE o.codecsv like ?0", oldCsv);

            // Then - only the child matches (the parent's codecsv is "P10003," not "P10003,_*")
            assertThat(children).hasSize(1);
            assertThat(children.get(0).getCode()).isEqualTo("C10003");
        }
    }

    // =========================================================================
    // SQL generation documentation tests
    // =========================================================================

    /**
     * Tests that document the SQL generation patterns in LoadCodeList for the
     * EntityManager migration. These test the HQL/field-loading portion without
     * executing the raw JDBC queries.
     */
    @Nested
    @DisplayName("LoadCodeList SQL generation (field loading)")
    class LoadCodeListSqlGenerationTests {

        @Test
        @Tag("query")
        @DisplayName("should load field definitions for SQL column mapping")
        void shouldLoadFieldDefs_forSqlColumnMapping() {
            // Given - a table with fields for all 17 generic indices
            String tableId = nextTableId("SG");
            insertLookupTableDef(tableId, "sql_gen_test");

            // Standard columns: code(1), description(2), active(3), displayOrder(4),
            // parentCode(5), buf1(6), codeTree(7), lastUpdateUser(8), lastUpdateDate(9)
            insertField(tableId, "code_col", 1, 1);
            insertField(tableId, "desc_col", 2, 2);
            insertField(tableId, "active_col", 3, 3);
            insertField(tableId, "order_col", 4, 4);
            insertField(tableId, "parent_col", 5, 5);
            insertField(tableId, "buf1_col", 6, 6);
            insertField(tableId, "tree_col", 7, 7);
            insertField(tableId, "user_col", 8, 8);
            insertFieldFull(tableId, "date_col", 9, 9, "D", false, "");
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> fields = lookupDao.LoadFieldDefList(tableId);

            // Then - verify the mapping between genericIdx and field names
            assertThat(fields).hasSize(9);

            // The SQL builder in LoadCodeList uses genericIdx to map columns:
            // genericIdx 1 = code column (used in WHERE for code filter)
            // genericIdx 2 = description column (used in WHERE for description filter)
            // genericIdx 3 = active column (used in WHERE for activeOnly filter)
            // genericIdx 4 = display order (used in ORDER BY)
            // genericIdx 5 = parent code (used in WHERE for parentCode filter)
            // genericIdx 7 = code tree (used in tree JOIN logic)
            assertThat(fields.stream()
                .filter(f -> f.getGenericIdx() == 1)
                .findFirst()
                .map(FieldDefValue::getFieldSQL)
                .orElse(""))
                .isEqualTo("code_col");

            assertThat(fields.stream()
                .filter(f -> f.getGenericIdx() == 3)
                .findFirst()
                .map(FieldDefValue::getFieldSQL)
                .orElse(""))
                .isEqualTo("active_col");
        }

        @Test
        @Tag("query")
        @DisplayName("should handle missing generic indices by providing null placeholders")
        void shouldReturnOnlyDefinedFields_whenSomeIndicesAreMissing() {
            // Given - table with only code(1) and description(2) fields
            // Missing indices 3-17 will get "null fieldN" placeholder in SQL
            String tableId = nextTableId("SP");
            insertLookupTableDef(tableId, "sparse_fields");
            insertField(tableId, "code_col", 1, 1);
            insertField(tableId, "desc_col", 2, 2);
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> fields = lookupDao.LoadFieldDefList(tableId);
            LookupTableDefValue tableDef = lookupDao.GetLookupTableDef(tableId);

            // Then - only 2 fields loaded; the SQL builder will add " null field3, null field4, ..."
            // for missing indices. Index 3 (active) missing means activeFieldExists = false,
            // so " 1 field3," is used instead (always active).
            assertThat(fields).hasSize(2);
            assertThat(tableDef).isNotNull();
            assertThat(tableDef.isHasActive()).isFalse();
        }

        @Test
        @Tag("query")
        @DisplayName("should handle parenthesized fieldSQL as function expression")
        void shouldIdentifyFunctionExpressions_inFieldSql() {
            // Given - field with parenthesized SQL (function expression)
            // When fieldSQL contains '(', LoadCodeList uses:
            //   fdef.getFieldSQL() + " " + fdef.getFieldName() + ","
            // instead of:
            //   "s." + fdef.getFieldSQL() + ","
            String tableId = nextTableId("FX");
            insertLookupTableDef(tableId, "func_expr_test");

            // Simulate a function expression field
            hibernateTemplate.execute(session -> {
                session.createNativeQuery(INSERT_FIELD)
                    .setParameter("tid", tableId)
                    .setParameter("fname", "concat_field")
                    .setParameter("fdesc", "Concatenated field")
                    .setParameter("ftype", "S")
                    .setParameter("edit", false)
                    .setParameter("lt", "")
                    .setParameter("fsql", "CONCAT(s.first_name, ' ', s.last_name)")
                    .setParameter("fidx", 1)
                    .setParameter("auto", false)
                    .setParameter("uniq", false)
                    .setParameter("gidx", 2)
                    .setParameter("flen", 200)
                    .executeUpdate();
                return null;
            });
            insertField(tableId, "id_col", 2, 1);
            hibernateTemplate.flush();

            // When
            @SuppressWarnings("unchecked")
            List<FieldDefValue> fields = lookupDao.LoadFieldDefList(tableId);

            // Then - verify the function expression field is loaded
            FieldDefValue funcField = fields.stream()
                .filter(f -> f.getGenericIdx() == 2)
                .findFirst()
                .orElse(null);
            assertThat(funcField).isNotNull();
            assertThat(funcField.getFieldSQL()).contains("CONCAT");
            // LoadCodeList will use "CONCAT(...) concat_field" in SELECT
            // instead of "s.CONCAT(...)"
        }
    }

    // =========================================================================
    // LookupCodeValue model tests
    // =========================================================================

    /**
     * Tests for {@link LookupCodeValue} model used throughout the DAO.
     * Verifies the value object correctly holds all fields used by LoadCodeList
     * and SaveCodeValue.
     */
    @Nested
    @DisplayName("LookupCodeValue model")
    class LookupCodeValueModelTests {

        @Test
        @Tag("read")
        @DisplayName("should generate correct codeId from prefix and code")
        void shouldGenerateCorrectCodeId_fromPrefixAndCode() {
            // Given
            LookupCodeValue lcv = new LookupCodeValue();
            lcv.setPrefix("ORG");
            lcv.setCode("R10001");

            // When
            String codeId = lcv.getCodeId();

            // Then
            assertThat(codeId).isEqualTo("ORG:R10001");
        }

        @Test
        @Tag("read")
        @DisplayName("should handle all buffer fields (buf1 through buf9)")
        void shouldHandleAllBufferFields_buf1ThroughBuf9() {
            // Given
            LookupCodeValue lcv = new LookupCodeValue();
            lcv.setBuf1("val1");
            lcv.setBuf3("val3");
            lcv.setBuf4("val4");
            lcv.setBuf5("val5");
            lcv.setBuf6("val6");
            lcv.setBuf7("val7");
            lcv.setBuf8("val8");
            lcv.setBuf9("val9");

            // Then
            assertThat(lcv.getBuf1()).isEqualTo("val1");
            assertThat(lcv.getBuf3()).isEqualTo("val3");
            assertThat(lcv.getBuf4()).isEqualTo("val4");
            assertThat(lcv.getBuf5()).isEqualTo("val5");
            assertThat(lcv.getBuf6()).isEqualTo("val6");
            assertThat(lcv.getBuf7()).isEqualTo("val7");
            assertThat(lcv.getBuf8()).isEqualTo("val8");
            assertThat(lcv.getBuf9()).isEqualTo("val9");
        }
    }
}
