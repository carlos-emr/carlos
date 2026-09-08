/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary.web;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryArtifact;
import io.github.carlos_emr.carlos.clinical.summary.ChartClinicalSummaryProvider;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryArtifactProvider;
import io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryRequest;
import io.github.carlos_emr.carlos.clinical.summary.SyntheticClinicalSummaryProvider;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import java.util.NoSuchElementException;

/** Read-only view with explicit, validated demographic scope. No artifact-path or model parameters. */
public final class AiClinicalSummaryPrototype2Action extends ActionSupport {
    public static final String ENABLED_PROPERTY = "clinical.ai_summary_prototype.enabled";
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final ClinicalSummaryArtifactProvider provider = new SyntheticClinicalSummaryProvider();
    private final ClinicalSummaryArtifactProvider chartProvider;

    public AiClinicalSummaryPrototype2Action() {
        this(null);
    }

    AiClinicalSummaryPrototype2Action(ClinicalSummaryArtifactProvider chartProvider) {
        this.chartProvider = chartProvider;
    }

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
            response.setHeader("Allow", "GET, HEAD");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }
        LoggedInInfo user = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (user == null || !securityInfoManager.hasPrivilege(user, "_eChart", "r", null)) {
            throw new SecurityException("missing required sec object (_eChart)");
        }
        if (!"true".equals(CarlosProperties.getInstance().getProperty(ENABLED_PROPERTY, "false"))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }
        ClinicalSummaryArtifact artifact;
        String[] demographicValues = request.getParameterValues("demographicNo");
        if (demographicValues == null) {
            artifact = provider.load(user, ClinicalSummaryRequest.synthetic());
        } else {
            int demographicNo;
            try {
                if (demographicValues.length != 1 || demographicValues[0] == null
                        || !demographicValues[0].matches("[1-9][0-9]{0,9}")) {
                    throw new NumberFormatException();
                }
                demographicNo = Integer.parseInt(demographicValues[0]);
            } catch (NumberFormatException invalid) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return NONE;
            }
            try {
                ClinicalSummaryArtifactProvider selected = chartProvider == null
                        ? new ChartClinicalSummaryProvider() : chartProvider;
                artifact = selected.load(user, ClinicalSummaryRequest.chart(demographicNo));
            } catch (NoSuchElementException missing) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return NONE;
            }
            request.setAttribute("summaryDemographicNo", demographicNo);
        }
        request.setAttribute("summaryArtifact", artifact.getView());
        request.setAttribute("summaryClaims", artifact.getClaimsById());
        request.setAttribute("summaryRenderable", artifact.isRenderable());
        return SUCCESS;
    }
}
