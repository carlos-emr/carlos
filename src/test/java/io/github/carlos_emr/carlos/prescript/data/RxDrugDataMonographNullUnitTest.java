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
package io.github.carlos_emr.carlos.prescript.data;

import java.util.Hashtable;
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Regression tests for {@link RxDrugData.DrugMonograph} built from a DrugRef result
 * that found nothing.
 *
 * <p>RxDrugRef.getDrug2 and getDrugByDIN both return null for an empty XML-RPC
 * result, and RxDrugData hands that straight to this constructor. It used to
 * dereference the hash immediately, so a drug DrugRef has no record for threw
 * NullPointerException out of RxWriteScript2Action.createNewRx. That reaches the
 * browser as a 500 on the staging XHR, whose success handler is the only thing
 * that reveals the prescription stage — so the drug the clinician picked failed
 * to stage with nothing on screen explaining why.
 *
 * @since 2026-08-25
 */
@DisplayName("RxDrugData DrugMonograph null-result handling")
@Tag("unit")
@Tag("prescription")
class RxDrugDataMonographNullUnitTest {

    @Test
    @DisplayName("should build an empty monograph when DrugRef returns no record")
    void shouldBuildEmptyMonograph_whenDrugRefReturnsNoRecord() {
        assertThatCode(() -> new RxDrugData().new DrugMonograph(null))
                .as("a DrugRef miss must not throw — it is a normal lookup outcome")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should leave the prescribing collections usable when DrugRef returns no record")
    void shouldLeaveCollectionsUsable_whenDrugRefReturnsNoRecord() {
        RxDrugData.DrugMonograph monograph = new RxDrugData().new DrugMonograph(null);

        // createNewRx reads exactly these; empty is fine, null would move the
        // NullPointerException one line further down rather than fixing it.
        assertThat(monograph.getDrugComponentList()).as("drug component list").isEmpty();
        assertThat(monograph.components).as("components").isEmpty();
        assertThat(monograph.route).as("route").isEmpty();
        assertThat(monograph.name).as("name").isNull();
        assertThat(monograph.drugId).as("drugId").isNull();
    }

    @Test
    @DisplayName("should still map every field when DrugRef returns a record")
    void shouldStillMapFields_whenDrugRefReturnsARecord() {
        // The happy path must survive the null guard: this is the shape the live
        // service returns for AMOXIL 250 CAP (DrugRef id 17210).
        Hashtable<String, Object> hash = new Hashtable<>();
        hash.put("name", "AMOXICILLIN");
        hash.put("atc", "J01CA04");
        hash.put("product", "AMOXIL 250 CAP");
        hash.put("regional_identifier", "00288497");
        hash.put("drugForm", "CAPSULE");
        hash.put("drugId", "17210");

        Vector<Hashtable<String, Object>> components = new Vector<>();
        Hashtable<String, Object> component = new Hashtable<>();
        component.put("name", "AMOXICILLIN");
        component.put("unit", "MG");
        component.put("strength", "250.0");
        components.add(component);
        hash.put("components", components);

        RxDrugData.DrugMonograph monograph = new RxDrugData().new DrugMonograph(hash);

        assertThat(monograph.name).isEqualTo("AMOXICILLIN");
        assertThat(monograph.atc).isEqualTo("J01CA04");
        assertThat(monograph.product).isEqualTo("AMOXIL 250 CAP");
        assertThat(monograph.regionalIdentifier).isEqualTo("00288497");
        assertThat(monograph.drugForm).isEqualTo("CAPSULE");
        assertThat(monograph.drugId).isEqualTo(17210);
        assertThat(monograph.getDrugComponentList()).hasSize(1);
    }
}
