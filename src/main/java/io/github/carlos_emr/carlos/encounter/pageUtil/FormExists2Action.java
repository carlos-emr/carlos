/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import java.io.IOException;
import java.util.Set;

/** Read-only, chart-specific check before opening a blank longitudinal encounter form. */
public class FormExists2Action extends ActionSupport {
    // Snapshot forms deliberately start blank. Caregiver is longitudinal too (#3354).
    private static final Set<String> LONGITUDINAL = Set.of(
            "formCaregiver", "formRourke", "formRourke2006", "formRourke2017", "formRourke2020",
            "formGrowth0_36", "formGrowthChart", "formBCAR", "formBCAR2007", "formBCAR2012",
            "formBCAR2020", "formBCHP", "formBCClientChartChecklist", "formchf",
            "formPalliativeCare", "formPeriMenopausal", "formMentalHealth", "formImmunAllergy");

    @Override
    public String execute() throws IOException {
        var request = ServletActionContext.getRequest();
        var response = ServletActionContext.getResponse();
        response.setHeader("Cache-Control", "no-store");
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            response.sendError(405);
            return NONE;
        }
        String patient = request.getParameter("demographicNo");
        String appointment = request.getParameter("appointmentNo");
        String name = request.getParameter("formname");
        if (patient == null || !patient.matches("[1-9][0-9]{0,8}") || name == null || name.isBlank() || name.length() > 255
                || (appointment != null && !appointment.isEmpty() && !appointment.matches("-1|[0-9]{1,9}"))) {
            response.sendError(400);
            return NONE;
        }
        var loggedIn = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!SpringUtils.getBean(SecurityInfoManager.class).hasPrivilege(loggedIn, "_form", "r", patient)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        ObjectNode result = new ObjectMapper().createObjectNode().put("exists", false);
        for (EncounterForm form : SpringUtils.getBean(EncounterFormDao.class).findByFormName(name)) {
            if (form.isHidden() || form.getFormTable() == null || !LONGITUDINAL.contains(form.getFormTable())) continue;
            var saved = EctFormData.getShortcutLatestPatientForm(patient, form.getFormTable());
            if (saved == null) break;
            String edited = saved.edited == null ? "" : saved.getEdited();
            result.put("exists", true);
            result.put("windowName", Math.abs((name + patient + edited).hashCode()) + "started");
            result.put("url", request.getContextPath() + "/form/forwardshortcutname?formname="
                    + SafeEncode.forUriComponent(name) + "&demographic_no=" + patient + "&formId=latest"
                    + (appointment != null && !appointment.isEmpty() ? "&appointmentNo=" + appointment : ""));
            break;
        }
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(result.toString());
        return NONE;
    }
}
