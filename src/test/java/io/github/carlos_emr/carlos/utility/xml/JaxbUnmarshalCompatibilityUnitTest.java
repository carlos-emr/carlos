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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.github.carlos_emr.carlos.PMmodule.web.reports.custom.UCRConfiguration;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.eform.data.EFormApConfig;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.prop.EctFormProp;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.OmdCds;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.xml.bind.JAXBContext;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real file formats consumed by every application-owned JAXB unmarshal site. */
@Tag("unit")
@Tag("xml")
class JaxbUnmarshalCompatibilityUnitTest {
    private record Sample(Class<?> type, Path path) { }

    @TestFactory
    Stream<DynamicTest> shouldReadIdenticalObjects_fromExistingXmlFiles() {
        List<Sample> samples = List.of(
                new Sample(EFormApConfig.class, Path.of("src/main/resources/oscar/eform/apconfig.xml")),
                new Sample(EctFormProp.class, Path.of("src/main/webapp/form/VTForm.xml")),
                new Sample(OmdCds.class, Path.of(".devcontainer/db/db_data/hrm/demo-hrm-diagnostic-imaging.xml")),
                new Sample(UCRConfiguration.class, Path.of("src/test/resources/xml/jaxb-ucr-report-config.xml")),
                new Sample(FaxJob.class, Path.of("src/test/resources/xml/jaxb-fax-response.xml")));
        return samples.stream().map(sample -> DynamicTest.dynamicTest(sample.path().toString(), () -> {
            JAXBContext moxy = context("org.eclipse.persistence.jaxb.JAXBContextFactory", sample.type());
            JAXBContext glassfish = context("org.glassfish.jaxb.runtime.v2.ContextFactory", sample.type());
            Object oldRead = read(moxy, sample);
            Object newRead = read(glassfish, sample);
            assertThat(newRead).usingRecursiveComparison().isEqualTo(oldRead);
            assertThat(glassfish.getClass().getName()).startsWith("org.glassfish.jaxb.");
        }));
    }

    private static JAXBContext context(String factory, Class<?> type) throws Exception {
        return (JAXBContext) Class.forName(factory).getMethod("createContext", Class[].class, Map.class)
                .invoke(null, new Class<?>[]{type}, Map.of());
    }

    private static Object read(JAXBContext context, Sample sample) throws Exception {
        try (var input = Files.newInputStream(sample.path())) {
            return context.createUnmarshaller().unmarshal(XmlUtils.createSecureJaxbSource(input), sample.type()).getValue();
        }
    }
}
