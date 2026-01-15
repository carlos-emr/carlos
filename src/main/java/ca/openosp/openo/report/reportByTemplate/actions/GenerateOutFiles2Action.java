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
//This action generates the CSV and XLS files on request


package ca.openosp.openo.report.reportByTemplate.actions;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.services.security.SecurityManager;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import ca.openosp.openo.utility.MiscUtils;

import com.Ostermiller.util.CSVParser;

/**
 * Created on December 21, 2006, 10:47 AM
 *
 * @author apavel (Paul)
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GenerateOutFiles2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    public String execute() {

        String roleName$ = (String) request.getSession().getAttribute("userrole") + "," + (String) request.getSession().getAttribute("user");
        if (!SecurityManager.hasPrivilege("_admin", roleName$) && !SecurityManager.hasPrivilege("_report", roleName$)) {
            throw new SecurityException("Insufficient Privileges");
        }

        String csv = (String) request.getSession().getAttribute("csv");
        if (csv == null) {
            csv = request.getParameter("csv");
        }
        String action = request.getParameter("getCSV");
        if (action != null) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.csv\"");
            try {
                response.getWriter().write(csv);
            } catch (Exception ioe) {
                MiscUtils.getLogger().error("Error", ioe);
            }
            return null;
        }
        action = request.getParameter("getXLS");
        if (action != null) {
            MiscUtils.getLogger().debug("Generating Spread Sheet file for the 'report by template' module ..");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"oscarReport.xls\"");
            String[][] data = CSVParser.parse(csv);
            HSSFWorkbook wb = new HSSFWorkbook();
            HSSFSheet sheet = wb.createSheet("OSCAR_Report");
            for (int x = 0; x < data.length; x++) {
                HSSFRow row = sheet.createRow((short) x);
                for (int y = 0; y < data[x].length; y++) {
                    try {
                        double d = Double.parseDouble(data[x][y]);
                        row.createCell((short) y).setCellValue(d);
                    } catch (Exception e) {
                        row.createCell((short) y).setCellValue(data[x][y]);
                    }
                }
            }
            try {
                wb.write(response.getOutputStream());
            } catch (Exception e) {
                MiscUtils.getLogger().error("Error", e);
            }
            return null;
        }
        return SUCCESS;
    }

}