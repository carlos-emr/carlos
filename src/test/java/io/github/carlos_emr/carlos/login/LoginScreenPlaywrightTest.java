/**
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
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
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.login;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Playwright browser-based CRUD tests for the CARLOS login screen.
 *
 * <p>CRUD mapping for a stateless login page (session lifecycle):
 * <ul>
 *   <li>Read   — page renders with required fields and CARLOS branding</li>
 *   <li>Create — valid credentials create an authenticated session</li>
 *   <li>Update — logout + re-login replaces an existing session</li>
 *   <li>Delete — logout destroys the session</li>
 * </ul>
 *
 * <p>Tests are auto-skipped when the application server is not reachable,
 * so they are safe to include in the standard test suite and will only run
 * in environments with a live server on {@value #BASE_URL}.
 *
 * @since 2026-05-20
 */
@Tag("e2e")
@Tag("playwright")
@Tag("login")
class LoginScreenPlaywrightTest {

    static final String BASE_URL = "http://localhost:8080/carlos";
    static final String VALID_USERNAME = "carlosdoc";
    static final String VALID_PASSWORD = "carlos2026";
    static final String VALID_PIN = "2026";

    static final Pattern SCHEDULER_URL = Pattern.compile(".*/provider/ViewAppointmentAdminDay.*");
    // Matches /logout (Logout2Action) and /logoutPage (display gate)
    static final Pattern LOGOUT_URL = Pattern.compile(".*/logout(?:Page)?.*");

    static Playwright playwright;
    static Browser browser;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void launchBrowser() {
        assumeTrue(isServerReachable(), "CARLOS server not reachable at " + BASE_URL + " — skipping e2e tests");
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openContext() {
        context = browser.newContext();
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) context.close();
    }

    // -------------------------------------------------------------------------
    // Read — page renders correctly without authentication
    // -------------------------------------------------------------------------

    @Nested
    @Tag("read")
    @DisplayName("Login page rendering")
    class LoginPageRendering {

        @Test
        @DisplayName("should render login form with required fields")
        void shouldRenderLoginForm_withRequiredFields() {
            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page.locator("input[name=username]")).isVisible();
            assertThat(page.locator("input[name=password]")).isVisible();
            assertThat(page.locator("input[name=pin]")).isVisible();
            assertThat(page.locator("input[name=submit]")).isVisible();
        }

