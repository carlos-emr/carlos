/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
// -----------------------------------------------------------------------------------------------------------------------
// *
// *
// * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved. *
// * This software is published under the GPL GNU General Public License.
// * This program is free software; you can redistribute it and/or
// * modify it under the terms of the GNU General Public License
// * as published by the Free Software Foundation; either version 2
// * of the License, or (at your option) any later version. *
// * This program is distributed in the hope that it will be useful,
// * but WITHOUT ANY WARRANTY; without even the implied warranty of
// * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// * GNU General Public License for more details. * * You should have received a copy of the GNU General Public License
// * along with this program; if not, write to the Free Software
// * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
// *
// * <OSCAR TEAM>
// * This software was written for the
// * Department of Family Medicine
// * McMaster University
// * Hamilton
// * Ontario, Canada
// *
// -----------------------------------------------------------------------------------------------------------------------

package ca.openosp.openo.commn.dao.utils;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.utility.MiscUtils;

/**
 * This class is a utility class to create and drop the database schema and all it's bits.
 * This class is highly database specific and alternate versions of this class will have to be
 * written for other databases.
 */
public class SchemaUtils {
    private static Logger logger = MiscUtils.getLogger();

    public static boolean inited = false;
    public static Map<String, String> createTableStatements = new HashMap<String, String>();

    private static Connection getConnection() throws InstantiationException, IllegalAccessException, ClassNotFoundException, SQLException {
        String driver = ConfigUtils.getProperty("db_driver");
        String user = ConfigUtils.getProperty("db_user");
        String password = ConfigUtils.getProperty("db_password");
        String urlPrefix = ConfigUtils.getProperty("db_url_prefix");
        Class.forName(driver).newInstance();

        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", user);
        connectionProperties.setProperty("password", password);

        String[] dbPropertiesArray = null;
        String dbProperties = ConfigUtils.getProperty("db_schema_properties");

        if (dbProperties.startsWith("?")) {
            dbProperties = dbProperties.replaceFirst("\\?", "");
        }

        if (dbProperties.contains("&")) {
            dbPropertiesArray = dbProperties.split("&");
        }

        if (dbPropertiesArray != null) {
            String[] dbPropertyItemArray;
            for (String dbPropertyItem : dbPropertiesArray) {
                if (dbPropertyItem.contains("=")) {
                    dbPropertyItemArray = dbPropertyItem.split("=");
                    connectionProperties.setProperty(dbPropertyItemArray[0], dbPropertyItemArray[1]);
                }
            }
        }

        logger.debug("#------------>>  getConnection() : " + driver + " " + urlPrefix + " " + connectionProperties);

        return DriverManager.getConnection(urlPrefix, connectionProperties);
    }

    /**
     * Intent is to support junit tests.
     *
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public static void dropDatabaseIfExists() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        logger.info("#------------>> dropDatabaseIfExists() : " + ConfigUtils.getProperty("db_schema"));

        Connection c = getConnection();

        try {
            Statement s = c.createStatement();
            s.executeUpdate("drop database if exists " + ConfigUtils.getProperty("db_schema"));
        } finally {
            c.close();
        }

    }

    /**
     * Intent is for new installations.
     *
     * @throws SQLException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     */
    public static void createDatabaseAndTables() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {

        logger.info("#------------>> createDatabaseAndTables()");

        boolean skipDbInit = false;
        if (System.getProperty("openo.dbinit.skip") != null && System.getProperty("openo.dbinit.skip").equalsIgnoreCase("true")) {
            skipDbInit = true;
        }

        String schema = ConfigUtils.getProperty("db_schema");
        logger.info("using schema : " + schema);

        Connection c = getConnection();
        try {
            Statement s = c.createStatement();

            if (!skipDbInit) {
                s.executeUpdate("create database " + schema + " DEFAULT CHARACTER SET utf8  DEFAULT COLLATE utf8_general_ci");
            }
            s.executeUpdate("use " + schema);

            runCreateTablesScript(c);
        } finally {
            c.close();
        }
    }


