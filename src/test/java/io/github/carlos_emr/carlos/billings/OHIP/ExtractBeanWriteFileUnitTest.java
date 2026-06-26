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
package io.github.carlos_emr.carlos.billings.OHIP;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the OHIP {@link ExtractBean#writeFile(String)} output-path containment.
 *
 * <p>The claim file is written to the configured {@code HOME_DIR} via
 * {@code validateGeneratedChildPath(validateGeneratedFileName(ohipFilename), homeDir)}. A normal
 * filename lands inside HOME_DIR; a traversal-bearing filename is rejected by the validators (and the
 * method's broad catch means no file is written outside HOME_DIR). The MSP variant shares this exact
 * helper composition (independently covered by {@code PathValidationUtilsUnitTest}); its writeFile
 * additionally reads a {@code user.home} properties file, so it is not unit-instantiated here.</p>
 *
 * <p>Extends {@link CarlosUnitTestBase} because {@code ExtractBean}'s field initializer resolves a
 * {@code BillingDao} via {@code SpringUtils.getBean} at construction time.</p>
 *
 * @since 2026-06-01
 */
@Tag("unit")
@Tag("fast")
@DisplayName("OHIP ExtractBean.writeFile output containment")
class ExtractBeanWriteFileUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path homeDir;

    @BeforeEach
    void registerBeans() {
        // new ExtractBean() runs `billingDao = SpringUtils.getBean(BillingDao.class)` at construction.
        registerMock(BillingDao.class, mock(BillingDao.class));
    }

    private static void setOhipFilename(ExtractBean bean, String value) throws Exception {
        Field f = ExtractBean.class.getDeclaredField("ohipFilename");
        f.setAccessible(true);
        f.set(bean, value);
    }

    @Test
    @DisplayName("should write the claim file inside HOME_DIR for a valid filename")
    void shouldWriteInsideHomeDir_whenFilenameValid() throws Exception {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("HOME_DIR")).thenReturn(homeDir.toString());

            ExtractBean bean = new ExtractBean();
            setOhipFilename(bean, "claim12345.txt");

            bean.writeFile("HELLO-CLAIM");

            Path written = homeDir.resolve("claim12345.txt");
            assertThat(written).exists();
            assertThat(Files.readString(written)).contains("HELLO-CLAIM");
        }
    }

    @Test
    @DisplayName("should not write outside HOME_DIR when the filename contains traversal")
    void shouldNotWriteOutsideHomeDir_whenFilenameContainsTraversal() throws Exception {
        try (MockedStatic<CarlosProperties> propsMock = mockStatic(CarlosProperties.class)) {
            CarlosProperties props = mock(CarlosProperties.class);
            propsMock.when(CarlosProperties::getInstance).thenReturn(props);
            when(props.getProperty("HOME_DIR")).thenReturn(homeDir.toString());

            ExtractBean bean = new ExtractBean();
            setOhipFilename(bean, "../escaped-claim.txt");

            bean.writeFile("SHOULD-NOT-ESCAPE");

            // The validators reject the traversal name (writeFile swallows the rejection): nothing is
            // written outside HOME_DIR, and nothing is written inside it either.
            assertThat(homeDir.getParent().resolve("escaped-claim.txt")).doesNotExist();
            long writtenInsideHomeDir;
            try (var entries = Files.list(homeDir)) {
                writtenInsideHomeDir = entries.count();
            }
            assertThat(writtenInsideHomeDir).isZero();
        }
    }
}
