/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import io.github.carlos_emr.carlos.webserv.rest.util.ProductionRestObjectMappers;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AppointmentXmlVersionContractUnitTest {
    @Test void shouldKeepEditVersionInJson_withoutExtendingThePublishedXmlSequence() throws Exception {
        var value = new AppointmentTo1(); value.setEditVersion("FAKE-token");
        for (var mapper : java.util.List.of(ProductionRestObjectMappers.oauthSurface(), ProductionRestObjectMappers.sessionSurface())) {
            assertThat(mapper.valueToTree(value).path("editVersion").textValue()).isEqualTo("FAKE-token");
            assertThat(mapper.readValue("{\"editVersion\":\"FAKE-incoming\"}", AppointmentTo1.class).getEditVersion()).isEqualTo("FAKE-incoming");
        }
        for (String provider : java.util.List.of("org.glassfish.jaxb.runtime.v2.ContextFactory", "org.eclipse.persistence.jaxb.JAXBContextFactory")) {
            var context = (jakarta.xml.bind.JAXBContext) Class.forName(provider).getMethod("createContext", Class[].class, java.util.Map.class)
                    .invoke(null, new Class<?>[]{AppointmentTo1.class}, java.util.Map.of());
            var output = new java.io.StringWriter();
            context.createMarshaller().marshal(new jakarta.xml.bind.JAXBElement<>(new javax.xml.namespace.QName("value"), AppointmentTo1.class, value), output);
            assertThat(output.toString()).doesNotContain("editVersion", "FAKE-token");
        }
    }
}
