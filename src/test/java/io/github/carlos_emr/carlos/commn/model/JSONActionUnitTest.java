/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.commn.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("JSONAction response context")
@Tag("unit")
class JSONActionUnitTest {

    @Test
    @DisplayName("should not throw when action context is absent")
    void shouldNotThrow_whenActionContextIsAbsent() {
        TestJsonAction action = new TestJsonAction();
        ObjectNode json = JSONAction.objectMapper.createObjectNode().put("ok", true);

        assertThatCode(() -> action.write(json)).doesNotThrowAnyException();
        assertThatCode(() -> action.write("{\"ok\":true}")).doesNotThrowAnyException();
    }

    private static final class TestJsonAction extends JSONAction {
        void write(ObjectNode json) {
            jsonResponse(json);
        }

        void write(String json) {
            jsonResponse(json);
        }
    }
}
