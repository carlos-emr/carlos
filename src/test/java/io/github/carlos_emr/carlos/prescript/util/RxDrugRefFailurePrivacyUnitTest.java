/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import java.io.IOException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("DrugRef failure diagnostic privacy")
class RxDrugRefFailurePrivacyUnitTest {
    private static final String ENDPOINT = "https://fixture-user:fixture-secret@localhost/drugref?token=fixture-token";

    @ParameterizedTest
    @ValueSource(strings = {"identify", "version"})
    @DisplayName("should omit endpoint credentials when verification metadata is absent")
    void shouldOmitEndpoint_whenVerificationMetadataIsMissing(String missingMethod) {
        try (MockedConstruction<SimpleXmlRpcClient> clients = mockConstruction(SimpleXmlRpcClient.class,
                (client, context) -> when(client.execute(anyString(), any())).thenAnswer(call ->
                        missingMethod.equals(call.getArgument(0)) ? null : "fixture metadata"))) {
            Throwable failure = catchThrowable(() -> new RxDrugRef(ENDPOINT).verify());
            assertThat(failure).hasMessage("DrugRef: '" + missingMethod + "' returned no result").hasNoCause();
            assertThat(clients.constructed()).isNotEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({"true,io", "true,interrupt", "true,fault", "true,empty",
                "false,io", "false,interrupt", "false,fault", "false,empty"})
    @DisplayName("should preserve failure semantics without exposing endpoint credentials in messages or causes")
    void shouldKeepDiagnosticsPrivate_whenClientFails(boolean propagating, String kind) throws Exception {
        Exception injected = switch (kind) {
            case "interrupt" -> new InterruptedException(ENDPOINT);
            case "fault" -> new XmlRpcFaultException(42, ENDPOINT);
            case "empty" -> new XmlRpcFaultException(0, ENDPOINT);
            default -> new IOException(ENDPOINT);
        };
        try (LogCapture logs = LogCapture.forLogger(RxDrugRef.class);
             MockedConstruction<SimpleXmlRpcClient> clients = mockConstruction(SimpleXmlRpcClient.class,
                     (client, context) -> when(client.execute(anyString(), any())).thenThrow(injected))) {
            RxDrugRef drugRef = new RxDrugRef(ENDPOINT);
            if (propagating && !"empty".equals(kind)) {
                Throwable failure = catchThrowable(drugRef::getLastUpdateTime);
                assertThat(failure).isNotNull().hasNoCause();
                assertThat(failure.getMessage()).doesNotContain("fixture-user", "fixture-secret", "fixture-token", ENDPOINT);
                if ("fault".equals(kind)) {
                    assertThat(failure).isInstanceOf(XmlRpcFaultException.class);
                    assertThat(((XmlRpcFaultException) failure).code).isEqualTo(42);
                }
            } else if (propagating) {
                assertThat(drugRef.getLastUpdateTime()).isNull();
            } else {
                assertThat(drugRef.atc("fixture drug")).isNull();
            }
            assertThat(Thread.currentThread().isInterrupted()).isEqualTo("interrupt".equals(kind));
            assertThat(String.join("\n", logs.messages())).doesNotContain("fixture-user", "fixture-secret", "fixture-token", ENDPOINT);
            assertThat(logs.events()).allSatisfy(event -> assertThat(event.getThrown()).isNull());
            if (!propagating || !"interrupt".equals(kind)) assertThat(logs.messages()).isNotEmpty();
            assertThat(clients.constructed()).hasSize(1);
        } finally {
            Thread.interrupted();
        }
    }
}
