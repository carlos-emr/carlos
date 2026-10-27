/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.prescript.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/**
 * {@link RxDrugRef#getInteractions}: an empty list is DrugRef's "compared, found nothing", so any
 * answer in which nothing was compared must throw instead (issue #4506).
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("rx")
@DisplayName("RxDrugRef interactions")
class RxDrugRefInteractionsUnitTest {

    private static Vector<String> codes() {
        return new Vector<>(List.of("J01MA02", "R03DA04"));
    }

    private static Hashtable<String, Object> interaction() {
        Hashtable<String, Object> entry = new Hashtable<>();
        entry.put("affectingatc", "J01MA02");
        entry.put("affectedatc", "R03DA04");
        entry.put("significance", "3");
        return entry;
    }

    private static List<Object> answerFor(Object answer) throws Exception {
        try (MockedConstruction<SimpleXmlRpcClient> _ = mockConstruction(SimpleXmlRpcClient.class,
                (client, context) -> when(client.execute(eq("fetch"), any())).thenReturn(answer))) {
            return new ArrayList<Object>(new RxDrugRef("http://localhost/drugref").getInteractions(codes()));
        }
    }

    @Test
    @DisplayName("should return the interactions DrugRef keys by its Holbrook source")
    void shouldReturnInteractions_fromHolbrookSourceStruct() throws Exception {
        Hashtable<String, Object> sources = new Hashtable<>();
        sources.put("Holbrook Drug Interactions", new Vector<>(List.of(interaction())));

        assertThat(answerFor(sources)).containsExactly(interaction());
    }

    @Test
    @DisplayName("should return a plain interaction list unchanged")
    void shouldReturnInteractions_fromPlainList() throws Exception {
        assertThat(answerFor(new Vector<>(List.of(interaction())))).containsExactly(interaction());
    }

    @Test
    @DisplayName("should return an empty list when DrugRef compared the drugs and found nothing")
    void shouldReturnEmpty_whenNoSourceReportsAnInteraction() throws Exception {
        assertThat(answerFor(new Hashtable<>())).isEmpty();
    }

    @Test
    @DisplayName("should throw, not answer empty, when DrugRef has no interaction source")
    void shouldThrow_whenDrugRefAnswersWithAnError() {
        Hashtable<String, Object> error = new Hashtable<>();
        error.put("Error", "interactions_byATC not provided by an registered service");

        assertThatThrownBy(() -> answerFor(error)).hasMessageContaining("not available");
    }

    @Test
    @DisplayName("should throw, not answer empty, when the Holbrook source answers with its own error")
    void shouldThrow_whenTheSourceAnswersWithAnErrorStruct() {
        // DrugrefApi.get answers {"Error": ...} when its lookup fails; fetch files it under the source name.
        Hashtable<String, Object> sourceError = new Hashtable<>();
        sourceError.put("Error", "Attribute interactions_byATC not available");
        Hashtable<String, Object> sources = new Hashtable<>();
        sources.put("Holbrook Drug Interactions", sourceError);

        assertThatThrownBy(() -> answerFor(sources)).hasMessageContaining("no interaction list");
    }

    @Test
    @DisplayName("should throw, not answer empty, when DrugRef returns nothing usable")
    void shouldThrow_whenDrugRefReturnsNoResult() {
        assertThatThrownBy(() -> answerFor(null)).hasMessageContaining("returned no result");
        assertThatThrownBy(() -> answerFor("not a list")).hasMessageContaining("returned no result");
    }
}
