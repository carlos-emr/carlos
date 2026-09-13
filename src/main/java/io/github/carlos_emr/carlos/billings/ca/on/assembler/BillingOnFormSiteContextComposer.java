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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingMultisiteContext;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.ClinicNbr;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Composer that pre-loads the multisite + RMA / clinic-nbr context the
 * legacy {@code billingON.jsp} fetched inline via 4 separate
 * {@code SpringUtils.getBean()} calls scattered across the form body
 * (provider/site setup near the header and multisite blocks around
 * the provider picker and service grid):
 *
 * <ul>
 *   <li>{@link SiteDao#getActiveSitesByProviderNo} — multisite list (only
 *       when {@link IsPropertiesOn#isMultisitesEnable()})</li>
 *   <li>{@link OscarAppointmentDao#findAppointmentAndProviderByAppointmentNo}
 *       — default selected site + xml_provider for the appointment</li>
 *   <li>{@link ClinicNbrDao#findAll} — clinic numbers (only when
 *       {@code rma_enabled=true})</li>
 *   <li>{@link ProviderDao#getProvider}.{@code .getComments()} →
 *       {@code SxmlMisc.getXmlContent("xml_p_nbr")} — selected
 *       clinic-nbr prefix from the user provider's comments XML</li>
 * </ul>
 *
 * <p>Mutates the supplied builder with the result; nothing returned.
 * The legacy multisite block was 100+ lines of inline scriptlet — this
 * composer replaces all of it with structured DTOs the JSP iterates
 * via JSTL.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingOnFormSiteContextComposer {

    private final SiteDao siteDao;
    private final OscarAppointmentDao oscarAppointmentDao;
    private final ClinicNbrDao clinicNbrDao;
    private final ProviderDao providerDao;

    public BillingOnFormSiteContextComposer(SiteDao siteDao,
                                     OscarAppointmentDao oscarAppointmentDao,
                                     ClinicNbrDao clinicNbrDao,
                                     ProviderDao providerDao) {
        this.siteDao = siteDao;
        this.oscarAppointmentDao = oscarAppointmentDao;
        this.clinicNbrDao = clinicNbrDao;
        this.providerDao = providerDao;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    void populate(BillingOnFormViewModel.Builder b,
                  HttpServletRequest request,
                  String userNo,
                  String apptProviderNo,
                  String apptNo) {
        boolean multisiteEnabled = IsPropertiesOn.isMultisitesEnable();
        b.multisiteEnabled(multisiteEnabled);

        if (multisiteEnabled) {
            populateMultisite(b, request, userNo, apptNo);
        } else {
            b.multisiteSites(Collections.emptyList())
                    .defaultSelectedSite(nullToEmpty(request.getParameter("site")))
                    .defaultXmlProvider("");
        }

        boolean rmaEnabled = CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true");
        b.rmaEnabled(rmaEnabled);

        if (rmaEnabled && clinicNbrDao != null) {
            List<BillingMultisiteContext.ClinicNbrEntry> clinicNbrs = new ArrayList<>();
            for (ClinicNbr c : clinicNbrDao.findAll()) {
                String value = nullToEmpty(c.getNbrValue());
                String label = String.format("%s | %s", value, nullToEmpty(c.getNbrString()));
                clinicNbrs.add(new BillingMultisiteContext.ClinicNbrEntry(value, label));
            }
            b.clinicNbrs(clinicNbrs);
        } else {
            b.clinicNbrs(Collections.emptyList());
        }

        // Selected clinic-nbr prefix: from the user provider's comments XML
        // (xml_p_nbr). The provider lookup is keyed on apptProviderNo with a
        // fallback to userNo when the bill is manual ("none" / empty).
        String selectedClinicNbrPrefix = "";
        if (rmaEnabled) {
            String providerSearch = (apptProviderNo == null || apptProviderNo.isEmpty()
                    || "none".equalsIgnoreCase(apptProviderNo))
                    ? nullToEmpty(userNo) : apptProviderNo;
            if (!providerSearch.isEmpty()) {
                Provider p = providerDao.getProvider(providerSearch);
                if (p != null) {
                    selectedClinicNbrPrefix = nullToEmpty(SxmlMisc.getXmlContent(p.getComments(), "xml_p_nbr"));
                }
            }
        }
        b.selectedClinicNbrPrefix(selectedClinicNbrPrefix);
    }

    private void populateMultisite(BillingOnFormViewModel.Builder b,
                                   HttpServletRequest request,
                                   String userNo,
                                   String apptNo) {
        // Site list (with each site's providers).
        List<BillingMultisiteContext.MultisiteSite> sites = new ArrayList<>();
        for (Site site : siteDao.getActiveSitesByProviderNo(nullToEmpty(userNo))) {
            List<BillingMultisiteContext.MultisiteProvider> siteProviders = new ArrayList<>();
            Set<Provider> raw = site.getProviders();
            if (raw != null) {
                List<Provider> sortedProviders = new ArrayList<>(raw);
                sortedProviders.sort(new Provider().ComparatorName());
                for (Provider p : sortedProviders) {
                    if ("1".equals(p.getStatus())
                            && p.getOhipNo() != null && !p.getOhipNo().isBlank()) {
                        siteProviders.add(new BillingMultisiteContext.MultisiteProvider(
                                nullToEmpty(p.getProviderNo()),
                                nullToEmpty(p.getOhipNo()),
                                nullToEmpty(p.getLastName()),
                                nullToEmpty(p.getFirstName())));
                    }
                }
            }
            sites.add(new BillingMultisiteContext.MultisiteSite(
                    nullToEmpty(site.getName()),
                    nullToEmpty(site.getBgColor()),
                    siteProviders));
        }
        b.multisiteSites(sites);

        // Default selected-site + xml_provider come from the appointment when
        // no `site` request param has been chosen yet. Without this lookup,
        // the multisite dropdown lands on a blank default for an appointment
        // already attached to a site.
        String selectedSite = nullToEmpty(request.getParameter("site"));
        String defaultXmlp = "";
        if (selectedSite.isEmpty() && oscarAppointmentDao != null) {
            try {
                int apptNoInt = ConversionUtils.fromIntString(nullToEmpty(apptNo));
                for (io.github.carlos_emr.carlos.commn.dao.projection.AppointmentProviderRow row :
                        oscarAppointmentDao.findAppointmentAndProviderByAppointmentNo(apptNoInt)) {
                    selectedSite = row.location();
                    defaultXmlp = row.appointmentProviderNo() + "|" + row.providerOhipNo();
                }
            } catch (RuntimeException rtEx) {
                MiscUtils.getLogger().warn(
                        "BillingOnFormSiteContextComposer: appointment lookup failed; "
                        + "rendering with empty default-site context",
                        rtEx);
                b.siteContextDegraded(true);
            }
        }
        b.defaultSelectedSite(selectedSite);
        b.defaultXmlProvider(defaultXmlp);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
