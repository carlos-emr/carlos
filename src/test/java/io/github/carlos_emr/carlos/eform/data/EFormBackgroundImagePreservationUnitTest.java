/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.eform.data;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("fast")
@DisplayName("EForm runtime background image preservation")
class EFormBackgroundImagePreservationUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void setUp() {
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should preserve displayImage-backed background img tags during runtime DOM mutation")
    void shouldPreserveBackgroundImages_whenRuntimeHelpersMutateFormHtml() {
        EForm eform = new EForm();
        eform.setFormHtml("<!DOCTYPE html><html><head><title>Test</title></head><body><form id=\"FormName\"><div id=\"page1\"><img id=\"BGImage1\" src=\"${oscar_image_path}background.png\" alt=\"background\"><input id=\"field1\" name=\"field1\" type=\"text\"></div></form></body></html>");

        eform.setImagePath("/carlos");
        eform.addHeadJavascript("/carlos/library/jquery/jquery-3.7.1.min.js");
        eform.addCSS("/carlos/library/jquery/jquery-ui-1.14.2.min.css", "all");
        eform.addHiddenInputElement("fdid", "123");

        String rendered = eform.getFormHtml();
        assertThat(rendered).contains("id=\"BGImage1\"");
        assertThat(rendered).contains("/carlos/eform/displayImage?imagefile=background.png");
        assertThat(rendered).contains("/carlos/library/jquery/jquery-3.7.1.min.js");
        assertThat(rendered).contains("id=\"fdid\"");
    }
}
