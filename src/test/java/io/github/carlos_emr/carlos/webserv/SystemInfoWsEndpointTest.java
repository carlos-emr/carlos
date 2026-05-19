/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Calendar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;

/**
 * SOAP-level endpoint tests for {@link SystemInfoWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline: SOAP envelope
 * marshalling/unmarshalling, WSDL processing, and response serialization.
 * {@code SystemInfoWs} is the simplest SOAP service (no authentication
 * required in production) making it ideal as the first SOAP endpoint test.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("SystemInfoWs SOAP endpoint tests")
class SystemInfoWsEndpointTest extends CarlosSoapTestBase {

    @Override
    protected Object getServiceBean() {
        return new SystemInfoWs();
    }

    @Override
    protected Class<?> getServiceInterface() {
        return SystemInfoWs.class;
    }

    /** Tests for the helloWorld SOAP operation. */
    @Nested
    @DisplayName("helloWorld operation")
    class HelloWorld {

        @Test
        @DisplayName("should return greeting message via SOAP")
        void shouldReturnGreeting_viaSoap() {
            SystemInfoWs proxy = createClient(SystemInfoWs.class);

            String result = proxy.helloWorld();

            assertThat(result).startsWith("Hello World!");
            assertThat(result).contains("configuration works");
        }
    }

    /** Tests for the isAlive SOAP operation. */
    @Nested
    @DisplayName("isAlive operation")
    class IsAlive {

        @Test
        @DisplayName("should return alive status via SOAP")
        void shouldReturnAlive_viaSoap() {
            SystemInfoWs proxy = createClient(SystemInfoWs.class);

            String result = proxy.isAlive();

            assertThat(result).isEqualTo("alive");
        }
    }

    /** Tests for the getMaxListReturnSize SOAP operation. */
    @Nested
    @DisplayName("getMaxListReturnSize operation")
    class GetMaxListReturnSize {

        @Test
        @DisplayName("should return positive max list size via SOAP")
        void shouldReturnPositiveMaxListSize_viaSoap() {
            SystemInfoWs proxy = createClient(SystemInfoWs.class);

            int result = proxy.getMaxListReturnSize();

            assertThat(result).isPositive();
        }
    }

    /** Tests for the getServerTime SOAP operation. */
    @Nested
    @DisplayName("getServerTime operation")
    class GetServerTime {

        @Test
        @DisplayName("should return current server time via SOAP")
        void shouldReturnCurrentServerTime_viaSoap() {
            SystemInfoWs proxy = createClient(SystemInfoWs.class);

            Calendar result = proxy.getServerTime();

            assertThat(result).isNotNull();
            assertThat(result.getTimeInMillis())
                .isCloseTo(System.currentTimeMillis(), org.assertj.core.data.Offset.offset(5000L));
        }
    }

    /** Tests for the getServerTimeGmtOffset SOAP operation. */
    @Nested
    @DisplayName("getServerTimeGmtOffset operation")
    class GetServerTimeGmtOffset {

        @Test
        @DisplayName("should return GMT offset via SOAP")
        void shouldReturnGmtOffset_viaSoap() {
            SystemInfoWs proxy = createClient(SystemInfoWs.class);

            int result = proxy.getServerTimeGmtOffset();

            // GMT offset is in milliseconds, reasonable range is -12h to +14h
            assertThat(result).isBetween(-12 * 3600 * 1000, 14 * 3600 * 1000);
        }
    }
}
