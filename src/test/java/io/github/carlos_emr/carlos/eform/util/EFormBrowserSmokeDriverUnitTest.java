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

package io.github.carlos_emr.carlos.eform.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.SessionNotCreatedException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.NoSuchDriverException;
import org.opentest4j.TestAbortedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies optional smoke-driver availability without starting a browser. */
@Tag("unit")
@Tag("eform")
class EFormBrowserSmokeDriverUnitTest {
    @Test
    void shouldSkipSmoke_whenDriverCannotBeObtained() {
        assertThatThrownBy(() -> EFormBrowserPdfServiceSeleniumSmokeIntegrationTest.startDriverOrSkip(() -> {
            throw new NoSuchDriverException("Unable to obtain chromedriver");
        })).isInstanceOf(TestAbortedException.class);
    }

    @Test
    void shouldSkipSmoke_whenChromeAndDriverVersionsDiffer() {
        assertThatThrownBy(() -> EFormBrowserPdfServiceSeleniumSmokeIntegrationTest.startDriverOrSkip(() -> {
            throw new SessionNotCreatedException("session not created: This version of ChromeDriver only supports Chrome version 147"
                    + "\nCurrent browser version is 153.0.0.0 with binary path /opt/chromium");
        })).isInstanceOf(TestAbortedException.class);
    }

    @Test
    void shouldFailSmoke_whenBrowserCannotStart() {
        for (String message : new String[] {"Chrome failed to start: crashed", "cannot find Chrome binary",
                "error while loading shared libraries: libnss3.so", "user data directory is already in use"}) {
            WebDriverException failure = new SessionNotCreatedException(message);
            assertThatThrownBy(() -> EFormBrowserPdfServiceSeleniumSmokeIntegrationTest.startDriverOrSkip(() -> {
                throw failure;
            })).isSameAs(failure);
        }
    }

    @Test
    void shouldFailSmoke_whenVersionTextAppearsOnlyInDriverMetadata() {
        WebDriverException failure = new SessionNotCreatedException("Chrome failed to start: crashed");
        failure.addInfo("Driver info", "This version of ChromeDriver only supports Chrome version 147; Current browser version is 153");
        assertThatThrownBy(() -> EFormBrowserPdfServiceSeleniumSmokeIntegrationTest.startDriverOrSkip(() -> {
            throw failure;
        })).isSameAs(failure);
    }

    @Test
    void shouldFailSmoke_whenVersionTextIsNotASessionCreationFailure() {
        WebDriverException failure = new WebDriverException(
                "This version of ChromeDriver only supports Chrome version 147; Current browser version is 153");
        assertThatThrownBy(() -> EFormBrowserPdfServiceSeleniumSmokeIntegrationTest.startDriverOrSkip(() -> {
            throw failure;
        })).isSameAs(failure);
    }
}
