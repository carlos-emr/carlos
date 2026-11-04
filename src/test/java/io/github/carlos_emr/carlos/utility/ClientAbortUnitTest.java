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
package io.github.carlos_emr.carlos.utility;

import java.io.IOException;

import jakarta.servlet.ServletException;

import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientAbort recognises Tomcat client aborts")
@Tag("unit")
class ClientAbortUnitTest {

    @Test
    @DisplayName("should recognise a bare client abort")
    void shouldRecognise_bareClientAbort() {
        assertThat(ClientAbort.isClientAbort(new ClientAbortException("Broken pipe"))).isTrue();
    }

    @Test
    @DisplayName("should recognise a client abort wrapped by filters")
    void shouldRecognise_wrappedClientAbort() {
        Throwable wrapped = new ServletException(new RuntimeException(new ClientAbortException()));

        assertThat(ClientAbort.isClientAbort(wrapped)).isTrue();
    }

    @Test
    @DisplayName("should recognise a subclass of the client abort")
    void shouldRecognise_subclassOfClientAbort() {
        assertThat(ClientAbort.isClientAbort(new ClientAbortException() { })).isTrue();
    }

    @Test
    @DisplayName("should not treat other I/O or server failures as client aborts")
    void shouldNotRecognise_otherFailures() {
        assertThat(ClientAbort.isClientAbort(null)).isFalse();
        assertThat(ClientAbort.isClientAbort(new IOException("Broken pipe"))).isFalse();
        assertThat(ClientAbort.isClientAbort(new ServletException(new IllegalStateException()))).isFalse();
    }

    @Test
    @DisplayName("should stop on a cyclic cause chain")
    void shouldTerminate_onCyclicCauseChain() {
        IOException first = new IOException("first");
        IOException second = new IOException("second", first);
        first.initCause(second);

        assertThat(ClientAbort.isClientAbort(first)).isFalse();
    }
}
