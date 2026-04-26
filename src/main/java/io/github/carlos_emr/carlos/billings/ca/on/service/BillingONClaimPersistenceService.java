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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingBatchHeaderData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDiskNameData;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONDiskNameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONFilenameDao;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingONHeaderDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONDiskName;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONFilename;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingONHeader;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONRepoDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnItemPaymentDao;
import io.github.carlos_emr.carlos.commn.dao.BillingOnTransactionDao;
import io.github.carlos_emr.carlos.commn.dao.BillingPaymentTypeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.BillingONPayment;
import io.github.carlos_emr.carlos.commn.model.BillingONRepo;
import io.github.carlos_emr.carlos.commn.model.BillingOnItemPayment;
import io.github.carlos_emr.carlos.commn.model.BillingOnTransaction;
import io.github.carlos_emr.carlos.commn.model.BillingPaymentType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

@org.springframework.stereotype.Service
@org.springframework.context.annotation.Lazy
@org.springframework.transaction.annotation.Transactional
public class BillingONClaimPersistenceService {
    private static final Logger _logger = MiscUtils.getLogger();
    private final BillingONHeaderDao dao;
    private final BillingONCHeader1Dao cheaderDao;
    private final BillingONItemDao itemDao;
    private final BillingONExtDao extDao;
    private final BillingONDiskNameDao diskNameDao;
    private final BillingONFilenameDao filenameDao;
    private final BillingONRepoDao repoDao;

    public BillingONClaimPersistenceService(BillingONHeaderDao dao, BillingONCHeader1Dao cheaderDao, BillingONItemDao itemDao, BillingONExtDao extDao, BillingONDiskNameDao diskNameDao, BillingONFilenameDao filenameDao, BillingONRepoDao repoDao) {
        this.dao = dao;
        this.cheaderDao = cheaderDao;
        this.itemDao = itemDao;
        this.extDao = extDao;
        this.diskNameDao = diskNameDao;
        this.filenameDao = filenameDao;
        this.repoDao = repoDao;
    }

    SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd");
    SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
    SimpleDateFormat tsFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public int addOneBatchHeaderRecord(BillingBatchHeaderData val) {
        BillingONHeader b = new BillingONHeader();
        b.setDiskId(Integer.parseInt(val.getDisk_id()));
        b.setTransactionId(val.getTransc_id());
        b.setRecordId(val.getRec_id());
        b.setSpecId(val.getSpec_id());
        b.setMohOffice(val.getMoh_office());
        b.setBatchId(val.getBatch_id());
        b.setOperator(val.getOperator());
        b.setGroupNum(val.getGroup_num());
        b.setProviderRegNum(val.getProvider_reg_num());
        b.setSpecialty(val.getSpecialty());
        b.sethCount(val.getH_count());
        b.setrCount(val.getR_count());
        b.settCount(val.getT_count());
        b.setBatchDate(new Date());
        b.setCreateDateTime(new Date());
        b.setUpdateDateTime(new Date());
        b.setCreator(val.getCreator());
        b.setAction(val.getAction());
        b.setComment(val.getComment());

        dao.persist(b);

        return b.getId();
    }

