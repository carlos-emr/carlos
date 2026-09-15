/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.login;

import java.time.Duration;

/**
 * Single source of truth for the TOTP acceptance window shared by MFA code validation and used-code
 * replay tracking.
 *
 * <p>{@code Login2Action} accepts a code that matches the current time step or either adjacent one,
 * and {@link MfaUsedCodeCache} must remember an accepted code for exactly as long as that window
 * lasts. Deriving both from the constants here keeps them in step: widening the tolerance without a
 * matching cache TTL would reopen the replay gap this window exists to close, and narrowing it
 * would keep codes blocked after they stopped being accepted.</p>
 */
final class TotpWindow {

    /**
     * Number of time steps accepted on either side of the current one, tolerating clock skew between
     * the server and the authenticator app.
     */
    static final int STEP_TOLERANCE = 1;

    /**
     * Time step {@code Login2Action} validates against. This mirrors the no-argument
     * {@code TimeBasedOneTimePasswordGenerator} default rather than reading it from an instance, so
     * the value is available during class initialisation of {@link MfaUsedCodeCache}.
     * {@code TotpWindowUnitTest} pins the two together.
     */
    static final Duration TIME_STEP = Duration.ofSeconds(30);

    /**
     * How long a single code stays acceptable: the current step plus the tolerated steps on each
     * side. This is the TTL used for replay tracking.
     */
    static final Duration ACCEPTANCE = TIME_STEP.multipliedBy(2L * STEP_TOLERANCE + 1);

    private TotpWindow() {
    }
}
