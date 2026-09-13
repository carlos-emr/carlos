/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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


package io.github.carlos_emr.carlos.billings.ca.on.web;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Struts action for viewing, creating, and deleting Ontario billing CSS style
 * snippets used by the legacy billing UI.
 *
 * <p>The action still routes by {@code method=} to stay compatible with the
 * existing JSP, but read and write paths are split by privilege and POST
 * guards so the operational contract remains explicit.</p>
 */
public class ManageCss2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private CSSStylesDAO cssStylesDao = (CSSStylesDAO) SpringUtils.getBean(CSSStylesDAO.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    /** Dispatch the legacy style-management screen to render, save, or delete flows. */
    public String execute() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }

        String method = request.getParameter("method");
        if ("save".equals(method)) {
            return save();
        } else if ("delete".equals(method)) {
            return delete();
        }
        // Default render path: gate on _admin/r before any DAO read. The
        // per-method save()/delete() paths still gate independently on
        // _admin/w because mutation writes need the stronger privilege.
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        styles = cssStylesDao.findAll();
        return "init";
    }

    /** Persist a new or edited CSS style after privilege and POST checks succeed. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public String save() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        // Mutation: persist or merge cssStyle. CSRFGuard's body-token check
        // only fires on non-GET; without an explicit POST gate a forged GET
        // URL would bypass it. Mirrors the pattern used by sibling 2Actions.
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        CssStyle cssStyle = null;
        boolean newStyle = false;
        List<CssStyle> styles = cssStylesDao.findAll();

        if (selectedStyle.equals("-1")) {
            cssStyle = new CssStyle();
            cssStyle.setStatus(CssStyle.ACTIVE);
            newStyle = true;
        } else {
            for (CssStyle cssStylecurrent : styles) {
                if (cssStylecurrent.getStyle().equalsIgnoreCase(this.getEditStyle())) {
                    cssStyle = cssStylecurrent;
                    break;
                }
            }
            // No-match guard: surface a clean validation message instead
            // of letting the next field-set NPE without an operator signal.
            if (cssStyle == null) {
                MiscUtils.getLogger().warn("ManageCss2Action.save: CSS style not found for editStyle={}",
                        io.github.carlos_emr.carlos.utility.LogSafe.sanitize(this.getEditStyle()));
                addActionError("CSS style not found.");
                this.setStyles(styles);
                return "init";
            }
        }

        cssStyle.setName(this.getStyleName());
        cssStyle.setStyle(this.getEditStyle());

        if (newStyle) {
            cssStylesDao.persist(cssStyle);
            styles.add(cssStyle);
        } else {
            cssStylesDao.merge(cssStyle);
        }

        this.setStyles(styles);
        request.setAttribute("success", "true");

        return "init";
    }


    /** Soft-delete a CSS style and clear any billing-service rows still pointing at it. */
    public String delete() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }
        // Mutation: cascades a null update to billing_service.display_style for
        // every code referencing this style. POST-only — see save() above.
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        // The cascade (css_styles soft-delete + billing_service.display_style
        // null on every referencing code) is bundled under @Transactional
        // inside CssStyleDeletionService — a mid-cascade DAO failure rolls
        // back, leaving both tables consistent.
        boolean deleted = SpringUtils.getBean(io.github.carlos_emr.carlos.billings.ca.on.service.CssStyleDeletionService.class)
                .deleteByStyleId(this.getEditStyle());
        if (!deleted) {
            MiscUtils.getLogger().warn("ManageCss2Action.delete: CSS style not found for editStyle={}",
                    io.github.carlos_emr.carlos.utility.LogSafe.sanitize(this.getEditStyle()));
            addActionError("CSS style not found.");
            this.setStyles(cssStylesDao.findAll());
            return "init";
        }

        this.setStyles(cssStylesDao.findAll());
        request.setAttribute("success", "true");

        return "init";
    }

    private List<CssStyle> styles;
    private String styleText = "";
    private String selectedStyle = "-1";
    private String editStyle = "-1";
    private String styleName = "";

    public List<CssStyle> getStyles() {
        return styles;
    }

    public void setStyles(List<CssStyle> styles) {
        this.styles = styles;
    }

    public String getStyleText() {
        return styleText;
    }

    @StrutsParameter
    public void setStyleText(String styleText) {
        this.styleText = styleText;
    }

    public String getSelectedStyle() {
        return selectedStyle;
    }

    @StrutsParameter
    public void setSelectedStyle(String selectedStyle) {
        this.selectedStyle = selectedStyle;
    }

    public String getEditStyle() {
        return editStyle;
    }

    @StrutsParameter
    public void setEditStyle(String editStyle) {
        this.editStyle = editStyle;
    }

    public String getStyleName() {
        return styleName;
    }

    @StrutsParameter
    public void setStyleName(String styleName) {
        this.styleName = styleName;
    }
}
