/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.managers.DigitalSignatureManager;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ConsultationPDFCreator#resolveSignatureBytes} — the signature-byte precedence
 * that decides whether a print preview renders the non-mutating override or the persisted signature.
 */
@DisplayName("ConsultationPDFCreator signature byte resolution")
@Tag("unit")
class ConsultationPDFCreatorUnitTest {

    private static final byte[] OVERRIDE_BYTES = new byte[]{1, 2, 3};
    private static final byte[] STORED_BYTES = new byte[]{9, 8, 7};

    @Test
    @DisplayName("prefers the non-mutating override and never loads the stored signature")
    void shouldReturnOverrideBytes_whenOverridePresent() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(OVERRIDE_BYTES, "5", mgr);

        assertThat(result).containsExactly(OVERRIDE_BYTES);
        verifyNoInteractions(mgr);
    }

    @Test
    @DisplayName("falls back to the stored signature when there is no override")
    void shouldReturnStoredBytes_whenNoOverrideAndValidId() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);
        DigitalSignature stored = new DigitalSignature();
        stored.setSignatureImage(STORED_BYTES);
        when(mgr.getDigitalSignature(5)).thenReturn(stored);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "5", mgr);

        assertThat(result).containsExactly(STORED_BYTES);
        verify(mgr).getDigitalSignature(5);
    }

    @Test
    @DisplayName("returns null when an empty override and a blank id leave nothing to render")
    void shouldReturnNull_whenNoOverrideAndBlankId() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(new byte[0], "", mgr);

        assertThat(result).isNull();
        verify(mgr, never()).getDigitalSignature(anyInt());
    }

    @Test
    @DisplayName("returns null when the stored signature id is not numeric")
    void shouldReturnNull_whenStoredIdNonNumeric() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "abc", mgr);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("returns null when the stored signature cannot be found")
    void shouldReturnNull_whenStoredSignatureMissing() {
        DigitalSignatureManager mgr = mock(DigitalSignatureManager.class);
        when(mgr.getDigitalSignature(5)).thenReturn(null);

        byte[] result = ConsultationPDFCreator.resolveSignatureBytes(null, "5", mgr);

        assertThat(result).isNull();
    }
}
