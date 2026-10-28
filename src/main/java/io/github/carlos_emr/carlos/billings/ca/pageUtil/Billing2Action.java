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
package io.github.carlos_emr.carlos.billings.ca.pageUtil;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Cross-province billing entry router. Decides whether a request is heading
 * into the BC or ON billing flow and returns the matching result name; the
 * struts mapping then chains into the province-specific setup action.
 *
 * <p>This class lives at the {@code ca} parent level on purpose — it has no
 * BC-specific or ON-specific imports. Province-specific bean population,
 * decision-support evaluation, and form binding live behind the chain in
 * {@code ca.bc.pageUtil.BillingBCSetup2Action} and (for ON)
 * {@code ca.on.web.ViewBillingOn2Action}, respectively.</p>
 *
 * <p>Region resolution: prefer the {@code billRegion} request parameter; fall
 * back to the deployment-wide {@code billregion} property in
 * {@code carlos.properties} (written by {@code carlos-ctl} at install time from
 * the configured province). Anything not exactly {@code "ON"} is treated as BC,
 * preserving the historical default.</p>
 *
 * <p>The property fall-back reads {@link CarlosProperties}, the singleton backed
 * by {@code carlos.properties}. It previously read a same-named holder in
 * {@code carlos.util.plugin} whose static field was never populated by any code
 * path, so the fall-back always evaluated to {@code null} and every caller that
 * omitted {@code billRegion} was silently routed to BC. On an Ontario install
 * that lands on {@code billingBC.jsp}, which queries BC-only tables that the
 * Ontario schema does not have — surfacing as "CARLOS Error: 500". The Ontario
 * bill-entry form reaches this router without {@code billRegion} whenever the
 * billing-type dropdown is switched to a type that re-opens the form
 * (3rd Party / Bonus Codes), which is how the defect was reported.</p>
 *
 * @since 2026-04-27
 */
public final class Billing2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;

    public Billing2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class));
    }

    Billing2Action(SecurityInfoManager securityInfoManager) {
        this.securityInfoManager = securityInfoManager;
    }

    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "r", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        String region = request.getParameter("billRegion");
        if (region == null || region.isEmpty()) {
            CarlosProperties props = CarlosProperties.getInstance();
            // Raw Hashtable read, not getProperty(): the CarlosProperties
            // override builds and logs a WARN for every key it cannot find.
            // `billregion` is legitimately absent on an install that never set
            // it, and this is the fall-back path, so a miss is expected rather
            // than exceptional — going through getProperty() would turn each
            // such request into log noise.
            //
            // The two reads agree for THIS key only. getProperty() also
            // substitutes a PROPERTY_DEFAULTS entry on a miss, and screens the
            // stored value against the deprecated-namespace blacklist; neither
            // applies to `billregion` (no default is registered for it, and a
            // province code cannot be blacklisted). Do not copy this pattern to
            // a key that has a registered default — there get() and
            // getProperty() genuinely differ.
            Object configured = props == null ? null : props.get("billregion");
            region = configured instanceof String stored ? stored : null;
        }
        return "ON".equals(region) ? "ON" : "BC";
    }
}
