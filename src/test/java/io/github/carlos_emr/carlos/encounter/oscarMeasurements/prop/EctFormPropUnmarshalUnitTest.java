/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.prop;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;

import jakarta.xml.bind.JAXBContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctValidationsBean;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * Pins that a form definition XML (the Vascular Tracker's {@code form/VTForm.xml}) unmarshals
 * its {@code <validationRule>} elements into {@link EctValidationsBean}.
 *
 * <p>The rules were declared on a raw {@code Vector}, which gave JAXB no element type to bind
 * to; it produced DOM elements instead, and the first cast in
 * {@code EctFindMeasurementTypeUtil.addMeasurementType} threw {@code ClassCastException}, so the
 * setup action behind the Vascular Tracker (and the measurement-type import) answered 500.
 *
 * @since 2026-09-11
 */
@Tag("unit")
@Tag("measurement")
class EctFormPropUnmarshalUnitTest {

    private static final Path VT_FORM_XML = resolveProjectPath(Path.of("src", "main", "webapp", "form", "VTForm.xml"));

    @Test
    @DisplayName("the Vascular Tracker definition should unmarshal its validation rules as beans")
    void shouldUnmarshalValidationRules_asValidationBeans() throws Exception {
        EctFormProp formProp;
        try (InputStream in = Files.newInputStream(VT_FORM_XML)) {
            formProp = (EctFormProp) JAXBContext.newInstance(EctFormProp.class).createUnmarshaller()
                    .unmarshal(XmlUtils.createSecureJaxbSource(in));
        }
        assertThat(formProp).isNotNull();

        // Read the instance just unmarshalled, not EctFormProp.getMeasurementTypes(): that is a
        // static accumulator every unmarshal in the JVM resets and appends to, so under parallel
        // Surefire it could hold another test's measurements (or none) at this point.
        Vector<EctMeasurementTypesBean> measurementTypes = formProp.getMeasurements();
        assertThat(measurementTypes).as("VTForm.xml declares measurements").isNotEmpty();
        int rules = 0;
        for (EctMeasurementTypesBean mt : measurementTypes) {
            assertThat(mt.getType()).isNotBlank();
            for (EctValidationsBean rule : mt.getValidationRules()) {
                rules++;
                assertThat(rule.getName()).as("rule of %s has a name", mt.getType()).isNotBlank();
            }
        }
        assertThat(rules).as("VTForm.xml declares validation rules").isGreaterThan(0);
    }

    /** Bounded like the other fixture helpers: a run from an unrelated directory fails fast. */
    private static final int MAX_PARENT_SEARCH_DEPTH = 8;

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; current != null && depth <= MAX_PARENT_SEARCH_DEPTH; depth++) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not resolve " + relativePath + " within "
                + MAX_PARENT_SEARCH_DEPTH + " parent directories of " + Path.of("").toAbsolutePath());
    }
}
