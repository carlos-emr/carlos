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
package io.github.carlos_emr.carlos.prevention.pageUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.prevention.PreventionData;
import io.github.carlos_emr.carlos.prevention.PreventionDisplayConfig;
import io.github.carlos_emr.carlos.prevention.PreventionPageData;
import io.github.carlos_emr.carlos.providers.data.ProviderData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Reads, from the chart, everything the prevention page's Print control puts on paper.
 *
 * <p>The Print form used to post every record's date, age, status, comments, reason, vaccine,
 * lot and provider back as hidden fields, and the PDF was drawn from those request values. Three
 * faults followed from that round trip. The packaged ModSecurity front door inspects the print
 * route, so one ordinary clinical comment anywhere in the chart (for example "reviewed with the
 * patient; find attached") had the whole print refused with 403, although the same comment had
 * saved through {@code /prevention/AddPrevention}, whose prose fields are excluded. The page's
 * "other preventions" list numbered its print boxes by its own position while the hidden fields
 * were numbered by configuration position, so ticking one printed a different type. And the PDF
 * printed whatever a request supplied, under the clinic's letterhead.
 *
 * <p>The form now posts only the names of the ticked types. This loader reads the records of
 * those types as the page itself lists them, through {@link PreventionPageData} (which makes the
 * {@code _demographic} read checks for the patient), so the printout cannot differ from the chart.
 * Names that are not configured prevention types, and types the patient has no record of, are
 * ignored rather than printed as empty sections. Comments are read only when the clinic's
 * {@code prevention_show_comments} setting is on, the same setting that shows them inline on the
 * page, so the printout carries exactly what goes on paper.
 *
 * @see PreventionPrintPdf
 * @see PreventionPrint2Action
 * @since 2026-10-16
 */
public class PreventionPrintLoader {

    /** The configuration heading that puts a type in the printout's Screenings part. */
    static final String SCREENINGS_HEADING = "Screenings";

    /**
     * One printed record.
     *
     * @param date the prevention date as the page shows it ({@code yyyy-MM-dd}, or partial)
     * @param age the patient's age at that date
     * @param status the stored status code ({@code 0} completed, {@code 1} refused, {@code 2}
     *        ineligible, {@code 3} completed externally); history-layout types use the same codes
     *        for Yes, Never and Previous
     * @param result the recorded result, or {@code null}
     * @param reason the reason recorded with the result, or {@code null}
     * @param comments the comments, or {@code null} when there are none or the clinic's
     *        {@code prevention_show_comments} setting is off
     * @param location the injection site, or {@code null}
     * @param manufacturer the manufacturer, or {@code null}
     * @param vaccineName the product or brand name, or {@code null}
     * @param lot the lot number, or {@code null}
     * @param dose the dose given, or {@code null}
     * @param provider the name of the provider who gave or recorded it, or {@code null}
     */
    public record Entry(String date, String age, String status, String result, String reason, String comments,
                        String location, String manufacturer, String vaccineName, String lot, String dose,
                        String provider) {
    }

    /**
     * One prevention type in the printout with the patient's records of it, in the page's order.
     *
     * @param preventionType the configured type name, printed as the section title
     * @param screening whether the type belongs under Screenings rather than Immunizations
     * @param historyLayout whether the type uses the history layout (Smoking, PHV), whose status
     *        codes read Yes, Never and Previous
     * @param entries the records, never empty
     */
    public record Section(String preventionType, boolean screening, boolean historyLayout, List<Entry> entries) {
        public Section {
            entries = List.copyOf(entries);
        }
    }

    /**
     * What one Print request puts on paper.
     *
     * @param patient the patient, read through {@code DemographicManager}
     * @param mrpName the most responsible provider's name, or {@code null} when the clinic does not
     *        use the MRP model or the patient has none
     * @param immunizationsOnly whether only immunizations were asked for
     * @param sections the immunization sections followed by the screening sections; empty when
     *        nothing printable was selected
     */
    public record Printout(Demographic patient, String mrpName, boolean immunizationsOnly, List<Section> sections) {
        public Printout {
            sections = List.copyOf(sections);
        }
    }

    private final Supplier<? extends List<? extends Map<String, String>>> preventionTypes;
    private final BiFunction<LoggedInInfo, String, PreventionPageData> pageDataFactory;
    private final Function<String, Map<String, String>> extensionReader;
    private final UnaryOperator<String> providerNames;
    private final BooleanSupplier mrpModel;
    private final BooleanSupplier showComments;

    /** Reads the live configuration, chart and provider records. */
    public PreventionPrintLoader() {
        this(() -> PreventionDisplayConfig.getInstance().getPreventions(), PreventionPageData::new,
                PreventionData::getPreventionKeyValues,
                ProviderData::getProviderName,
                () -> CarlosProperties.getInstance().getBooleanProperty("mrp_model", "yes"),
                () -> CarlosProperties.getInstance().isPropertyActive("prevention_show_comments"));
    }

    /**
     * @param preventionTypes the configured prevention types in page order, each with its
     *        {@code name}, {@code headingName} and {@code layout}
     */
    PreventionPrintLoader(Supplier<? extends List<? extends Map<String, String>>> preventionTypes,
                          BiFunction<LoggedInInfo, String, PreventionPageData> pageDataFactory,
                          Function<String, Map<String, String>> extensionReader,
                          UnaryOperator<String> providerNames,
                          BooleanSupplier mrpModel,
                          BooleanSupplier showComments) {
        this.preventionTypes = preventionTypes;
        this.pageDataFactory = pageDataFactory;
        this.extensionReader = extensionReader;
        this.providerNames = providerNames;
        this.mrpModel = mrpModel;
        this.showComments = showComments;
    }

    /**
     * Reads the printout for the selected prevention types.
     *
     * <p>Sections follow the configured order of prevention types, the order the page lists them
     * in, with every immunization before every screening; the order of the request's values does
     * not matter.
     *
     * @param loggedInInfo the logged-in user
     * @param demographicNo the patient's demographic number
     * @param selectedTypes the ticked prevention type names, possibly {@code null}
     * @param immunizationsOnly whether to leave screenings out
     * @return the printout; its sections are empty when nothing printable was selected
     * @throws NumberFormatException if {@code demographicNo} is not a number
     * @throws IllegalArgumentException if no patient has that number
     * @throws RuntimeException if the user may not read the patient, as {@link PreventionPageData}
     *         throws it
     */
    public Printout load(LoggedInInfo loggedInInfo, String demographicNo, String[] selectedTypes,
                         boolean immunizationsOnly) {
        PreventionPageData pageData = pageDataFactory.apply(loggedInInfo, demographicNo);
        Demographic patient = pageData.getDemographic();
        if (patient == null) {
            throw new IllegalArgumentException("Patient not found");
        }

        boolean withComments = showComments.getAsBoolean();
        Set<String> selected = selectedTypes == null ? Set.of() : new HashSet<>(Arrays.asList(selectedTypes));
        Set<String> printed = new HashSet<>();
        List<Section> immunizations = new ArrayList<>();
        List<Section> screenings = new ArrayList<>();
        for (Map<String, String> type : preventionTypes.get()) {
            String name = type.get("name");
            boolean screening = SCREENINGS_HEADING.equals(type.get("headingName"));
            // The configuration can list a name twice (a CVC picklist entry and the XML item); a
            // type prints once.
            boolean wanted = name != null && selected.contains(name) && !(screening && immunizationsOnly);
            if (wanted && printed.add(name)) {
                List<Entry> entries = pageData.getPreventionData(name).stream()
                        .map(row -> entry(row, withComments)).toList();
                if (!entries.isEmpty()) {
                    (screening ? screenings : immunizations)
                            .add(new Section(name, screening, "history".equals(type.get("layout")), entries));
                }
            }
        }
        immunizations.addAll(screenings);
        return new Printout(patient, mrpName(patient), immunizationsOnly, immunizations);
    }

    private Entry entry(Map<String, Object> row, boolean withComments) {
        Map<String, String> ext = extensionReader.apply(text(row.get("id")));
        return new Entry(text(row.get("prevention_date_no_time")), text(row.get("age")), text(row.get("refused")),
                ext.get("result"), ext.get("reason"), withComments ? ext.get("comments") : null, ext.get("location"),
                ext.get("manufacture"), ext.get("name"), ext.get("lot"), ext.get("dose"),
                text(row.get("provider_name")));
    }

    /**
     * The patient's own MRP, by the clinic's {@code mrp_model} setting. It used to come from a
     * posted provider number looked up in the session's {@code providerBean}, which only the day
     * schedule fills, so a session that had not shown it failed with a NullPointerException.
     */
    private String mrpName(Demographic patient) {
        String providerNo = patient.getProviderNo();
        if (StringUtils.isBlank(providerNo) || !mrpModel.getAsBoolean()) {
            return null;
        }
        String name = providerNames.apply(providerNo);
        return StringUtils.isBlank(name) ? "unknown" : name.trim();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
