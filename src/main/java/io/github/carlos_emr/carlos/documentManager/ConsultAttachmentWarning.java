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
package io.github.carlos_emr.carlos.documentManager;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import jakarta.servlet.ServletRequest;

/**
 * One consultation attachment left out of a rendered consult, named by type and id only.
 *
 * <p>It never holds a file name, title or any other text from the attachment, so it is safe to
 * show to staff, put in a JSON answer, or log. Pages and alerts turn it into words through the
 * bundle keys it names ({@link #getMessageKey()} with {@link #getTypeLabelKey()} and
 * {@link #getId()} as its two parameters), so the text is translated.</p>
 *
 * @since 2026-10-08
 */
public final class ConsultAttachmentWarning implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Why the attachment was left out. */
    public enum Reason {
        /**
         * Its target is gone, deleted, a patient-independent eForm, or another patient's; the
         * consult is sent without it.
         */
        UNAVAILABLE("encounter.oscarConsultationRequest.attachmentWarning.unavailable"),
        /** Its target exists but could not be read or rendered; print and fax refuse the consult. */
        NOT_RENDERED("encounter.oscarConsultationRequest.attachmentWarning.notRendered");

        private final String messageKey;

        Reason(String messageKey) {
            this.messageKey = messageKey;
        }
    }

    private static final String BUNDLE = "oscarResources";
    private static final String TYPE_LABEL_PREFIX = "encounter.oscarConsultationRequest.attachmentType.";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,40}");
    private static final String UNKNOWN_ID = "?";

    private final DocumentType type;
    private final String id;
    private final Reason reason;

    private ConsultAttachmentWarning(DocumentType type, Object id, Reason reason) {
        this.type = type;
        String text = id == null ? null : String.valueOf(id);
        // Ids come from the database, but nothing below may carry anything but a plain id.
        this.id = text != null && SAFE_ID.matcher(text).matches() ? text : UNKNOWN_ID;
        this.reason = reason;
    }

    /**
     * An attachment whose target no longer exists, was deleted, is a patient-independent eForm, or
     * belongs to another patient.
     *
     * @param type the attachment type, or {@code null} if it is not known
     * @param id the attachment id; anything but letters, digits, '-' and '_' shows as "?"
     * @return the warning
     */
    public static ConsultAttachmentWarning unavailable(DocumentType type, Object id) {
        return new ConsultAttachmentWarning(type, id, Reason.UNAVAILABLE);
    }

    /**
     * An attachment whose target exists but could not be read or rendered.
     *
     * @param type the attachment type, or {@code null} if it is not known
     * @param id the attachment id; anything but letters, digits, '-' and '_' shows as "?"
     * @return the warning
     */
    public static ConsultAttachmentWarning notRendered(DocumentType type, Object id) {
        return new ConsultAttachmentWarning(type, id, Reason.NOT_RENDERED);
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isUnavailable() {
        return reason == Reason.UNAVAILABLE;
    }

    /** @return the attachment id, or "?" when it was missing or not a plain id */
    public String getId() {
        return id;
    }

    /**
     * @return the type code and id, such as {@code D:41}, which the fax cover page sends back to
     *         say which left-out attachments staff confirmed
     */
    public String getKey() {
        return (type == null ? "?" : type.getType()) + ":" + id;
    }

    /** @return the bundle key for the sentence, with the type label as {0} and the id as {1} */
    public String getMessageKey() {
        return reason.messageKey;
    }

    /** @return the bundle key for the attachment type's name, such as "Document" */
    public String getTypeLabelKey() {
        if (type == null) {
            return TYPE_LABEL_PREFIX + "unknown";
        }
        switch (type) {
            case DOC:
                return TYPE_LABEL_PREFIX + "document";
            case LAB:
                return TYPE_LABEL_PREFIX + "lab";
            case EFORM:
                return TYPE_LABEL_PREFIX + "eform";
            case HRM:
                return TYPE_LABEL_PREFIX + "hrm";
            case FORM:
                return TYPE_LABEL_PREFIX + "form";
            default:
                return TYPE_LABEL_PREFIX + "unknown";
        }
    }

    /**
     * @param locale the reader's locale
     * @return the sentence in that locale, such as "Document 41 is no longer available (it was
     *         deleted or does not belong to this patient)."
     */
    public String format(Locale locale) {
        return MessageFormat.format(bundleText(locale, getMessageKey()), bundleText(locale, getTypeLabelKey()), id);
    }

    /**
     * @param warnings the warnings to name
     * @param locale the reader's locale
     * @return each warning's type and id, such as "Document 41, Lab result 7", for a sentence
     *         that names several
     */
    public static String formatNames(Collection<ConsultAttachmentWarning> warnings, Locale locale) {
        return warnings.stream()
                .map(warning -> bundleText(locale, warning.getTypeLabelKey()) + " " + warning.id)
                .collect(Collectors.joining(", "));
    }

    /**
     * @param request the request a consult render ran in
     * @return the warnings it left in {@link DocumentAttachmentManager#ATTACHMENT_WARNINGS_ATTRIBUTE};
     *         empty when there are none
     */
    public static List<ConsultAttachmentWarning> fromRequest(ServletRequest request) {
        Object attribute = request.getAttribute(DocumentAttachmentManager.ATTACHMENT_WARNINGS_ATTRIBUTE);
        List<ConsultAttachmentWarning> warnings = new ArrayList<>();
        if (attribute instanceof Collection<?> collection) {
            for (Object warning : collection) {
                if (warning instanceof ConsultAttachmentWarning attachmentWarning) {
                    warnings.add(attachmentWarning);
                }
            }
        }
        return warnings;
    }

    /**
     * @param warnings the warnings to filter
     * @return the ones whose target exists but could not be read or rendered
     */
    public static List<ConsultAttachmentWarning> notRenderedOnly(Collection<ConsultAttachmentWarning> warnings) {
        return warnings.stream().filter(warning -> warning.reason == Reason.NOT_RENDERED).toList();
    }

    /**
     * @param locale the reader's locale
     * @param key a key in {@code oscarResources}, the bundle the JSPs use
     * @return its text in that locale, or the key itself if the bundle has no such key
     */
    public static String bundleText(Locale locale, String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, locale).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    @Override
    public String toString() {
        return getKey() + " " + reason;
    }
}