    public int addOneClaimHeaderRecord(BillingClaimHeader1Data val) {
        BillingONCHeader1 b = new BillingONCHeader1();
        b.setHeaderId(0);
        b.setTranscId(val.getTransc_id());
        b.setRecId(val.getRec_id());
        b.setHin(val.getHin());
        b.setVer(val.getVer());
        b.setDob(val.getDob());
        b.setPayProgram(val.getPay_program());
        b.setPayee(val.getPayee());
        b.setRefNum(val.getRef_num());
        b.setFaciltyNum(val.getFacilty_num());
        if (val.getAdmission_date().length() > 0)
            try {
                b.setAdmissionDate(dateformatter.parse(val.getAdmission_date()));
            } catch (ParseException e) {/*empty*/}

        b.setRefLabNum(val.getRef_lab_num());
        b.setManReview(val.getMan_review());
        b.setLocation(val.getLocation());
        b.setDemographicNo(Integer.parseInt(val.getDemographic_no()));
        b.setProviderNo(val.getProvider_no());
        String apptNo = StringUtils.trimToNull(val.getAppointment_no());

        if (apptNo != null) {
            b.setAppointmentNo(Integer.parseInt(val.getAppointment_no()));
        } else {
            b.setAppointmentNo(null);
        }

        b.setDemographicName(val.getDemographic_name());
        b.setSex(val.getSex());
        b.setProvince(val.getProvince());
        if (val.getBilling_date().length() > 0)
            try {
                b.setBillingDate(dateformatter.parse(val.getBilling_date()));
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Invalid date", e);
            }
        if (val.getBilling_time().length() > 0)
            try {
                b.setBillingTime(timeFormatter.parse(val.getBilling_time()));
            } catch (ParseException e) {
                MiscUtils.getLogger().error("Invalid time", e);
            }


        b.setTotal(new BigDecimal(val.getTotal() == null ? "0.00" : val.getTotal()));

        if (val.getPaid() == null || val.getPaid().isEmpty()) {
            b.setPaid(new BigDecimal("0.00"));
        } else {
            b.setPaid(new BigDecimal(val.getPaid()));
        }

        b.setStatus(val.getStatus());
        b.setComment(val.getComment());
        b.setVisitType(val.getVisittype());
        b.setProviderOhipNo(val.getProvider_ohip_no());
        b.setProviderRmaNo(val.getProvider_rma_no());
        b.setApptProviderNo(val.getApptProvider_no());
        b.setAsstProviderNo(val.getAsstProvider_no());
        b.setCreator(val.getCreator());
        b.setClinic(val.getClinic());

        cheaderDao.persist(b);

        return b.getId();
    }

    public boolean addItemRecord(List lVal, int id) {

        boolean retval = true;
        for (int i = 0; i < lVal.size(); i++) {
            BillingItemData val = (BillingItemData) lVal.get(i);

            BillingONItem b = new BillingONItem();
            b.setCh1Id(id);
            b.setTranscId(val.getTransc_id());
            b.setRecId(val.getRec_id());
            b.setServiceCode(val.getService_code());
            b.setFee(val.getFee());
            b.setServiceCount(val.getSer_num());
            if (val.getService_date().length() > 0)
                try {
                    b.setServiceDate(dateformatter.parse(val.getService_date()));
                } catch (ParseException e) {/*empty*/}
            b.setDx(val.getDx());
            b.setDx1(val.getDx1());
            b.setDx2(val.getDx2());
            b.setStatus(val.getStatus());

            itemDao.persist(b);
            val.setId(b.getId().toString());
        }
        return retval;
    }

    public boolean addItemPaymentRecord(List lVal, int id, int paymentId, int paymentType) {
        int retval = 0;
        BillingOnItemPayment billOnItemPayment = null;
        Timestamp ts = new Timestamp(new Date().getTime());
        BillingOnItemPaymentDao billOnItemPaymentDao = (BillingOnItemPaymentDao) SpringUtils.getBean(BillingOnItemPaymentDao.class);
        for (int i = 0; i < lVal.size(); i++) {
            BillingItemData val = (BillingItemData) lVal.get(i);
            billOnItemPayment = new BillingOnItemPayment();
            billOnItemPayment.setBillingOnItemId(Integer.parseInt(val.getId()));
            billOnItemPayment.setBillingOnPaymentId(paymentId);
            billOnItemPayment.setCh1Id(id);
            try {
                billOnItemPayment.setDiscount(new BigDecimal(val.getDiscount()).setScale(2, BigDecimal.ROUND_HALF_UP));
            } catch (Exception e) {
                billOnItemPayment.setDiscount(new BigDecimal("0.00"));
            }
            try {
                billOnItemPayment.setPaid(new BigDecimal(val.getPaid()).setScale(2, BigDecimal.ROUND_HALF_UP));
            } catch (Exception e) {
                billOnItemPayment.setPaid(new BigDecimal("0.00"));
            }
            billOnItemPayment.setPaymentTimestamp(ts);
            try {
                billOnItemPayment.setRefund(new BigDecimal(val.getRefund()).setScale(2, BigDecimal.ROUND_HALF_UP));
            } catch (Exception e) {
                billOnItemPayment.setRefund(new BigDecimal("0.00"));
            }
            billOnItemPaymentDao.persist(billOnItemPayment);
            val.setId(billOnItemPayment.getId().toString());
        }
        return (retval != 0);
    }

