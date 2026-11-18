/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.mcedt;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilder;
import io.github.carlos_emr.carlos.integration.ebs.client.ng.EdtClientBuilderConfig;
import io.github.carlos_emr.carlos.integration.mcedt.mailbox.User2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.SecretKeySpec;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated("Temporarily installs an owned encryption key and MCEDT beans")
class McedtCredentialsUnitTest extends CarlosUnitTestBase {
    private static final String PASSWORD = "FAKE-MCEDT-credential-Ω-123";
    private final List<UserProperty> rows = new ArrayList<>();
    private EntityManager em;
    private McedtCredentials credentials;
    private UserPropertyDAO dao;
    private Field keyField;
    private Object originalKey;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() throws Exception {
        keyField = EncryptionUtils.class.getDeclaredField("SECRET_KEY_SPEC");
        keyField.setAccessible(true);
        originalKey = keyField.get(null);
        keyField.set(null, new SecretKeySpec(new byte[32], "AES"));
        em = mock(EntityManager.class);
        TypedQuery<UserProperty> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(UserProperty.class))).thenReturn(query);
        when(query.setParameter(eq("name"), eq(UserProperty.MCEDT_ACCOUNT_PASSWORD))).thenReturn(query);
        when(query.setLockMode(LockModeType.PESSIMISTIC_WRITE)).thenReturn(query);
        when(query.getResultList()).thenReturn(rows);
        doAnswer(call -> { rows.add(call.getArgument(0)); return null; }).when(em).persist(any(UserProperty.class));
        credentials = new McedtCredentials();
        ReflectionTestUtils.setField(credentials, "entityManager", em);
        dao = mock(UserPropertyDAO.class);
        when(dao.getProp(UserProperty.MCEDT_ACCOUNT_PASSWORD)).thenAnswer(call -> rows.isEmpty() ? null : rows.get(0));
        doAnswer(call -> { UserProperty row = call.getArgument(0); if (!rows.contains(row)) rows.add(row); return null; }).when(dao).saveProp(any(UserProperty.class));
        registerMock(UserPropertyDAO.class, dao);
        registerMock(McedtCredentials.class, credentials);
    }

    @AfterEach
    void restoreKey() throws Exception { keyField.set(null, originalKey); }

    private UserProperty row(String value) {
        UserProperty row = new UserProperty(); row.setName(UserProperty.MCEDT_ACCOUNT_PASSWORD); row.setValue(value); rows.add(row); return row;
    }

    @Test
    void newPasswordIsReversiblyEncrypted() throws Exception {
        credentials.savePassword(PASSWORD);
        assertEquals(1, rows.size());
        assertNotEquals(PASSWORD, rows.get(0).getValue());
        assertTrue(EncryptionUtils.isEncrypted(rows.get(0).getValue()));
        assertEquals(PASSWORD, EncryptionUtils.decrypt(rows.get(0).getValue()));
        verify(em).flush();
    }

    @Test
    void replacementKeepsTheRowAndUsesFreshCiphertext() throws Exception {
        UserProperty original = row("legacy");
        credentials.savePassword(PASSWORD);
        String first = original.getValue();
        credentials.savePassword(PASSWORD);
        assertEquals(List.of(original), rows);
        assertNotEquals(first, original.getValue());
        assertEquals(PASSWORD, EncryptionUtils.decrypt(original.getValue()));
        verify(em, never()).persist(any());
    }

    @Test
    void legacyRowsMigrateAndStillSupplyTheSamePassword() throws Exception {
        UserProperty first = row(PASSWORD), duplicate = row("FAKE-second-credential");
        assertEquals(PASSWORD, credentials.getPassword("fallback"));
        assertTrue(EncryptionUtils.isEncrypted(first.getValue()));
        assertTrue(EncryptionUtils.isEncrypted(duplicate.getValue()));
        assertEquals("FAKE-second-credential", EncryptionUtils.decrypt(duplicate.getValue()));
    }

    @Test
    void encryptedPasswordIsNotEncryptedAgain() throws Exception {
        String encrypted = EncryptionUtils.encrypt(PASSWORD); row(encrypted);
        assertEquals(PASSWORD, credentials.getPassword("fallback"));
        assertEquals(encrypted, rows.get(0).getValue());
    }

    @Test
    void absentPropertyUsesConfiguredFallback() { assertEquals("FAKE-configured", credentials.getPassword("FAKE-configured")); }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankLegacyPropertyPreservesFallback(String value) { row(value); assertEquals("FAKE-configured", credentials.getPassword("FAKE-configured")); }

    @Test
    void missingKeyCannotOverwriteAnExistingPassword() throws Exception {
        UserProperty original = row("FAKE-existing"); keyField.set(null, null);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> credentials.savePassword(PASSWORD));
        assertNull(failure.getCause()); assertFalse(failure.toString().contains(PASSWORD));
        assertEquals("FAKE-existing", original.getValue()); verify(em, never()).flush();
    }

    @Test
    void missingKeyCannotSilentlySendLegacyPlaintext() throws Exception {
        row(PASSWORD); keyField.set(null, null);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> credentials.getPassword("fallback"));
        assertNull(failure.getCause()); assertFalse(failure.toString().contains(PASSWORD));
        assertEquals(PASSWORD, rows.get(0).getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ENC}not-base64!", "{ENC}AA=="})
    void corruptedCiphertextNeverFallsBackOrBecomesATransportPassword(String value) {
        row(value);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> credentials.getPassword("fallback"));
        assertNull(failure.getCause()); assertFalse(failure.toString().contains(value));
    }

    @Test
    void persistenceFailureHasNoCredentialBearingCause() {
        doThrow(new IllegalStateException(PASSWORD)).when(em).flush();
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> credentials.savePassword(PASSWORD));
        assertNull(failure.getCause()); assertFalse(failure.toString().contains(PASSWORD));
    }

    @Test
    void changePasswordActionStoresCiphertextAndReportsSuccess() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            User2Action action = new User2Action(); action.setPassword(PASSWORD);
            assertEquals("success", action.changePassword());
            assertEquals("true", request.getSession().getAttribute("isPassChange"));
            assertNotEquals(PASSWORD, rows.get(0).getValue());
            assertEquals(PASSWORD, EncryptionUtils.decrypt(rows.get(0).getValue()));
        }
    }

    @Test
    void changePasswordActionReportsFailureWhenEncryptionIsUnavailable() throws Exception {
        UserProperty existing = row("FAKE-existing"); keyField.set(null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            User2Action action = new User2Action(); action.setPassword(PASSWORD); action.changePassword();
            assertEquals("false", request.getSession().getAttribute("isPassChange"));
            assertEquals("FAKE-existing", existing.getValue());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delegateReceivesOnlyTheDecryptedCredential(boolean legacy) throws Exception {
        row(legacy ? PASSWORD : EncryptionUtils.encrypt(PASSWORD));
        CarlosProperties props = mock(CarlosProperties.class);
        when(props.getProperty("mcedt.service.pass")).thenReturn("FAKE-fallback");
        AtomicReference<EdtClientBuilderConfig> config = new AtomicReference<>();
        try (MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class);
             var builders = mockConstruction(EdtClientBuilder.class, (mock, context) -> config.set((EdtClientBuilderConfig) context.arguments().get(0)))) {
            properties.when(CarlosProperties::getInstance).thenReturn(props);
            DelegateFactory.setUserPropertyDAO(dao);
            Method method = DelegateFactory.class.getDeclaredMethod("newDelegate", String.class); method.setAccessible(true); method.invoke(null, "FAKE-local-service");
            assertEquals(PASSWORD, config.get().getUserNameTokenPassword());
            assertTrue(EncryptionUtils.isEncrypted(rows.get(0).getValue()));
        }
    }
}
