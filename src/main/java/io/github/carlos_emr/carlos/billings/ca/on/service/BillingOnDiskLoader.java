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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONHeader;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingProviderDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Query-side counterpart to {@link BillingOnClaimPersister} —
 * holds the read methods that previously lived on the persistence service
 * but never participated in its writes. CQRS-lite: persistence stays focused
 * on the {@code add*}/{@code update*} surface; this class exposes the disk-
 * metadata lookups (latest batch number, prior create date, MRI list, the
 * disk-name DTO assembly).
 *
 * <p>All methods are pure reads — class-level
 * {@code @Transactional(readOnly = true)} lets Hibernate skip dirty-checking
 * and flush on the read paths.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingOnDiskLoader {

    private final BillingONHeaderDao headerDao;
    private final BillingONDiskNameDao diskNameDao;
    private final BillingONFilenameDao filenameDao;

    public BillingOnDiskLoader(BillingONHeaderDao headerDao, BillingONDiskNameDao diskNameDao, BillingONFilenameDao filenameDao) {
        this.headerDao = headerDao;
        this.diskNameDao = diskNameDao;
        this.filenameDao = filenameDao;
    }

    private final SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat tsFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**

     * Returns latest solo month code batch num.

     *

     * @param providerOhipNo String

     * @return String[]

     */

    public String[] getLatestSoloMonthCodeBatchNum(String providerOhipNo) {
        BillingONDiskName b = diskNameDao.getLatestSoloMonthCodeBatchNum(providerOhipNo);
        if (b == null) return null;
        return new String[] { b.getMonthCode(), "" + b.getBatchCount() };
    }

    /**

     * Returns latest grp month code batch num.

     *

     * @param groupNo String

     * @return String[]

     */

    public String[] getLatestGrpMonthCodeBatchNum(String groupNo) {
        BillingONDiskName b = diskNameDao.findByGroupNo(groupNo);
        if (b == null) return null;
        return new String[] { b.getMonthCode(), "" + b.getBatchCount() };
    }

    /**

     * Returns prev disk create date.

     *

     * @param diskId String

     * @return String

     */

    public String getPrevDiskCreateDate(String diskId) {
        BillingONDiskName b = diskNameDao.find(Integer.valueOf(diskId));
        if (b == null) return null;
        Date curDate = b.getCreateDateTime();
        String groupNo = b.getGroupNo();

        BillingONDiskName x = diskNameDao.getPrevDiskCreateDate(curDate, groupNo);
        if (x == null) return null;
        return dateformatter.format(x.getCreateDateTime());
    }

    /**

     * Returns disk create date.

     *

     * @param diskId String

     * @return String

     */

    public String getDiskCreateDate(String diskId) {
        BillingONDiskName b = diskNameDao.find(Integer.parseInt(diskId));
        if (b == null) return null;
        return dateformatter.format(b.getCreateDateTime());
    }

    /**

     * Returns m r i list.

     *

     * @param sDate String

     * @param eDate String

     * @param status String

     * @return List

     */

    public List getMRIList(String sDate, String eDate, String status) {
        List retval = new ArrayList();
        try {
            List<BillingONDiskName> results = diskNameDao.findByCreateDateRangeAndStatus(
                    dateformatter.parse(sDate), dateformatter.parse(eDate), status);

            for (BillingONDiskName b : results) {
                BillingDiskNameDto obj = new BillingDiskNameDto();
                obj.setId("" + b.getId());
                obj.setMonthCode(b.getMonthCode());
                obj.setBatchcount("" + b.getBatchCount());
                obj.setOhipfilename(b.getOhipFilename());
                obj.setGroupno(b.getGroupNo());
                obj.setClaimrecord(b.getClaimRecord());
                obj.setCreatedatetime(tsFormatter.format(b.getCreateDateTime()));
                obj.setUpdatedatetime(tsFormatter.format(b.getTimestamp()));
                obj.setStatus(b.getStatus());
                obj.setTotal(b.getTotal());

                List<BillingONFilename> ff = filenameDao.findByDiskIdAndStatus(b.getId(), status);
                List<DiskFilenameRow> rows = new ArrayList<>();
                for (BillingONFilename f : ff) {
                    rows.add(new DiskFilenameRow(
                            "" + f.getId(),
                            f.getHtmlFilename(),
                            f.getProviderOhipNo(),
                            f.getProviderNo(),
                            f.getClaimRecord(),
                            f.getStatus(),
                            f.getTotal()));
                }
                obj.setFilenames(rows);
                retval.add(obj);
            }
        } catch (Exception e) {
            // Rethrow as a typed BillingDataLoadException so the action's
            // exception mapping renders an explicit failure page rather
            // than a silently-empty MRI grid the operator would interpret
            // as "clean books".
            MiscUtils.getLogger().error(
                    "Failed to load MRI list for date range {}..{} status={}; rethrowing",
                    LogSafe.sanitize(sDate),
                    LogSafe.sanitize(eDate),
                    LogSafe.sanitize(status),
                    e);
            throw new BillingDataLoadException("Failed to load MRI list", e,
                    BillingDataLoadException.Phase.DAO_QUERY,
                    java.util.Map.of(
                            "startDate", sDate == null ? "" : sDate,
                            "endDate", eDate == null ? "" : eDate,
                            "status", status == null ? "" : status));
        }
        return retval;
    }

    /**

     * Returns ohipfilename.

     *

     * @param diskId int

     * @return String

     */

    public String getOhipfilename(int diskId) {
        BillingONDiskName b = diskNameDao.find(diskId);
        return b == null ? "" : b.getOhipFilename();
    }

    /**

     * Returns htmlfilename.

     *

     * @param diskId int

     * @param providerNo String

     * @return String

     */

    public String getHtmlfilename(int diskId, String providerNo) {
        String obj = "";
        List<BillingONFilename> results = filenameDao.findByDiskIdAndProvider(diskId, providerNo);
        for (BillingONFilename result : results) {
            obj = result.getHtmlFilename();
        }
        return obj;
    }

    /**

     * Returns diskname obj.

     *

     * @param diskId String

     * @return BillingDiskNameDto

     */

    public BillingDiskNameDto getDisknameObj(String diskId) {
        BillingDiskNameDto obj = new BillingDiskNameDto();

        BillingONDiskName b = diskNameDao.find(Integer.valueOf(diskId));
        if (b != null) {
            obj.setId("" + b.getId());
            obj.setMonthCode(b.getMonthCode());
            obj.setBatchcount("" + b.getBatchCount());
            obj.setOhipfilename(b.getOhipFilename());
            obj.setGroupno(b.getGroupNo());
            obj.setClaimrecord(b.getCreator());
            obj.setClaimrecord(b.getClaimRecord());
            obj.setCreatedatetime(tsFormatter.format(b.getCreateDateTime()));
            obj.setStatus(b.getStatus());
            obj.setTotal(b.getTotal());
            obj.setUpdatedatetime(tsFormatter.format(b.getTimestamp()));

            List<BillingONFilename> ff = filenameDao.findCurrentByDiskId(b.getId());
            List<DiskFilenameRow> rows = new ArrayList<>();
            for (BillingONFilename f : ff) {
                rows.add(new DiskFilenameRow(
                        "" + f.getId(),
                        f.getHtmlFilename(),
                        f.getProviderOhipNo(),
                        f.getProviderNo(),
                        f.getClaimRecord(),
                        f.getStatus(),
                        f.getTotal()));
            }
            obj.setFilenames(rows);
        }

        return obj;
    }

    /**

     * Returns batch header obj.

     *

     * @param providerData BillingProviderDto

     * @param disk_id String

     * @return BillingBatchHeaderDto

     */

    public BillingBatchHeaderDto getBatchHeaderObj(BillingProviderDto providerData, String disk_id) {
        BillingBatchHeaderDto obj = new BillingBatchHeaderDto();

        List<BillingONHeader> bs = headerDao.findByDiskIdAndProviderRegNum(
                Integer.parseInt(disk_id), providerData.getOhipNo());

        for (BillingONHeader b : bs) {
            obj.setId("" + b.getId());
            obj.setDiskId(disk_id);
            obj.setTranscId(b.getTransactionId());
            obj.setRecId(b.getRecordId());
            obj.setSpecId(b.getSpecId());
            obj.setMohOffice(b.getMohOffice());

            obj.setBatchId(b.getBatchId());
            obj.setOperator(b.getOperator());
            obj.setGroupNum(b.getGroupNum());
            obj.setProviderRegNum(b.getProviderRegNum());
            obj.setSpecialty(b.getSpecialty());
            obj.setHCount(b.gethCount());
            obj.setRCount(b.getrCount());
            obj.setTCount(b.gettCount());
            obj.setBatchDate(dateformatter.format(b.getBatchDate()));

            obj.setCreatedatetime(tsFormatter.format(b.getCreateDateTime()));
            obj.setUpdatedatetime(tsFormatter.format(b.getUpdateDateTime()));
            obj.setCreator(b.getCreator());
            obj.setAction(b.getAction());
            obj.setComment(b.getComment());
        }

        return obj;
    }
}
