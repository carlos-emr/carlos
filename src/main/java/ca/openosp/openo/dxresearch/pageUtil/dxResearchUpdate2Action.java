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

package ca.openosp.openo.dxresearch.pageUtil;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import ca.openosp.openo.commn.dao.DxresearchDAO;
import ca.openosp.openo.commn.dao.PartialDateDao;
import ca.openosp.openo.commn.model.Dxresearch;
import ca.openosp.openo.commn.model.PartialDate;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;
import ca.openosp.openo.log.LogAction;
import ca.openosp.openo.log.LogConst;
import ca.openosp.openo.util.ConversionUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class dxResearchUpdate2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static final PartialDateDao partialDateDao = (PartialDateDao) SpringUtils.getBean(PartialDateDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws ServletException, IOException {
        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_dxresearch", "u", null)) {
            throw new RuntimeException("missing required sec object (_dxresearch)");
        }

        String status = request.getParameter("status");
        String did = request.getParameter("did");
        String demographicNo = request.getParameter("demographicNo");
        String providerNo = request.getParameter("providerNo");
        String startDate = request.getParameter("startdate");


        partialDateDao.setPartialDate(startDate, PartialDate.DXRESEARCH, Integer.valueOf(did), PartialDate.DXRESEARCH_STARTDATE);
        startDate = partialDateDao.getFullDate(startDate);


        DxresearchDAO dao = SpringUtils.getBean(DxresearchDAO.class);
        Dxresearch research = dao.find(ConversionUtils.fromIntString(did));
        if (research != null) {
            if (status != null && (status.equals("C") || status.equals("D"))) {
                research.setStatus(status.charAt(0));
            }
            if (startDate != null) {
                if (research.getStatus() == 'A') {
                    try {
                        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
                        research.setStartDate(fmt.parse(startDate));
                    } catch (ParseException e) {

                    }
                }
            }
            research.setUpdateDate(new Date());

            dao.merge(research);
        }

        StringBuffer forward = new StringBuffer(request.getContextPath() + "/oscarResearch/dxresearch/setupDxResearch.do");
        forward.append("?demographicNo=").append(demographicNo);
        forward.append("&providerNo=").append(providerNo);
        forward.append("&quickList=");

        String ip = request.getRemoteAddr();
        LogAction.addLog((String) request.getSession().getAttribute("user"), LogConst.UPDATE, "DX", "" + research.getId(), ip, "");


        response.sendRedirect(forward.toString());
        return NONE;
    }

}