    private void addCreate3rdInvoiceTrans(BillingClaimHeader1Data billHeader, List<BillingItemData> billItemList, BillingONPayment billOnPayment) {
        if (billItemList.size() < 1) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Timestamp updateTs = new Timestamp(new Date().getTime());
        BillingOnTransaction billTrans = null;
        BillingOnTransactionDao billTransDao = (BillingOnTransactionDao) SpringUtils.getBean(BillingOnTransactionDao.class);
        for (BillingItemData billItem : billItemList) {
            billTrans = new BillingOnTransaction();
            billTrans.setActionType(BillingDataHlp.ACTION_TYPE.C.name());
            try {
                billTrans.setAdmissionDate(sdf.parse(billHeader.getAdmission_date()));
            } catch (Exception e) {
                billTrans.setAdmissionDate(null);
            }
            try {
                billTrans.setBillingDate(sdf.parse(billHeader.getBilling_date()));
            } catch (Exception e) {
                billTrans.setBillingDate(null);
            }

            billTrans.setBillingNotes(billHeader.getComment());
            billTrans.setBillingOnItemPaymentId(Integer.parseInt(billItem.getId()));
            billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
            billTrans.setClinic(billHeader.getClinic());
            billTrans.setCreator(billHeader.getCreator());
            billTrans.setDemographicNo(Integer.parseInt(billHeader.getDemographic_no()));
            billTrans.setDxCode(billItem.getDx());
            billTrans.setFacilityNum(billHeader.getFacilty_num());
            billTrans.setManReview(billHeader.getMan_review());
            billTrans.setPaymentDate(billOnPayment.getPaymentDate());
            billTrans.setPaymentId(billOnPayment.getId());
            billTrans.setPaymentType(billOnPayment.getPaymentTypeId());
            billTrans.setPayProgram(billHeader.getPay_program());
            billTrans.setProviderNo(billHeader.getProviderNo());
            billTrans.setProvince(billHeader.getProvince());
            billTrans.setRefNum(billHeader.getRef_num());
            billTrans.setServiceCode(billItem.getService_code());
            billTrans.setServiceCodeInvoiced(billItem.getFee());
            try {
                billTrans.setServiceCodeDiscount(new BigDecimal(billItem.getDiscount()));
            } catch (Exception e) {
                billTrans.setServiceCodeDiscount(BigDecimal.ZERO);
            }
            billTrans.setServiceCodeNum(billItem.getSer_num());
            try {
                billTrans.setServiceCodePaid(new BigDecimal(billItem.getPaid()));
            } catch (Exception e) {
                billTrans.setServiceCodePaid(BigDecimal.ZERO);
            }
            try {
                billTrans.setServiceCodeRefund(new BigDecimal(billItem.getRefund()));
            } catch (Exception e) {
                billTrans.setServiceCodeRefund(BigDecimal.ZERO);
            }
            billTrans.setStatus(billHeader.getStatus());
            billTrans.setSliCode(billHeader.getLocation());
            billTrans.setUpdateDatetime(updateTs);
            billTrans.setUpdateProviderNo(billHeader.getCreator());
            billTrans.setVisittype(billHeader.getVisittype());
            billTransDao.persist(billTrans);
        }
    }

