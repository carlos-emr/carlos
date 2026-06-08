/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 *
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.utility;

import org.apache.velocity.VelocityContext;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VelocityUtils} template engine utilities.
 *
 * @since 2026-03-31
 */
@DisplayName("VelocityUtils Unit Tests")
@Tag("unit") @Tag("fast") @Tag("utility")
class VelocityUtilsUnitTest {

    @Test
    @DisplayName("should create context with tools")
    void shouldCreateContext_withTools() {
        VelocityContext ctx = VelocityUtils.createVelocityContextWithTools();
        assertThat(ctx).isNotNull();
        assertThat(ctx.get("escapeTool")).isNotNull();
        assertThat(ctx.get("numberTool")).isNotNull();
        assertThat(ctx.get("dateTool")).isNotNull();
    }

    @Test
    @DisplayName("should evaluate simple template")
    void shouldEvaluateSimpleTemplate() {
        VelocityContext ctx = VelocityUtils.createVelocityContextWithTools();
        ctx.put("name", "John");
        String result = VelocityUtils.velocityEvaluate(ctx, "Hello $name");
        assertThat(result).isEqualTo("Hello John");
    }

    @Test
    @DisplayName("should return null for null template")
    void shouldReturnNull_forNullTemplate() {
        VelocityContext ctx = VelocityUtils.createVelocityContextWithTools();
        String result = VelocityUtils.velocityEvaluate(ctx, null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("should return template as-is when no variables")
    void shouldReturnAsIs_whenNoVariables() {
        VelocityContext ctx = VelocityUtils.createVelocityContextWithTools();
        String result = VelocityUtils.velocityEvaluate(ctx, "plain text");
        assertThat(result).isEqualTo("plain text");
    }

    @Test
    @DisplayName("should have initialized velocity engine")
    void shouldHaveInitializedEngine() {
        assertThat(VelocityUtils.velocityEngine).isNotNull();
    }

    @Test
    @DisplayName("should have initialized escape tool")
    void shouldHaveInitializedEscapeTool() {
        assertThat(VelocityUtils.escapeTool).isNotNull();
    }
}
