/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.util;

import org.apache.commons.text.StringEscapeUtils;

import io.github.carlos_emr.SxmlMisc;

/**
 * Builds and reads the legacy demographic XML fragments stored in
 * {@code demographic.family_doctor} and {@code demographiccust.notes}.
 *
 * <p>Both columns are plain {@code VARCHAR}/{@code TEXT} columns holding a
 * hand-assembled XML fragment, and every reader in the tree pulls values back
 * out with the substring-based {@link SxmlMisc#getXmlContent(String, String)}
 * rather than a real parser. Text nodes therefore have to be escaped on the way
 * in, or crafted input closes the element early and rewrites the stored
 * structure.</p>
 *
 * <p><strong>Escaping and unescaping are a matched pair.</strong> Because the
 * substring readers return the stored bytes verbatim, every read path must run
 * the value back through {@link #unescapeXmlText(String)} exactly once;
 * otherwise {@code Dr O'Brien &amp; Co.} is redisplayed as
 * {@code Dr O'Brien &amp;amp; Co.} and each edit/save cycle compounds the
 * encoding. The accessors on this class ({@link #referralDoctor(String)} and
 * friends) do the extraction and the unescape together so callers cannot get
 * only half of the pair.</p>
 *
 * <p>Only {@code &amp;}, {@code &lt;} and {@code &gt;} (plus characters that are
 * illegal in XML 1.1) are encoded. Apostrophes and double quotes are legal raw
 * inside a text node, and encoding them would inflate the fragment against the
 * 80-character {@code family_doctor} column for ordinary names such as
 * {@code O'Brien} — see {@link #escapeXmlText(String)}.</p>
 *
 * <p>Rows written before this escaping existed hold raw text. Unescaping them is
 * a no-op for everything except the rare legacy value that literally contains an
 * entity such as {@code &amp;amp;}, which is read back as {@code &amp;}.</p>
 *
 * @since 2026-05-29
 */
public final class DemographicXml {

    private static final String RDOHIP_TAG = "rdohip";
    private static final String RD_TAG = "rd";
    private static final String FAMILY_DOC_TAG = "family_doc";
    private static final String UNOTES_TAG = "unotes";

    private DemographicXml() {
    }

    /**
     * Builds the stored family doctor fragment for the demographic table.
     * The returned fragment is stored in {@code demographic.family_doctor};
     * all text content is XML-escaped before it is inserted into the fragment.
     *
     * @param referralDoctorOhip the referring doctor's OHIP number; null becomes empty text
     * @param referralDoctor the referring doctor's display name; null becomes empty text
     * @param familyDoctor the family doctor display name; null omits the optional element
     * @return a {@code <rdohip>}, {@code <rd>}, and optional {@code <family_doc>} XML fragment
     * @since 2026-05-29
     */
    public static String familyDoctor(String referralDoctorOhip, String referralDoctor, String familyDoctor) {
        return "<rdohip>" + escapeXmlText(referralDoctorOhip) + "</rdohip>" +
                "<rd>" + escapeXmlText(referralDoctor) + "</rd>" +
                (familyDoctor != null
                        ? "<family_doc>" + escapeXmlText(familyDoctor) + "</family_doc>"
                        : "");
    }

    /**
     * Wraps demographic notes in the legacy unotes element.
     * The returned fragment is stored in {@code demographiccust.notes};
     * the note text is XML-escaped before it is inserted into the fragment.
     *
     * @param notes the demographic note text; null becomes empty text
     * @return a {@code <unotes>} XML fragment
     * @since 2026-05-29
     */
    public static String userNotes(String notes) {
        return "<unotes>" + escapeXmlText(notes) + "</unotes>";
    }

    /**
     * Reads the referring doctor's OHIP number out of a stored family doctor fragment.
     *
     * @param familyDoctorXml the raw {@code demographic.family_doctor} value
     * @return the decoded {@code <rdohip>} text, {@code ""} when the element is absent,
     *         or {@code null} when {@code familyDoctorXml} is null (matching
     *         {@link SxmlMisc#getXmlContent(String, String)})
     * @since 2026-09-15
     */
    public static String referralDoctorOhip(String familyDoctorXml) {
        return elementText(familyDoctorXml, RDOHIP_TAG);
    }