    public void addCreateOhipInvoiceTrans(BillingClaimHeader1Data billHeader, List<BillingItemData> billItemList) {
        if (billItemList.size() < 1) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Timestamp updateTs = new Timestamp(new Date().getTime());
        BillingOnTransaction billTrans = null;
        BillingOnTransactionDao billTransDao = (BillingOnTransactionDao) SpringUtils.getBean(BillingOnTransactionDao.class);
        for (BillingItemData billItem : billItemList) {
            billTrans = new BillingOnTransaction();
            billTrans.setActionType(BillingDataHlp.ACTION_TYPE.C.name());
            try {
                billTrans.setAdmissionDate(sdf.parse(billHeader.getAdmission_date()));
            } catch (Exception e) {
                billTrans.setAdmissionDate(null);
            }
            try {
                billTrans.setBillingDate(sdf.parse(billHeader.getBilling_date()));
            } catch (Exception e) {
                billTrans.setBillingDate(null);
            }

            billTrans.setBillingNotes(billHeader.getComment());
            billTrans.setBillingOnItemPaymentId(Integer.parseInt(billItem.getId()));
            billTrans.setCh1Id(Integer.parseInt(billHeader.getId()));
            billTrans.setClinic(billHeader.getClinic());
            billTrans.setCreator(billHeader.getCreator());
            billTrans.setDemographicNo(Integer.parseInt(billHeader.getDemographic_no()));
            billTrans.setDxCode(billItem.getDx());
            billTrans.setFacilityNum(billHeader.getFacilty_num());
            billTrans.setManReview(billHeader.getMan_review());
            billTrans.setPaymentDate(null);
            billTrans.setPaymentId(0);
            billTrans.setPaymentType(0);
            billTrans.setPayProgram(billHeader.getPay_program());
            billTrans.setProviderNo(billHeader.getProviderNo());
            billTrans.setProvince(billHeader.getProvince());
            billTrans.setRefNum(billHeader.getRef_num());
            billTrans.setServiceCode(billItem.getService_code());
            billTrans.setServiceCodeInvoiced(billItem.getFee());
            billTrans.setServiceCodeDiscount(BigDecimal.ZERO);
            billTrans.setServiceCodePaid(BigDecimal.ZERO);
            billTrans.setServiceCodeRefund(BigDecimal.ZERO);
            billTrans.setServiceCodeNum(billItem.getSer_num());
            billTrans.setStatus(billHeader.getStatus());
            billTrans.setSliCode(billHeader.getLocation());
            billTrans.setUpdateDatetime(updateTs);
            billTrans.setUpdateProviderNo(billHeader.getCreator());
            billTrans.setVisittype(billHeader.getVisittype());
            billTransDao.persist(billTrans);
        }
    }


    @SuppressWarnings("unchecked")
    public boolean add3rdBillExt(Map<String, String> mVal, int id, ArrayList vecObj) {
        BillingClaimHeader1Data claim1Obj = (BillingClaimHeader1Data) vecObj.get(0);
        boolean retval = true;
        String[] temp = {"billTo", "remitTo", "total", "payment", "discount", "provider_no", "gst", "payDate", "payMethod"};
        String demoNo = mVal.get("demographic_no");
        String dateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        mVal.put("payDate", dateTime);
        String paymentSumParam = null;
        String paymentDateParam = null;
        String paymentTypeParam = null;
        String provider_no = mVal.get("provider_no");
        for (int i = 0; i < temp.length; i++) {
            String val = mVal.get(temp[i]);
            if ("discount".equals(temp[i])) {
                val = mVal.get("total_discount"); // 'refund' stands for write off, here totoal_discount is write off
            }
            if ("payment".equals(temp[i])) {
                val = mVal.get("total_payment");
            }
            BillingONExt billingONExt = new BillingONExt();
            billingONExt.setBillingNo(id);
            billingONExt.setDemographicNo(Integer.parseInt(demoNo));
            billingONExt.setKeyVal(temp[i]);
            billingONExt.setValue(val);
            billingONExt.setDateTime(new Date());
            billingONExt.setStatus('1');
            extDao.persist(billingONExt);

            if (i == 3) paymentSumParam = mVal.get("total_payment"); // total_payment
            else if (i == 7) paymentDateParam = mVal.get(temp[i]); // paymentDate
            else if (i == 8) paymentTypeParam = mVal.get(temp[i]); // paymentMethod

        }

        if (paymentSumParam != null) {
            BillingONPaymentDao billingONPaymentDao = (BillingONPaymentDao) SpringUtils.getBean(BillingONPaymentDao.class);
            BillingPaymentTypeDao billingPaymentTypeDao = (BillingPaymentTypeDao) SpringUtils.getBean(BillingPaymentTypeDao.class);
            BillingONCHeader1 ch1 = cheaderDao.find(id);
            Date paymentDate = null;
            try {
                paymentDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(paymentDateParam);
            } catch (ParseException ex) {
                _logger.error("add3rdBillExt wrong date format " + paymentDateParam);
                return retval;
            }
            if (paymentTypeParam == null || paymentTypeParam.equals("")) {
                paymentTypeParam = "1";
            }
            BillingPaymentType type = billingPaymentTypeDao.find(Integer.parseInt(paymentTypeParam));
            BillingONPayment payment = null;

            if (paymentSumParam != null) {
                payment = new BillingONPayment();
                payment.setTotal_payment(BigDecimal.valueOf(Double.parseDouble(paymentSumParam)));
                payment.setTotal_discount(BigDecimal.valueOf(Double.parseDouble(mVal.get("total_discount"))));
                payment.setTotal_refund(new BigDecimal(0));
                payment.setPaymentDate(paymentDate);
                payment.setBillingOnCheader1(ch1);
                payment.setBillingNo(id);
                payment.setCreator(claim1Obj.getCreator());
                payment.setPaymentTypeId(Integer.parseInt(paymentTypeParam));

                //payment.setBillingPaymentType(type);
                billingONPaymentDao.persist(payment);
                addItemPaymentRecord((List) vecObj.get(1), id, payment.getId(), Integer.parseInt(paymentTypeParam));
                addCreate3rdInvoiceTrans((BillingClaimHeader1Data) vecObj.get(0), (List<BillingItemData>) vecObj.get(1), payment);
            }
        }
        return retval;
    }


