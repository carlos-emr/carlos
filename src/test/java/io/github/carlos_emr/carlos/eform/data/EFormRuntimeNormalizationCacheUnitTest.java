/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.eform.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the caching contract of {@link EForm#getFormHtml()}'s DOM normalization pass: it runs once
 * per content generation, and replacing the content re-arms it. Runs with a SpringUtils-mocked
 * base because the DOM pass parses through {@code ConvertToEdoc}, whose static init resolves
 * {@code NioFileManager}; without that registration the pass silently no-ops (its
 * {@code LinkageError} fallback), which is exactly the environment
 * {@code EFormSetContextPathUnitTest} exercises for the string-level rewrites.
 */
@DisplayName("EForm runtime normalization cache")
@Tag("unit")
@Tag("fast")
class EFormRuntimeNormalizationCacheUnitTest extends CarlosUnitTestBase {

    @BeforeEach
    void registerConvertToEdocDependencies() {
        // ConvertToEdoc resolves NioFileManager in a static-final initializer; register the mock
        // BEFORE anything touches that class or its static init fails and the DOM pass no-ops.
        registerMock(NioFileManager.class, Mockito.mock(NioFileManager.class));
    }

    @Test
    @DisplayName("should keep normalized output stable across repeated reads and re-normalize replaced content")
    void shouldRenormalizeContent_afterSetFormHtml() {
        EForm eform = new EForm();
        eform.setFormHtml("<html><body><script src=\"jquery-1.12.0.min.js\"></script></body></html>");
        eform.setContextPath("/carlos");

        // The DOM pass runs once per content generation and is cached; repeated reads must return
        // the identical normalized output.
        String first = eform.getFormHtml();
        String second = eform.getFormHtml();
        assertThat(first).contains("/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js");
        assertThat(second).isEqualTo(first);

        // Replacing the content (as the render composer does mid-pipeline) must reset the cache so
        // legacy constructs in the NEW content are normalized on the next read.
        eform.setFormHtml("<html><body><script src=\"/eform/jquery-1.12.0.min.js\"></script></body></html>");
        assertThat(eform.getFormHtml())
                .contains("/carlos/eform/displayImage?imagefile=jquery-1.12.0.min.js")
                .doesNotContain("src=\"/eform/jquery-1.12.0.min.js\"");
    }

}
