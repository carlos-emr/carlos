/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.report.data.ParameterizedSql;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.List;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** Execute the JSP's actual query invocation through EFormUtil and a real JDBC result. */
@Tag("integration")
class ApCacheLookupPatientContextIntegrationTest {
    @TempDir Path scratch;

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"42", "  "})
    void shouldPreservePatientIndependentApResult_withOptionalPatientContext(String patient) throws Exception {
        var request = new MockHttpServletRequest();
        if (patient != null) request.addParameter("demographic_no", patient);
        String demographicNo = ApCacheLookupParameterResolver.resolveDemographicNo(request);
        String jsp = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/eform/efmformapconfig_lookup.jsp"));
        int start = jsp.indexOf("ArrayList<String> values = EFormUtil.getValuesOrNull(");
        assertThat(start).isNotNegative();
        int end = jsp.indexOf(';', start);
        String call = jsp.substring(start, end + 1);
        String source = "import java.util.*; import io.github.carlos_emr.carlos.eform.EFormUtil; "
                + "import io.github.carlos_emr.carlos.report.data.ParameterizedSql; "
                + "public class ApPatientQuery { public static List<String> read(List<String> names, "
                + "ParameterizedSql query, String demographic_no) { " + call + " return values; } }";
        Path file = scratch.resolve("ApPatientQuery.java"); Files.writeString(file, source);
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assertThat(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-classpath", classpath,
                "-d", scratch.toString(), file.toString())).isZero();
        var query = new ParameterizedSql("SELECT 7 AS result_value", List.of());
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:ap-patient-" + java.util.UUID.randomUUID());
                var statement = connection.prepareStatement(query.getSql());
                var rows = statement.executeQuery();
                var jdbc = mockStatic(LegacyJdbcQuery.class);
                var loader = new URLClassLoader(new java.net.URL[]{scratch.toUri().toURL()}, getClass().getClassLoader())) {
            jdbc.when(() -> LegacyJdbcQuery.getPreparedResultSet(query)).thenReturn(rows);
            var method = loader.loadClass("ApPatientQuery").getMethod("read", List.class, ParameterizedSql.class, String.class);
            assertThat(method.invoke(null, List.of("RESULT_VALUE"), query, demographicNo)).isEqualTo(List.of("7"));
            jdbc.verify(() -> LegacyJdbcQuery.getPreparedResultSet(query));
            assertThat(rows.isClosed()).isTrue();
        }
    }
}
