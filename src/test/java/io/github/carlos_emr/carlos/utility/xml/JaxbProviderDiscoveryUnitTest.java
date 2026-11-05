/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.ServiceLoader;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AppointmentTransfer;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBContextFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MOXy is on the test class path only as a compatibility oracle (#5476). Both it and
 * {@code jaxb-runtime} register a {@link JAXBContextFactory} service, and {@code ContextFinder}
 * takes the first registration on the class path unless the {@code jakarta.xml.bind.JAXBContextFactory}
 * system property names one. Surefire sets that property; this test also pins the class-path order
 * so an IDE runner or an ad hoc Maven goal without the property still validates production Glassfish.
 */
@Tag("unit")
@Tag("xml")
class JaxbProviderDiscoveryUnitTest {
    private static final String GLASSFISH = "org.glassfish.jaxb.runtime.v2.";

    @Test
    void shouldDiscoverGlassfishFirst_withoutTheSystemProperty() {
        ServiceLoader<JAXBContextFactory> loader = ServiceLoader.load(JAXBContextFactory.class);
        assertThat(loader.iterator().hasNext()).isTrue();
        assertThat(loader.iterator().next().getClass().getName())
                .as("first JAXBContextFactory registration on the class path (pom declares MOXy after jaxb-runtime)")
                .startsWith(GLASSFISH);
    }

    @Test
    void shouldCreateGlassfishContexts_forOrdinaryJaxbCalls() throws Exception {
        String property = System.getProperty("jakarta.xml.bind.JAXBContextFactory");
        if (property != null) {
            assertThat(property).as("surefire selects production Glassfish").startsWith(GLASSFISH);
        }
        assertThat(JAXBContext.newInstance(AppointmentTransfer.class).getClass().getName()).startsWith(GLASSFISH);
    }
}
