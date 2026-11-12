/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.ProviderConverter;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * The four provider billing identifiers ({@code billingNo}, {@code ohipNo}, {@code rmaNo},
 * {@code hsoNo}) are part of the published provider representations. Existing REST and SOAP
 * clients read them without holding the {@code _billing} read right, so the converters return
 * them to every caller that may reach the provider record at all.
 */
@Tag("unit")
@Tag("security")
class ProviderBillingDisclosureUnitTest extends CarlosUnitTestBase {
    /** Denies every privilege, {@code _billing r} included. */
    private SecurityInfoManager policy;

    @BeforeEach
    void denyEveryPrivilege() {
        policy = createAndRegisterMock(SecurityInfoManager.class);
    }

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
    void shouldReturnAllFourIdentifiers_withoutBillingReadOrMutatingTheProvider() {
        var actor = mock(LoggedInInfo.class);
        var provider = provider();
        var rest = new ProviderConverter().getAsTransferObject(actor, provider);
        var soap = ProviderTransfer.toTransfer(provider);
        var soapList = ProviderTransfer.toTransfers(List.of(provider));
        for (Object response : new Object[]{rest, soap, soapList[0]}) {
            assertThat(response).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                    .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
            assertThat(response).extracting("providerNo", "email").containsExactly("41", "synthetic@example.invalid");
        }
        assertThat(provider).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
        // The identifiers do not depend on any permission lookup, least of all _billing.
        verifyNoInteractions(policy);
    }

    @Test
    void shouldReturnAllFourIdentifiers_whenThereIsNoCallerContext() {
        var provider = provider();
        assertThat(ProviderTransfer.toTransfer(provider)).extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
        assertThat(new ProviderConverter().getAsTransferObject(null, provider))
                .extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
        assertThat(ProviderTransfer.toTransfer(null)).isNull();
        verifyNoInteractions(policy);
    }

    @Test
    void shouldPreserveTheRoundTrip_ofExistingInboundFields() {
        var actor = mock(LoggedInInfo.class);
        var converter = new ProviderConverter();
        var dto = converter.getAsTransferObject(actor, provider());
        assertThat(converter.getAsDomainObject(actor, dto))
                .extracting("billingNo", "ohipNo", "rmaNo", "hsoNo")
                .containsExactly("FAKE-BILLING", "FAKE-OHIP", "FAKE-RMA", "FAKE-HSO");
    }
}
