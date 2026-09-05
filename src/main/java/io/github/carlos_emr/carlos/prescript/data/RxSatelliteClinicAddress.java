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
package io.github.carlos_emr.carlos.prescript.data;

import java.util.ArrayList;
import java.util.List;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * The satellite-clinic address block a prescriber can put at the head of a printed or faxed
 * prescription, in the one wire shape both ends of that exchange agree on.
 *
 * <p>{@code rx/ViewScript2.jsp} lists the prescriber's satellite clinics and, when one is chosen,
 * sends its block to {@code FrmCustomedPDFServlet} as the {@code scAddress} parameter, which the
 * servlet parses back into clinic name, address, telephone and fax for the page header. The block
 * is therefore a wire format, not presentation: the page must compose it exactly as the servlet
 * splits it (bold prescriber name, then {@code <br>}-separated lines), and the servlet must be able
 * to recompute the set of blocks the page could legitimately have sent, because {@code scAddress}
 * is a request parameter and an outbound fax must not carry a clinic header the caller typed.
 * Keeping the composition and the candidate list here, used by both, is what stops the two from
 * drifting apart.</p>
 *
 * @since 2026-09-05
 */
public final class RxSatelliteClinicAddress {

    private RxSatelliteClinicAddress() {
    }

    /**
     * Composes one satellite clinic block: {@code <b>prescriber</b><br>name<br>address<br>city,
     * province postal<br>Tel: phone<br>Fax: fax}. Every clinic value is HTML-encoded here; the
     * prescriber name and the two labels arrive already encoded because the page localizes and
     * encodes them once for all of its blocks.
     */
    public static String html(String encodedDoctorName, String name, String address, String city, String province,
                              String postal, String phone, String fax, String encodedTelLabel, String encodedFaxLabel) {
        return "<b>" + nz(encodedDoctorName) + "</b><br>"
                + SafeEncode.forHtml(nz(name)) + "<br>"
                + SafeEncode.forHtml(nz(address)) + "<br>"
                + SafeEncode.forHtml(nz(city)) + ", "
                + SafeEncode.forHtml(nz(province)) + " "
                + SafeEncode.forHtml(nz(postal)) + "<br>"
                + nz(encodedTelLabel) + ": "
                + SafeEncode.forHtml(nz(phone)) + "<br>"
                + nz(encodedFaxLabel) + ": "
                + SafeEncode.forHtml(nz(fax));
    }

    /**
     * The clinic part of a block — everything after the bold prescriber name — which is all the
     * servlet's parser reads and therefore all that identifies a satellite clinic. {@code null} when
     * the value is not a block at all.
     */
    public static String clinicPart(String block) {
        if (block == null) {
            return null;
        }
        int end = block.indexOf("</b>");
        return end < 0 ? null : block.substring(end + "</b>".length());
    }

    /**
     * The blocks {@code rx/ViewScript2.jsp} offers the given provider, from the same two sources and
     * in the same order it reads them: the provider's active {@link Site}s when multisites is on,
     * otherwise the {@code clinicSatellite*} properties. Empty when neither is configured, in which
     * case the page shows no satellite selector and no {@code scAddress} is legitimate.
     *
     * @param providerNo the logged-in provider the page lists satellite clinics for
     * @param encodedTelLabel the localized, HTML-encoded telephone label the page used
     * @param encodedFaxLabel the localized, HTML-encoded fax label the page used
     */
    public static List<String> blocksFor(String providerNo, String encodedTelLabel, String encodedFaxLabel) {
        List<String> blocks = new ArrayList<>();
        if (IsPropertiesOn.isMultisitesEnable()) {
            SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
            List<Site> sites = siteDao == null || providerNo == null ? List.of() : siteDao.getActiveSitesByProviderNo(providerNo);
            for (Site s : sites) {
                blocks.add(html("", s.getName(), s.getAddress(), s.getCity(), s.getProvince(), s.getPostal(),
                        s.getPhone(), s.getFax(), encodedTelLabel, encodedFaxLabel));
            }
            return blocks;
        }
        CarlosProperties props = CarlosProperties.getInstance();
        if (props.getProperty("clinicSatelliteName") == null) {
            return blocks;
        }
        String[] names = split(props, "clinicSatelliteName");
        String[] addresses = split(props, "clinicSatelliteAddress");
        String[] cities = split(props, "clinicSatelliteCity");
        String[] provinces = split(props, "clinicSatelliteProvince");
        String[] postals = split(props, "clinicSatellitePostal");
        String[] phones = split(props, "clinicSatellitePhone");
        String[] faxes = split(props, "clinicSatelliteFax");
        for (int i = 0; i < names.length; i++) {
            blocks.add(html("", names[i], at(addresses, i), at(cities, i), at(provinces, i), at(postals, i),
                    at(phones, i), at(faxes, i), encodedTelLabel, encodedFaxLabel));
        }
        return blocks;
    }

    private static String[] split(CarlosProperties props, String key) {
        String value = props.getProperty(key);
        return (value == null ? "" : value).split("\\|");
    }

    /** The page indexes every property list by the name list's index; a shorter list reads as blank. */
    private static String at(String[] values, int i) {
        return i < values.length ? values[i] : "";
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
