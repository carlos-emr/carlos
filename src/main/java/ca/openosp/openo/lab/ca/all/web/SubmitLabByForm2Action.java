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


package ca.openosp.openo.lab.ca.all.web;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.model.Lab;
import ca.openosp.openo.commn.model.LabTest;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.MiscUtils;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.openo.lab.FileUploadCheck;
import ca.openosp.openo.lab.ca.all.upload.HandlerClassFactory;
import ca.openosp.openo.lab.ca.all.upload.handlers.MessageHandler;
import ca.openosp.openo.lab.ca.all.util.CMLLabHL7Generator;
import ca.openosp.openo.lab.ca.all.util.GDMLLabHL7Generator;
import ca.openosp.openo.lab.ca.all.util.MDSLabHL7Generator;
import ca.openosp.openo.lab.ca.all.util.Utilities;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class SubmitLabByForm2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();


    Logger logger = MiscUtils.getLogger();
    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public String execute() throws Exception {
        if ("saveManage".equals(request.getParameter("method"))) {
            return saveManage();
        }
        return manage();
    }

    public String manage() {
        return "manage";
    }

    /**
     * Process a lab form submission: validate privileges, construct a Lab with its LabTest entries,
     * generate an HL7 message, save and register the HL7 file, and invoke the configured message handler.
     *
     * @return the Struts result name "manage"
     * @throws SecurityException if the current user lacks the required "_lab" write privilege
     * @throws Exception for parse, I/O, or handler invocation errors that are propagated to the caller
     */
    public String saveManage() throws Exception {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        if (!securityInfoManager.hasPrivilege(LoggedInInfo.getLoggedInInfoFromSession(request), "_lab", "w", null)) {
            throw new SecurityException("missing required sec object (_lab)");
        }

        logger.info("in save lab from form");
        String labName = request.getParameter("labname");
        String accession = request.getParameter("accession");
        String labReqDate = request.getParameter("lab_req_date");

        String lastName = request.getParameter("lastname");
        String firstName = request.getParameter("firstname");
        String hin = request.getParameter("hin");
        String sex = request.getParameter("sex");
        String dob = request.getParameter("dob");
        String phone = request.getParameter("phone");

        String billingNo = request.getParameter("billingNo");
        String pLastName = request.getParameter("pLastname");
        String pFirstName = request.getParameter("pFirstname");
        String cc = request.getParameter("cc");

        String ipAddr = request.getRemoteAddr();
        SimpleDateFormat dateTimeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");


        Lab lab = new Lab();
        lab.setLabName(labName);
        lab.setAccession(accession);
        lab.setLabReqDate(dateTimeFormatter.parse(labReqDate));
        lab.setLastName(lastName);
        lab.setFirstName(firstName);
        lab.setHin(hin);
        lab.setSex(sex);
        lab.setDob(dateFormatter.parse(dob));
        lab.setPhone(phone);
        lab.setBillingNo(billingNo);
        lab.setProviderLastName(pLastName);
        lab.setProviderFirstName(pFirstName);
        lab.setCc(cc);

        int maxTest = Integer.parseInt(request.getParameter("test_num"));
        for (int x = 1; x <= maxTest; x++) {
            String id = request.getParameter("test_" + x + ".id");
            if (id != null) {
                logger.info("test #" + x);
                String otherId = request.getParameter("test_" + x + ".id");
                if (otherId.length() == 0 || otherId.equals("0")) {
                    continue;
                }

                String testDate = request.getParameter("test_" + x + ".valDate");
                String testName = request.getParameter("test_" + x + ".lab_test_name");
                String testDescr = request.getParameter("test_" + x + ".test_descr");
                String codeType = request.getParameter("test_" + x + ".codeType");
                String code = request.getParameter("test_" + x + ".code");
                String codeVal = request.getParameter("test_" + x + ".codeVal");
                String codeUnit = request.getParameter("test_" + x + ".codeUnit");
                String refRangeLow = request.getParameter("test_" + x + ".refRangeLow");
                String refRangeHigh = request.getParameter("test_" + x + ".refRangeHigh");
                String refRangeText = request.getParameter("test_" + x + ".refRangeText");
                String flag = request.getParameter("test_" + x + ".flag");
                String stat = request.getParameter("test_" + x + ".stat");
                String blocked = request.getParameter("test_" + x + ".blocked");
                String labNotes = request.getParameter("test_" + x + ".labnotes");
                LabTest test = new LabTest();
                test.setDate(dateTimeFormatter.parse(testDate));
                test.setName(testName);
                test.setDescription(testDescr);
                test.setCodeType(codeType);
                test.setCode(code);
                test.setCodeValue(codeVal);
                test.setCodeUnit(codeUnit);
                test.setRefRangeLow(refRangeLow);
                test.setRefRangeHigh(refRangeHigh);
                test.setRefRangeText(refRangeText);
                test.setFlag(flag);
                test.setStat(stat);
                test.setBlocked(blocked);
                test.setNotes(labNotes);
                lab.getTests().add(test);
            }
        }


        //generate the HL7 from the Lab object.
        String hl7 = generateHL7(lab);
        logger.info(hl7);

        //save file
        String filename = "Lab" + providerNo + ((int) (Math.random() * 1000)) + ".hl7";
        ByteArrayInputStream is = new ByteArrayInputStream(hl7.getBytes());
        String filePath = Utilities.saveFile(is, filename);
        is.close();
        File file = new File(filePath);

        FileInputStream fis = new FileInputStream(filePath);
        int checkFileUploadedSuccessfully = FileUploadCheck.addFile(file.getName(), fis, providerNo);
        fis.close();

        String outcome = null;

        if (checkFileUploadedSuccessfully != FileUploadCheck.UNSUCCESSFUL_SAVE) {
            logger.info("filePath" + filePath);
            logger.info("Type :" + labName);
            MessageHandler msgHandler = HandlerClassFactory.getHandler(labName);
            if (msgHandler != null) {
                logger.info("MESSAGE HANDLER " + msgHandler.getClass().getName());
            }
            if ((msgHandler.parse(loggedInInfo, getClass().getSimpleName(), filePath, checkFileUploadedSuccessfully, ipAddr)) != null)
                outcome = "success";

        } else {
            outcome = "uploaded previously";
        }

        logger.info("outcome=" + outcome);


        return manage();
    }

	/**
	 * Generate an HL7 message for the provided lab using a lab-specific generator.
	 *
	 * Uses the lab's labName to select a generator; if the lab type is unsupported the CML generator is used.
	 *
	 * @param lab the Lab model containing patient and test data to include in the message
	 * @return the generated HL7 message as a String
	 */
	private String generateHL7(Lab lab) {
		// Generate appropriate HL7 format based on lab type
		String labType = lab.getLabName();
		labType = labType == null ? "" : labType.trim().toUpperCase();
		logger.info("Generating HL7 for lab type: [" + labType + "]");

		switch (labType) {
			case "MDS":
				return MDSLabHL7Generator.generate(lab);
			case "GDML":
				return GDMLLabHL7Generator.generate(lab);
			case "CML":
				return CMLLabHL7Generator.generate(lab);
			default:
				logger.error("Unsupported lab type: [" + labType + "]; defaulting to CML.");
				return CMLLabHL7Generator.generate(lab);
		}
	}
}