        @Test
        @DisplayName("should display CARLOS branding in page title")
        void shouldDisplayCarlosBranding_inPageTitle() {
            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).hasTitle(Pattern.compile(".*CARLOS.*", Pattern.CASE_INSENSITIVE));
        }

        @Test
        @DisplayName("should show login form when navigating to index without authentication")
        void shouldShowLoginForm_whenNavigatingToIndexWithoutAuthentication() {
            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page.locator("input[name=username]")).isVisible();
            assertThat(page.locator("input[name=submit]")).isVisible();
        }

        @Test
        @DisplayName("should remain on logout page when navigating to logoutPage without authentication")
        void shouldStayOnLogoutPage_whenNavigatingToLogoutPageWithoutAuthentication() {
            page.navigate(BASE_URL + "/logoutPage");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).hasURL(LOGOUT_URL);
        }
    }

    // -------------------------------------------------------------------------
    // Create — valid login creates an authenticated session
    // -------------------------------------------------------------------------

    @Nested
    @Tag("create")
    @DisplayName("Session creation")
    class SessionCreation {

        @Test
        @DisplayName("should create session and redirect to scheduler when valid credentials submitted")
        void shouldCreateSessionAndRedirectToScheduler_whenValidCredentialsSubmitted() {
            loginAsDefaultUser();

            assertThat(page).hasURL(SCHEDULER_URL);
        }

        @Test
        @DisplayName("should reject GET request to login action")
        void shouldRejectGetRequest_toLoginAction() {
            page.navigate(BASE_URL + "/login");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // A GET to the login action should NOT produce a scheduler redirect
            org.assertj.core.api.Assertions.assertThat(page.url())
                    .doesNotMatch(SCHEDULER_URL.pattern());
        }

        @Test
        @DisplayName("should not create session when invalid password submitted")
        void shouldNotCreateSession_whenInvalidPasswordSubmitted() {
            page.navigate(BASE_URL + "/index");
            fillLoginForm(VALID_USERNAME, "wrongpassword", VALID_PIN);
            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).not().hasURL(SCHEDULER_URL);
        }

        @Test
        @DisplayName("should not create session when username exceeds max length")
        void shouldNotCreateSession_whenUsernameExceedsMaxLength() {
            page.navigate(BASE_URL + "/index");
            fillLoginForm("averylongusername123", VALID_PASSWORD, VALID_PIN);
            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).not().hasURL(SCHEDULER_URL);
        }

        @Test
        @DisplayName("should not create session when PIN is non-numeric")
        void shouldNotCreateSession_whenPinIsNonNumeric() {
            page.navigate(BASE_URL + "/index");
            fillLoginForm(VALID_USERNAME, VALID_PASSWORD, "abcd");
            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).not().hasURL(SCHEDULER_URL);
        }
    }

    // -------------------------------------------------------------------------
    // Update — logout then re-login produces a fresh authenticated session
    // -------------------------------------------------------------------------

    @Nested
    @Tag("update")
    @DisplayName("Session update (re-authentication)")
    class SessionUpdate {

        @Test
        @DisplayName("should create fresh session after logout and re-login")
        void shouldCreateFreshSession_afterLogoutAndReLogin() {
            loginAsDefaultUser();

            page.navigate(BASE_URL + "/logout");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page).hasURL(LOGOUT_URL);

            loginAsDefaultUser();
            assertThat(page).hasURL(SCHEDULER_URL);
        }

        @Test
        @DisplayName("should allow valid login after prior failed attempt on fresh context")
        void shouldAllowValidLogin_afterPriorFailedAttemptOnFreshContext() {
            page.navigate(BASE_URL + "/index");
            fillLoginForm(VALID_USERNAME, "bad-password", VALID_PIN);
            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page).not().hasURL(SCHEDULER_URL);

            page.navigate(BASE_URL + "/index");
            fillLoginForm(VALID_USERNAME, VALID_PASSWORD, VALID_PIN);
            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page).hasURL(SCHEDULER_URL);
        }

        @Test
        @DisplayName("should create session when password revealed via toggle before submit")
        void shouldCreateSession_whenPasswordRevealedViaToggleBeforeSubmit() {
            page.navigate(BASE_URL + "/index");
            fillLoginForm(VALID_USERNAME, VALID_PASSWORD, VALID_PIN);

            if (page.locator("#toggleBtn").count() > 0) {
                page.locator("#toggleBtn").click();
            }

            page.locator("input[name=submit]").click();
            page.waitForLoadState(LoadState.NETWORKIDLE);
            assertThat(page).hasURL(SCHEDULER_URL);
        }
    }

    // -------------------------------------------------------------------------
    // Delete — logout destroys the authenticated session
    // -------------------------------------------------------------------------

    @Nested
    @Tag("delete")
    @DisplayName("Session deletion (logout)")
    class SessionDeletion {

        @Test
        @DisplayName("should destroy session when logout action visited")
        void shouldDestroySession_whenLogoutPageVisited() {
            loginAsDefaultUser();

            page.navigate(BASE_URL + "/logout");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).hasURL(LOGOUT_URL);
        }

        @Test
        @DisplayName("should show login form at index after logout")
        void shouldShowLoginFormAtIndex_afterLogout() {
            loginAsDefaultUser();

            page.navigate(BASE_URL + "/logout");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page.locator("input[name=username]")).isVisible();
            assertThat(page.locator("input[name=submit]")).isVisible();
        }

        @Test
        @DisplayName("should show login form after session destroyed")
        void shouldShowLoginForm_afterSessionDestroyed() {
            loginAsDefaultUser();

            page.navigate(BASE_URL + "/logoutPage");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page.locator("input[name=username]")).isVisible();
            assertThat(page.locator("input[name=submit]")).isVisible();
        }

        @Test
        @DisplayName("should not auto-login after logout when visiting index")
        void shouldNotAutoLogin_afterLogoutWhenVisitingIndex() {
            loginAsDefaultUser();

            page.navigate(BASE_URL + "/logout");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            page.navigate(BASE_URL + "/index");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            assertThat(page).not().hasURL(SCHEDULER_URL);
            assertThat(page.locator("input[name=username]")).isVisible();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void loginAsDefaultUser() {
        page.navigate(BASE_URL + "/index");
        page.waitForLoadState(LoadState.NETWORKIDLE);
        fillLoginForm(VALID_USERNAME, VALID_PASSWORD, VALID_PIN);
        page.locator("input[name=submit]").click();
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private void fillLoginForm(String username, String password, String pin) {
        page.locator("input[name=username]").fill(username);
        page.locator("input[name=password]").fill(password);
        page.locator("input[name=pin]").fill(pin);
    }

    @SuppressWarnings("deprecation")
    private static boolean isServerReachable() {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(BASE_URL + "/index").openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
