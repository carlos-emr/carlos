/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProviderConverter;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("security")
class ProviderBillingDisclosureUnitTest {
    private static Provider provider() {
        var provider = new Provider();
        provider.setProviderNo("41");
        provider.setFirstName("Synthetic");
        provider.setLastName("Provider");
        provider.setEmail("synthetic@example.invalid");
        provider.setBillingNo("FAKE-BILLING");
        provider.setOhipNo("FAKE-OHIP");
        provider.setRmaNo("FAKE-RMA");
        provider.setHsoNo("FAKE-HSO");
        return provider;
    }

    @Test
    void shouldGateAllIdentifiers_withoutMutatingTheManagedProvider() {
        var actor = mock(LoggedInInfo.class);
        var policy = mock(SecurityInfoManager.class);
        var converter = new ProviderConverter(policy);
        var provider = provider();
        for (boolean allowed : new boolean[]{false, true}) {
            when(policy.hasPrivilege(actor, "_billing", "r", null)).thenReturn(allowed);
            var rest = converter.getAsTransferObject(actor, provider);
            var soap = ProviderTransfer.toTransfer(provider, ProviderBillingAccess.canRead(actor, policy));
            for (Object response : new Object[]{rest, soap}) {
                assertThat(response).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                        .containsExactly(allowed ? "FAKE-BILLING" : null, allowed ? "FAKE-OHIP" : null,
                                allowed ? "FAKE-RMA" : null, allowed ? "FAKE-HSO" : null);
                assertThat(response).extracting("providerNo", "email").containsExactly("41", "synthetic@example.invalid");
            }
            assertThat(provider).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                    .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
        }
    }

    @Test
    void shouldOmitIdentifiers_whenThereIsNoDisclosureContext() {
        var provider = provider();
        assertThat(ProviderTransfer.toTransfer(provider)).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsOnlyNulls();
        var policy = mock(SecurityInfoManager.class);
        assertThat(new ProviderConverter(policy).getAsTransferObject(null, provider))
                .extracting("billingNo", "ohipNo", "rmaNo", "hsoNo").containsOnlyNulls();
        verifyNoInteractions(policy);
    }

    @Test
    void shouldPreserveTheAuthorizedRoundTrip_andExistingInboundFields() {
        var actor = mock(LoggedInInfo.class);
        var policy = mock(SecurityInfoManager.class);
        when(policy.hasPrivilege(actor, "_billing", "r", null)).thenReturn(true);
        var converter = new ProviderConverter(policy);
        var dto = converter.getAsTransferObject(actor, provider());
        assertThat(converter.getAsDomainObject(actor, dto))
                .extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
    }

    @Test
    void shouldPropagatePolicyFailures_insteadOfReportingAnAuthorizedDisclosure() {
        var actor = mock(LoggedInInfo.class);
        var policy = mock(SecurityInfoManager.class);
        when(policy.hasPrivilege(actor, "_billing", "r", null)).thenThrow(new IllegalStateException("policy unavailable"));
        var converter = new ProviderConverter(policy);
        var provider = provider();
        assertThatThrownBy(() -> converter.getAsTransferObject(actor, provider)).isInstanceOf(IllegalStateException.class);
    }
}
