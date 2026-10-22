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


package io.github.carlos_emr.carlos.utility;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.tagext.TagSupport;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;

import io.github.carlos_emr.CarlosProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class CustomInterfaceTag extends TagSupport {

    Logger logger = MiscUtils.getLogger();
    private String name;
    private String section;

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public int doStartTag() throws JspException {
        CarlosProperties props = CarlosProperties.getInstance();
        String customJs = props.getProperty("cme_js");

        HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (name != null && name.length() > 0) {
            customJs = name;
        }
        if (customJs == null || customJs.length() == 0) {
            customJs = "default";
        }

        if (customJs.equals("default") && getSection().equals("cme")) {
            //check preferences
            CppPreferencesUIBean bean = new CppPreferencesUIBean(loggedInInfo.getLoggedInProviderNo());
            bean.loadValues();
            if (bean.getEnable() != null && bean.getEnable().equals("on")) {
                logger.info("Use preference based echart");
                try {
                    JspWriter out = super.pageContext.getOut();
                    out.println(this.getPreferenceBasedEChart(bean));
                } catch (IOException e) {
                    logger.error("Error:", e);
                }
                return SKIP_BODY;
            }
        }

        if (customJs != null && customJs.length() > 0) {
            JspWriter out = super.pageContext.getOut();
            ServletContext servletContext = this.pageContext.getServletContext();
            String contextPath = servletContext.getContextPath();
            try {
                if (getSection() != null && getSection().length() > 0) {
                    String scriptPath = "/js/custom/" + customJs + "/" + getSection() + ".js";

                    // Skip emitting the <script> tag when the theme has no file for this
                    // section. This prevents 404s and MIME-type console errors for theme/
                    // section combinations that are not provided (e.g. the default theme
                    // ships only billing.js). See GitHub issue: 404 on main.js from admin.
                    if (!scriptResourceExists(servletContext, scriptPath)) {
                        logger.debug("Skipping <oscar:customInterface> script tag; resource not found: " + scriptPath);
                        return SKIP_BODY;
                    }

                    boolean hide_ConReport = props.isPropertyActive("hide_ConReport_link");
                    boolean cardswipe = props.getBooleanProperty("cardswipe", "false");
                    String customTag = "";
                    if (customJs.equalsIgnoreCase("ocean")) {
                        customTag = "ocean-host=" + SafeEncode.forUriComponent(props.getProperty("ocean_host"));
                    }

                    String cacheBuster = UUID.randomUUID().toString();
                    out.println("<script id=\"mainScript\" src=\"" + contextPath + scriptPath + "?no-cache=" + cacheBuster + "&autoRefresh=true\" hide_ConReport=\"" + hide_ConReport + "\" cardswipe=\"" + cardswipe + "\" " + customTag + " ></script>");
                }
            } catch (IOException e) {
                logger.error("Error", e);
            }
        }
        return SKIP_BODY;
    }

    /**
     * Checks whether a webapp-relative resource path exists on disk or in the
     * deployed WAR. Tries {@link ServletContext#getResource(String)} first (works
     * for unpacked and packed WARs), then falls back to
     * {@link ServletContext#getRealPath(String)} for extra robustness.
     *
     * @param servletContext the servlet context
     * @param path the webapp-relative resource path (e.g. {@code /js/custom/default/main.js})
     * @return {@code true} if the resource exists; {@code false} otherwise
     */
    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private boolean scriptResourceExists(ServletContext servletContext, String path) {
        try {
            if (servletContext.getResource(path) != null) {
                return true;
            }
        } catch (java.net.MalformedURLException e) {
            logger.debug("Malformed resource path: " + path, e);
            return false;
        }
        String realPath = servletContext.getRealPath(path);
        return realPath != null && PathValidationUtils.resolveTrustedPath(new File(realPath)).isFile();
    }

    @Override
    public int doEndTag() throws JspException {
        return EVAL_PAGE;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }


    private void appendIssueNoteUrl(StringBuilder sb, String position, String issueCode, String titleVariable) {
        String rawPosition = position == null ? "" : position;
        String jsPosition = SafeEncode.forJavaScript(rawPosition);
        String jsCommand = SafeEncode.forJavaScript(SafeEncode.forUriComponent("div" + rawPosition));
        sb.append("\"div").append(jsPosition)
                .append("\":    ctx + \"/CaseManagementView?hc=996633&method=listNotes&providerNo=\" + providerNo + \"&demographicNo=\" + demographicNo + \"&issue_code=")
                .append(issueCode)
                .append("&title=\" + ").append(titleVariable)
                .append(" + \"&cmd=").append(jsCommand).append("\"");
    }

    private String getPreferenceBasedEChart(CppPreferencesUIBean bean) {
        StringBuilder sb = new StringBuilder();
        sb.append("<script>");
        sb.append("jQuery(document).ready(function(){");
        sb.append("issueNoteUrls = {");
        boolean flag = false, row1 = false, row2 = false;
        if (!bean.getSocialHxPosition().equals("")) {
            appendIssueNoteUrl(sb, bean.getSocialHxPosition(), "SocHistory", "socHistoryLabel");
            flag = true;
            if (bean.getSocialHxPosition().startsWith("R1")) {
                row1 = true;
            }
            if (bean.getSocialHxPosition().startsWith("R2")) {
                row2 = true;
            }
        }
        if (!bean.getMedicalHxPosition().equals("")) {
            if (flag) {
                sb.append(",");
            }
            appendIssueNoteUrl(sb, bean.getMedicalHxPosition(), "MedHistory", "medHistoryLabel");
            flag = true;
            if (bean.getMedicalHxPosition().startsWith("R1")) {
                row1 = true;
            }
            if (bean.getMedicalHxPosition().startsWith("R2")) {
                row2 = true;
            }
        }
        if (!bean.getOngoingConcernsPosition().equals("")) {
            if (flag) {
                sb.append(",");
            }
            appendIssueNoteUrl(sb, bean.getOngoingConcernsPosition(), "Concerns", "onGoingLabel");
            flag = true;
            if (bean.getOngoingConcernsPosition().startsWith("R1")) {
                row1 = true;
            }
            if (bean.getOngoingConcernsPosition().startsWith("R2")) {
                row2 = true;
            }
        }
        if (!bean.getRemindersPosition().equals("")) {
            if (flag) {
                sb.append(",");
            }
            appendIssueNoteUrl(sb, bean.getRemindersPosition(), "Reminders", "remindersLabel");
            flag = true;
            if (bean.getRemindersPosition().startsWith("R1")) {
                row1 = true;
            }
            if (bean.getRemindersPosition().startsWith("R2")) {
                row2 = true;
            }
        }
        sb.append("};");

        //can we delete a row?
        if (!row1) {
            sb.append("removeCppRow(1);");
        }
        if (!row2) {
            sb.append("removeCppRow(2);");
        }

        sb.append("init();");

        //show/hide Cpp items
        if ("".equals(bean.getPreventionsDisplay())) {
            sb.append("hideCpp('preventions');");
        }
        if ("".equals(bean.getDxRegistryDisplay())) {
            sb.append("hideCpp('Dx');");
        }
        if ("".equals(bean.getFormsDisplay())) {
            sb.append("hideCpp('forms');");
        }
        if ("".equals(bean.getEformsDisplay())) {
            sb.append("hideCpp('eforms');");
        }
        if ("".equals(bean.getDocumentsDisplay())) {
            sb.append("hideCpp('docs');");
        }
        if ("".equals(bean.getLabsDisplay())) {
            sb.append("hideCpp('labs');");
        }
        if ("".equals(bean.getMeasurementsDisplay())) {
            sb.append("hideCpp('measurements');");
        }
        if ("".equals(bean.getConsultationsDisplay())) {
            sb.append("hideCpp('consultation');");
        }
        if ("".equals(bean.getHrmDisplay())) {
            sb.append("hideCpp('HRM');");
        }
        if ("".equals(bean.getAllergiesDisplay())) {
            sb.append("hideCpp('allergies');");
        }
        if ("".equals(bean.getMedicationsDisplay())) {
            sb.append("hideCpp('Rx');");
        }
        if ("".equals(bean.getOtherMedsDisplay())) {
            sb.append("hideCpp('OMeds');");
        }
        if ("".equals(bean.getRiskFactorsDisplay())) {
            sb.append("hideCpp('RiskFactors');");
        }
        if ("".equals(bean.getFamilyHxDisplay())) {
            sb.append("hideCpp('FamHistory');");
        }
        if ("".equals(bean.getUnresolvedIssuesDisplay())) {
            sb.append("hideCpp('unresolvedIssues');");
        }
        if ("".equals(bean.getResolvedIssuesDisplay())) {
            sb.append("hideCpp('resolvedIssues');");
        }
        if ("".equals(bean.getEpisodesDisplay())) {
            sb.append("hideCpp('episode');");
        }

        sb.append("});");
        sb.append("function notifyIssueUpdate() {}");
        sb.append("function notifyDivLoaded(divId) {}");
        sb.append("</script>");
        return sb.toString();
    }
}
