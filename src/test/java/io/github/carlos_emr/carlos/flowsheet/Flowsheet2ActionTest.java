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
package io.github.carlos_emr.carlos.flowsheet;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetUserCreatedDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;

import java.util.Collections;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Direct-response contract tests for {@link Flowsheet2Action}.
 *
 * <p>Every method that streams JSON to the servlet response must terminate
 * Struts processing with an explicit {@code return NONE} rather than a bare
 * {@code null}. The action is mapped with no {@code <result>} elements
 * (struts-form.xml), so any named/ambiguous result would let Struts attempt
 * result resolution after the JSON body is already written. These tests pin a
 * representative read path, a simple list path, and a mutator-style success
 * path so that contract cannot silently regress.
 *
 * @since 2026-06-16
 */
@DisplayName("Flowsheet2Action - direct-response NONE contract")
@Tag("integration")
@Tag("clinical")
class Flowsheet2ActionTest extends CarlosWebTestBase {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=UTF-8";

    @Mock
    private FlowSheetUserCreatedDao mockFlowSheetUserCreatedDao;
    @Mock
    private ValidationsDao mockValidationsDao;

    @BeforeEach
    void setUpAction() {
        replaceSpringUtilsBean(FlowSheetUserCreatedDao.class, mockFlowSheetUserCreatedDao);
        replaceSpringUtilsBean(ValidationsDao.class, mockValidationsDao);
        mockRequest.setMethod("POST");
    }

    @Test
    @DisplayName("should return NONE after writing JSON when listing flowsheets")
    void shouldReturnNone_whenListWritesJsonResponse() throws Exception {
        when(mockFlowSheetUserCreatedDao.getAllUserCreatedFlowSheets())
                .thenReturn(Collections.emptyList());
        addRequestParameter("method", "list");

        String result = executeAction(new Flowsheet2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).isEqualTo(JSON_CONTENT_TYPE);
        assertThat(mockResponse.getContentAsString()).contains("results");
    }

    @Test
    @DisplayName("should return NONE after writing JSON when fetching validations")
    void shouldReturnNone_whenGetValidationsWritesJsonResponse() throws Exception {
        when(mockValidationsDao.findAll()).thenReturn(Collections.emptyList());
        addRequestParameter("method", "getValidations");

        String result = executeAction(new Flowsheet2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).isEqualTo(JSON_CONTENT_TYPE);
        assertThat(mockResponse.getContentAsString()).contains("results");
    }

    @Test
    @DisplayName("should return NONE after writing success JSON when removing an item")
    void shouldReturnNone_whenRemoveItemWritesSuccessJson() throws Exception {
        // find() returning null skips the XML rewrite branch and writes the
        // {"success":true} response, exercising the mutator-style direct-response path.
        when(mockFlowSheetUserCreatedDao.find(1)).thenReturn(null);
        addRequestParameter("method", "removeItem");
        addRequestParameter("flowsheetId", "1");
        addRequestParameter("id", "weight");

        String result = executeAction(new Flowsheet2Action());

        assertThat(result).isEqualTo(ActionSupport.NONE);
        assertThat(mockResponse.getContentType()).isEqualTo(JSON_CONTENT_TYPE);
        assertThat(mockResponse.getContentAsString()).contains("success");
    }
}
