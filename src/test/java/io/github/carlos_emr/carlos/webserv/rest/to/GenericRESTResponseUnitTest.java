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
package io.github.carlos_emr.carlos.webserv.rest.to;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Regression tests for the generic REST response JSON contract.
 *
 * @since 2026-05-07
 */
@DisplayName("Generic REST response regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class GenericRESTResponseUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockedStatic<CarlosProperties> carlosProperties;

    @BeforeEach
    void setUp() {
        carlosProperties = mockStatic(CarlosProperties.class);
        carlosProperties.when(CarlosProperties::getBuildDate).thenReturn("2026-05-07");
        carlosProperties.when(CarlosProperties::getBuildTag).thenReturn("test");
    }

    @AfterEach
    void tearDown() {
        carlosProperties.close();
    }

    @Test
    @DisplayName("should default to success status for success factory")
    void shouldDefaultToSuccessStatus_whenUsingSuccessFactory() {
        RestResponse<String> response = RestResponse.successResponse("ok");

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("should set error status with message")
    void shouldSetErrorStatus_withMessage() {
        RestResponse<String> response = RestResponse.errorResponse("msg");

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(response.getError().getMessage()).isEqualTo("msg");
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("should serialize to JSON correctly")
    void shouldSerializeToJson_correctly() throws Exception {
        RestResponse<String> response = RestResponse.successResponse("payload");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(json.get("body").asText()).isEqualTo("payload");
        assertThat(json.get("error").isNull()).isTrue();
        assertThat(json.get("headers").get("buildDate").asText()).isEqualTo("2026-05-07");
        assertThat(json.get("headers").get("buildTag").asText()).isEqualTo("test");
    }
}
