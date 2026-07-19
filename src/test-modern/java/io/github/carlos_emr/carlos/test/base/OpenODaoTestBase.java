/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * DAO Test Base Class with Database Support
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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

import org.dbunit.database.DatabaseConnection;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;

import io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Base class for DAO tests that need database interaction.
 * This class handles:
 * - Database setup and teardown
 * - Transaction management
 * - DBUnit integration for test data
 * - Schema management compatibility with legacy SchemaUtils
 */
@TestPropertySource(properties = {
    "test.db.schema.auto=create",
    "test.db.data.auto=false"
})
public abstract class OpenODaoTestBase extends OpenOTestBase {

    @Autowired
    protected DataSource dataSource;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    protected JdbcTemplate jdbcTemplate;
    protected TransactionTemplate transactionTemplate;
    protected IDatabaseConnection dbUnitConnection;

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

        // Set up DBUnit connection
        Connection connection = dataSource.getConnection();
        this.dbUnitConnection = new DatabaseConnection(connection);

        // Initialize database schema if needed
        initializeSchema();

        // Clean tables before test
        cleanTables();

        // Load test data if specified
        loadTestData();
    }

    @AfterEach
    public void tearDownDatabase() throws Exception {
        // Clean up after test if not using @Rollback
        if (!isTransactionRollback()) {
            cleanTables();
        }

        if (dbUnitConnection != null) {
            dbUnitConnection.close();
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
     * Invoke legacy SchemaUtils for backward compatibility
     */
    private void invokeLegacySchemaUtils() {
        try {
            // Use reflection to avoid compile-time dependency
            Class<?> schemaUtilsClass = Class.forName("io.github.carlos_emr.carlos.commn.dao.utils.SchemaUtils");
            Method restoreMethod = schemaUtilsClass.getMethod("restoreTable", String[].class);

            String[] tables = cleanableTables();
            if (tables.length > 0) {
                restoreMethod.invoke(null, (Object) tables);
            }
        } catch (Exception e) {
            logger.warn("Could not use legacy SchemaUtils, falling back to modern approach", e);
            createModernSchema();
        }
    }

    /**
     * Modern schema creation approach
     */
    private void createModernSchema() {
        // This would execute schema creation scripts
        // You can customize this based on your needs
        logger.info("Creating schema using modern approach");
    }

    /**
     * Clean specified tables
     */
    protected void cleanTables() {
        List<String> tablesToClean = new ArrayList<>(Arrays.asList(cleanableTables()));
        tablesToClean.removeAll(Arrays.asList(preservedTables()));

        for (String table : tablesToClean) {
            try {
                if (tableExists(table)) {
                    JdbcTestUtils.deleteFromTables(jdbcTemplate, table);
                    logger.debug("Cleaned table: {}", table);
                }
            } catch (Exception e) {
                logger.warn("Could not clean table: {}", table, e);
            }
        }
    }

    /**
     * Check if a table exists in the database
     */
    protected boolean tableExists(String tableName) {
        try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, tableName
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Load test data from XML files or other sources
     * Override this method to load specific test data
     */
    protected void loadTestData() throws Exception {
        String testDataFile = getTestDataFile();
        if (testDataFile != null) {
            IDataSet dataSet = new FlatXmlDataSetBuilder()
                .build(getClass().getResourceAsStream(testDataFile));
            DatabaseOperation.CLEAN_INSERT.execute(dbUnitConnection, dataSet);
            logger.info("Loaded test data from: {}", testDataFile);
        }
    }

    /**
     * Get the test data file path
     * Override this to specify test data file
     */
    protected String getTestDataFile() {
        return null;
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
        // Verify that SpringUtils.getBean returns the same DAO instance
        Object contextDao = applicationContext.getBean(daoClass);
        Object utilsDao = SpringUtils.getBean(daoClass);

        if (contextDao != utilsDao) {
            throw new AssertionError(
                String.format("DAO mismatch for %s. SpringUtils not properly initialized for tests.",
                    daoClass.getSimpleName())
            );
        }
    }
}