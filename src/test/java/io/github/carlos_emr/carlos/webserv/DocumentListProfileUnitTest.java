/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.cxf.binding.soap.SoapFault;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The selection rule behind {@code X-Carlos-Document-List-Profile}: a request without the header is
 * an unmodified client and keeps inline content; only a named value changes the response.
 */
@Tag("unit")
@DisplayName("DocumentListProfile selection")
class DocumentListProfileUnitTest {

    @Test
    @DisplayName("should keep inline content without a whole-page fault when no header is sent")
    void shouldKeepInlineContent_whenNoHeaderIsSent() {
        DocumentListProfile profile = DocumentListProfile.parse(null);

        assertThat(profile).isSameAs(DocumentListProfile.PUBLISHED_DEFAULT);
        assertThat(profile.inline()).isTrue();
        assertThat(profile.faultsOnUnreadableContent()).isFalse();
        assertThat(profile.wireName()).isNull();
    }

    @Test
    @DisplayName("should use the header-less default outside a CXF exchange")
    void shouldUseTheHeaderlessDefault_outsideACxfExchange() {
        assertThat(DocumentListProfile.current()).isSameAs(DocumentListProfile.PUBLISHED_DEFAULT);
    }

    @Test
    @DisplayName("should select metadata only when the header names it")
    void shouldSelectMetadata_whenTheHeaderNamesIt() {
        DocumentListProfile profile = DocumentListProfile.parse(List.of("metadata"));

        assertThat(profile).isSameAs(DocumentListProfile.METADATA);
        assertThat(profile.inline()).isFalse();
    }

    @Test
    @DisplayName("should keep the stricter complete-or-fault page for the explicit legacy-inline value")
    void shouldKeepCompleteOrFaultPages_forTheExplicitLegacyInlineValue() {
        DocumentListProfile profile = DocumentListProfile.parse(List.of("legacy-inline"));

        assertThat(profile).isSameAs(DocumentListProfile.LEGACY_INLINE);
        assertThat(profile.inline()).isTrue();
        assertThat(profile.faultsOnUnreadableContent()).isTrue();
    }

    @Test
    @DisplayName("should refuse blank, unknown, combined and repeated values as a client fault")
    void shouldRefuseUnsupportedValues_asAClientFault() {
        for (List<String> values : List.of(List.<String>of(), List.of(""), List.of("unknown"), List.of("METADATA"),
                List.of("metadata,legacy-inline"), List.of("metadata", "legacy-inline"),
                // The header-less default has no name a request could send.
                List.of("null"), List.of("published-default"))) {
            assertThatThrownBy(() -> DocumentListProfile.parse(values))
                    .as("values %s", values)
                    .isInstanceOfSatisfying(SoapFault.class, fault ->
                            assertThat(fault.getFaultCode()).isEqualTo(SoapFault.FAULT_CODE_CLIENT))
                    .hasMessage("Unsupported document list profile");
        }
    }
}