    public static void restoreTable(String... tableNames) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        restoreTable(true, tableNames);
    }

    public static void dropTable(String... tableNames) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        logger.info("#------------>> dropTable() : " + tableNames);

        String schema = ConfigUtils.getProperty("db_schema");

        Connection c = getConnection();
        try {
            Statement s = c.createStatement();
            s.executeUpdate("use " + schema);
            s.executeUpdate("set foreign_key_checks = 0");
            for (int i = 0; i < tableNames.length; i++) {
                s.executeUpdate("drop table if exists " + tableNames[i]);
            }
            s.executeUpdate("set foreign_key_checks = 1");
            s.close();

        } finally {
            c.close();
        }
    }

    private static boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName.toLowerCase().contains("windows");
    }

    public static void restoreTable(boolean includeInitData, String... tableNames) throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        long start = System.currentTimeMillis();
        String schema = ConfigUtils.getProperty("db_schema");

        Connection c = getConnection();
        try {
            Statement s = c.createStatement();
            s.executeUpdate("use " + schema);
            s.executeUpdate("set foreign_key_checks = 0");
            for (int i = 0; i < tableNames.length; i++) {
                String tableName = tableNames[i];
                if (isWindows()) {
                    tableName = tableName.toLowerCase(); // make it case insensitive by default
                }

                // Drop foreign key constraints associated with tableName_maventest
                Statement s1 = c.createStatement();
                ResultSet newrs = s1.executeQuery("SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_NAME = '" + tableName + "_maventest" + "' AND " +
                        "CONSTRAINT_NAME LIKE 'fk_%'");
                while (newrs.next()) {

                    String constraintName = newrs.getString("CONSTRAINT_NAME");
                    s.executeUpdate("ALTER TABLE " + tableName + "_maventest" + " DROP FOREIGN KEY " + constraintName);
                }
                newrs.close();
                s1.close();

                s.executeUpdate("drop table if exists " + tableName);

                String createTableStatement = createTableStatements.get(tableName);
                if (createTableStatement == null) {
                    throw new IllegalStateException("Unable to locate create table statement for " + tableName + ". Please make sure that the table exists in the schema.");
                }
                s.executeUpdate(createTableStatement.replaceAll("_maventest", ""));

                if (includeInitData)
                    s.executeUpdate("insert into " + tableName + " select * from " + tableName + "_maventest");
            }
            s.executeUpdate("set foreign_key_checks = 1");
            s.close();

        } finally {
            c.close();
        }
        long end = System.currentTimeMillis();
        long secsTaken = (end - start) / 1000;
        if (secsTaken > 30) {
            MiscUtils.getLogger().info("Restoring db took " + secsTaken + " seconds.");
        }
    }


    public static void restoreAllTables() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException {
        logger.info("#------------>> restoreAllTables()");

        long start = System.currentTimeMillis();
        String schema = ConfigUtils.getProperty("db_schema");

        Connection c = getConnection();
        try {
            Statement s = c.createStatement();
            s.executeUpdate("use " + schema);
            s.executeUpdate("SET foreign_key_checks = 0");

            // Drop foreign key constraints associated with tableName_maventest
            for (String tableName : createTableStatements.keySet()) {
                Statement s1 = c.createStatement();
                ResultSet newrs = s1.executeQuery("SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
                        "WHERE TABLE_NAME = '" + tableName + "_maventest" + "' AND " +
                        "CONSTRAINT_NAME LIKE 'fk_%'");
                while (newrs.next()) {
                    String constraintName = newrs.getString("CONSTRAINT_NAME");
                    s.executeUpdate("ALTER TABLE " + tableName + "_maventest" + " DROP FOREIGN KEY " + constraintName);
                }
                newrs.close();
                s1.close();

                s.executeUpdate("drop table if exists " + tableName);
                s.executeUpdate(createTableStatements.get(tableName));
                s.executeUpdate("insert into " + tableName + " select * from " + tableName + "_maventest");
            }

            s.executeUpdate("SET foreign_key_checks = 1");
            s.close();
        } finally {
            c.close();
        }
        long end = System.currentTimeMillis();
        long secsTaken = (end - start) / 1000;
        if (secsTaken > 30) {
            MiscUtils.getLogger().info("Restoring db took " + secsTaken + " seconds.");
        }
    }

    public static int loadFileIntoMySQL(String filename) throws IOException {

        logger.info("#------------>> loadFileIntoMySQL() : " + filename);

        String dir = new File(filename).getParent();
        String env[] = null;
        String envStr = null;
        if (System.getProperty("os.name").startsWith("Windows")) {
            envStr = "SYSTEMROOT=" + System.getenv("SYSTEMROOT");
        }
        if (envStr != null) {
            env = new String[]{envStr};
        } else {
            env = new String[]{};
        }

        String[] commandString = {"mysql", "--user=" + ConfigUtils.getProperty("db_user"), "--password=" + ConfigUtils.getProperty("db_password"), "--host=" + ConfigUtils.getProperty("db_host"), "-e", "source " + filename, ConfigUtils.getProperty("db_schema")};
        logger.debug("Runtime exec command string : " + Arrays.toString(commandString));
        Process p = Runtime.getRuntime().exec(commandString, env, new File(dir));

        try (
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {

            // read the output from the command
            String s = null;
            while ((s = stdInput.readLine()) != null) {
                MiscUtils.getLogger().info(s);
            }

            // read any errors from the attempted command
            while ((s = stdError.readLine()) != null) {
                if (!s.contains("[Warning]")) {
                    MiscUtils.getLogger().info(s);
                }
            }
        }
        int exitValue = -1;
        try {
            exitValue = p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("error with process");
        }

        return exitValue;
    }

    private static void runCreateTablesScript(Connection c) throws IOException, SQLException {
        logger.info("#------------>> runCreateTablesScript()");

        boolean skipDbInit = false;
        if (System.getProperty("openo.dbinit.skip") != null && System.getProperty("openo.dbinit.skip").equalsIgnoreCase("true")) {
            skipDbInit = true;
        }

        if (!skipDbInit) {
            String baseDir = System.getProperty("user.dir");
            logger.info("using baseDir : " + baseDir);

            try (Statement stmt = c.createStatement()) {
                stmt.executeUpdate("set foreign_key_checks = 0");
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscarinit.sql"), 0);

                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscarinit_on.sql"), 0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscardata.sql"), 0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscardata_on.sql"), 0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/icd9.sql"), 0);

                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/caisi/initcaisi.sql"), 0);

                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/caisi/initcaisidata.sql"), 0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/caisi/populate_issue_icd9.sql"), 0);
                //		assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/icd9_issue_groups.sql"),0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/measurementMapData.sql"), 0);
                //		assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/expire_oscardoc.sql"),0);

                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscarinit_bc.sql"), 0);
                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscardata_bc.sql"), 0);

                assertEquals(loadFileIntoMySQL(baseDir + "/database/mysql/oscarinit_2025.sql"), 0);
                stmt.executeUpdate("set foreign_key_checks = 1");
            }


            createTableStatements.clear();
            try {
                ResultSet rs = c.getMetaData().getTables(ConfigUtils.getProperty("db_schema"), null, "%", null);
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");

                    Statement stmt2 = c.createStatement();
                    ResultSet rs2 = stmt2.executeQuery("show create table " + tableName + ";");
                    if (rs2.next()) {
                        String sql = rs2.getString(2);
                        createTableStatements.put(tableName, sql);

                        Statement stmt = c.createStatement();
                        stmt.executeUpdate("alter table " + tableName + " rename to " + tableName + "_maventest");
                        stmt.close();
                    }
                    rs2.close();
                    stmt2.close();
                }
                rs.close();


            } catch (SQLException e) {
                MiscUtils.getLogger().error("Error:", e);
            }
        } else {
            //we're assuming a db ready to go..we just need to build the createTableStatementsMap
            createTableStatements.clear();
            try {
                ResultSet rs = c.getMetaData().getTables(ConfigUtils.getProperty("db_schema"), null, "%", null);
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    if (!tableName.endsWith("_maventest"))
                        continue;
                    Statement stmt2 = c.createStatement();
                    ResultSet rs2 = stmt2.executeQuery("show create table " + tableName + ";");
                    if (rs2.next()) {
                        String sql = rs2.getString(2).replaceAll(tableName, tableName.substring(0, tableName.length() - 10));
                        createTableStatements.put(tableName.substring(0, tableName.length() - 10), sql);
                    }
                    rs2.close();
                    stmt2.close();
                }
                rs.close();


            } catch (SQLException e) {
                MiscUtils.getLogger().error("Error:", e);
            }
        }

        try {
            restoreTable(false, "ResourceStorage");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error:", e);
        }
        inited = true;
    }

    /**
     * Intended for existing installations.
     */
    public static void upgradeDatabase() {
        throw (new NotImplementedException("Not implemented yet..."));
    }

    /**
     * Intent is for junit tests.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws SQLException
     * @throws NoSuchAlgorithmException
     */
    public static void dropAndRecreateDatabase() throws SQLException, InstantiationException, IllegalAccessException, ClassNotFoundException, IOException {
        boolean skipDbInit = false;
        if (System.getProperty("openo.dbinit.skip") != null && System.getProperty("openo.dbinit.skip").equalsIgnoreCase("true")) {
            skipDbInit = true;
        }
        if (!skipDbInit) {
            dropDatabaseIfExists();
        }
        createDatabaseAndTables();
    }

    public static void main(String... argv) throws Exception {
        dropAndRecreateDatabase();
    }
}
