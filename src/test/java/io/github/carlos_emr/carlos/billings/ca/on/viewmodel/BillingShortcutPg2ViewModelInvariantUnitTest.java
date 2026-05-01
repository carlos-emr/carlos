/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Pins the round-7 build-time pair invariant on
 * {@link BillingShortcutPg2ViewModel}: a non-empty {@code redirectUrl} is
 * required iff {@code postSaveAction == REDIRECT_TO_PG1}. The contract
 * exists so the JSP doesn't have to invent fallback logic for incoherent
 * pairs (e.g., "redirect with no URL" = "fall through to close window?").
 *
 * <p>A future refactor that drops the guard would silently allow the JSP
 * to render incoherent navigation; without these tests that drift is
 * invisible to CI.
 */
@DisplayName("BillingShortcutPg2ViewModel pair invariant")
@Tag("unit")
@Tag("billing")
class BillingShortcutPg2ViewModelInvariantUnitTest {

    @Test
    void shouldThrow_whenRedirectActionIsPairedWithEmptyUrl() {
        assertThatThrownBy(() -> BillingShortcutPg2ViewModel.builder()
                .postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.REDIRECT_TO_PG1)
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIRECT_TO_PG1 requires a non-empty redirectUrl");
    }

    @Test
    void shouldThrow_whenNoneActionIsPairedWithNonEmptyUrl() {
        assertThatThrownBy(() -> BillingShortcutPg2ViewModel.builder()
                .postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.NONE)
                .redirectUrl("/billing/CA/ON/billingShortcutPg1View?demographic_no=1")
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("redirectUrl must be empty unless");
    }

    @Test
    void shouldThrow_whenCloseWindowActionIsPairedWithNonEmptyUrl() {
        // Symmetric for CLOSE_WINDOW — any non-redirect action with a URL.
        assertThatThrownBy(() -> BillingShortcutPg2ViewModel.builder()
                .postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.CLOSE_WINDOW)
                .redirectUrl("/some/url")
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldBuild_whenRedirectActionIsPairedWithNonEmptyUrl() {
        // Positive path: the only legitimate redirect shape.
        assertThatNoException().isThrownBy(() -> {
            BillingShortcutPg2ViewModel vm = BillingShortcutPg2ViewModel.builder()
                    .postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.REDIRECT_TO_PG1)
                    .redirectUrl("/billing/CA/ON/billingShortcutPg1View?demographic_no=1")
                    .build();
            assertThat(vm.getPostSaveAction())
                    .isEqualTo(BillingShortcutPg2ViewModel.PostSaveAction.REDIRECT_TO_PG1);
            assertThat(vm.getRedirectUrl()).contains("billingShortcutPg1View");
        });
    }

    @Test
    void shouldBuild_whenNonRedirectActionsHaveNoUrl() {
        // CLOSE_WINDOW + empty URL is fine.
        assertThatNoException().isThrownBy(() -> BillingShortcutPg2ViewModel.builder()
                .postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.CLOSE_WINDOW)
                .build());
        // NONE (the default) + empty URL is fine.
        assertThatNoException().isThrownBy(() -> BillingShortcutPg2ViewModel.builder().build());
    }
}
