/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
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
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.managers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import org.apache.logging.log4j.Logger;
import org.owasp.encoder.Encode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.itextpdf.text.DocumentException;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.util.StringUtils;


@Service
public class LabManagerImpl implements LabManager {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String TEMP_PDF_DIRECTORY = "hl7PDF";
    private static final String DEFAULT_FILE_SUFFIX = ".pdf";

    @Autowired
    Hl7TextInfoDao hl7textInfoDao;

    @Autowired
    Hl7TextMessageDao hl7TextMessageDao;

    @Autowired
    private ProviderLabRoutingDao providerLabRoutingDao;

    @Autowired
    private NioFileManager nioFileManager;

    @Autowired
    private PatientLabRoutingDao patientLabRoutingDao;

    @Autowired
    SecurityInfoManager securityInfoManager;

    @Autowired
    ProviderManager2 providerManager2;

    public List<Hl7TextMessage> getHl7Messages(LoggedInInfo loggedInInfo, Integer demographicNo, int offset, int limit) {
        checkPrivilege(loggedInInfo, "r");

        LogAction.addLogSynchronous(loggedInInfo, "LabManager.getHl7Messages", "demographicNo=" + demographicNo);

        List<Hl7TextMessage> results = hl7TextMessageDao.findByDemographicNo(demographicNo, offset, limit);

        return results;
    }

    public List<Hl7TextInfo> getHl7TextInfo(LoggedInInfo loggedInInfo, int demographicNo) {
        checkPrivilege(loggedInInfo, "r");

        List<PatientLabRouting> patientLabRoutingList = patientLabRoutingDao.findByDemographicAndLabType(demographicNo, PatientLabRoutingDao.HL7);
        List<Integer> labIds = new ArrayList<Integer>();
        if (patientLabRoutingList != null) {
            for (PatientLabRouting patientLabRouting : patientLabRoutingList) {
                labIds.add(patientLabRouting.getLabNo());
            }
        }

        LogAction.addLogSynchronous(loggedInInfo, "LabManager.getHl7TextInfo", "demographicNo=" + demographicNo);

        return hl7textInfoDao.findByLabIdList(labIds);

    }

    public Hl7TextMessage getHl7Message(LoggedInInfo loggedInInfo, int labId) {
        checkPrivilege(loggedInInfo, "r");

        LogAction.addLogSynchronous(loggedInInfo, "LabManager.getHl7Message", "labId=" + labId);

        Hl7TextMessage result = hl7TextMessageDao.find(labId);

        return result;
    }

    public Path renderLab(LoggedInInfo loggedInInfo, Integer segmentId) throws PDFGenerationException {
        checkPrivilege(loggedInInfo, "r");
        LogAction.addLogSynchronous(loggedInInfo, "LabManager.getHl7MessageAsPDF", "labId=" + segmentId);

        Path path = null;
        try {
            String fileName = System.currentTimeMillis() + "_" + segmentId + "_LabReport";
            File tempPDF = File.createTempFile(fileName, "pdf");
            try (FileOutputStream fileOutputStream = new FileOutputStream(tempPDF);
                 ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();) {
                LabPDFCreator labPDFCreator = new LabPDFCreator(fileOutputStream, String.valueOf(segmentId), null);
                labPDFCreator.printPdf();
                labPDFCreator.addEmbeddedDocuments(tempPDF, byteOutputStream);
                path = nioFileManager.saveTempFile("temporaryPDF" + new Date().getTime(), byteOutputStream);
            }
            tempPDF.delete();
        } catch (IOException | DocumentException e) {
            throw new PDFGenerationException("Error Details: Lab [" + getDisplayLabName(segmentId) + "] could not be converted into a PDF", e);
        }

        return path;
    }

    /**
     * Returns all {@link ProviderLabRoutingModel} records that match the given lab number,
     * lab type, and provider number.
     *
     * @param loggedInInfo LoggedInInfo the currently logged-in user; used to enforce {@code _lab} read privilege
     * @param labId Integer the unique lab segment ID to look up
     * @param labType String the lab type (e.g. {@code "HL7"}, {@code "MDS"})
     * @param providerNo String the provider number to filter routing records by
     * @return List&lt;ProviderLabRoutingModel&gt; matching routing records; empty list if none exist
     * @throws RuntimeException if the logged-in user lacks {@code _lab} read privilege
     */
    @Override
    public List<ProviderLabRoutingModel> findByLabNoAndLabTypeAndProviderNo(LoggedInInfo loggedInInfo, Integer labId, String labType, String providerNo) {
        checkPrivilege(loggedInInfo, "r");
        return providerLabRoutingDao.findByLabNoAndLabTypeAndProviderNo(labId, labType, providerNo);
    }

