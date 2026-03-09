/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.billing.CA.ON.web;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.CssStyle;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public class ManageCSS2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private CSSStylesDAO cssStylesDao = (CSSStylesDAO) SpringUtils.getBean(CSSStylesDAO.class);
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("save".equals(method)) {
            return save();
        } else if ("delete".equals(method)) {
            return delete();
        }
        styles = cssStylesDao.findAll();
        return "init";
    }

    public String save() {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
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


    public String delete() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        List<CssStyle> styles = cssStylesDao.findAll();
        int idx = 0;
        for (CssStyle cssStylecurrent : styles) {
            if (cssStylecurrent.getStyle().equalsIgnoreCase(this.getEditStyle())) {
                cssStylecurrent.setStatus(CssStyle.DELETED);
                cssStylesDao.merge(cssStylecurrent);
                styles.remove(idx);

                BillingServiceDao billingServiceDao = (BillingServiceDao) SpringUtils.getBean(BillingServiceDao.class);
                List<BillingService> serviceCodes = billingServiceDao.findBillingCodesByFontStyle(cssStylecurrent.getId());
                for (BillingService servicecode : serviceCodes) {
                    servicecode.setDisplayStyle(null);
                    billingServiceDao.merge(servicecode);
                }
                break;
            }
            ++idx;
        }

        this.setStyles(styles);
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
