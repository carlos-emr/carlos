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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypesBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.prop.EctFormProp;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The measurement definitions a form save or setup reads must be the ones bound for the stream
 * it was handed. {@link EctFormProp#getMeasurementTypes()} is a static accumulator that every
 * unmarshal in the JVM resets and refills, so two forms opened at once could read each other's
 * definitions through it; the production readers go through
 * {@link EctFindMeasurementTypeUtil#loadMeasurementTypes(InputStream)} instead.
 *
 * @since 2026-09-11
 */
@Tag("unit")
@Tag("measurement")
class EctFindMeasurementTypeUtilUnitTest extends CarlosUnitTestBase {

    private static final Path VT_FORM_XML = resolveProjectPath(Path.of("src", "main", "webapp", "form", "VTForm.xml"));

    @BeforeEach
    void registerDao() {
        // The utility looks its DAO up statically when the class loads.
        registerMock(MeasurementTypeDao.class, Mockito.mock(MeasurementTypeDao.class));
    }

    @Test
    @DisplayName("each stream should yield its own measurement list, untouched by a later unmarshal")
    void shouldReturnInstanceMeasurements_forEachStream() throws Exception {
        Vector<EctMeasurementTypesBean> first;
        Vector<EctMeasurementTypesBean> second;
        try (InputStream in = Files.newInputStream(VT_FORM_XML)) {
            first = EctFindMeasurementTypeUtil.loadMeasurementTypes(in);
        }
        int declared = first.size();
        assertThat(declared).as("VTForm.xml declares measurements").isGreaterThan(0);

        try (InputStream in = Files.newInputStream(VT_FORM_XML)) {
            second = EctFindMeasurementTypeUtil.loadMeasurementTypes(in);
        }

        // The second unmarshal reset and refilled the static accumulator; the first caller's
        // list is neither that vector nor changed by it.
        assertThat(second).isNotSameAs(first).hasSize(declared);
        assertThat(first).isNotSameAs(EctFormProp.getMeasurementTypes()).hasSize(declared);
        second.clear();
        assertThat(first).hasSize(declared);
    }

    @Test
    @DisplayName("a stream that does not unmarshal should fail closed, not yield an empty rule set")
    void shouldThrow_whenStreamIsNotAFormDefinition() {
        java.io.InputStream notXml = new java.io.ByteArrayInputStream(
                "not xml".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> EctFindMeasurementTypeUtil.loadMeasurementTypes(notXml))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("could not be read");
    }

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
