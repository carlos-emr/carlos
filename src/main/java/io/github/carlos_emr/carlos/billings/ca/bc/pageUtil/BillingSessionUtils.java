/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.BillingBean;
import jakarta.servlet.http.HttpSession;

/**
 * BC compatibility helper for legacy billing correction session keys.
 */
final class BillingSessionUtils {

    private BillingSessionUtils() {
    }

    static BillingBean getBillingBean(HttpSession session) {
        Object billing = session.getAttribute("billing");
        if (billing instanceof BillingBean billingBean) {
            return billingBean;
        }
        Object fallback = session.getAttribute("billingBean");
        if (fallback instanceof BillingBean billingBean) {
            return billingBean;
        }
        return null;
    }
}
