/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.integration.ebs.client.ng;

import java.io.Serial;

import org.apache.wss4j.common.crypto.PasswordEncryptor;

/**
 * Disables WSS4J's implicit Jasypt dependency for EBS Crypto properties.
 *
 * <p>Merlin reads ordinary keystore/truststore passwords directly and calls this SPI only
 * for {@code ENC(...)} values. CARLOS provisions these properties externally and its EBS
 * callback does not supply a {@code PASSWORD_ENCRYPTOR_PASSWORD} master password. Reject
 * encrypted properties explicitly instead of falling back to Jasypt or treating ciphertext
 * as a password. This SPI does not perform SOAP message encryption or decryption.</p>
 */
enum PlaintextCryptoPasswordEncryptor implements PasswordEncryptor {
    INSTANCE;

    private static final String UNSUPPORTED_MESSAGE =
            "ENC(...) WS-Security Crypto passwords are not supported. Provision the keystore/truststore "
            + "password in the access-restricted, deployment-managed Crypto properties file; "
            + "see docs/migration/ebs-without-jasypt.md.";

    @Override
    public String encrypt(String password) {
        throw new EncryptedPasswordException();
    }

    @Override
    public String decrypt(String encryptedPassword) {
        throw new EncryptedPasswordException();
    }

    static final class EncryptedPasswordException extends UnsupportedOperationException {
        @Serial
        private static final long serialVersionUID = 1L;

        private EncryptedPasswordException() {
            super(UNSUPPORTED_MESSAGE);
        }
    }
}
