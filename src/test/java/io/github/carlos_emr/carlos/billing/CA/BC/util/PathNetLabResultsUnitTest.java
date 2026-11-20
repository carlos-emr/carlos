/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.BC.util;

import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Msh;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Obr;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Orc;
import io.github.carlos_emr.carlos.billing.CA.BC.model.Hl7Pid;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Both HQL constructor projections must retain the database's textual status. */
@DisplayName("PathNet projected result statuses")
class PathNetLabResultsUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"F", "P", "C", "X", "007"})
    void shouldPreserveStatus_withProviderRouting(String status) {
        Hl7Msh msh = new Hl7Msh();
        Hl7Pid pid = new Hl7Pid();
        Hl7Orc orc = new Hl7Orc();
        Hl7Obr obr = new Hl7Obr();
        ProviderLabRoutingModel routing = new ProviderLabRoutingModel();
        PathNetLabResults result = new PathNetLabResults(msh, pid, orc, obr, routing, status);

        assertThat(result.getMinResultStatus()).isEqualTo(status);
        assertThat(result.getHl7Msh()).isSameAs(msh);
        assertThat(result.getHl7Pid()).isSameAs(pid);
        assertThat(result.getHl7Orc()).isSameAs(orc);
        assertThat(result.getHl7Obr()).isSameAs(obr);
        assertThat(result.getProviderLabRouting()).isSameAs(routing);
        assertThat(result.getPatientLabRouting()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"F", "P", "C", "X", "007"})
    void shouldPreserveStatus_withPatientRouting(String status) {
        PatientLabRouting routing = new PatientLabRouting();
        PathNetLabResults result = new PathNetLabResults(null, null, null, null, routing, status);

        assertThat(result.getMinResultStatus()).isEqualTo(status);
        assertThat(result.getPatientLabRouting()).isSameAs(routing);
        assertThat(result.getProviderLabRouting()).isNull();
    }
}
