/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingGroupNumber;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billings.ca.on.validator.InvalidBillingGroupException;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.providers.data.ProviderBillCenter;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.ObjectFactory;
/**
 * Shared mutation service for the two MOH disk-creation forward-shim JSPs:
 * {@code ongenreport.jsp} (new disk for current period) and
 * {@code onregenreport.jsp} (regenerate an existing disk by id). Both pages
 * iterated providers, used {@link BillingDiskCreationService} +
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
public class BillingOnDiskService {

    private static final String[] BILLING_STATUS_NEW = new String[]{"O", "W", "I"};
    private static final String[] BILLING_STATUS_REGEN = new String[]{"B"};

    private final ProviderDao providerDao;
    private final BillingDiskCreationService prep;
    private final BillingOnDiskLoader diskQueryService;
    private final ObjectFactory<OhipClaimFileService> ohipClaimFileFactory;
    private final BillingOnDiskTransactionService transactionService;

    BillingOnDiskService(ProviderDao providerDao,
                         BillingDiskCreationService prep,
                         BillingOnDiskLoader diskQueryService,
                         ObjectFactory<OhipClaimFileService> ohipClaimFileFactory,
                         BillingOnDiskTransactionService transactionService) {
        this.providerDao = providerDao;
        this.prep = prep;
        this.diskQueryService = diskQueryService;
        this.ohipClaimFileFactory = ohipClaimFileFactory;
        this.transactionService = transactionService;
    }

    /**
     * Run the {@code ongenreport.jsp} flow: create new solo/group disks for
     * the requested provider(s) over the requested date range, then write
     * the OHIP claim files and HTML previews.
     */
    @SuppressWarnings("unchecked")
    public void generateNewDisk(HttpServletRequest request) {
        BillingOutputLock.run(() -> generateNewDiskLocked(request));
    }

    private void generateNewDiskLocked(HttpServletRequest request) {
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
            // The lookup already normalized each member's group number (a short
            // all-digit value is zero-padded). What is left to check, before even
            // the first solo disk is allocated, is a value that cannot be made
            // into an OHIP group number at all; that is reported per provider
            // instead of letting "All Providers" commit other disks first.
            // Reuse this snapshot for generation so validation and writing see the same configuration.
            List<BillingProviderDto> groupProviders = prep.getCurGrpProvider();
            if (groupReport && groupProviders.stream().noneMatch(member -> provider.equals(member.getProviderNo()))) {
                throw new BillingValidationException("Selected provider is not available for group billing.");
            }
            List<String> invalidProviders = groupProviders.stream()
                    .filter(member -> !groupReport || provider.equals(member.getProviderNo()))
                    .filter(member -> !BillingGroupNumber.isWellFormed(member.getBillingGroupNo()))
                    .map(BillingProviderDto::getProviderNo)
                    .distinct()
                    .toList();
            if (!invalidProviders.isEmpty()) {
                throw new InvalidBillingGroupException(invalidProviders);
            }
            if (!groupReport) {
                writeSoloDisks(prep, prep.getCurSoloProvider(), loggedInInfo, request,
                        dateRange, mohOffice, useProviderMOH, currentUser);
            }
            writeGroupDisks(prep, groupProviders, loggedInInfo, request,
                    dateRange, mohOffice, useProviderMOH, currentUser, groupReport, provider);
        } else {
            BillingProviderDto soloProvider = prep.getProviderObj(provider);
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
        BillingOutputLock.run(() -> regenerateDiskLocked(request));
    }

    private void regenerateDiskLocked(HttpServletRequest request) {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String diskId = request.getParameter("diskId");
        String mohOffice = request.getParameter("billcenter");
        boolean useProviderMOH = "true".equals(request.getParameter("useProviderMOH"));
        String defaultMOH = mohOffice;
        String currentUser = (String) request.getSession().getAttribute("user");

        String dateEnd = diskQueryService.getDiskCreateDate(diskId);
        DateRange dateRange = new DateRange(null, ConversionUtils.fromDateString(dateEnd));

        List<BillingProviderDto> lProvider = prep.getProvider(diskId);

        if (lProvider != null && lProvider.size() == 1
                && isSoloGroupNo(lProvider.get(0).getBillingGroupNo())) {
            BillingProviderDto dataProvider = lProvider.get(0);
            String resolvedMoh = resolveMohForRegen(useProviderMOH, dataProvider.getProviderNo(),
                    defaultMOH);
            var prepared = prep.prepareBatchHeader(dataProvider, diskId, resolvedMoh, "1", currentUser);
            String headerId = prepared.replacement().getId();
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(Integer.parseInt(diskId)),
                    prep.getHtmlfilename(Integer.parseInt(diskId), dataProvider.getProviderNo()));
            objFile.stageRegeneratedBatchHeader(prepared.replacement(), () -> prep.finalizeBatchHeader(prepared));
            regenerateSoloDiskFilesAndFinalize(objFile, loggedInInfo, headerId,
                    resolvedMoh, Integer.parseInt(diskId));
        } else if (lProvider != null && !lProvider.isEmpty()) {
            regenerateGroupDisk(prep, lProvider, loggedInInfo, request, dateRange, mohOffice,
                    diskId, currentUser);
        }
    }

    private boolean isGroupProvider(String provider) {
        if (provider == null || "all".equals(provider)) return false;
        Provider p = providerDao.getProvider(provider);
        if (p == null) return false;
        // Same normalization as BillingOnLookupService, so "0", "000" and a
        // blank all mean solo while "123" is the group 0123.
        return !BillingGroupNumber.isSolo(SxmlMisc.getXmlContent(p.getComments(),
                "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>"));
    }

    private static DateRange parseDateRange(String dateBegin, String dateEnd, String curDate) {
        if (dateEnd == null || dateEnd.isEmpty()) dateEnd = curDate;
        if (dateBegin == null || dateBegin.isEmpty()) {
            return new DateRange(null, ConversionUtils.fromDateString(dateEnd));
        }
        return new DateRange(ConversionUtils.fromDateString(dateBegin),
                ConversionUtils.fromDateString(dateEnd));
    }

    private void writeSoloDisks(BillingDiskCreationService prep, List<BillingProviderDto> soloProviders,
                                 LoggedInInfo loggedInInfo, HttpServletRequest request,
                                 DateRange dateRange, String mohOffice, String useProviderMOH,
                                 String currentUser) {
        ProviderBillCenter oriBillCenter = new ProviderBillCenter();
        for (BillingProviderDto dataProvider : soloProviders) {
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
            writeNewDiskFilesAndFinalize(objFile, diskId);
        }
    }

    private void writeGroupDisks(BillingDiskCreationService prep, List<BillingProviderDto> grpProviders,
                                  LoggedInInfo loggedInInfo, HttpServletRequest request,
                                  DateRange dateRange, String mohOffice, String useProviderMOH,
                                  String currentUser, boolean groupReport, String provider) {
        ProviderBillCenter oriBillCenter = new ProviderBillCenter();
        Set<String> groupNos = new HashSet<>();
        List<String> providerNos = new ArrayList<>();
        for (BillingProviderDto dataProvider : grpProviders) {
            if (groupReport && !provider.equals(dataProvider.getProviderNo())) continue;
            groupNos.add(dataProvider.getBillingGroupNo());
            providerNos.add(dataProvider.getProviderNo());
        }
        if (groupNos.isEmpty()) return;

        for (Iterator<String> it = groupNos.iterator(); it.hasNext(); ) {
            String groupNo = it.next();
            // BillingDiskCreationService#createNewGrpDiskName casts these to ArrayList
            // — pass ArrayList to preserve legacy behavior.
            ArrayList<String> providerNoCopy = new ArrayList<>();
            ArrayList<String> ohipNoCopy = new ArrayList<>();
            List<BillingProviderDto> selectedMembers = new ArrayList<>();
            for (int copyi = 0; copyi < providerNos.size(); copyi++) {
                BillingProviderDto bpd = findByProviderNo(grpProviders, providerNos.get(copyi));
                if (groupNo.equals(bpd.getBillingGroupNo())) {
                    providerNoCopy.add(providerNos.get(copyi));
                    ohipNoCopy.add(bpd.getOhipNo());
                    selectedMembers.add(bpd);
                }
            }
            MiscUtils.getLogger().info("creating group disk for =" + groupNo);
            int diskId = prep.createNewGrpDiskName(providerNoCopy, ohipNoCopy, groupNo,
                    currentUser);
            GroupDiskGeneration generation = writeGroupMembers(prep, selectedMembers, groupNo, diskId,
                    loggedInInfo, request, dateRange, mohOffice, useProviderMOH, currentUser,
                    oriBillCenter);
            OhipClaimFileService finalize = ohipClaimFileFactory.getObject();
            finalize.setContextPath(request.getContextPath());
            finalize.setOhipFilename(prep.getOhipfilename(diskId));
            if (generation != null) {
                writeNewGroupDiskFileAndFinalize(generation, finalize, diskId);
            } else {
                // OSCAR 19 contract: the disk row and its headers already exist, so the
                // listed download must exist too; an empty claim file is what it wrote.
                // Nothing is billed on it, so a write failure is logged (as OSCAR 19 did)
                // rather than aborting the remaining groups of an All Providers run; the
                // file can be regenerated from the MRI page.
                try {
                    finalize.writeFile("");
                } catch (BillingFileWriteException failure) {
                    MiscUtils.getLogger().warn("Could not write the empty OHIP file for claimless group disk {} ({})",
                            diskId, failure.getClass().getSimpleName());
                }
            }
        }
    }

    /**
     * Builds the claim batches for every provider of one billing group on a new
     * group disk before publishing any member's HTML preview.
     *
     * <p>A member is included when its generated batch contains at least one
     * claim item, regardless of the batch total, so {@code $0}-value claims are
     * submitted rather than silently dropped. A member with no claim items is
     * skipped: its batch text (header and trailer) is not appended to the OHIP
     * file and its writer is not finalized. The member's
     * {@code billing_on_header} row is still created up front because the
     * writer resolves the batch header by id, and disk regeneration later
     * resolves one header per provider filename row on the disk.</p>
     *
     * @return the concatenated claim body and included writers, or {@code null}
     *         when no member of the group has any claim item
     */
    private GroupDiskGeneration writeGroupMembers(BillingDiskCreationService prep,
                                                   List<BillingProviderDto> grpProviders,
                                                   String groupNo, int diskId,
                                                   LoggedInInfo loggedInInfo,
                                                   HttpServletRequest request,
                                                   DateRange dateRange,
                                                   String mohOffice, String useProviderMOH,
                                                   String currentUser,
                                                   ProviderBillCenter oriBillCenter) {
        StringBuilder value = new StringBuilder();
        List<OhipClaimFileService> writers = new ArrayList<>();
        boolean wroteAny = false;
        for (int i = 0; i < grpProviders.size(); i++) {
            BillingProviderDto dataProvider = grpProviders.get(i);
            if (!groupNo.equals(dataProvider.getBillingGroupNo())) continue;
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(diskId),
                    prep.getHtmlfilename(diskId, dataProvider.getProviderNo()));
            int headerId = createSoloHeader(prep, dataProvider, diskId, oriBillCenter, mohOffice,
                    currentUser, "" + (i + 1));
            objFile.createBillingFileStr(loggedInInfo, "" + headerId, BILLING_STATUS_NEW, false,
                    mohOffice, false, "on".equals(useProviderMOH));
            // Membership in the group disk is decided by claim items, not by the
            // dollar total: $0 tracking codes and items that net to $0 are still
            // claims OHIP must receive, and skipping them left those claims
            // unsubmitted and unbilled. Members with no items contribute no batch
            // (header + trailer) to the OHIP file and are not finalized.
            if (!hasClaimRecords(objFile)) continue;
            value.append(objFile.getValue());
            writers.add(objFile);
            wroteAny = true;
        }
        return wroteAny ? new GroupDiskGeneration(value.toString(), writers) : null;
    }

    private void writeSingleSoloDisk(BillingDiskCreationService prep, BillingProviderDto dataProvider,
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
        writeNewDiskFilesAndFinalize(objFile, diskId);
    }

    private void regenerateGroupDisk(BillingDiskCreationService prep,
                                      List<BillingProviderDto> lProvider,
                                      LoggedInInfo loggedInInfo, HttpServletRequest request,
                                      DateRange dateRange, String mohOffice, String diskId,
                                      String currentUser) {
        StringBuilder value = new StringBuilder();
        OhipClaimFileService lastWriter = null;
        List<OhipClaimFileService> writers = new ArrayList<>();
        for (int i = 0; i < lProvider.size(); i++) {
            BillingProviderDto dataProvider = lProvider.get(i);
            OhipClaimFileService objFile = newFileWriter(request, dateRange,
                    dataProvider.getProviderNo(),
                    prep.getOhipfilename(Integer.parseInt(diskId)),
                    prep.getHtmlfilename(Integer.parseInt(diskId), dataProvider.getProviderNo()));
            var prepared = prep.prepareBatchHeader(dataProvider, diskId, mohOffice, "" + (i + 1), currentUser);
            objFile.stageRegeneratedBatchHeader(prepared.replacement(), () -> prep.finalizeBatchHeader(prepared));
            objFile.readInBillingNo();
            objFile.createBillingFileStr(loggedInInfo, prepared.replacement().getId(), BILLING_STATUS_REGEN, false,
                    mohOffice, false, false);
            if (!hasClaimRecords(objFile)) continue;
            value.append(objFile.getValue()).append('\n');
            writers.add(objFile);
            lastWriter = objFile;
        }
        if (lastWriter != null) {
            writeRegeneratedGroupDiskFileAndFinalize(writers, lastWriter, value.toString(),
                    Integer.parseInt(diskId));
        }
    }

    private void writeNewDiskFilesAndFinalize(OhipClaimFileService writer, int diskId) {
        var outcome = new BillingOnDiskTransactionService.Outcome();
        try {
            writer.writeFile(writer.getValue());
            writer.writeHtml(writer.getHtmlCode());
            transactionService.finalizeGeneratedDisk(writer, diskId, outcome);
        } catch (RuntimeException failure) {
            if (outcome.mayHaveCommitted()) throw uncertainCommit(failure);
            cleanupNewFiles(List.of(writer), writer);
            throw failure;
        }
    }

    private void regenerateSoloDiskFilesAndFinalize(OhipClaimFileService writer,
                                                     LoggedInInfo loggedInInfo,
                                                     String headerId,
                                                     String mohOffice,
                                                     int diskId) {
        writer.readInBillingNo();
        // Rendering is read-only; preserve the existing files until a complete replacement exists.
        writer.createBillingFileStr(loggedInInfo, headerId, BILLING_STATUS_REGEN, false,
                mohOffice, false, false);
        var outcome = new BillingOnDiskTransactionService.Outcome();
        boolean renamed = false;
        try {
            writer.backupHtmlForRollback();
            writer.backupFileForRollback();
            renamed = true;
            writer.writeFile(writer.getValue());
            writer.writeHtml(writer.getHtmlCode());
            transactionService.finalizeGeneratedDisk(writer, diskId, outcome);
            writer.discardHtmlBackup();
            writer.retainFileBackup();
        } catch (RuntimeException failure) {
            if (outcome.mayHaveCommitted()) throw uncertainCommit(failure);
            if (renamed) restoreRegeneratedFiles(List.of(writer), writer, failure);
            else writer.discardHtmlBackup();
            throw failure;
        }
    }

    private void writeNewGroupDiskFileAndFinalize(GroupDiskGeneration generation,
                                                   OhipClaimFileService ohipWriter,
                                                   int diskId) {
        var outcome = new BillingOnDiskTransactionService.Outcome();
        try {
            for (OhipClaimFileService writer : generation.writers()) writer.writeHtml(writer.getHtmlCode());
            ohipWriter.writeFile(generation.claimBody());
            transactionService.finalizeGeneratedDisks(generation.writers(), diskId, outcome);
        } catch (RuntimeException failure) {
            if (outcome.mayHaveCommitted()) throw uncertainCommit(failure);
            cleanupNewFiles(generation.writers(), ohipWriter);
            throw failure;
        }
    }

    private void writeRegeneratedGroupDiskFileAndFinalize(List<OhipClaimFileService> writers,
                                                           OhipClaimFileService ohipWriter,
                                                           String claimBody,
                                                           int diskId) {
        var outcome = new BillingOnDiskTransactionService.Outcome();
        boolean renamed = false;
        try {
            for (OhipClaimFileService writer : writers) writer.backupHtmlForRollback();
            ohipWriter.backupFileForRollback();
            renamed = true;
            for (OhipClaimFileService writer : writers) writer.writeHtml(writer.getHtmlCode());
            ohipWriter.writeFile(claimBody);
            transactionService.finalizeGeneratedDisks(writers, diskId, outcome);
            for (OhipClaimFileService writer : writers) writer.discardHtmlBackup();
            ohipWriter.retainFileBackup();
        } catch (RuntimeException failure) {
            if (outcome.mayHaveCommitted()) throw uncertainCommit(failure);
            if (renamed) restoreRegeneratedFiles(writers, ohipWriter, failure);
            else for (OhipClaimFileService writer : writers) writer.discardHtmlBackup();
            throw failure;
        }
    }

    private static BillingFileWriteException uncertainCommit(RuntimeException cause) {
        return BillingFileWriteException.forReason(BillingFileWriteException.Reason.UNCERTAIN_COMMIT, cause);
    }

    private static void cleanupNewFiles(List<OhipClaimFileService> htmlWriters,
                                       OhipClaimFileService ohipWriter) {
        if (ohipWriter != null) ohipWriter.deleteOhipFileQuietly();
        for (OhipClaimFileService writer : htmlWriters) writer.deleteHtmlFileQuietly();
    }

    private static void restoreRegeneratedFiles(List<OhipClaimFileService> htmlWriters,
                                                OhipClaimFileService ohipWriter,
                                                RuntimeException originalFailure) {
        boolean incomplete = false;
        try { ohipWriter.restoreRenamedFile(); }
        catch (RuntimeException restoreFailure) {
            incomplete = true;
            originalFailure.addSuppressed(restoreFailure);
        }
        for (OhipClaimFileService writer : htmlWriters) {
            try { writer.restoreHtmlForRollback(); }
            catch (RuntimeException restoreFailure) {
                incomplete = true;
                originalFailure.addSuppressed(restoreFailure);
            }
        }
        if (incomplete) throw BillingFileWriteException.forReason(
                BillingFileWriteException.Reason.RESTORE_FAILED, originalFailure);
    }

    private record GroupDiskGeneration(String claimBody, List<OhipClaimFileService> writers) {
    }

    private static int createSoloHeader(BillingDiskCreationService prep, BillingProviderDto dataProvider,
                                         int diskId, ProviderBillCenter oriBillCenter,
                                         String mohOffice, String currentUser) {
        return createSoloHeader(prep, dataProvider, diskId, oriBillCenter, mohOffice, currentUser,
                "1");
    }

    private static int createSoloHeader(BillingDiskCreationService prep, BillingProviderDto dataProvider,
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

    private static BillingProviderDto findByProviderNo(List<BillingProviderDto> providers,
                                                         String providerNo) {
        for (BillingProviderDto bpd : providers) {
            if (bpd.getProviderNo().equals(providerNo)) return bpd;
        }
        throw new BillingValidationException(
                "Billing disk generation could not resolve provider ["
                        + LogSafe.sanitizeForDisplay(providerNo) + "]");
    }

    /**
     * Whether a generated claim batch contains at least one claim item.
     * Originally reported and fixed for {@code ongenreport.jsp} by Sebastian
     * Ibanez in openo-beta/Open-O PR #2510.
     */
    static boolean hasClaimRecords(OhipClaimFileService objFile) {
        return objFile.getRecordCount() > 0;
    }

    private static boolean isSoloGroupNo(String groupNo) {
        return BillingGroupNumber.isSolo(groupNo);
    }
}
