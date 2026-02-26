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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.web.BillingInvoice2Action;
import io.github.carlos_emr.carlos.utility.LocaleUtils;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.github.carlos_emr.carlos.util.DateUtils;

import java.io.InputStream;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

/**
 * @author mweston4
 */
@Service
public class BillingONManager {

    private static final String BILLING_INVOICE_EMAIL_TEMPLATE_FILE = "/billing_invoice_email_notification_template.txt";
    private static final String BILLING_INVOICE_EMAIL_PROPERTIES_FILE = "/billing_invoice_email.properties";

    @Autowired
    private BillingONCHeader1Dao billingONCHeader1Dao;

    @Autowired
    private BillingONExtDao billingONExtDao;

    @Autowired
    private ClinicDAO clinicDAO;

    @Autowired
    private DemographicDao demographicDao;

    protected static Properties emailProperties = getBillingEmailProperties();

    private static Properties getBillingEmailProperties() {

        Properties p = new Properties();
        InputStream is = null;
        try {
            is = BillingInvoice2Action.class.getResourceAsStream(BILLING_INVOICE_EMAIL_PROPERTIES_FILE);
            p.load(is);
        } catch (java.io.IOException e) {
            MiscUtils.getLogger().error("Error reading properties file : " + BILLING_INVOICE_EMAIL_PROPERTIES_FILE, e);
        } finally {
            try {
                if (is != null) is.close();
            } catch (java.io.IOException e) {
                MiscUtils.getLogger().error("Error closing properties file : " + BILLING_INVOICE_EMAIL_PROPERTIES_FILE, e);
            }
        }
        return (p);
    }

    @Deprecated
    public void sendInvoiceEmailNotification(Integer invoiceNo, Locale locale) {
        throw new UnsupportedOperationException("This method is no longer supported.");
        // if (billingONCHeader1 != null) {

        //     if (EmailUtilsOld.isValidEmailAddress(emailAddress)) {

        //         //Get Due Date of Invoice
        //         if (OscarProperties.getInstance().hasProperty("invoice_due_date")) {
        //             if (dueDateExt != null) {


        //         //Compile email                    


        //         try {
        //             is = BillingInvoice2Action.class.getResourceAsStream(BILLING_INVOICE_EMAIL_TEMPLATE_FILE);
        //             try {
        //                 if (is != null) is.close();

        //         try {
    }

    public void addPrintedBillingComment(Integer invoiceNo, Locale locale) {
        String printedMsg = LocaleUtils.getMessage(locale, "billing.billing3rdInv.msgPrinted");
        addBillingComment(printedMsg, invoiceNo, locale);
    }

    public void addEmailedBillingComment(Integer invoiceNo, Locale locale) {
        String emailedMsg = LocaleUtils.getMessage(locale, "billing.billing3rdInv.msgEmailed");
        addBillingComment(emailedMsg, invoiceNo, locale);
    }

    private void addBillingComment(String comment, Integer invoiceNo, Locale locale) {
        BillingONCHeader1 billingONCHeader1 = billingONCHeader1Dao.find(invoiceNo);
        //Log that we printed the invoice in billing comments
        StringBuilder sb = new StringBuilder(billingONCHeader1.getComment().trim());

        if (!sb.toString().isEmpty()) {
            sb.append("\n");
        }

        billingONCHeader1.setComment(sb.append(comment).append(": ").append(DateUtils.formatDateTime(new Date(), locale)).toString());
        billingONCHeader1Dao.merge(billingONCHeader1);
    }
}
