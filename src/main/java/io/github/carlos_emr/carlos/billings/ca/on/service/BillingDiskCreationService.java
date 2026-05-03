/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
// NOTE: this service is read+write — addBillingDiskName/addRepoDiskName/
// updateDiskName (lines ~135/166/177/187) write through the persister. Class-
// level MUST NOT be readOnly=true; Hibernate would skip the flush on those
// writes (or throw on commit). Same fix as BillingOnRaService.
/**
 * Builds the per-provider OHIP submission "disk" that backs an MOH file
 * upload — assembles {@code BillingONDiskName} headers and per-provider
 * {@code BillingONFilename} rows, looks up the active provider set (solo
 * vs group), and resolves the OHIP / HTML filename pair for an existing
 * disk id.
 *
 * <p>This service straddles read+write: read paths consult
 * {@link BillingOnDiskLoader}, writes go through
 * {@link BillingOnClaimPersister}. If it grows further, the write side is
 * a candidate for a dedicated {@code BillingOnDiskPersister}.</p>
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@Transactional
public class BillingDiskCreationService {

    private static final int DISK_NAME_INSERT_RETRIES = 3;
    private static final Logger _logger = MiscUtils.getLogger();
    private final BillingOnClaimPersister claimPersister;
    private final BillingOnDiskLoader diskQuery;
    private final BillingOnLookupService lookupService;
    private final TransactionOperations diskNameAllocationTx;

    BillingDiskCreationService(BillingOnClaimPersister claimPersister,
                          BillingOnDiskLoader diskQuery,
                          BillingOnLookupService lookupService) {
        this(claimPersister, diskQuery, lookupService, directTransactionOperations());
    }

    BillingDiskCreationService(BillingOnClaimPersister claimPersister,
                          BillingOnDiskLoader diskQuery,
                          BillingOnLookupService lookupService,
                          TransactionOperations diskNameAllocationTx) {
        this.claimPersister = claimPersister;
        this.diskQuery = diskQuery;
        this.lookupService = lookupService;
        this.diskNameAllocationTx = diskNameAllocationTx;
    }

    @org.springframework.beans.factory.annotation.Autowired
    BillingDiskCreationService(BillingOnClaimPersister claimPersister,
                          BillingOnDiskLoader diskQuery,
                          BillingOnLookupService lookupService,
                          PlatformTransactionManager txManager) {
        this(claimPersister, diskQuery, lookupService, requiresNewTransactions(txManager));
    }

    private static TransactionOperations requiresNewTransactions(PlatformTransactionManager txManager) {
        TransactionTemplate txTemplate = new TransactionTemplate(txManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return txTemplate;
    }

    private static TransactionOperations directTransactionOperations() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }

    public Properties getPropProviderOHIP() {
        return lookupService.getPropProviderOHIP();
    }

    public List getCurSoloProvider() {
        return lookupService.getCurSoloProvider();
    }

    public List getCurGrpProvider() {
        return lookupService.getCurGrpProvider();
    }

    public List getProvider(String diskId) {
        return lookupService.getProvider(diskId);
    }

    public BillingProviderDto getProviderObj(String providerNo) {
        return lookupService.getProviderObj(providerNo);
    }

    public String getOhipfilename(int diskId) {
        String ret = null;
        ret = diskQuery.getOhipfilename(diskId);
        return ret;
    }

    public String getHtmlfilename(int diskId, String providerNo) {
        String ret = null;
        ret = diskQuery.getHtmlfilename(diskId, providerNo);
        return ret;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int createNewSoloDiskName(String providerNo, String creator) {
        String ohipNo = lookupService.getPropProviderOHIP().getProperty(providerNo);
        for (int attempt = 1; attempt <= DISK_NAME_INSERT_RETRIES; attempt++) {
            try {
                return diskNameAllocationTx.execute(status ->
                        claimPersister.addBillingDiskName(newSoloDiskName(providerNo, creator, ohipNo)));
            } catch (RuntimeException e) {
                if (attempt == DISK_NAME_INSERT_RETRIES || !isDuplicateDiskNameFailure(e)) {
                    throw e;
                }
            }
        }
        throw new BillingValidationException("Unable to allocate unique solo billing disk name for provider " + providerNo);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int createNewGrpDiskName(List providerNo, List ohipNo, String groupNo, String creator) {
        for (int attempt = 1; attempt <= DISK_NAME_INSERT_RETRIES; attempt++) {
            try {
                return diskNameAllocationTx.execute(status ->
                        claimPersister.addBillingDiskName(newGroupDiskName(providerNo, ohipNo, groupNo, creator)));
            } catch (RuntimeException e) {
                if (attempt == DISK_NAME_INSERT_RETRIES || !isDuplicateDiskNameFailure(e)) {
                    throw e;
                }
            }
        }
        throw new BillingValidationException("Unable to allocate unique group billing disk name for group " + groupNo);
    }

    private BillingDiskNameDto newSoloDiskName(String providerNo, String creator, String ohipNo) {
        String groupNo = "";
        String[] temp = getCurSoloMonthCodeBatchNum(ohipNo);
        BillingDiskNameDto diskName = new BillingDiskNameDto();
        diskName.setMonthCode(temp[0]);
        diskName.setBatchcount(temp[1]);

        diskName.setGroupno(groupNo);
        diskName.setOhipfilename(getSoloOhipfilename(ohipNo, temp[0], temp[1]));
        diskName.setCreator(creator);
        diskName.setClaimrecord("");
        diskName.setCreatedatetime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        diskName.setStatus(BillingOnConstants.BILLINGFILE_STATUS_UNCERT);
        diskName.setTotal("");
        String htmlFilename = (String) getSoloHtmlfilename(ohipNo, temp[0], temp[1]).get(0);
        diskName.setFilenames(List.of(new DiskFilenameRow(
                null,
                htmlFilename,
                ohipNo,
                providerNo,
                "",
                BillingOnConstants.BILLINGFILE_STATUS_UNCERT,
                "")));
        return diskName;
    }

    private BillingDiskNameDto newGroupDiskName(List providerNo, List ohipNo, String groupNo, String creator) {
        String[] temp = getCurGrpMonthCodeBatchNum(groupNo);
        BillingDiskNameDto diskName = new BillingDiskNameDto();
        diskName.setMonthCode(temp[0]);
        diskName.setBatchcount(temp[1]);

        String groupno = (groupNo != null && groupNo.length() == 4) ? groupNo : "";
        diskName.setGroupno(groupno);
        diskName.setOhipfilename(getGrpOhipfilename(groupno, temp[0], temp[1]));
        diskName.setCreator(creator);
        diskName.setClaimrecord("");
        diskName.setCreatedatetime(UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss"));
        diskName.setStatus(BillingOnConstants.BILLINGFILE_STATUS_UNCERT);
        diskName.setTotal("");
        // Group disk: one filename row per (provider, ohipNo) pair, with the
        // disk-header claimRecord/status/total duplicated across each row so
        // the consumer's read-at-zero contract still produces the disk-header
        // value while a future per-row consumer would also see consistent data.
        ArrayList<String> htmlFilenames = getGrpHtmlfilename(ohipNo, groupno, temp[0], temp[1]);
        List<DiskFilenameRow> rows = new ArrayList<>();
        for (int i = 0; i < providerNo.size(); i++) {
            rows.add(new DiskFilenameRow(
                    null,
                    (String) htmlFilenames.get(i),
                    (String) ohipNo.get(i),
                    (String) providerNo.get(i),
                    "",
                    BillingOnConstants.BILLINGFILE_STATUS_UNCERT,
                    ""));
        }
        diskName.setFilenames(rows);
        return diskName;
    }

    private static boolean isDuplicateDiskNameFailure(RuntimeException e) {
        Throwable t = e;
        while (t != null) {
            String message = t.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);
                if (lower.contains("duplicate") || lower.contains("unique")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    public boolean updateSoloDiskName(String diskId, String creator) {
        boolean ret = false;
        // set up obj
        // String groupNo = "";

        // get diskName obj
        BillingDiskNameDto diskName = diskQuery.getDisknameObj(diskId);
        claimPersister.addRepoDiskName(diskName);

        // diskName.setGroupno(groupNo);
        diskName.setCreator(creator);
        diskName.setClaimrecord("");
        // diskName.setCreatedatetime(UtilDateUtilities.getToday("yyyy-MM-dd
        // HH:mm:ss"));
        diskName.setStatus(BillingOnConstants.BILLINGFILE_STATUS_UNCERT);
        diskName.setTotal("");

        ret = claimPersister.updateDiskName(diskName);
        return ret;
    }

    public int createBatchHeader(BillingProviderDto providerData, String disk_id, String moh_office, String seqNum,
                                 String creator) {
        int ret = 0;
        BillingBatchHeaderDto obj = new BillingBatchHeaderDto();
        obj.setDiskId(disk_id);
        obj.setTranscId(BillingOnConstants.BATCHHEADER_TRANSACTIONIDENTIFIER);
        obj.setRecId(BillingOnConstants.BATCHHEADER_REORDIDENTIFICATION);
        obj.setSpecId(BillingOnConstants.BATCHHEADER_SPECID);
        obj.setMohOffice(moh_office);

        String batchid = UtilDateUtilities.getToday("yyyyMMdd") + getDefaultRightJust("0", 4, seqNum);
        obj.setBatchId(batchid);
        obj.setOperator("");
        obj.setGroupNum(providerData.getBillingGroupNo());
        obj.setProviderRegNum(providerData.getOhipNo());
        obj.setSpecialty(providerData.getSpecialtyCode());
        obj.setHCount("");
        obj.setRCount("");
        obj.setTCount("");
        obj.setBatchDate(UtilDateUtilities.getToday("yyyy-MM-dd"));

        String strDateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        obj.setCreatedatetime(strDateTime);
        obj.setUpdatedatetime(strDateTime);
        obj.setCreator(creator);
        obj.setAction(BillingOnConstants.BILLINGACTION_CREATE);
        obj.setComment("");
        ret = claimPersister.addOneBatchHeaderRecord(obj);
        return ret;
    }

    public int updateBatchHeader(BillingProviderDto providerData, String disk_id, String moh_office, String seqNum,
                                 String creator) {
        boolean ret = false;
        BillingBatchHeaderDto obj = diskQuery.getBatchHeaderObj(providerData, disk_id);
        claimPersister.addRepoBatchHeader(obj);
        obj.setDiskId(disk_id);
        obj.setTranscId(BillingOnConstants.BATCHHEADER_TRANSACTIONIDENTIFIER);
        obj.setRecId(BillingOnConstants.BATCHHEADER_REORDIDENTIFICATION);
        obj.setSpecId(BillingOnConstants.BATCHHEADER_SPECID);
        obj.setMohOffice(moh_office);

        String batchid = UtilDateUtilities.getToday("yyyyMMdd") + getDefaultRightJust("0", 4, seqNum);
        obj.setBatchId(batchid);
        obj.setOperator("");
        obj.setGroupNum(providerData.getBillingGroupNo());
        obj.setProviderRegNum(providerData.getOhipNo());
        obj.setSpecialty(providerData.getSpecialtyCode());
        obj.setHCount("");
        obj.setRCount("");
        obj.setTCount("");
        obj.setBatchDate(UtilDateUtilities.getToday("yyyy-MM-dd"));

        String strDateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        // obj.setCreatedatetime(strDateTime);
        obj.setUpdatedatetime(strDateTime);
        obj.setCreator(creator);
        obj.setAction(BillingOnConstants.BILLINGACTION_UPDATE);
        obj.setComment("");
        ret = claimPersister.updateBatchHeaderRecord(obj);
        int retval = ret ? Integer.parseInt(obj.getId()) : 0;
        return retval;
    }

    private ArrayList getSoloHtmlfilename(String ohipNo, String monthCode, String batchNum) {
        ArrayList ret = new ArrayList();
        String diskName = "H" + monthCode + ohipNo + "_" + getDefaultRightJust("0", 3, batchNum) + ".html";
        ret.add(diskName);
        return ret;
    }

    private ArrayList getGrpHtmlfilename(List ohipNo, String groupNo, String monthCode, String batchNum) {
        ArrayList ret = new ArrayList();
        for (int i = 0; i < ohipNo.size(); i++) {
            String diskName = "H" + monthCode + groupNo + "_" + ohipNo.get(i) + "_"
                    + getDefaultRightJust("0", 3, batchNum) + ".html";
            ret.add(diskName);
        }
        return ret;
    }

    private String getSoloOhipfilename(String ohipNo, String monthCode, String batchNum) {
        String ret = null;
        String diskName = "H" + monthCode + ohipNo + "." + getDefaultRightJust("0", 3, batchNum);
        ret = diskName;
        return ret;
    }

    private String getGrpOhipfilename(String groupNo, String monthCode, String batchNum) {
        String ret = null;
        String diskName = "H" + monthCode + groupNo + "." + getDefaultRightJust("0", 3, batchNum);
        ret = diskName;
        return ret;
    }

    private String getDefaultRightJust(String ch, int num, String val) {
        if (val == null) {
            throw new BillingValidationException(
                    "BillingDiskCreationService: fixed-width value is null for width=" + num);
        }
        if (val.length() > num) {
            throw new BillingValidationException(
                    "BillingDiskCreationService: fixed-width value [" + val + "] does not fit width=" + num);
        }
        String ret = "";
        for (int i = 0; i < num; i++) {
            ret += ch;
        }
        int n = val.length();
        ret = ret.substring(0, num - n) + val;
        return ret;
    }

    private String[] getCurSoloMonthCodeBatchNum(String ohipNo) {
        String[] ret = new String[2];
        GregorianCalendar now = new GregorianCalendar();
        int curMonth = (now.get(Calendar.MONTH) + 1);
        String curMonthCode = BillingOnConstants.propMonthCode.getProperty("" + curMonth);
        String[] last = diskQuery.getLatestSoloMonthCodeBatchNum(ohipNo);

        if (last != null && curMonthCode.equals(last[0])) {
            ret[0] = curMonthCode;
            ret[1] = "" + (Integer.parseInt(last[1]) + 1);
        } else {
            ret[0] = curMonthCode;
            ret[1] = "1";
        }
        return ret;
    }

    private String[] getCurGrpMonthCodeBatchNum(String groupNo) {
        String[] ret = new String[2];
        GregorianCalendar now = new GregorianCalendar();
        // int curYear = now.get(Calendar.YEAR); int curDay =
        // now.get(Calendar.DAY_OF_MONTH);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        String curMonthCode = BillingOnConstants.propMonthCode.getProperty("" + curMonth);
        String[] last = diskQuery.getLatestGrpMonthCodeBatchNum(groupNo);

        if (last != null && curMonthCode.equals(last[0])) {
            ret[0] = curMonthCode;
            ret[1] = "" + (Integer.parseInt(last[1]) + 1);
        } else {
            ret[0] = curMonthCode;
            ret[1] = "1";
        }
        return ret;
    }
}
