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
package io.github.carlos_emr.carlos.fax.admin;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang3.time.DateUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.dao.FaxClientLogDao;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxClientLog;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClient;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderClientFactory;
import io.github.carlos_emr.carlos.fax.provider.FaxProviderException;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.form.JSONUtil;
import io.github.carlos_emr.carlos.fax.action.Fax2Action;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ManageFaxes2Action extends Fax2Action {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final Logger log = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
    private final FaxProviderClientFactory faxProviderClientFactory = SpringUtils.getBean(FaxProviderClientFactory.class);

    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of the literal HTTP method name (GET/HEAD) for the method-verb gate; not a security or authorization decision on user identity")
    @Override
    public String execute() {
        String method = request.getParameter("method");
        // CancelFax/ResendFax/SetCompleted mutate fax jobs (and CancelFax reaches the provider);
        // they must never ride a GET/HEAD. This gate has to run HERE because these methods
        // dispatch before the parent's verb gate in super.execute() is ever reached.
        // manageFaxes.jsp issues all three via POST, so no UI change is required.
        boolean mutator = "CancelFax".equals(method) || "ResendFax".equals(method) || "SetCompleted".equals(method);
        String httpMethod = request.getMethod();
        if (mutator && ("GET".equalsIgnoreCase(httpMethod) || "HEAD".equalsIgnoreCase(httpMethod))) {
            sendErrorQuietly(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Method not allowed");
            return NONE;
        }
        if ("CancelFax".equals(method)) {
            return CancelFax();
        } else if ("ResendFax".equals(method)) {
            return ResendFax();
        } else if ("viewFax".equals(method)) {
            viewFax();
            // Direct-response paths return NONE so Struts never attempts result
            // resolution after the response has been written (direct-response contract).
            return NONE;
        } else if ("fetchFaxStatus".equals(method)) {
            return fetchFaxStatus();
        } else if ("SetCompleted".equals(method)) {
            SetCompleted();
            return NONE;
        }

        // Delegate to parent for getPageCount, getPreview, etc.
        return super.execute();

    }


    /**
     * Cancels an outbound fax through the provider-abstracted {@code cancelFax} operation.
     *
     * <p>Behavior change from the legacy implementation: a SENT (in-progress at provider) job
     * with a provider job id is now cancelled AT THE PROVIDER (SRFax {@code Stop_Fax},
     * middleware HTTP PUT) instead of being silently marked cancelled locally, and the
     * provider's own outcome — including "already transmitted, could not cancel" — is
     * reflected back. A WAITING job that never reached the provider is cancelled locally.</p>
     */
    @SuppressWarnings("unused")
    public String CancelFax() {

        requireFaxAdminPrivilege("w");

        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", false);

        String jobIdParam = request.getParameter("jobId");
        Integer faxJobRowId = null;
        if (jobIdParam != null) {
            try {
                faxJobRowId = Integer.valueOf(jobIdParam.trim());
            } catch (NumberFormatException e) {
                // fall through to the bad-request response below
            }
        }
        if (faxJobRowId == null) {
            sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Invalid jobId");
            return NONE;
        }

        FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
        FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
        FaxJob faxJob = faxJobDao.find(faxJobRowId);
        if (faxJob == null) {
            sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Unknown fax job");
            return NONE;
        }

        FaxConfig faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());
        log.info("Cancel requested for fax row id {} (provider job id {})", faxJob.getId(), faxJob.getJobId());

        if (faxConfig == null) {
            log.error("Could not find faxConfig while processing fax id: {} Has the fax number changed?", faxJob.getId());
        } else if (faxConfig.isActive()) {

            boolean cancellableStatus = FaxJob.STATUS.WAITING.equals(faxJob.getStatus())
                    || FaxJob.STATUS.SENT.equals(faxJob.getStatus());

            if (faxJob.getJobId() == null && FaxJob.STATUS.WAITING.equals(faxJob.getStatus())) {
                // Never reached the provider: safe to cancel locally.
                faxJob.setStatus(FaxJob.STATUS.CANCELLED);
                faxJob.setStatusString("Cancelled before transmission");
                faxJobDao.merge(faxJob);
                result.put("success", true);
            } else if (faxJob.getJobId() != null && cancellableStatus) {
                try {
                    FaxProviderClient providerClient = faxProviderClientFactory.getClient(faxConfig);
                    FaxJob cancelled = providerClient.cancelFax(faxConfig, faxJob);
                    faxJob.setStatus(cancelled.getStatus());
                    if (cancelled.getStatusString() != null) {
                        faxJob.setStatusString(cancelled.getStatusString());
                    }
                    faxJobDao.merge(faxJob);
                    result.put("success", FaxJob.STATUS.CANCELLED.equals(faxJob.getStatus()));
                    if (faxJob.getStatusString() != null) {
                        result.put("message", faxJob.getStatusString());
                    }
                } catch (FaxProviderException e) {
                    // Provider exception messages never carry credentials (provider-client contract).
                    log.error("Provider cancel failed for fax row id {}", faxJob.getId(), e);
                    result.put("message", e.getMessage() == null ? "Cancel failed" : e.getMessage());
                }
            } else {
                log.info("Fax row id {} not in a cancellable state ({})", faxJob.getId(), faxJob.getStatus());
            }
        }

        JSONUtil.jsonResponse(response, result);

        return NONE;

    }

    /**
     * Requires fax queue admin rights, accepting either {@code _admin.fax} or the broader
     * {@code _admin} — mirroring the {@code ViewManageFaxes} gate and manageFaxes.jsp, so a
     * user who can open the Manage Faxes page can also drive its endpoints.
     *
     * @param rights privilege letter required ("r" or "w")
     * @throws SecurityException when the session holds neither security object
     */
    private void requireFaxAdminPrivilege(String rights) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.fax", rights, null)
                && !securityInfoManager.hasPrivilege(loggedInInfo, "_admin", rights, null)) {
            throw new SecurityException("missing required sec object (_admin.fax)");
        }
    }

    @SuppressWarnings("unused")
    public String ResendFax() {

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", false);
        String JobId = request.getParameter("jobId");
        String faxNumber = request.getParameter("faxNumber");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        requireFaxAdminPrivilege("w");

        boolean success = false;

        /*
         *  Dont even try to resend a fax if the service is not enabled.
         */
        if (FaxManager.isEnabled()) {
            success = faxManager.resendFax(loggedInInfo, JobId, faxNumber);
        }

        ObjectNode jsonObjectResponse = objectMapper.createObjectNode();
        jsonObjectResponse.put("success", success);

        JSONUtil.jsonResponse(response, jsonObjectResponse);

        return NONE;
    }

    @SuppressWarnings("unused")
    public void viewFax() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        getPreview();
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @SuppressWarnings("unused")
    public String fetchFaxStatus() {

        // Returns fax rows carrying demographic and destination identifiers plus audit log
        // entries — must be gated like the rest of the fax admin surface.
        requireFaxAdminPrivilege("r");

        String statusStr = request.getParameter("status");
        String teamStr = request.getParameter("team");
        String dateBeginStr = request.getParameter("dateBegin");
        String dateEndStr = request.getParameter("dateEnd");
        String provider_no = request.getParameter("oscarUser");
        String demographic_no = request.getParameter("demographic_no");

        // Constant-first comparisons: absent request params are null filters, not NPEs.
        if ("-1".equalsIgnoreCase(provider_no)) {
            provider_no = null;
        }

        if ("-1".equalsIgnoreCase(statusStr)) {
            statusStr = null;
        }

        if ("-1".equalsIgnoreCase(teamStr)) {
            teamStr = null;
        }

        if ("null".equalsIgnoreCase(demographic_no) || "".equals(demographic_no)) {
            demographic_no = null;
        }

        Calendar calendar = GregorianCalendar.getInstance();
        Date dateBegin = null, dateEnd = null;
        String datePattern[] = new String[]{"yyyy-MM-dd"};

        if (dateBeginStr != null && !dateBeginStr.isEmpty()) {
            try {
                dateBegin = DateUtils.parseDate(dateBeginStr, datePattern);
                calendar.setTime(dateBegin);
                calendar.set(Calendar.HOUR, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                dateBegin = calendar.getTime();
            } catch (ParseException e) {
                dateBegin = null;
                MiscUtils.getLogger().error("UNPARSEABLE DATE " + dateBeginStr);
            }
        }
        if (dateEndStr != null && !dateEndStr.isEmpty()) {
            try {
                dateEnd = DateUtils.parseDate(dateEndStr, datePattern);
                calendar.setTime(dateEnd);
                calendar.set(Calendar.HOUR, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.MILLISECOND, 59);
                dateEnd = calendar.getTime();

            } catch (ParseException e) {
                dateEnd = null;
                MiscUtils.getLogger().error("UNPARSEABLE DATE " + dateEndStr);
            }
        }

        FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
        FaxClientLogDao faxClientLogDao = SpringUtils.getBean(FaxClientLogDao.class);

        List<FaxJob> faxJobList = faxJobDao.getFaxStatusByDateDemographicProviderStatusTeam(demographic_no, provider_no, statusStr, teamStr, dateBegin, dateEnd);

        List<Integer> faxIds = new ArrayList<>();
        for (FaxJob faxJob : faxJobList) {
            faxIds.add(faxJob.getId());
        }
        List<FaxClientLog> faxClientLogs = faxClientLogDao.findClientLogbyFaxIds(faxIds);

        request.setAttribute("faxes", faxJobList);
        request.setAttribute("faxClientLogs", faxClientLogs);

        return "faxstatus";
    }

    @SuppressWarnings("unused")
    public void SetCompleted() {

        requireFaxAdminPrivilege("w");


        String id = request.getParameter("jobId");
        FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);

        FaxJob faxJob = id == null ? null : faxJobDao.find(Integer.parseInt(id.trim()));
        if (faxJob == null) {
            sendErrorQuietly(HttpServletResponse.SC_BAD_REQUEST, "Unknown fax job");
            return;
        }
        faxJob.setStatus(FaxJob.STATUS.RESOLVED);
        faxJobDao.merge(faxJob);
    }

}
