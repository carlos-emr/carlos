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
package io.github.carlos_emr.carlos.commn.web;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;

/** Renders complete referral-label batches, preserving the selection if generation fails. */
public class PrintReferralLabel2Action extends ActionSupport {
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public PrintReferralLabel2Action() {
    }

    JasperReport loadTemplate() throws Exception {
        Path custom = Path.of(System.getProperty("user.home"), "reflabel.xml");
        // An absent override is normal; an unreadable/broken override must fail,
        // rather than silently substituting a different clinic label layout.
        try (InputStream template = Files.notExists(custom)
                ? getClass().getResourceAsStream("/org/oscarehr/common/web/reflabel.xml")
                : new FileInputStream(custom.toFile())) {
            if (template == null) throw new IOException("Referral label template is unavailable");
            return JasperCompileManager.compileReport(template);
        }
    }

    byte[] renderLabel(JasperReport template, String id) throws Exception {
        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("billingreferral_no", id);
        try (Connection connection = LegacyJdbcQuery.getConnection()) {
            JasperPrint print = JasperFillManager.fillReport(template, parameters, connection);
            if (print.getPages().isEmpty()) throw new MissingReferralException();
            return JasperExportManager.exportReportToPdf(print);
        }
    }

    private static final class MissingReferralException extends Exception { }

    @Override
    public String execute() throws IOException {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_demographic", "r", null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        boolean checklist = "true".equals(request.getParameter("useCheckList"));
        List<String> ids = new ArrayList<>();
        if (checklist) {
            Object selected = request.getSession().getAttribute("billingReferralAdminCheckList");
            if (selected instanceof List<?> entries) {
                for (Object entry : entries) {
                    if (entry instanceof ProfessionalSpecialist specialist) {
                        ids.add(String.valueOf(specialist.getId()));
                    } else {
                        // A malformed cached selection must not silently print a partial batch.
                        ids.add("");
                    }
                }
            }
        } else {
            String list = request.getParameter("ids");
            if (StringUtils.isBlank(list)) list = request.getParameter("billingreferralNo");
            if (StringUtils.isNotBlank(list)) ids.addAll(List.of(list.split(",", -1)));
        }
        if (ids.isEmpty() || ids.stream().anyMatch(id -> !id.matches("[1-9][0-9]{0,9}"))) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Select a referral to print.");
            return NONE;
        }
        try {
            JasperReport template = loadTemplate();
            List<Object> labels = new ArrayList<>();
            for (String id : ids) labels.add(new ByteArrayInputStream(renderLabel(template, id)));
            // Validate and merge every label before committing HTTP 200 or PDF bytes.
            // Required merging refuses a partial batch if any label cannot be read.
            ByteArrayOutputStream pdf = new ByteArrayOutputStream();
            ConcatPDF.concatRequired(labels, pdf);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=label_.pdf");
            response.setHeader("Cache-Control", "no-store");
            response.setDateHeader("Expires", 0);
            pdf.writeTo(response.getOutputStream());
            if (checklist) {
                request.getSession().setAttribute("billingReferralAdminCheckList", new ArrayList<ProfessionalSpecialist>());
            }
        } catch (MissingReferralException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "A selected referral no longer exists.");
        } catch (Exception e) {
            MiscUtils.getLogger().error("Referral label generation failed ({})", e.getClass().getSimpleName());
            if (response.isCommitted()) throw new IOException("Referral label response failed", e);
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Referral labels could not be generated. Please try again.");
        }
        return NONE;
    }
}
