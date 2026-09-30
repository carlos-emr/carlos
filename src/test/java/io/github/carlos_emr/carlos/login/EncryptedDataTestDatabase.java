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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import io.github.carlos_emr.carlos.utility.ImageMagicNumbers;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Properties;
import java.util.UUID;

/**
 * A private in-memory H2 database shaped like the CARLOS tables that hold data encrypted with the
 * application key, for the #3939 startup check. Every value it writes is synthetic.
 *
 * <p>One connection is held open for the life of the object, which keeps the in-memory database
 * alive while the code under test opens and closes its own connections to it.</p>
 */
final class EncryptedDataTestDatabase implements AutoCloseable {

    /** Obviously synthetic 32-byte AES key: the ASCII text below, Base64-encoded. Never a real key. */
    static final String SYNTHETIC_KEY = Base64.getEncoder().encodeToString(
            "synthetic-test-key-not-real-0001".getBytes(StandardCharsets.US_ASCII));

    /** Synthetic database password, so tests can assert it never reaches a log line. */
    static final String DB_PASSWORD = "synthetic-db-password-not-real"; // NOSONAR java:S2068 - synthetic test fixture

    static final String DB_USER = "sa";

    private final String dbName;
    private final Connection keeper;

    EncryptedDataTestDatabase() throws SQLException {
        // NON_KEYWORDS=VALUE: property.value is a plain column in MariaDB but a keyword in H2.
        dbName = "startup_key_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;NON_KEYWORDS=VALUE";
        keeper = DriverManager.getConnection(uri() + dbName, DB_USER, DB_PASSWORD);
    }

    static String uri() {
        return "jdbc:h2:mem:";
    }

    String dbName() {
        return dbName;
    }

    /** A loader that opens its own fresh connection, as Startup's does. */
    EncryptedDataCountLoader loader() {
        return new EncryptedDataCountLoader(() -> DriverManager.getConnection(uri() + dbName, DB_USER, DB_PASSWORD));
    }

    /** Points the {@code db_*} settings the application data source uses at this database. */
    void applyTo(Properties properties) {
        properties.setProperty("db_driver", "org.h2.Driver");
        properties.setProperty("db_uri", uri());
        properties.setProperty("db_name", dbName);
        properties.setProperty("db_username", DB_USER);
        properties.setProperty("db_password", DB_PASSWORD);
    }

    /** Creates every table the check reads, with the production column names. */
    EncryptedDataTestDatabase withAllTables() throws SQLException {
        return withEmailConfig().withFaxConfig().withProperty().withSecurity().withDigitalSignature();
    }

    EncryptedDataTestDatabase withEmailConfig() throws SQLException {
        execute("CREATE TABLE emailConfig (id INT PRIMARY KEY, active BOOLEAN, configDetails TEXT)");
        return this;
    }

    EncryptedDataTestDatabase withFaxConfig() throws SQLException {
        execute("CREATE TABLE fax_config (id INT PRIMARY KEY, passwd VARCHAR(255) DEFAULT '',"
                + " faxUser VARCHAR(255) DEFAULT '', faxPasswd VARCHAR(255) DEFAULT '')");
        return this;
    }

    /** An older schema without the {@code faxPasswd} column. */
    EncryptedDataTestDatabase withFaxConfigLackingFaxPasswd() throws SQLException {
        execute("CREATE TABLE fax_config (id INT PRIMARY KEY, passwd VARCHAR(255) DEFAULT '')");
        return this;
    }

    EncryptedDataTestDatabase withProperty() throws SQLException {
        execute("CREATE TABLE property (id INT PRIMARY KEY, name VARCHAR(255), value VARCHAR(2000),"
                + " provider_no VARCHAR(6))");
        return this;
    }

    EncryptedDataTestDatabase withSecurity() throws SQLException {
        execute("CREATE TABLE security (security_no INT PRIMARY KEY, user_name VARCHAR(30),"
                + " usingMfa BOOLEAN, mfaSecret VARCHAR(255))");
        return this;
    }

    EncryptedDataTestDatabase withDigitalSignature() throws SQLException {
        execute("CREATE TABLE DigitalSignature (id INT PRIMARY KEY, providerNo VARCHAR(6),"
                + " signatureImage MEDIUMBLOB NOT NULL)");
        return this;
    }

    void insertEmailConfig(int id, String configDetails) throws SQLException {
        update("INSERT INTO emailConfig (id, active, configDetails) VALUES (?, TRUE, ?)", id, configDetails);
    }

    void insertFaxConfig(int id, String passwd, String faxPasswd) throws SQLException {
        update("INSERT INTO fax_config (id, passwd, faxUser, faxPasswd) VALUES (?, ?, '000000', ?)",
                id, passwd, faxPasswd);
    }

    void insertProperty(int id, String name, String value) throws SQLException {
        update("INSERT INTO property (id, name, value, provider_no) VALUES (?, ?, ?, '')", id, name, value);
    }

    void insertSecurity(int id, String userName, String mfaSecret) throws SQLException {
        update("INSERT INTO security (security_no, user_name, usingMfa, mfaSecret) VALUES (?, ?, TRUE, ?)",
                id, userName, mfaSecret);
    }

    void insertDigitalSignature(int id, byte[] image) throws SQLException {
        try (PreparedStatement statement = keeper.prepareStatement(
                "INSERT INTO DigitalSignature (id, providerNo, signatureImage) VALUES (?, '999998', ?)")) {
            statement.setInt(1, id);
            statement.setBytes(2, image);
            statement.executeUpdate();
        }
    }

    void execute(String sql) throws SQLException {
        try (Statement statement = keeper.createStatement()) {
            statement.execute(sql);
        }
    }

    private void update(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = keeper.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        }
    }

    /** A synthetic JPEG header followed by filler: what a legacy plaintext signature row holds. */
    static byte[] plaintextJpeg() {
        byte[] image = new byte[64];
        image[0] = (byte) 0xFF;
        image[1] = (byte) 0xD8;
        image[2] = (byte) 0xFF;
        image[3] = (byte) 0xE0;
        return image;
    }

    /** A synthetic PNG header followed by filler. */
    static byte[] plaintextPng() {
        byte[] image = new byte[64];
        image[0] = (byte) 0x89;
        image[1] = 0x50;
        image[2] = 0x4E;
        image[3] = 0x47;
        return image;
    }

    /**
     * Encrypts under {@link #SYNTHETIC_KEY}. The caller must have prepared that key with
     * {@link EncryptionUtils#prepareSecretKeySpec()} first.
     */
    static String encryptText(String plaintext) throws Exception {
        return EncryptionUtils.encrypt(plaintext);
    }

    /** Encrypts bytes under the prepared key; output starts with a random IV, never image magic. */
    static byte[] encryptBytes(byte[] plaintext) throws Exception {
        byte[] encrypted;
        do {
            // A random IV starts with 'BM' (a BMP magic number) about once in 65,536 tries; retry so
            // the fixture is always recognisable as ciphertext.
            encrypted = EncryptionUtils.encrypt(plaintext);
        } while (ImageMagicNumbers.isKnownRasterImage(encrypted));
        return encrypted;
    }

    @Override
    public void close() throws SQLException {
        keeper.close();
    }
}
