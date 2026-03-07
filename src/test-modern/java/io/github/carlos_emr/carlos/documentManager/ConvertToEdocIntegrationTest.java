/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.documentManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.EFormData;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Integration test for {@link ConvertToEdoc}, verifying that eForm data can be
 * converted to an EDoc without errors.
 *
 * <p>Migrated from legacy JUnit 4 {@code ConvertToEdocTest}. The original test
 * had three identical methods testing thread safety; this modern version
 * consolidates them into one test that exercises the same conversion logic.</p>
 *
 * @see ConvertToEdoc
 * @see EFormData
 * @since 2015-01-01
 */
@Tag("integration")
@Tag("document")
@DisplayName("ConvertToEdoc Integration Tests")
class ConvertToEdocIntegrationTest {

    private static final String FORM_DATA = "<!DOCTYPE html><html><head><meta http-equiv=\"content-type\" content=\"text/html; charset=UTF-8\"><title>Rich Text Letter</title><style type=\"text/css\">.butn {width: 140px;}</style></head><body bgcolor=\"FFFFFF\"><form action=\"../eform/addEForm.do\" method=\"POST\" name=\"RichTextLetter\"><input type=\"hidden\" value=\"Colcamex Test Clinic\" name=\"clinic_name\" id=\"clinic_name\"><input type=\"hidden\" value=\"TEST, MISTER\" name=\"patient_name\" id=\"patient_name\"><textarea name=\"Letter\" id=\"Letter\" style=\"width:600px; display: none;\"><section><div id=\"container\"><header><div id=\"returnaddress\"><span>Second Floor, 2405 Wesbrook Mall</span></div></header></div></section></textarea></form></body></html>";

    private EFormData eformData;

    @BeforeEach
    void setUp() {
        eformData = new EFormData();
        eformData.setFormId(100);
        eformData.setFormName(" Test Form ");
        eformData.setFormData(FORM_DATA);
    }

    @Test
    @DisplayName("should convert eForm data to EDoc without throwing exception")
    void shouldConvertEFormDataToEDoc_withoutThrowingException() {
        assertThatNoException().isThrownBy(() -> ConvertToEdoc.from(eformData));
    }
}
