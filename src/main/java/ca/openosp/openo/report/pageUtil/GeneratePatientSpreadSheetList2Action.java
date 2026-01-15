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


package ca.openosp.openo.report.pageUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.demographic.data.DemographicData;
import ca.openosp.openo.util.UtilDateUtilities;

import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;

/**
 * @author jay
 */
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class GeneratePatientSpreadSheetList2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() {

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
            throw new SecurityException("missing required sec object (_report)");
        }

        String[] demos = request.getParameterValues("demo");

        MiscUtils.getLogger().debug("Generating Spread Sheet file ..");
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=\"patientlist_spreadsheet-" + UtilDateUtilities.getToday("yyyy-mm-dd.hh.mm.ss") + ".xls\"");


        HSSFWorkbook wb = new HSSFWorkbook();
        HSSFSheet sheet = wb.createSheet("patient list");

        for (int i = 0; i < demos.length; i++) {
            DemographicData demoData = new DemographicData();
            Demographic d = demoData.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), demos[i]);


            // Create a row and put some cells in it. Rows are 0 based.
            HSSFRow row = sheet.createRow((short) i);

            row.createCell((short) 0).setCellValue(d.getFirstName());
            row.createCell((short) 1).setCellValue(d.getLastName());
            row.createCell((short) 2).setCellValue(d.getAddress());
            row.createCell((short) 3).setCellValue(d.getCity());
            row.createCell((short) 3).setCellValue(d.getProvince());
            row.createCell((short) 3).setCellValue(d.getPostal());

        }
        try {
            wb.write(response.getOutputStream());
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }
        return null;
    }

    Paragraph getEnvelopeLabel(String text) {
        Paragraph p = new Paragraph(text, FontFactory.getFont(FontFactory.HELVETICA, 18));
        p.setLeading(22);
        return p;
    }
}
