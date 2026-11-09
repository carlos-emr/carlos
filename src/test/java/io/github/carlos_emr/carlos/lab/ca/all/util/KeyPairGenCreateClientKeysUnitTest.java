/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen.ClientKeyStatus;
import io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen.ClientKeys;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KeyPairGen#createClientKeys(String, String)}: a client key pair is stored
 * only once the CARLOS ("oscar") key pair exists, so a refused attempt leaves the service name
 * free for a retry (issue #6009).
 *
 * <p>Kept apart from {@code KeyPairGenUnitTest}, which covers key size and {@code getPublic()}.</p>
 *
 * @since 2026-11-09
 */
@Tag("unit")
@Tag("fast")
@Tag("lab")
@DisplayName("KeyPairGen.createClientKeys")
class KeyPairGenCreateClientKeysUnitTest extends CarlosUnitTestBase {

    private static final String SERVICE = "fake-lab.client";
    // Readable placeholders, not key material: these tests never decode a key.
    private static final String CARLOS_PUBLIC_KEY = "FAKE-carlos-public-key";

    @Mock
    private PublicKeyDao mockPublicKeyDao;

    @Mock
    private OscarKeyDao mockOscarKeyDao;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        registerMock(PublicKeyDao.class, mockPublicKeyDao);
        registerMock(OscarKeyDao.class, mockOscarKeyDao);
    }

    private static OscarKey carlosKey(String publicKey) {
        OscarKey key = new OscarKey();
        key.setName("oscar");
        key.setPublicKey(publicKey);
        key.setPrivateKey("FAKE-carlos-private-key");
        return key;
    }

    @Test
    @DisplayName("should store nothing and report the missing CARLOS key when it has not been created")
    void shouldStoreNothing_whenCarlosKeyMissing() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(null);

        ClientKeys result;
        try (LogCapture logs = LogCapture.forLogger(KeyPairGen.class)) {
            result = KeyPairGen.createClientKeys(SERVICE, "CML");
            // An install that has not created its CARLOS key yet is an expected state, not an error.
            assertThat(logs.events()).isEmpty();
        }

        assertThat(result.status()).isEqualTo(ClientKeyStatus.CARLOS_KEY_MISSING);
        assertThat(result.clientPrivateKey()).isNull();
        assertThat(result.carlosPublicKey()).isNull();
        verifyNoInteractions(mockPublicKeyDao);
    }

    @Test
    @DisplayName("should store the client key and return both keys when the CARLOS key exists")
    void shouldReturnBothKeys_whenCarlosKeyExists() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(carlosKey(CARLOS_PUBLIC_KEY));

        ClientKeys result = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(result.status()).isEqualTo(ClientKeyStatus.CREATED);
        ArgumentCaptor<PublicKey> stored = ArgumentCaptor.forClass(PublicKey.class);
        verify(mockPublicKeyDao).persist(stored.capture());
        assertThat(stored.getValue().getService()).isEqualTo(SERVICE);
        assertThat(stored.getValue().getType()).isEqualTo("CML");
        assertThat(result.clientPrivateKey()).isNotBlank().isEqualTo(stored.getValue().getBase64EncodedPrivateKey());
        assertThat(result.carlosPublicKey()).isEqualTo(CARLOS_PUBLIC_KEY);
    }

    @Test
    @DisplayName("should create the client key on a retry once the CARLOS key has been created")
    void shouldCreateClientKey_whenRetriedAfterCarlosKeyCreated() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(null, carlosKey(CARLOS_PUBLIC_KEY));

        ClientKeys refused = KeyPairGen.createClientKeys(SERVICE, "CML");
        verifyNoInteractions(mockPublicKeyDao);
        ClientKeys retried = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(refused.status()).isEqualTo(ClientKeyStatus.CARLOS_KEY_MISSING);
        assertThat(retried.status()).isEqualTo(ClientKeyStatus.CREATED);
        verify(mockPublicKeyDao).persist(any(PublicKey.class));
    }

    @Test
    @DisplayName("should report a failure, not a missing key, when the CARLOS key cannot be read")
    void shouldReportFailure_whenCarlosKeyLookupFails() {
        IllegalStateException failure = new IllegalStateException("Synthetic database failure");
        when(mockOscarKeyDao.find("oscar")).thenThrow(failure);

        ClientKeys result;
        try (LogCapture logs = LogCapture.forLogger(KeyPairGen.class)) {
            result = KeyPairGen.createClientKeys(SERVICE, "CML");
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage()).isEqualTo("Could not read the CARLOS key pair");
                assertThat(event.getThrown()).isSameAs(failure);
            });
        }

        assertThat(result.status()).isEqualTo(ClientKeyStatus.FAILED);
        verifyNoInteractions(mockPublicKeyDao);
    }

    @Test
    @DisplayName("should store nothing when the stored CARLOS key has no public key")
    void shouldStoreNothing_whenCarlosPublicKeyBlank() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(carlosKey(" "));

        ClientKeys result;
        try (LogCapture logs = LogCapture.forLogger(KeyPairGen.class)) {
            result = KeyPairGen.createClientKeys(SERVICE, "CML");
            assertThat(logs.events()).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage())
                        .isEqualTo("The CARLOS key pair is stored without a public key");
            });
        }

        assertThat(result.status()).isEqualTo(ClientKeyStatus.FAILED);
        assertThat(result.carlosPublicKey()).isNull();
        verifyNoInteractions(mockPublicKeyDao);
    }

    @Test
    @DisplayName("should report a failure without keys when the client key cannot be stored")
    void shouldReportFailure_whenClientKeyNotStored() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(carlosKey(CARLOS_PUBLIC_KEY));
        doThrow(new IllegalStateException("Synthetic duplicate service")).when(mockPublicKeyDao).persist(any(PublicKey.class));

        ClientKeys result = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(result.status()).isEqualTo(ClientKeyStatus.FAILED);
        assertThat(result.clientPrivateKey()).isNull();
        assertThat(result.carlosPublicKey()).isNull();
    }

    @Test
    @DisplayName("should refuse the oscar service name, which names the CARLOS key pair")
    void shouldRefuseOscarName_forClientKeys() {
        assertThatThrownBy(() -> KeyPairGen.createClientKeys("oscar", "CML"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mockOscarKeyDao, mockPublicKeyDao);
    }

    @Test
    @DisplayName("should leave the private key out of the result's string form")
    void shouldOmitPrivateKey_fromToString() {
        when(mockOscarKeyDao.find("oscar")).thenReturn(carlosKey(CARLOS_PUBLIC_KEY));

        ClientKeys result = KeyPairGen.createClientKeys(SERVICE, "CML");

        assertThat(result.toString()).isEqualTo("ClientKeys[status=CREATED]")
                .doesNotContain(result.clientPrivateKey());
    }
}