    public int addOneItemRecord(BillingItemData val) throws ParseException {
        BillingONItem item = new BillingONItem();
        item.setCh1Id(Integer.parseInt(val.getCh1_id()));
        item.setTranscId(val.getTransc_id());
        item.setRecId(val.getRec_id());
        item.setServiceCode(val.getService_code());
        item.setFee(val.getFee());
        item.setServiceCount(val.getSer_num());
        item.setServiceDate(dateformatter.parse(val.getService_date()));
        item.setDx(val.getDx());
        item.setDx1(val.getDx1());
        item.setDx2(val.getDx2());
        item.setStatus(val.getStatus());
        BillingONItem returnItem = itemDao.saveEntity(item);
        return returnItem.getId(); //return ID

    }

    public boolean add3rdBillExt(Map<String, String> mVal, int id) {
        boolean retval = true;
        String[] temp = {"billTo", "remitTo", "total", "payment", "refund", "provider_no", "gst", "payDate", "payMethod"};
        String demoNo = mVal.get("demographic_no");
        String dateTime = UtilDateUtilities.getToday("yyyy-MM-dd HH:mm:ss");
        mVal.put("payDate", dateTime);

        BillingONPaymentDao billingONPaymentDao = SpringUtils.getBean(BillingONPaymentDao.class);
        BillingONPayment newPayment = new BillingONPayment();
        BillingONCHeader1 ch1 = cheaderDao.find(id);
        newPayment.setBillingOnCheader1(ch1);
        newPayment.setPaymentDate(UtilDateUtilities.StringToDate(dateTime));

        for (int i = 0; i < temp.length; i++) {
            BillingONExt b = new BillingONExt();
            b.setBillingNo(id);
            b.setDemographicNo(Integer.valueOf(demoNo));
            b.setKeyVal(temp[i]);
            b.setValue(mVal.get(temp[i]));
            b.setDateTime(new Date());
            b.setStatus('1');
            b.setPaymentId(0);
            newPayment.getBillingONExtItems().add(b);
        }

        billingONPaymentDao.persist(newPayment);

        return retval;
    }

