/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.SecurityToken;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class SecurityTokenDaoCompatibilityUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldSupportOldDaoImplementations_withoutChangingTheirExistingLookup() {
        var extension = mock(SecurityTokenDao.class, CALLS_REAL_METHODS);
        String hash = "soap1:" + "1".repeat(64);
        SecurityToken row = new SecurityToken();
        row.setToken(hash);
        when(extension.findAll(0, 500)).thenReturn(List.of(row));
        assertThat(extension.findSoapToken(hash)).isSameAs(row);
        verify(extension, never()).getByTokenAndExpiry(anyString(), any(Date.class));
    }

    @Test
    void shouldRevokeAcrossPages_withoutSkippingRowsOrRemovingAnotherAccountOrPurpose() {
        List<SecurityToken> remaining = new ArrayList<>();
        List<SecurityToken> retained = new ArrayList<>();
        for (int index = 0; index < 900; index++) {
            SecurityToken row = new SecurityToken();
            injectDependency(row, "id", index + 1);
            row.setToken(index % 3 == 2 ? "FAKE-other-purpose" : "soap1:" + index);
            row.setData(index % 3 == 1 ? "soap1:410:metadata" : "soap1:41:metadata");
            remaining.add(row);
            if (index % 3 != 0) retained.add(row);
        }
        var extension = mock(SecurityTokenDao.class, CALLS_REAL_METHODS);
        when(extension.findAll(anyInt(), eq(500))).thenAnswer(call -> {
            int offset = call.getArgument(0);
            return List.copyOf(remaining.subList(Math.min(offset, remaining.size()), Math.min(offset + 500, remaining.size())));
        });
        doAnswer(call -> {
            remaining.remove(call.getArgument(0));
            return null;
        }).when(extension).remove(any(SecurityToken.class));
        assertThat(extension.revokeSoapTokens("soap1:41:")).isEqualTo(300);
        assertThat(remaining).containsExactlyElementsOf(retained);
        assertThat(extension.revokeSoapTokens("soap1:41:")).isZero();
    }
}
