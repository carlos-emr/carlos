/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Verify recovery guidance through the same Java bundle loader used by the Tickler JSP. */
@Tag("unit")
@Tag("tickler")
@DisplayName("Tickler draft recovery localization")
class TicklerDraftLocalizationUnitTest {
    /** Exact rendered text catches unsupported property escapes that otherwise load without an error. */
    static Stream<Arguments> recoveryMessages() {
        return Stream.of(
            Arguments.of("en", "Patient search could not start. Copy your reminder text before reloading this window."),
            Arguments.of("es", "No se pudo iniciar la búsqueda de pacientes. Copie el texto del recordatorio antes de volver a cargar esta ventana."),
            Arguments.of("fr", "Impossible de lancer la recherche de patients. Copiez le texte du rappel avant de recharger cette fenêtre."),
            Arguments.of("pl", "Nie można rozpocząć wyszukiwania pacjenta. Skopiuj tekst przypomnienia przed ponownym wczytaniem tego okna."),
            Arguments.of("pt-BR", "Não foi possível iniciar a busca de pacientes. Copie o texto do lembrete antes de recarregar esta janela.")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recoveryMessages")
    @DisplayName("should preserve accented recovery text when Java loads each locale")
    void shouldPreserveLocalizedRecoveryText_whenLoadingTheBundle(String locale, String expected) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(locale));
        assertThat(bundle.getString("tickler.draft.searchUnavailable")).isEqualTo(expected);
    }
}
