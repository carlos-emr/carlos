/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.dao;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * The role-name fold behind the export's "read ROLE notes" rule. The rule itself is exercised
 * against MariaDB in {@code CdsExportNoteAccessDaoMariaDbIntegrationTest}; this pins only that
 * the fold does not follow the JVM's default locale.
 */
@Tag("unit")
@Tag("dao")
@Isolated("Changes the JVM default locale")
@DisplayName("CdsExportNoteAccessDao role-name fold")
class CdsExportNoteAccessDaoUnitTest {
    @Test
    @DisplayName("should fold a role name the same way under a Turkish default locale")
    void shouldFoldRoleNameTheSame_underTurkishDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.of("tr", "TR"));

            // String.toLowerCase() would give a dotless i here and miss "read admin notes".
            assertThat(CdsExportNoteAccessDao.folded("ADMIN")).isEqualTo("admin");
            assertThat(CdsExportNoteAccessDao.folded("Clinical Assistant"))
                    .isEqualTo("clinical assistant");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("should not fold a look-alike role name to the granted name")
    void shouldKeepLookAlikeNamesDistinct_forExactComparison() {
        // Dotless i (U+0131) and Cyrillic a (U+0430) are not the ASCII letters of "admin".
        assertThat(CdsExportNoteAccessDao.folded("ADMıN")).isNotEqualTo("admin");
        assertThat(CdsExportNoteAccessDao.folded("аdmin")).isNotEqualTo("admin");
    }
}
