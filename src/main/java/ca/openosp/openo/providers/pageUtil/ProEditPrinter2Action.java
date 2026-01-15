//CHECKSTYLE:OFF
/**
 * Copyright (c) 2012- Centre de Medecine Integree
 * <p>
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
 * This software was written for
 * Centre de Medecine Integree, Saint-Laurent, Quebec, Canada to be provided
 * as part of the OSCAR McMaster EMR System
 */
package ca.openosp.openo.providers.pageUtil;

import javax.servlet.http.HttpServletRequest;

import ca.openosp.openo.commn.dao.UserPropertyDAO;
import ca.openosp.openo.commn.model.UserProperty;
import ca.openosp.openo.utility.LoggedInInfo;
import ca.openosp.openo.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class ProEditPrinter2Action extends ActionSupport {
    private HttpServletRequest request = ServletActionContext.getRequest();
    private UserPropertyDAO propertyDao = SpringUtils.getBean(UserPropertyDAO.class);

    public String execute() throws Exception {
        String forward;
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        String providerNo = loggedInInfo.getLoggedInProviderNo();

        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT, defaultPrinterNameAppointmentReceipt);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE, defaultPrinterNamePDFEnvelope);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_LABEL, defaultPrinterNamePDFLabel);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL, defaultPrinterNamePDFAddressLabel);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL, defaultPrinterNamePDFChartLabel);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL, defaultPrinterNameClientLabLabel);
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_APPOINTMENT_RECEIPT_SILENT_PRINT, silentPrintAppointmentReceipt ? "yes" : "no");
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_ENVELOPE_SILENT_PRINT, silentPrintPDFEnvelope ? "yes" : "no");
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_LABEL_SILENT_PRINT, silentPrintPDFLabel ? "yes" : "no");
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_ADDRESS_LABEL_SILENT_PRINT, silentPrintPDFAddressLabel ? "yes" : "no");
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_PDF_CHART_LABEL_SILENT_PRINT, silentPrintPDFChartLabel ? "yes" : "no");
        createOrUpdateProperty(providerNo, UserProperty.DEFAULT_PRINTER_CLIENT_LAB_LABEL_SILENT_PRINT, silentPrintClientLabLabel ? "yes" : "no");

        request.setAttribute("status", new String("complete"));
        return SUCCESS;
    }

    private void createOrUpdateProperty(String providerNo, String key, String value) {
        UserProperty prop = propertyDao.getProp(providerNo, key);
        if (prop != null) {
            prop.setValue(value);
        } else {
            prop = new UserProperty();
            prop.setName(key);
            prop.setProviderNo(providerNo);
            prop.setValue(value);
        }
        propertyDao.saveProp(prop);
    }

    private String defaultPrinterNameAppointmentReceipt;
    private String defaultPrinterNamePDFEnvelope;
    private String defaultPrinterNamePDFLabel;
    private String defaultPrinterNamePDFAddressLabel;
    private String defaultPrinterNamePDFChartLabel;
    private String defaultPrinterNameClientLabLabel;

    private boolean silentPrintAppointmentReceipt;
    private boolean silentPrintPDFEnvelope;
    private boolean silentPrintPDFLabel;
    private boolean silentPrintPDFAddressLabel;
    private boolean silentPrintPDFChartLabel;
    private boolean silentPrintClientLabLabel;

    public String getDefaultPrinterNameAppointmentReceipt() {
        return defaultPrinterNameAppointmentReceipt;
    }

    public void setDefaultPrinterNameAppointmentReceipt(String defaultPrinterNameAppointmentReceipt) {
        this.defaultPrinterNameAppointmentReceipt = defaultPrinterNameAppointmentReceipt;
    }

    public String getDefaultPrinterNamePDFEnvelope() {
        return defaultPrinterNamePDFEnvelope;
    }

    public void setDefaultPrinterNamePDFEnvelope(String defaultPrinterNamePDFEnvelope) {
        this.defaultPrinterNamePDFEnvelope = defaultPrinterNamePDFEnvelope;
    }

    public String getDefaultPrinterNamePDFLabel() {
        return defaultPrinterNamePDFLabel;
    }

    public void setDefaultPrinterNamePDFLabel(String defaultPrinterNamePDFLabel) {
        this.defaultPrinterNamePDFLabel = defaultPrinterNamePDFLabel;
    }

    public String getDefaultPrinterNamePDFAddressLabel() {
        return defaultPrinterNamePDFAddressLabel;
    }

    public void setDefaultPrinterNamePDFAddressLabel(String defaultPrinterNamePDFAddressLabel) {
        this.defaultPrinterNamePDFAddressLabel = defaultPrinterNamePDFAddressLabel;
    }

    public String getDefaultPrinterNamePDFChartLabel() {
        return defaultPrinterNamePDFChartLabel;
    }

    public void setDefaultPrinterNamePDFChartLabel(String defaultPrinterNamePDFChartLabel) {
        this.defaultPrinterNamePDFChartLabel = defaultPrinterNamePDFChartLabel;
    }

    public String getDefaultPrinterNameClientLabLabel() {
        return defaultPrinterNameClientLabLabel;
    }

    public void setDefaultPrinterNameClientLabLabel(String defaultPrinterNameClientLabLabel) {
        this.defaultPrinterNameClientLabLabel = defaultPrinterNameClientLabLabel;
    }

    public boolean isSilentPrintAppointmentReceipt() {
        return silentPrintAppointmentReceipt;
    }

    public void setSilentPrintAppointmentReceipt(boolean silentPrintAppointmentReceipt) {
        this.silentPrintAppointmentReceipt = silentPrintAppointmentReceipt;
    }

    public boolean isSilentPrintPDFEnvelope() {
        return silentPrintPDFEnvelope;
    }

    public void setSilentPrintPDFEnvelope(boolean silentPrintPDFEnvelope) {
        this.silentPrintPDFEnvelope = silentPrintPDFEnvelope;
    }

    public boolean isSilentPrintPDFLabel() {
        return silentPrintPDFLabel;
    }

    public void setSilentPrintPDFLabel(boolean silentPrintPDFLabel) {
        this.silentPrintPDFLabel = silentPrintPDFLabel;
    }

    public boolean isSilentPrintPDFAddressLabel() {
        return silentPrintPDFAddressLabel;
    }

    public void setSilentPrintPDFAddressLabel(boolean silentPrintPDFAddressLabel) {
        this.silentPrintPDFAddressLabel = silentPrintPDFAddressLabel;
    }

    public boolean isSilentPrintPDFChartLabel() {
        return silentPrintPDFChartLabel;
    }

    public void setSilentPrintPDFChartLabel(boolean silentPrintPDFChartLabel) {
        this.silentPrintPDFChartLabel = silentPrintPDFChartLabel;
    }

    public boolean isSilentPrintClientLabLabel() {
        return silentPrintClientLabLabel;
    }

    public void setSilentPrintClientLabLabel(boolean silentPrintClientLabLabel) {
        this.silentPrintClientLabLabel = silentPrintClientLabLabel;
    }
}
