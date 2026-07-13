/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.test.base;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.jdbc.JdbcTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.annotation.Rollback;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Base class for DAO tests that need database interaction.
 * This class handles:
 * - Database setup and teardown
 * - Transaction management
 * - Schema management compatibility with legacy SchemaUtils
 */
@TestPropertySource(properties = {
    "test.db.schema.auto=create",
    "test.db.data.auto=false"
})
public abstract class CarlosDaoTestBase extends CarlosTestBase {

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected JdbcTemplate jdbcTemplate;
    protected TransactionTemplate transactionTemplate;

    /**
     * Tables to be preserved across test runs
     * Override this to specify tables that shouldn't be cleared
     */
    protected String[] preservedTables() {
        return new String[] {
            "issue", "issueGroup", "secRole", "secObjPrivilege",
            "LookupList", "LookupListItem", "measurementType", "measurementGroup"
        };
    }

    /**
     * Tables to be cleaned before each test
     * Override this to specify which tables should be cleared
     */
    protected String[] cleanableTables() {
        return new String[] {
            "demographic", "appointment", "billing", "drugs",
            "casemgmt_note", "allergies", "prevention"
        };
    }

    @BeforeEach
    public void setUpDatabase() throws Exception {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(transactionManager);

        // Initialize database schema if needed
        initializeSchema();

        // Clean tables before test
        cleanTables();
    }

    @AfterEach
    public void tearDownDatabase() {
        if (!isTransactionRollback()) {
            cleanTables();
        }
    }

    /**
     * Initialize schema - compatible with legacy SchemaUtils approach
     */
    protected void initializeSchema() throws Exception {
        // Check if we need to create schema
        if (shouldCreateSchema()) {
            logger.info("Initializing database schema for test");

            // Try to use legacy SchemaUtils if available
            if (isLegacySchemaUtilsAvailable()) {
                invokeLegacySchemaUtils();
            } else {
                // Use modern approach
                createModernSchema();
            }
        }
    }

    /**
     * Check if legacy SchemaUtils is available and can be used
     */
    private boolean isLegacySchemaUtilsAvailable() {
        try {
            Class<?> schemaUtilsClass = Class.forName("io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils");
            return schemaUtilsClass != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Invoke legacy SchemaUtils for backward compatibility.
     *
     * <p>{@code ClassNotFoundException} and {@code NoSuchMethodException} mean
     * the class or method does not exist — safe to fall back to the no-op schema
     * setup.  {@code IllegalAccessException} means the class was found but is
     * inaccessible from this call site, which is a configuration problem and is
     * rethrown as {@link IllegalStateException} so the test fails with a clear
     * diagnostic rather than silently using the no-op fallback.</p>
     *
     * <p>If {@code restoreTable} itself throws, {@code Method.invoke()} wraps
     * the cause in {@code InvocationTargetException}, which propagates through
     * {@code throws Exception} to {@link #initializeSchema()} and then to
     * {@link #setUpDatabase()}, causing the test to fail visibly.</p>
     */
    private void invokeLegacySchemaUtils() throws Exception {
        try {
            // Use reflection to avoid compile-time dependency
            Class<?> schemaUtilsClass = Class.forName("io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils");
            Method restoreMethod = schemaUtilsClass.getMethod("restoreTable", String[].class);

            String[] tables = cleanableTables();
            if (tables.length > 0) {
                restoreMethod.invoke(null, (Object) tables);
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            logger.warn("Legacy SchemaUtils not available (class or method not found), " +
                        "falling back to modern approach", e);
            createModernSchema();
        } catch (IllegalAccessException e) {
            // SchemaUtils exists but is inaccessible — configuration problem, not "not available".
            throw new IllegalStateException(
                "Legacy SchemaUtils found but not accessible via reflection — " +
                "check module/access configuration", e);
        }
    }

    /**
     * No-op fallback invoked when legacy SchemaUtils is unavailable.
     * The test persistence unit uses {@code hbm2ddl.auto=create}, so Hibernate
     * already creates the schema before any test runs.
     */
    private void createModernSchema() {
        // Schema is managed by hbm2ddl.auto=create in the test persistence unit.
        // No additional action is required here.
    }

    /**
     * Deletes all rows from the tables returned by {@link #cleanableTables()},
     * minus those in {@link #preservedTables()}.
     * {@code DataAccessException} is rethrown so a cleanup failure causes the
     * test to fail with a clear error rather than silently leaving dirty state.
     */
    protected void cleanTables() {
        List<String> tablesToClean = new ArrayList<>(Arrays.asList(cleanableTables()));
        tablesToClean.removeAll(Arrays.asList(preservedTables()));

        for (String table : tablesToClean) {
            if (tableExists(table)) {
                JdbcTestUtils.deleteFromTables(jdbcTemplate, table);
                logger.debug("Cleaned table: {}", table);
            }
        }
    }

    /**
     * Returns {@code true} if {@code tableName} exists in the test database.
     * Uses a count query so the result is unambiguous (0 = absent, &gt;0 = present).
     * {@code DataAccessException} is rethrown — a connectivity or SQL failure
     * must not silently masquerade as "table not found".
     */
    protected boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
            Integer.class, tableName
        );
        return count != null && count > 0;
    }

    /**
     * Check if transaction rollback is enabled
     */
    protected boolean isTransactionRollback() {
        return this.getClass().isAnnotationPresent(Rollback.class);
    }

    /**
     * Check if schema should be created
     */
    protected boolean shouldCreateSchema() {
        String property = System.getProperty("test.db.schema.auto", "create");
        return "create".equals(property);
    }

    /**
     * Execute in a new transaction
     */
    protected <T> T executeInTransaction(TransactionCallback<T> callback) {
        return transactionTemplate.execute(status -> callback.doInTransaction());
    }

    /**
     * Functional interface for transaction callbacks
     */
    @FunctionalInterface
    protected interface TransactionCallback<T> {
        T doInTransaction();
    }

    /**
     * Helper method to count rows in a table
     */
    protected int countRowsInTable(String tableName) {
        return JdbcTestUtils.countRowsInTable(jdbcTemplate, tableName);
    }

    /**
     * Helper to verify SpringUtils works with DAOs
     */
    protected void verifyDaoSpringUtilsIntegration(Class<?> daoClass) {
        // Verify that SpringUtils.getBean returns a DAO of the correct type.
        // When multiple Spring contexts exist across test classes, the instances
        // may differ in identity but should be of the same implementation class.
        Object contextDao = applicationContext.getBean(daoClass);
        Object utilsDao = SpringUtils.getBean(daoClass);

        if (!contextDao.getClass().equals(utilsDao.getClass())) {
            throw new AssertionError(
                String.format("DAO type mismatch for %s. Context: %s, Utils: %s",
                    daoClass.getSimpleName(), contextDao.getClass().getName(), utilsDao.getClass().getName())
            );
        }
    }
}