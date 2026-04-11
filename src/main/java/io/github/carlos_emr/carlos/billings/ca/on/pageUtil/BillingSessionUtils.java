/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import io.github.carlos_emr.BillingBean;
import jakarta.servlet.http.HttpSession;

/**
 * Utility methods for accessing billing-related session beans shared
 * by both ON and BC billing correction submit actions.
 *
 * @since 2026
 */
public final class BillingSessionUtils {

    private BillingSessionUtils() {
    }

    /**
     * Retrieves the {@link BillingBean} from the HTTP session.
     *
     * <p>The session key used by {@code billingCorrectionValid.jsp} is {@code "billing"}.
     * The old JSP {@code <jsp:useBean id="billingBean" .../>} created a separate empty
     * bean under {@code "billingBean"}. This method tries {@code "billing"} first, then
     * falls back to {@code "billingBean"} for compatibility.
     *
     * @param session the current {@link HttpSession}
     * @return the {@link BillingBean} from the session, or {@code null} if not present
     */
    public static BillingBean getBillingBean(HttpSession session) {
        BillingBean billingBean = (BillingBean) session.getAttribute("billing");
        if (billingBean == null) {
            billingBean = (BillingBean) session.getAttribute("billingBean");
        }
        return billingBean;
    }
}
