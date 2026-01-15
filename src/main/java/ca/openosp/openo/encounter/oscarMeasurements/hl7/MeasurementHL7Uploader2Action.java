//CHECKSTYLE:OFF
/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 */

package ca.openosp.openo.encounter.oscarMeasurements.hl7;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.PublicKey;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.http.HttpStatus;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.commn.dao.MeasurementDao;
import ca.openosp.openo.commn.model.Demographic;
import ca.openosp.openo.commn.model.Measurement;
import ca.openosp.openo.managers.DemographicManager;
import ca.openosp.openo.managers.SecurityInfoManager;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import ca.openosp.OscarProperties;
import ca.openosp.openo.lab.ca.all.pageUtil.LabUpload2Action;
import ca.openosp.openo.lab.ca.all.util.Utilities;
import ca.uhn.hl7v2.model.v23.datatype.CE;
import ca.uhn.hl7v2.model.v23.group.ORU_R01_ORDER_OBSERVATION;
import ca.uhn.hl7v2.model.v23.group.ORU_R01_RESPONSE;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;
import ca.uhn.hl7v2.model.v23.segment.MSH;
import ca.uhn.hl7v2.model.v23.segment.OBX;
import ca.uhn.hl7v2.model.v23.segment.PID;
import ca.uhn.hl7v2.parser.GenericParser;
import ca.uhn.hl7v2.parser.Parser;
import ca.uhn.hl7v2.validation.impl.NoValidation;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class MeasurementHL7Uploader2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private static SimpleDateFormat sdf = new SimpleDateFormat(OscarProperties.getInstance().getProperty("oscar.measurements.hl7.datetime.format", "yyyyMMddHHmmss"));

    private static MeasurementDao measurementsDao = SpringUtils.getBean(MeasurementDao.class);
    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    // settings to be set in spring config xml, if needed
    private String defaultProviderNo = OscarProperties.getInstance().getProperty("oscar.measurements.hl7.defaultProviderNo", "999998");
    private String hl7UploadPassword = OscarProperties.getInstance().getProperty("oscar.measurements.hl7.password");

    public void setHl7UploadPassword(String uploadPassword) {
        this.hl7UploadPassword = uploadPassword;
    }

    public void setDefaultProviderNo(String defaultProviderNo) {
        this.defaultProviderNo = defaultProviderNo;
    }

    public String execute() {
        return upload();
    }

    public String upload() {

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_measurement", "w", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        Date dateEntered = new Date();
        String hl7msg = null;

        try {
            boolean checkPassword = StringUtils.isNotBlank(hl7UploadPassword);

            // file is encrypted using RSA public keys if no password enforced
            hl7msg = checkPassword ? IOUtils.toString(Files.newInputStream(importFile.toPath())) : extractEncryptedMessage(importFile, request);

            if (checkPassword && hl7UploadPassword.length() < 16)
                throw new RuntimeException("Upload password length is too weak, please check oscar property file and make sure it's more than 16 letters.");

            Parser p = new GenericParser();
            p.setValidationContext(new NoValidation());
            ORU_R01 msg = (ORU_R01) p.parse(hl7msg);

            MSH msh = msg.getMSH();
            String msgType = msh.getMessageType().getMessageType().getValue() + "_" + msh.getMessageType().getTriggerEvent().getValue();

            if (!"ORU_R01".equals(msgType)) throw new RuntimeException("Message type is not ORU_R01 - " + msgType);

            if (checkPassword && !hl7UploadPassword.equals(msh.getSecurity().getValue()))
                throw new RuntimeException("Password in MSH is invalid.");

            String sender = msh.getSendingApplication().getNamespaceID().getValue();
            String receiver = msh.getReceivingApplication().getNamespaceID().getValue();

            String msgId = msh.getMessageControlID().getValue();
            logger.info("HL7 message [" + msgId + "] received from: " + sender + " to: " + receiver + " on " + dateEntered);

            // TODO: handle multiple responses in one upload, right now only
            // assumes 1 per upload
            ORU_R01_RESPONSE resp = msg.getRESPONSE();

            PID patient = resp.getPATIENT().getPID();

            String hcn = patient.getPatientIDInternalID(0).getID().getValue();
            String hcnType = patient.getPatientIDInternalID(0).getAssigningAuthority().getNamespaceID().getValue();
            // get demographic no from hcn
            DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
            List<Demographic> demos = demographicManager.getActiveDemosByHealthCardNo(loggedInInfo, hcn, hcnType);
            if (demos == null || demos.size() == 0)
                throw new RuntimeException("There is no active patient with the supplied health card number: " + hcn + " " + hcnType);

            // try to get consult doctor's providerID
            String providerNo = resp.getPATIENT().getVISIT().getPV1().getConsultingDoctor(0).getIDNumber().getValue();
            if (providerNo == null) providerNo = defaultProviderNo;

            // now handle OBR
            ORU_R01_ORDER_OBSERVATION obr = resp.getORDER_OBSERVATION();

            CE univId = obr.getOBR().getUniversalServiceIdentifier();

            // now get all observation data
            int len = obr.getOBSERVATIONReps();
            for (int i = 0; i < len; i++) {
                logger.info("Processing OBX no." + i);
                OBX obx = obr.getOBSERVATION(i).getOBX();
                Date dateObserved = sdf.parse(obx.getDateTimeOfTheObservation().getTimeOfAnEvent().getValue());
                CE obvId = obx.getObservationIdentifier();
                // the 1st part of obvId is the unique short name
                String measurementType = obvId.getIdentifier().getValue();

                String unit = obx.getUnits().getIdentifier().getValue();
                if (unit == null) // oscar does not allow unit to be null
                    unit = "";
                String range = obx.getReferencesRange().getValue();
                if (range != null) unit += " Range:" + range;

                String data = obx.getObservationValue(0).getData().toString();

                String abnormal = StringUtils.join(obx.getAbnormalFlags(), "|");
                if (StringUtils.isNotEmpty(abnormal)) abnormal = " Abnormal:" + abnormal;

                logger.info(measurementType + " : " + data + " : " + unit + " : " + dateObserved + " : " + abnormal);

                // since oscar may have duplicate patient records, just add to
                // all of them.
                for (Demographic demo : demos) {
                    Integer demographicNo = demo.getDemographicNo();

                    // add to oscar measurements table
                    Measurement m = new Measurement();
                    m.setComments(abnormal + " by " + sender);
                    m.setDataField(data);
                    m.setDateObserved(dateObserved);
                    m.setDemographicId(demographicNo);
                    m.setMeasuringInstruction(unit);
                    m.setProviderNo(providerNo);
                    m.setType(measurementType);

                    measurementsDao.persist(m);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to parse HL7 ORU_R01 messages:\n" + hl7msg, e);
            response.setStatus(HttpStatus.SC_BAD_REQUEST);
            try {
                response.getWriter().println("Invalid HL7 ORU_R01 format/request");
            } catch (IOException e1) {
                logger.error("Failed to write to response", e1);
            }
            return null;
        }

        response.setStatus(HttpStatus.SC_OK);
        return null;
    }

    /**
     * Extract encrypted data from http request. Assuming message file is attached as multipart request, and <LI>signature - signed by MD5WithRSA <LI>key - symetric key encrypted by client's private key <LI>service - the ID of client's public key in
     * oscar's 'publicKeys' table All keys are base64 encoded.
     *
     * @param form
     * @param request
     * @return
     */
    private String extractEncryptedMessage(File importFile, HttpServletRequest request) {

        String signature = request.getParameter("signature");
        String key = request.getParameter("key");
        String service = request.getParameter("service");

        ArrayList<Object> clientInfo = LabUpload2Action.getClientInfo(service);
        PublicKey clientKey = (PublicKey) clientInfo.get(0);
        String type = (String) clientInfo.get(1);

        try {

            InputStream is = LabUpload2Action.decryptMessage(Files.newInputStream(importFile.toPath()), key, clientKey);
            String fileName = importFile.getName();
            String filePath = Utilities.saveFile(is, fileName);

            File file = new File(filePath);

            if (LabUpload2Action.validateSignature(clientKey, signature, file)) {
                return FileUtils.readFileToString(file);
            } else throw new RuntimeException("Invalid signature, upload rejected.");

        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt upload file stream", e);
        }
    }

    private File importFile;

    public File getImportFile() {
        return importFile;
    }

    public void setImportFile(File importFile) {
        this.importFile = importFile;
    }
}
