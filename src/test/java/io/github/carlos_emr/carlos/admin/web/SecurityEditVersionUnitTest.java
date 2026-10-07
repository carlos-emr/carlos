/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.model.Security;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class SecurityEditVersionUnitTest {
    @Test
    void shouldCanonicalizeDateTypes_andPreserveFieldBoundariesAndNulls() {
        Security row = new Security(); row.setUserName("ab"); row.setProviderNo("c");
        row.setLastUpdateDate(new java.util.Date(1000));
        String original = SecurityEditVersion.of(row);
        row.setLastUpdateDate(new java.sql.Timestamp(1000));
        assertThat(SecurityEditVersion.of(row)).isEqualTo(original);
        row.setUserName("a"); row.setProviderNo("bc");
        assertThat(SecurityEditVersion.of(row)).isNotEqualTo(original);
        row.setMfaSecret(null); original = SecurityEditVersion.of(row); row.setMfaSecret("");
        assertThat(SecurityEditVersion.of(row)).isNotEqualTo(original);
    }
}
