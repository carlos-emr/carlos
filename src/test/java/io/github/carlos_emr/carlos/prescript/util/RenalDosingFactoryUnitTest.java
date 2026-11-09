/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class RenalDosingFactoryUnitTest {
    @Test
    void shouldSurfaceFailureAndRetry_whenGuidanceCannotBeLoaded() throws Exception {
        var previous = new java.util.Hashtable<>(RenalDosingFactory.currentDosingInformation);
        boolean loaded = RenalDosingFactory.loaded;
        var realParser = io.github.carlos_emr.carlos.utility.XmlUtils.createSecureSAXBuilder();
        org.jdom2.Document document;
        try (var input = getClass().getClassLoader().getResourceAsStream("oscar/oscarRx/RenalDosing.xml")) {
            document = realParser.build(input);
        }
        var parser = mock(org.jdom2.input.SAXBuilder.class);
        when(parser.build(any(java.io.InputStream.class))).thenThrow(new java.io.IOException("Synthetic guidance failure")).thenReturn(document);
        try (var xml = mockStatic(io.github.carlos_emr.carlos.utility.XmlUtils.class)) {
            xml.when(io.github.carlos_emr.carlos.utility.XmlUtils::createSecureSAXBuilder).thenReturn(parser);
            RenalDosingFactory.loaded = false;
            RenalDosingFactory.currentDosingInformation.clear();
            assertThatThrownBy(() -> RenalDosingFactory.getDosingInformation("J01MA02")).isInstanceOf(IllegalStateException.class);
            assertThat(RenalDosingFactory.loaded).isFalse();
            assertThat(RenalDosingFactory.currentDosingInformation).isEmpty();
            assertThat(RenalDosingFactory.getDosingInformation("J01MA02")).isNotNull();
            assertThat(RenalDosingFactory.loaded).isTrue();
            verify(parser, times(2)).build(any(java.io.InputStream.class));
        } finally {
            RenalDosingFactory.currentDosingInformation.clear();
            RenalDosingFactory.currentDosingInformation.putAll(previous);
            RenalDosingFactory.loaded = loaded;
        }
    }
}