    // add disk file
    public int addBillingDiskName(BillingDiskNameData val) {
        BillingONDiskName b = new BillingONDiskName();
        b.setMonthCode(val.getMonthCode());
        b.setBatchCount(Integer.parseInt(val.getBatchcount()));
        b.setOhipFilename(val.getOhipfilename());
        b.setGroupNo(val.getGroupno());
        b.setCreator(val.getCreator());
        b.setClaimRecord(val.getClaimrecord());
        b.setCreateDateTime(new Date());
        b.setStatus(val.getStatus());
        b.setTotal(val.getTotal());

        diskNameDao.persist(b);

        int retval = b.getId();

        if (b.getId() > 0) {
            // add filenames, if needed
            for (int i = 0; i < val.getProviderohipno().size(); i++) {
                BillingONFilename f = new BillingONFilename();
                f.setDiskId(b.getId());
                f.setHtmlFilename((String) val.getHtmlfilename().get(i));
                f.setProviderOhipNo((String) val.getProviderohipno().get(i));
                f.setProviderNo((String) val.getProviderno().get(i));
                f.setClaimRecord((String) val.getVecClaimrecord().get(0));
                f.setStatus((String) val.getVecStatus().get(0));
                f.setTotal((String) val.getVecTotal().get(0));
                filenameDao.persist(f);
            }

        } else {
            retval = 0;
        }

        return retval;
    }

    public int addRepoDiskName(BillingDiskNameData val) {
        int retval = 0;
        BillingONRepo b = new BillingONRepo();
        b.sethId(Integer.parseInt(val.getId()));
        b.setCategory("billing_on_diskname");
        b.setContent(val.getMonthCode() + "|" + val.getBatchcount() + "|" + val.getOhipfilename() + "|" + val.getGroupno() + "|" + val.getCreator()
                + "|" + val.getClaimrecord() + "|" + val.getCreatedatetime() + "|" + val.getStatus() + "|" + val.getTotal() + "|"
                + val.getUpdatedatetime());
        b.setCreateDateTime(new Date());

        repoDao.persist(b);
        retval = b.getId();

        if (b.getId() > 0) {
            // add filenames, if needed
            for (int i = 0; i < val.getProviderohipno().size(); i++) {
                BillingONRepo r = new BillingONRepo();
                r.sethId(Integer.valueOf((String) val.getVecFilenameId().get(i)));
                r.setCategory("billing_on_filename");
                r.setContent(val.getId() + "|" + val.getHtmlfilename().get(i) + "|"
                        + val.getProviderohipno().get(i) + "|" + val.getProviderno().get(i) + "|" + val.getVecClaimrecord().get(0)
                        + "|" + val.getVecStatus().get(0) + "|" + val.getVecTotal().get(0) + "|" + val.getUpdatedatetime());

                r.setCreateDateTime(new Date());

                repoDao.persist(r);
            }
        } else {
            retval = 0;
        }
        return retval;
    }

    public boolean updateDiskName(BillingDiskNameData val) {
        BillingONDiskName b = diskNameDao.find(Integer.parseInt(val.getId()));
        if (b != null) {
            b.setCreator(val.getCreator());
            diskNameDao.merge(b);
        }
        return true;
    }

    public int addRepoBatchHeader(BillingBatchHeaderData val) {
        BillingONRepo b = new BillingONRepo();
        b.sethId(Integer.parseInt(val.getId()));
        b.setCategory("billing_on_header");
        b.setContent(val.getDisk_id() + "|" + val.getTransc_id() + "|" + val.getRec_id() + "|" + val.getSpec_id() + "|" + val.getMoh_office() + "|"
                + val.getBatch_id() + "|" + val.getOperator() + "|" + val.getGroup_num() + "|" + val.getProvider_reg_num() + "|"
                + val.getSpecialty() + "|" + val.getH_count() + "|" + val.getR_count() + "|" + val.getT_count() + "|" + val.getBatch_date()
                + "|" + val.getCreatedatetime() + "|" + val.getUpdatedatetime() + "|" + val.getCreator() + "|" + val.getAction() + "|"
                + val.getComment());
        b.setCreateDateTime(new Date());
        repoDao.persist(b);

        return b.getId();
    }

    // TODO more update data
    public boolean updateBatchHeaderRecord(BillingBatchHeaderData val) {
        BillingONHeader b = dao.find(Integer.parseInt(val.getId()));
        if (b != null) {
            b.setMohOffice(val.getMoh_office());
            b.setBatchId(val.getBatch_id());
            b.setSpecialty(val.getSpecialty());
            b.setCreator(val.getCreator());
            b.setUpdateDateTime(new Date());
            b.setAction(val.getAction());
            b.setComment(val.getComment());
            dao.merge(b);
        }

        return true;
    }

}