    /**
     * Files lab results for a provider up to (and including) a specific flagged lab,
     * depending on the fileUpToLabNo flag. Skips acknowledged or already filed results.
     *
     * This method is specifically designed to support filing labs on behalf of another provider,
     * so the logic and conditions (such as checking for lab status 'N' when filing on behalf)
     * are tailored for that use case.
     *
     * @param loggedInInfo the currently logged-in user
     * @param providerNo the provider number
     * @param flaggedLabId the lab ID that was flagged (i.e., selected by the user)
     * @param labType the type of the lab
     * @param comment the comment to add while filing
     * @param fileUpToLabNo if true, file all labs up to and including flaggedLabId
     * @param onBehalfOfOtherProvider if true, updates lab status only if it is 'N' (Not Acknowledged)
     */
    @Override
    public void fileLabsForProviderUpToFlaggedLab(LoggedInInfo loggedInInfo, String providerNo, String flaggedLabId, String labType, String comment, boolean fileUpToLabNo, boolean onBehalfOfOtherProvider) {
        checkPrivilege(loggedInInfo, "w");

        int parsedFlaggedLabId;
        try {
            parsedFlaggedLabId = Integer.parseInt(flaggedLabId.trim());
        } catch (NumberFormatException e) {
            logger.error("fileLabsForProviderUpToFlaggedLab: invalid flaggedLabId='" + Encode.forJava(flaggedLabId) + "'");
            return;
        }

        CommonLabResultData commonLabResultData = new CommonLabResultData();

        // Gets lab IDs in order from oldest to latest (e.g., v1, v2, ..., vn)
        String labs = commonLabResultData.getMatchingLabs(flaggedLabId, labType);

        if (labs == null || labs.trim().isEmpty()) {
            logger.warn("fileLabsForProviderUpToFlaggedLab: no matching labs for flaggedLabId=" + Encode.forJava(flaggedLabId) + ", labType=" + Encode.forJava(labType));
            return;
        }

        // The UI disables the checkbox, but this guards against crafted requests.
        if (onBehalfOfOtherProvider && !providerManager2.isHl7AllowOthersFileForYou(loggedInInfo, providerNo)) {
            throw new SecurityException("Provider " + providerNo + " has not allowed others to file on their behalf");
        }

        // Filter labs: if fileUpToLabNo is true, include only those <= flaggedLabId
        List<Integer> filteredLabs = Arrays.stream(labs.split(","))
            .map(String::trim)
            .map(Integer::parseInt)
            .filter(labId -> !fileUpToLabNo || labId <= parsedFlaggedLabId)
            .collect(Collectors.toList());

        for (Integer labId : filteredLabs) {
            // Get routing info for the lab and provider
            List<ProviderLabRoutingModel> providerLabRoutings = findByLabNoAndLabTypeAndProviderNo(loggedInInfo, labId, labType, providerNo);
            if (providerLabRoutings.isEmpty()) continue;

            ProviderLabRoutingModel providerLabRouting = providerLabRoutings.get(0);

            // Determine whether to skip updating comment based on existing content
            boolean skipCommentOnUpdate = true;
            if (providerLabRouting.getComment() == null || providerLabRouting.getComment().trim().isEmpty()) {
                skipCommentOnUpdate = false;
            }

            // Skip if lab is already Acknowledged or Filed
            String status = providerLabRouting.getStatus();
            if (onBehalfOfOtherProvider && ProviderLabRoutingDao.STATUS.A.name().equals(status) || ProviderLabRoutingDao.STATUS.F.name().equals(status)) {
                continue;
            }

            // Update report status and remove it from the queue
            CommonLabResultData.updateReportStatus(labId, providerNo, ProviderLabRoutingDao.STATUS.F.name().charAt(0), comment, labType, skipCommentOnUpdate);
            CommonLabResultData.removeFromQueue(labId);
            LogAction.addLogSynchronous(loggedInInfo, "LabManager.fileLabsForProviderUpToFlaggedLab",
                "labId=" + labId + ", filedForProviderNo=" + providerNo + ", onBehalf=" + onBehalfOfOtherProvider);
        }
    }

    private void checkPrivilege(LoggedInInfo loggedInInfo, String privilege) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_lab", privilege, null)) {
            throw new RuntimeException("missing required sec object (_lab)");
        }
    }

    private String getDisplayLabName(Integer segmentId) {
        Hl7TextInfo hl7TextInfo = hl7textInfoDao.findLabId(segmentId);
        return StringUtils.isNullOrEmpty(hl7TextInfo.getDiscipline()) ? "UNLABELLED" : hl7TextInfo.getDiscipline();
    }
}