    /**
     * Reads the referring doctor's name out of a stored family doctor fragment.
     *
     * @param familyDoctorXml the raw {@code demographic.family_doctor} value
     * @return the decoded {@code <rd>} text, {@code ""} when the element is absent,
     *         or {@code null} when {@code familyDoctorXml} is null
     * @since 2026-09-15
     */
    public static String referralDoctor(String familyDoctorXml) {
        return elementText(familyDoctorXml, RD_TAG);
    }

    /**
     * Reads the optional family doctor name out of a stored family doctor fragment.
     *
     * @param familyDoctorXml the raw {@code demographic.family_doctor} value
     * @return the decoded {@code <family_doc>} text, {@code ""} when the element is absent,
     *         or {@code null} when {@code familyDoctorXml} is null
     * @since 2026-09-15
     */
    public static String familyDoc(String familyDoctorXml) {
        return elementText(familyDoctorXml, FAMILY_DOC_TAG);
    }

    /**
     * Reads the note text out of a stored {@code demographiccust.notes} fragment.
     *
     * @param notesXml the raw {@code demographiccust.notes} value
     * @return the decoded {@code <unotes>} text, {@code ""} when the element is absent,
     *         or {@code null} when {@code notesXml} is null
     * @since 2026-09-15
     */
    public static String userNotesText(String notesXml) {
        return elementText(notesXml, UNOTES_TAG);
    }

    /**
     * Escapes a value for insertion as a text node in a legacy demographic fragment.
     *
     * <p>Delegates to {@link StringEscapeUtils#escapeXml11(String)} so that characters
     * which are illegal in XML 1.1 are handled, then folds {@code &apos;} and
     * {@code &quot;} back to the literal characters. Both are legal raw inside a text
     * node, and encoding them costs four or five characters each against the
     * 80-character {@code demographic.family_doctor} column — enough for an ordinary
     * 40-character name such as {@code O'Brien} to be rejected by
     * {@code Demographic.validateFieldLengths()} on an otherwise unrelated save.</p>
     *
     * <p>The fold cannot corrupt user input: a literal {@code &apos;} typed by the user
     * is escaped to {@code &amp;apos;} first, which does not contain the
     * {@code &apos;} sequence being replaced.</p>
     *
     * @param value the raw text; null becomes empty text
     * @return the escaped text, never null
     * @since 2026-05-29
     */
    public static String escapeXmlText(String value) {
        if (value == null) {
            return "";
        }
        return StringEscapeUtils.escapeXml11(value)
                .replace("&apos;", "'")
                .replace("&quot;", "\"");
    }

    /**
     * Reverses {@link #escapeXmlText(String)} for a value read back out of a stored fragment.
     *
     * @param value the stored text node content
     * @return the decoded text, or null when {@code value} is null
     * @since 2026-09-15
     */
    public static String unescapeXmlText(String value) {
        if (value == null) {
            return null;
        }
        return StringEscapeUtils.unescapeXml(value);
    }

    /**
     * Reverses {@link #escapeXmlText(String)} for a value that is already known to be present.
     *
     * <p>This is the exact inverse of {@link #escapeXmlText(String)}: null decodes to empty,
     * never to null. Use it wherever the decoded value is dereferenced straight away — a
     * regex group, a substring, a column read that has already been null-checked — so the
     * call site does not have to carry a defensive null ternary. Use
     * {@link #unescapeXmlText(String)} instead only where a null must be propagated, which
     * is what the element accessors above do to mirror
     * {@link SxmlMisc#getXmlContent(String, String)}.</p>
     *
     * @param value the stored text node content; null becomes empty text
     * @return the decoded text, never null
     * @since 2026-09-15
     */
    public static String unescapeXmlTextOrEmpty(String value) {
        if (value == null) {
            return "";
        }
        return StringEscapeUtils.unescapeXml(value);
    }

    private static String elementText(String xml, String tag) {
        return unescapeXmlText(SxmlMisc.getXmlContent(xml, tag));
    }
}
