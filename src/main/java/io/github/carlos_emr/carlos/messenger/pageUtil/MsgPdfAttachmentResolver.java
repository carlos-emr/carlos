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
package io.github.carlos_emr.carlos.messenger.pageUtil;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;

import io.github.carlos_emr.carlos.commn.dao.EChartDao;
import io.github.carlos_emr.carlos.commn.model.EChart;

/**
 * Resolves the chart items a clinician can attach to a Messenger message as PDFs into the
 * internal application routes that render them.
 *
 * <p>The attachment chooser ({@code generatePreviewPDF.jsp}) used to load each item's page into
 * a hidden frame, read the frame's HTML back in the browser and POST it to
 * {@code messenger/Doc2PDF} as {@code srcText}. That made the server convert whatever HTML the
 * client sent (a forged request could put any markup into a stored message), and the front-door
 * WAF rightly refused the body, which carries the page's own {@code <script>} blocks. The chooser
 * now posts only an item key and the patient number; this class turns those into a fixed,
 * server-chosen route, so the HTML that becomes the PDF is always rendered by CARLOS itself.</p>
 *
 * <p>Routes are application-relative (no context path), ready for
 * {@code RequestDispatcher.include}. Titles are computed here too, from the same message keys the
 * chooser displays, instead of being taken from the request.</p>
 *
 * @since 2026-10-01
 */
public class MsgPdfAttachmentResolver {

    /** Message bundle holding the chooser's labels. */
    public static final String BUNDLE = "oscarResources";

    /**
     * One kind of chart item the chooser offers, with the security object its rendering route
     * requires (checked before the route is included, so a refusal is never rendered as a PDF).
     */
    public enum Item {
        DEMOGRAPHIC("demographic", "_demographic"),
        ENCOUNTER("encounter", "_eChart"),
        PRESCRIPTIONS("prescriptions", "_rx");

        private final String key;
        private final String securityObject;

        Item(String key, String securityObject) {
            this.key = key;
            this.securityObject = securityObject;
        }

        /** @return the value the chooser posts for this item */
        public String key() {
            return key;
        }

        /** @return the security object the item's rendering route requires read on */
        public String securityObject() {
            return securityObject;
        }

        /**
         * @param key a posted item key
         * @return the matching item, or empty for anything else (including {@code null})
         */
        public static Optional<Item> fromKey(String key) {
            for (Item item : values()) {
                if (item.key.equals(key)) {
                    return Optional.of(item);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A resolved attachment: what to render and what to call it.
     *
     * @param item the chart item
     * @param route application-relative route that renders the item's HTML
     * @param title attachment title stored with the PDF and shown to the recipient
     */
    public record Attachment(Item item, String route, String title) {
    }

    private final EChartDao eChartDao;

    public MsgPdfAttachmentResolver(EChartDao eChartDao) {
        this.eChartDao = eChartDao;
    }

    /**
     * Resolves one item for one patient.
     *
     * @param item the chart item
     * @param demographicNo the patient; must be positive
     * @param patientName display name used in the demographic item's title (may be empty)
     * @param labels the chooser's message bundle for the viewer's locale
     * @return the attachment, or empty when the item does not exist for this patient
     */
    public Optional<Attachment> resolve(Item item, int demographicNo, String patientName, ResourceBundle labels) {
        if (item == null || demographicNo <= 0) {
            return Optional.empty();
        }
        switch (item) {
            case DEMOGRAPHIC:
                return Optional.of(new Attachment(item,
                        "/demographic/DemographicPdfLabel?demographic_no=" + demographicNo,
                        safeTitle(joinNonBlank(patientName,
                                label(labels, "messenger.generatePreviewPDF.information", "Information")))));
            case ENCOUNTER:
                EChart chart = eChartDao.getLatestChart(demographicNo);
                if (chart == null || chart.getId() == null) {
                    return Optional.empty();
                }
                return Optional.of(new Attachment(item,
                        "/encounter/ViewEcharthistoryprint?echartid=" + chart.getId()
                                + "&demographic_no=" + demographicNo,
                        safeTitle(joinNonBlank(label(labels, "messenger.generatePreviewPDF.encounter", "Encounter"),
                                chart.getTimestamp() == null ? "" : chart.getTimestamp().toString()))));
            case PRESCRIPTIONS:
                return Optional.of(new Attachment(item,
                        "/rx/ViewPrintDrugProfile2?demographic_no=" + demographicNo,
                        safeTitle(label(labels, "messenger.generatePreviewPDF.currentPrescriptions",
                                "Current Prescriptions"))));
            default:
                return Optional.empty();
        }
    }

    /**
     * Loads the chooser's message bundle for a locale.
     *
     * @param locale viewer locale, or {@code null} for the default
     * @return the bundle
     */
    public static ResourceBundle labels(Locale locale) {
        return ResourceBundle.getBundle(BUNDLE, locale == null ? Locale.getDefault() : locale);
    }

    private static String label(ResourceBundle labels, String key, String fallback) {
        if (labels == null) {
            return fallback;
        }
        try {
            return labels.getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }

    private static String joinNonBlank(String first, String second) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) {
            return b;
        }
        return b.isEmpty() ? a : a + " " + b;
    }

    /**
     * The stored attachment list is a flat {@code <PDF><TITLE>..</TITLE>..} string that
     * {@code Doc2PDF.getXMLTagValue} reads back by plain substring search, so a title holding
     * angle brackets (a patient name typed with them, say) would split the record. Titles are
     * display text, so the brackets are simply dropped.
     */
    static String safeTitle(String title) {
        return title == null ? "" : title.replace("<", "").replace(">", "");
    }
}
