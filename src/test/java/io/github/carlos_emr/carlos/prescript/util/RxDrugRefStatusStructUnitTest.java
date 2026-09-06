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
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The contract {@code RxDrugRef.getUpdateStatus()} documents: every key it names is present as a
 * String, whatever the DrugRef build on the other side of the pin actually answers with.
 *
 * <p>This matters because the value is relayed to the browser as JSON. A key the server omits
 * used to be absent from the map and therefore from the JSON, reaching
 * {@code updateDrugref.jsp} as {@code undefined} rather than the empty string the JavaDoc
 * promises — so a caller could not tell "not supplied" from "supplied empty".
 */
@Tag("unit")
class RxDrugRefStatusStructUnitTest {

    @Test
    @DisplayName("should supply every documented key as empty when the server omits it")
    void shouldSupplyEveryDocumentedKey_whenTheStructIsSparse() {
        Map<String, String> status = RxDrugRef.normalizeStatusStruct(new Hashtable<>(Map.of("state", "IDLE")));

        assertThat(status).containsOnlyKeys(RxDrugRef.STATUS_KEYS);
        assertThat(status.get("state")).isEqualTo("IDLE");
        assertThat(status.get("step")).isEmpty();
        assertThat(status.get("message")).isEmpty();
        assertThat(status.get("startedAt")).isEmpty();
        assertThat(status.get("finishedAt")).isEmpty();
        assertThat(status.get("lastUpdate")).isEmpty();
    }

    @Test
    @DisplayName("should keep the server values when the struct is complete")
    void shouldKeepTheServerValues_whenTheStructIsComplete() {
        Hashtable<String, Object> struct = new Hashtable<>();
        struct.put("state", "FAILED");
        struct.put("step", "downloading Health Canada DPD archives");
        struct.put("message", "ConnectException: Connection refused");
        struct.put("startedAt", "2026-09-05 23:44:10");
        struct.put("finishedAt", "2026-09-05 23:44:21");
        struct.put("lastUpdate", "2020-05-28 00:00:00.0");

        Map<String, String> status = RxDrugRef.normalizeStatusStruct(struct);

        assertThat(status).containsOnlyKeys(RxDrugRef.STATUS_KEYS);
        assertThat(status.get("state")).isEqualTo("FAILED");
        assertThat(status.get("message")).isEqualTo("ConnectException: Connection refused");
        assertThat(status.get("lastUpdate")).isEqualTo("2020-05-28 00:00:00.0");
    }

    @Test
    @DisplayName("should render a null value as empty rather than the string null")
    void shouldRenderANullValue_asEmpty() {
        // HashMap, not Hashtable: XML-RPC cannot carry a null, but the relay must not depend on
        // that to avoid writing the literal "null" into the page.
        Map<String, Object> struct = new HashMap<>();
        struct.put("state", "SUCCEEDED");
        struct.put("message", null);

        Map<String, String> status = RxDrugRef.normalizeStatusStruct(struct);

        assertThat(status.get("message")).isEmpty();
    }

    @Test
    @DisplayName("should pass through a key the documented set does not name")
    void shouldPassThroughAnExtraKey_fromANewerServer() {
        Map<String, String> status = RxDrugRef.normalizeStatusStruct(
                new Hashtable<>(Map.of("state", "RUNNING", "percentComplete", "42")));

        assertThat(status.get("percentComplete")).isEqualTo("42");
        assertThat(status.keySet()).contains(RxDrugRef.STATUS_KEYS);
    }
}
