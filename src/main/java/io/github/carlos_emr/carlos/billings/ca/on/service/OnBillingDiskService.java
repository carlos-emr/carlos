/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingProviderData;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.providers.data.ProviderBillCenter;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.ObjectFactory;
/**
 * Shared mutation service for the two MOH disk-creation forward-shim JSPs:
 * {@code ongenreport.jsp} (new disk for current period) and
 * {@code onregenreport.jsp} (regenerate an existing disk by id). Both pages
 * iterated providers, used {@link BillingDiskCreatePrep} +
 * {@link OhipClaimFileService} to write OHIP/HTML disk files, and
 * {@code <jsp:forward>}'d to {@code ViewBillingONMRI}.
 *
 * <p>{@link OhipClaimFileService} is {@code @Scope("prototype")} — per-claim
 * mutable state; sharing one instance across two concurrent disk generations
 * would corrupt both. Injected as {@link ObjectFactory} so each
 * {@link #newFileWriter} call produces a fresh instance.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
@org.springframework.transaction.annotation.Transactional
public class OnBillingDiskService {

    private static final String[] BILLING_STATUS_NEW = new String[]{"O", "W", "I"};
    private static final String[] BILLING_STATUS_REGEN = new String[]{"B"};

    private final ProviderDao providerDao;
    private final BillingDiskCreatePrep prep;
    private final BillingONDiskQueryService diskQueryService;
    private final ObjectFactory<OhipClaimFileService> ohipClaimFileFactory;

    OnBillingDiskService(ProviderDao providerDao,
                         BillingDiskCreatePrep prep,
                         BillingONDiskQueryService diskQueryService,
                         ObjectFactory<OhipClaimFileService> ohipClaimFileFactory) {
        this.providerDao = providerDao;
        this.prep = prep;
        this.diskQueryService = diskQueryService;
        this.ohipClaimFileFactory = ohipClaimFileFactory;
    }

    /**
     * Run the {@code ongenreport.jsp} flow: create new solo/group disks for
     * the requested provider(s) over the requested date range, then write
     * the OHIP claim files and HTML previews.
     */
    @SuppressWarnings("unchecked")
    public void generateNewDisk(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String provider = request.getParameter("providers");
        String mohOffice = request.getParameter("billcenter");
        String useProviderMOH = request.getParameter("useProviderMOH");
        String currentUser = (String) request.getSession().getAttribute("user");

        DateRange dateRange = parseDateRange(
                request.getParameter("xml_vdate"),
                request.getParameter("xml_appointment_date"),
                request.getParameter("curDate"));

        boolean groupReport = isGroupProvider(provider);

        if ("all".equals(provider) || groupReport) {
            if (!groupReport) {
                writeSoloDisks(prep, prep.getCurSoloProvider(), loggedInInfo, request,
                        dateRange, mohOffice, useProviderMOH, currentUser);
            }
            writeGroupDisks(prep, prep.getCurGrpProvider(), loggedInInfo, request,
                    dateRange, mohOffice, useProviderMOH, currentUser, groupReport, provider);
        } else {
            BillingProviderData soloProvider = prep.getProviderObj(provider);
            if (soloProvider != null && isSoloGroupNo(soloProvider.getBillingGroupNo())) {
                writeSingleSoloDisk(prep, soloProvider, loggedInInfo, request,
                        dateRange, mohOffice, useProviderMOH, currentUser);
            }
        }
    }

    /**
     * Run the {@code onregenreport.jsp} flow: regenerate the existing disk
     * keyed by {@code diskId}, rewriting the OHIP claim file and HTML preview.
     */
    @SuppressWarnings("unchecked")
    public void regenerateDisk(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String diskId = request.getParameter("diskId");
        String mohOffice = request.getParameter("billcenter");
        boolean useProviderMOH = "true".equals(request.getParameter("useProviderMOH"));
        String defaultMOH = mohOffice;
        String currentUser = (String) request.getSession().getAttribute("user");

        String dateEnd = diskQueryService.getDiskCreateDate(diskId);
        DateRange dateRange = new DateRange(null, ConversionUtils.fromDateString(dateEnd));

        List<BillingProviderData> lProvider = prep.getProvider(diskId);

        if (lProvider != null && lProvider.size() == 1
                && isSoloGroupNo(lProvider.get(0).getBillingGroupNo())) {
            BillingProviderData dataProvider = lProvider.get(0);
            String resolvedMoh = resolveMohForRegen(useProviderMOH, dataProvider.getProviderNo(),
                    defaultMOH);
            int headerId = prep.updateBatchHeader(dataProvider, diskId, resolvedMoh, "1",
                    currentUser);
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(Integer.parseInt(diskId)),
                    prep.getHtmlfilename(Integer.parseInt(diskId), dataProvider.getProviderNo()));
            objFile.readInBillingNo();
            objFile.renameFile();
            objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_REGEN, false,
                    resolvedMoh, false, false);
            objFile.writeFile(objFile.getValue());
            objFile.writeHtml(objFile.getHtmlCode());
            objFile.updateDisknameSum(Integer.parseInt(diskId));
        } else if (lProvider != null && !lProvider.isEmpty()) {
            regenerateGroupDisk(prep, lProvider, loggedInInfo, request, dateRange, mohOffice,
                    diskId, currentUser);
        }
    }

    private boolean isGroupProvider(String provider) {
        if (provider == null || "all".equals(provider)) return false;
        Provider p = providerDao.getProvider(provider);
        if (p == null) return false;
        String groupNo = SxmlMisc.getXmlContent(p.getComments(),
                "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>");
        return groupNo != null && !groupNo.isEmpty() && !"0000".equals(groupNo);
    }

    private static DateRange parseDateRange(String dateBegin, String dateEnd, String curDate) {
        if (dateEnd == null || dateEnd.isEmpty()) dateEnd = curDate;
        if (dateBegin == null || dateBegin.isEmpty()) {
            return new DateRange(null, ConversionUtils.fromDateString(dateEnd));
        }
        return new DateRange(ConversionUtils.fromDateString(dateBegin),
                ConversionUtils.fromDateString(dateEnd));
    }

    private void writeSoloDisks(BillingDiskCreatePrep prep, List<BillingProviderData> soloProviders,
                                 LoggedInInfo loggedInInfo, HttpServletRequest request,
                                 DateRange dateRange, String mohOffice, String useProviderMOH,
                                 String currentUser) {
        ProviderBillCenter oriBillCenter = new ProviderBillCenter();
        for (BillingProviderData dataProvider : soloProviders) {
            MiscUtils.getLogger().info("creating solo disk for =" + dataProvider);
            int diskId = prep.createNewSoloDiskName(dataProvider.getProviderNo(), currentUser);
            int headerId = createSoloHeader(prep, dataProvider, diskId, oriBillCenter, mohOffice,
                    currentUser);
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(diskId),
                    prep.getHtmlfilename(diskId, dataProvider.getProviderNo()));
            objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_NEW, false,
                    mohOffice, false, "on".equals(useProviderMOH));
            objFile.writeFile(objFile.getValue());
            objFile.writeHtml(objFile.getHtmlCode());
            objFile.updateDisknameSum(diskId);
        }
    }

    private void writeGroupDisks(BillingDiskCreatePrep prep, List<BillingProviderData> grpProviders,
                                  LoggedInInfo loggedInInfo, HttpServletRequest request,
                                  DateRange dateRange, String mohOffice, String useProviderMOH,
                                  String currentUser, boolean groupReport, String provider) {
        ProviderBillCenter oriBillCenter = new ProviderBillCenter();
        Set<String> groupNos = new HashSet<>();
        List<String> providerNos = new ArrayList<>();
        for (BillingProviderData dataProvider : grpProviders) {
            if (groupReport && !provider.equals(dataProvider.getProviderNo())) continue;
            groupNos.add(dataProvider.getBillingGroupNo());
            providerNos.add(dataProvider.getProviderNo());
        }
        if (groupNos.isEmpty()) return;

        for (Iterator<String> it = groupNos.iterator(); it.hasNext(); ) {
            String groupNo = it.next();
            // BillingDiskCreatePrep#createNewGrpDiskName casts these to ArrayList
            // — pass ArrayList to preserve legacy behavior.
            ArrayList<String> providerNoCopy = new ArrayList<>();
            ArrayList<String> ohipNoCopy = new ArrayList<>();
            for (int copyi = 0; copyi < providerNos.size(); copyi++) {
                BillingProviderData bpd = findByProviderNo(grpProviders, providerNos.get(copyi));
                if (bpd != null && groupNo.equals(bpd.getBillingGroupNo())) {
                    providerNoCopy.add(providerNos.get(copyi));
                    ohipNoCopy.add(bpd.getOhipNo());
                }
            }
            MiscUtils.getLogger().info("creating group disk for =" + groupNo);
            int diskId = prep.createNewGrpDiskName(providerNoCopy, ohipNoCopy, groupNo,
                    currentUser);
            String aggregatedClaim = writeGroupMembers(prep, grpProviders, groupNo, diskId,
                    loggedInInfo, request, dateRange, mohOffice, useProviderMOH, currentUser,
                    oriBillCenter);
            if (aggregatedClaim != null) {
                OhipClaimFileService finalize = ohipClaimFileFactory.getObject();
                finalize.setContextPath(request.getContextPath());
                finalize.setOhipFilename(prep.getOhipfilename(diskId));
                finalize.writeFile(aggregatedClaim);
            }
        }
    }

    private String writeGroupMembers(BillingDiskCreatePrep prep,
                                      List<BillingProviderData> grpProviders, String groupNo,
                                      int diskId, LoggedInInfo loggedInInfo,
                                      HttpServletRequest request, DateRange dateRange,
                                      String mohOffice, String useProviderMOH, String currentUser,
                                      ProviderBillCenter oriBillCenter) {
        StringBuilder value = new StringBuilder();
        boolean wroteAny = false;
        for (int i = 0; i < grpProviders.size(); i++) {
            BillingProviderData dataProvider = grpProviders.get(i);
            if (!groupNo.equals(dataProvider.getBillingGroupNo())) continue;
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(diskId),
                    prep.getHtmlfilename(diskId, dataProvider.getProviderNo()));
            int headerId = createSoloHeader(prep, dataProvider, diskId, oriBillCenter, mohOffice,
                    currentUser, "" + (i + 1));
            objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_NEW, false,
                    mohOffice, false, "on".equals(useProviderMOH));
            if (objFile.getBigTotal().compareTo(BigDecimal.ZERO) == 0) continue;
            value.append(objFile.getValue());
            objFile.writeHtml(objFile.getHtmlCode());
            objFile.updateDisknameSum(diskId);
            wroteAny = true;
        }
        return wroteAny ? value.toString() : null;
    }

    private void writeSingleSoloDisk(BillingDiskCreatePrep prep, BillingProviderData dataProvider,
                                      LoggedInInfo loggedInInfo, HttpServletRequest request,
                                      DateRange dateRange, String mohOffice,
                                      String useProviderMOH, String currentUser) {
        int diskId = prep.createNewSoloDiskName(dataProvider.getProviderNo(), currentUser);
        int headerId = prep.createBatchHeader(dataProvider, "" + diskId, mohOffice, "1",
                currentUser);
        OhipClaimFileService objFile = newFileWriter(request, dateRange,
                dataProvider.getProviderNo(),
                prep.getOhipfilename(diskId),
                prep.getHtmlfilename(diskId, dataProvider.getProviderNo()));
        objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_NEW, false,
                mohOffice, false, "on".equals(useProviderMOH));
        objFile.writeFile(objFile.getValue());
        objFile.writeHtml(objFile.getHtmlCode());
        objFile.updateDisknameSum(diskId);
    }

    private void regenerateGroupDisk(BillingDiskCreatePrep prep,
                                      List<BillingProviderData> lProvider,
                                      LoggedInInfo loggedInInfo, HttpServletRequest request,
                                      DateRange dateRange, String mohOffice, String diskId,
                                      String currentUser) {
        StringBuilder value = new StringBuilder();
        OhipClaimFileService lastWriter = null;
        for (int i = 0; i < lProvider.size(); i++) {
            BillingProviderData dataProvider = lProvider.get(i);
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(Integer.parseInt(diskId)),
                    prep.getHtmlfilename(Integer.parseInt(diskId), dataProvider.getProviderNo()));
            int headerId = prep.updateBatchHeader(dataProvider, diskId, mohOffice, "" + (i + 1),
                    currentUser);
            objFile.readInBillingNo();
            objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_REGEN, false,
                    mohOffice, false, false);
            value.append(objFile.getValue()).append('\n');
            objFile.writeHtml(objFile.getHtmlCode());
            objFile.updateDisknameSum(Integer.parseInt(diskId));
            lastWriter = objFile;
        }
        if (lastWriter != null) {
            lastWriter.renameFile();
            lastWriter.writeFile(value.toString());
        }
    }

    private static int createSoloHeader(BillingDiskCreatePrep prep, BillingProviderData dataProvider,
                                         int diskId, ProviderBillCenter oriBillCenter,
                                         String mohOffice, String currentUser) {
        return createSoloHeader(prep, dataProvider, diskId, oriBillCenter, mohOffice, currentUser,
                "1");
    }

    private static int createSoloHeader(BillingDiskCreatePrep prep, BillingProviderData dataProvider,
                                         int diskId, ProviderBillCenter oriBillCenter,
                                         String mohOffice, String currentUser, String seqNum) {
        boolean existBillCenter = oriBillCenter.hasBillCenter(dataProvider.getProviderNo());
        String resolvedMoh = (existBillCenter
                && !oriBillCenter.getBillCenter(dataProvider.getProviderNo()).equals(mohOffice))
                ? oriBillCenter.getBillCenter(dataProvider.getProviderNo())
                : mohOffice;
        return prep.createBatchHeader(dataProvider, "" + diskId, resolvedMoh, seqNum, currentUser);
    }

    private static String resolveMohForRegen(boolean useProviderMOH, String providerNo,
                                              String defaultMOH) {
        if (!useProviderMOH) return defaultMOH;
        ProviderBillCenter pbc = new ProviderBillCenter();
        String billCenter = pbc.getBillCenter(providerNo);
        return (billCenter != null && billCenter.length() == 1) ? billCenter : defaultMOH;
    }

    private OhipClaimFileService newFileWriter(HttpServletRequest request,
                                               DateRange dateRange,
                                               String providerNo,
                                               String ohipFilename,
                                               String htmlFilename) {
        OhipClaimFileService objFile = ohipClaimFileFactory.getObject();
        objFile.setContextPath(request.getContextPath());
        objFile.setDateRange(dateRange);
        objFile.setProviderNo(providerNo);
        objFile.setOhipFilename(ohipFilename);
        objFile.setHtmlFilename(htmlFilename);
        return objFile;
    }

    private static BillingProviderData findByProviderNo(List<BillingProviderData> providers,
                                                         String providerNo) {
        for (BillingProviderData bpd : providers) {
            if (bpd.getProviderNo().equals(providerNo)) return bpd;
        }
        return null;
    }

    private static boolean isSoloGroupNo(String groupNo) {
        return groupNo == null || groupNo.isEmpty() || "0000".equals(groupNo);
    }
}
