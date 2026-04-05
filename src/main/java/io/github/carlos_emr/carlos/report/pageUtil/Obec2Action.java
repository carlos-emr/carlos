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


package io.github.carlos_emr.carlos.report.pageUtil;

import java.io.IOException;
import java.util.Properties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctValidation;
import io.github.carlos_emr.carlos.report.data.ObecData;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class Obec2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

	
	private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);
   
   public String execute()
   throws ServletException, IOException {
	   
	   if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_report", "r", null)) {
	  		  throw new SecurityException("missing required sec object (_report)");
	  	  	}
	   
      Properties proppies = CarlosProperties.getInstance();
      
      ObecData obecData1 = new ObecData();
      DateUtils dateUtils = new DateUtils();
      EctValidation validation = new EctValidation();

      String startDate = this.getXml_vdate();
      if (startDate == null || startDate.isEmpty()) {
         return SUCCESS;
      }
      if (!validation.isDate(startDate)) {
         MiscUtils.getLogger().debug("Invalid date format submitted to OBEC report: {}", startDate);
         return SUCCESS;
      }
      
      int numDays = this.getNumDays();
      int startYear = 0;
      int startMonth = 0;
      int startDay = 0;
      
      int slashIndex1 = startDate.indexOf("-");
      if (slashIndex1>=0){
         startYear = Integer.parseInt(startDate.substring(0, slashIndex1));
         int slashIndex2 = startDate.indexOf("-", slashIndex1+1);
         if (slashIndex2>slashIndex1){
            startMonth = Integer.parseInt(startDate.substring(slashIndex1+1, slashIndex2));
            int length = startDate.length();
            startDay = Integer.parseInt(startDate.substring(slashIndex2+1, length));
         }
      }
      
      
      
      String endDate = dateUtils.NextDay(startDay, startMonth, startYear, numDays);
      
      String obectxt = obecData1.generateOBEC(startDate, endDate, proppies);
      request.setAttribute("obectxt", obectxt);
      
      return SUCCESS;
   }

   private String xml_vdate;
   private int numDays;

   public String getXml_vdate() {
      return xml_vdate;
   }

   @StrutsParameter
   public void setXml_vdate(String id) {
      this.xml_vdate = id;
   }

   public int getNumDays() {
      return numDays;
   }

   @StrutsParameter
   public void setNumDays(int numDays) {
      this.numDays = numDays;
   }
}
