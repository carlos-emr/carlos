//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.fax.admin;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpStatus;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.FaxClientLogDao;
import ca.openosp.openo.commn.dao.FaxConfigDao;
import ca.openosp.openo.commn.dao.FaxJobDao;
import ca.openosp.openo.commn.model.FaxClientLog;
import ca.openosp.openo.commn.model.FaxConfig;
import ca.openosp.openo.commn.model.FaxJob;
import ca.openosp.openo.managers.FaxManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.form.JSONUtil;
import ca.openosp.OscarProperties;
import ca.openosp.openo.fax.action.Fax2Action;

public class ManageFaxes2Action extends Fax2Action {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private final Logger log = MiscUtils.getLogger();
    private final SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    private final FaxManager faxManager = SpringUtils.getBean(FaxManager.class);

    @Override
    public String execute() {
        String method = request.getParameter("method");
        if ("CancelFax".equals(method)) {
            return CancelFax();
        } else if ("ResendFax".equals(method)) {
            return ResendFax();
        } else if ("viewFax".equals(method)) {
            viewFax();
            return null;
        } else if ("fetchFaxStatus".equals(method)) {
            return fetchFaxStatus();
        } else if ("SetCompleted".equals(method)) {
            SetCompleted();
            return null;
        }

        // Delegate to parent for getPageCount, getPreview, etc.
        return super.execute();

    }


    @SuppressWarnings("unused")
    public String CancelFax() {

        String jobId = request.getParameter("jobId");

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);
        FaxConfigDao faxConfigDao = SpringUtils.getBean(FaxConfigDao.class);
        FaxJob faxJob = faxJobDao.find(Integer.parseInt(jobId));
        FaxConfig faxConfig = faxConfigDao.getConfigByNumber(faxJob.getFax_line());
        ObjectNode result = objectMapper.createObjectNode();
        result.put("success", false);

        log.info("TRYING TO CANCEL FAXJOB " + faxJob.getJobId());

        if (faxConfig == null) {
            //log.error("Could not find faxConfig while processing fax id: " + faxJob.getId() + " Has the fax number changed?");
        } else if (faxConfig.isActive()) {

            if (faxJob.getStatus().equals(FaxJob.STATUS.SENT)) {
                faxJob.setStatus(FaxJob.STATUS.CANCELLED);
                faxJobDao.merge(faxJob);
                result = objectMapper.createObjectNode();
                result.put("success", true);

            }

            if (faxJob.getJobId() != null) {

                if (faxJob.getStatus().equals(FaxJob.STATUS.WAITING)) {
                    try (DefaultHttpClient client = new DefaultHttpClient()) {
                        client.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(faxConfig.getSiteUser(), faxConfig.getPasswd()));

                        HttpPut mPut = new HttpPut(faxConfig.getUrl() + "/fax/" + faxJob.getJobId());
                        mPut.setHeader("accept", "application/json");
                        mPut.setHeader("user", faxConfig.getFaxUser());
                        mPut.setHeader("passwd", faxConfig.getFaxPasswd());

                        HttpResponse httpResponse = client.execute(mPut);

                        if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

                            HttpEntity httpEntity = httpResponse.getEntity();
                            result = objectMapper.createObjectNode();
                            result = (ObjectNode) objectMapper.readTree(EntityUtils.toString(httpEntity));

                            faxJob.setStatus(FaxJob.STATUS.CANCELLED);
                            faxJobDao.merge(faxJob);
                        }

                    } catch (IOException e) {
                        log.error("PROBLEM COMM WITH WEB SERVICE");
                    }
                }
            }
        }

        JSONUtil.jsonResponse(response, result);

        return null;

    }

    @SuppressWarnings("unused")
    public String ResendFax() {

        ObjectNode jsonObject = objectMapper.createObjectNode();
        jsonObject.put("success", false);
        String JobId = request.getParameter("jobId");
        String faxNumber = request.getParameter("faxNumber");
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

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

        return null;
    }

    @SuppressWarnings("unused")
    public void viewFax() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "r", null)) {
            throw new SecurityException("missing required sec object (_edoc)");
        }

        getPreview();
    }

    @SuppressWarnings("unused")
    public String fetchFaxStatus() {

        String statusStr = request.getParameter("status");
        String teamStr = request.getParameter("team");
        String dateBeginStr = request.getParameter("dateBegin");
        String dateEndStr = request.getParameter("dateEnd");
        String provider_no = request.getParameter("oscarUser");
        String demographic_no = request.getParameter("demographic_no");

        if (provider_no.equalsIgnoreCase("-1")) {
            provider_no = null;
        }

        if (statusStr.equalsIgnoreCase("-1")) {
            statusStr = null;
        }

        if (teamStr.equalsIgnoreCase("-1")) {
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

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_admin", "w", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }


        String id = request.getParameter("jobId");
        FaxJobDao faxJobDao = SpringUtils.getBean(FaxJobDao.class);

        FaxJob faxJob = faxJobDao.find(Integer.parseInt(id));
        faxJob.setStatus(FaxJob.STATUS.RESOLVED);
        faxJobDao.merge(faxJob);
    }

}
