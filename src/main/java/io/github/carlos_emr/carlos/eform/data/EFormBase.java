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


package io.github.carlos_emr.carlos.eform.data;

import org.jsoup.nodes.Document;
import io.github.carlos_emr.carlos.documentManager.ConvertToEdoc;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.util.StringBuilderUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;

import java.util.Date;

public class EFormBase {
    protected final String imageMarker = "${oscar_image_path}";
    protected final String jsMarker = "${oscar_javascript_path}";
    protected final String signatureMarker = "${oscar_signature_code}";
    protected final String sourceMarker = "${source}";
    protected final String fdidMarker = "${fdid}";

    protected String fdid;
    protected String fid;
    protected String formName;
    protected String formSubject;
    protected String formHtml;
    protected String formFileName;
    protected String formCreator;
    protected String demographicNo;
    protected String providerNo;
    protected String formDate;
    protected String formTime;
    protected boolean showLatestFormOnly = false;
    protected boolean patientIndependent = false;
    protected String roleType;
    private Document document;
    private String realPath;

    public EFormBase() {

    }

    public EFormBase(String fid, String formName, String formSubject,
                     String formFileName, String formHtml, String roleType) {
        this.fid = fid;
        this.formName = formName;
        this.formSubject = formSubject;
        this.formHtml = formHtml;
        this.formFileName = formFileName;
        this.roleType = roleType;
        dateTimeStamp();
    }

    public EFormBase(String fid, String formName, String formSubject,
                     String formFileName, String formHtml, boolean showLatestFormOnly, boolean patientIndependent, String roleType) {
        this.fid = fid;
        this.formName = formName;
        this.formSubject = formSubject;
        this.formHtml = formHtml;
        this.formFileName = formFileName;
        this.showLatestFormOnly = showLatestFormOnly;
        this.patientIndependent = patientIndependent;
        this.roleType = roleType;
        dateTimeStamp();
    }

    public void setImagePath() {
        setImagePath("/oscar");
    }

    public void setImagePath(String contextPath) {
        String output = contextPath + "/eform/displayImage?imagefile=";
        StringBuilder html = new StringBuilder(formHtml);
        int pointer = StringBuilderUtils.indexOfIgnoreCase(html, imageMarker, 0);
        while (pointer >= 0) {
            // Read the delimiter before rewriting: once the prefix is substituted it contains its
            // own '=' and '?', so the opening quote can no longer be found by scanning backwards.
            char openingDelimiter = pointer > 0 ? html.charAt(pointer - 1) : '\0';
            html = html.replace(pointer, pointer + imageMarker.length(), output);
            encodeUrlHostileFileNameCharacters(html, pointer + output.length(), openingDelimiter);
            pointer = StringBuilderUtils.indexOfIgnoreCase(html, imageMarker, 0);
        }
        formHtml = html.toString();
    }

    /**
     * Characters that are legal in a filename but illegal unencoded in a request target. Tomcat
     * rejects the whole request at the HTTP parser — {@code Invalid character found in the request
     * target}, HTTP 400 — before any application code runs, so an eForm packaging an image named
     * e.g. {@code scan-1[1].png} cannot display it at all. Real OSCAR Galaxy packages ship exactly
     * such names (a Windows "(1)" duplicate-download artifact), and the ZIP importer stores them
     * verbatim.
     *
     * <p>Deliberately a small fixed set rather than general URL encoding: {@code /}, {@code ?},
     * {@code &amp;} and {@code =} are structural in these values and encoding them would break
     * references that work today. Quotes and angle brackets are excluded for a different reason —
     * they delimit the value, so treating them as both "encode this" and "the filename ends here"
     * is ambiguous, and no filename an HTML attribute can unambiguously reference contains them.</p>
     *
     * <p>Parentheses are legal in a URL but are encoded anyway, because the render grant's
     * asset-URL pattern must exclude {@code )} to avoid swallowing a CSS {@code url(...)}
     * terminator. Left raw, a name like {@code Requisition-(2021).png} was captured truncated at
     * the closing paren, so the grant held a filename that did not exist and the real request was
     * refused 403 — the asset could never render. They are only encoded where the value is quoted
     * and the closing quote delimits it; inside an unquoted {@code url(...)} the paren still
     * terminates the token.</p>
     */
    private static final String URL_HOSTILE_FILENAME_CHARACTERS = "[]{}|\\^ ()";

    /**
     * Percent-encodes {@link #URL_HOSTILE_FILENAME_CHARACTERS} in the filename that follows a
     * just-substituted {@code imagefile=} prefix, in place.
     *
     * <p>Where the filename ends depends on how it was authored, which is why the delimiter
     * preceding the marker is passed in:</p>
     * <ul>
     *   <li><strong>Quoted</strong> ({@code src="${oscar_image_path}my scan[1].png"}) — only the
     *       matching quote ends the value, so spaces are part of the filename and get encoded.</li>
     *   <li><strong>Unquoted</strong> ({@code url(${oscar_image_path}bg.png)}) — the first quote,
     *       {@code >}, {@code )} or whitespace ends it. The {@code )} case matters for
     *       {@code style="background-image:url(…)"}, where encoding the closing paren would
     *       corrupt the CSS.</li>
     * </ul>
     *
     * <p>The marker also appears in comments and in JavaScript string literals, where forms
     * concatenate the filename on at runtime ({@code '${oscar_image_path}' + name}). There the
     * closing quote immediately follows the prefix, so the scan stops at once and nothing is
     * rewritten.</p>
     *
     * @param html             the document being rewritten
     * @param start            index just past the substituted prefix, where the filename begins
     * @param openingDelimiter the character immediately before the marker, or {@code '\0'} if the
     *                         marker began the document
     */
    private static void encodeUrlHostileFileNameCharacters(
            StringBuilder html, int start, char openingDelimiter) {
        boolean quoted = openingDelimiter == '"' || openingDelimiter == '\'';
        for (int index = start; index < html.length(); index++) {
            char current = html.charAt(index);
            boolean atEnd = quoted
                    ? current == openingDelimiter
                    : current == '"' || current == '\'' || current == '>' || current == ')'
                            || Character.isWhitespace(current);
            if (atEnd) {
                return;
            }
            if (URL_HOSTILE_FILENAME_CHARACTERS.indexOf(current) >= 0) {
                String encoded = String.format("%%%02X", (int) current);
                html.replace(index, index + 1, encoded);
                index += encoded.length() - 1;
            }
        }
    }

    //------------getters/setters----
    public String getFormTime() {
        return formTime;
    }

    public void setFormTime(String formTime) {
        this.formTime = formTime;
    }

    public String getFormDate() {
        return formDate;
    }

    public void setFormDate(String formDate) {
        this.formDate = formDate;
    }

    public java.lang.String getFid() {
        return fid;
    }

    public String getFdid() {
        return fdid;
    }

    public void setFid(String fid) {
        this.fid = fid;
    }

    public String getFormName() {
        return formName;
    }

    public void setFormName(String formName) {
        this.formName = formName;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getFormHtml() {
        if (this.document != null) {
            /*
             * This ensures that HTML edited in a DOM object
             * is fetched as a required String object.
             */
            this.formHtml = document.outerHtml();
            this.document = null;
        }
        return formHtml;
    }

    public void setFormHtml(String formHtml) {
        this.formHtml = formHtml;
        /*
         * Invalidate any cached DOM. getFormHtml() re-serializes `document` whenever it is non-null,
         * so leaving a stale parse here silently discards the string just assigned. That is precisely
         * how the browser-render path lost Rich Text Letter bodies: the composer caches a Document via
         * addHeadJavascript(), then injects the stored letter with setFormHtml(), and getFormHtml()
         * handed back the pre-letter template. The render gates cannot see this -- every subresource
         * loads and the page divs still measure -- so the blank form faxes and archives as if correct.
         */
        this.document = null;
    }

    public String getDemographicNo() {
        return demographicNo;
    }

    public void setDemographicNo(String demographicNo) {
        this.demographicNo = demographicNo;
    }

    public String getFormSubject() {
        if (formSubject == null) {
            return "";
        }
        return formSubject;
    }

    public void setFormSubject(String formSubject) {
        this.formSubject = formSubject;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public void setProviderNo(String providerNo) {
        this.providerNo = providerNo;
    }

    public void setFormFileName(String formFileName) {
        this.formFileName = formFileName;
    }

    public String getFormFileName() {
        return formFileName;
    }

    private void dateTimeStamp() {
        formDate = UtilDateUtilities.DateToString(new Date(), "yyyy-MM-dd");
        formTime = UtilDateUtilities.DateToString(new Date(), "HH:mm:ss");
    }

    public void setFormCreator(String formCreator) {
        this.formCreator = formCreator;
    }

    public String getFormCreator() {
        return this.formCreator;
    }

    public boolean isShowLatestFormOnly() {
        return this.showLatestFormOnly;
    }

    public void setShowLatestFormOnly(boolean showLatestFormOnly) {
        this.showLatestFormOnly = showLatestFormOnly;
    }

    public boolean isPatientIndependent() {
        return this.patientIndependent;
    }

    public void setPatientIndependent(boolean patientIndependent) {
        this.patientIndependent = patientIndependent;
    }

    /*
     * Parse and fetch the JSoup DOM for clean HTML and accurate editing.
     * TODO this method should be used in all of the extended classes in place of the String.replace methods
     */
    protected Document getDocument() {
        if (this.document == null && this.formHtml != null) {
            /*
             * use the ConvertToEdoc utilities for consistent use of the JSoup parser.
             */
            this.document = ConvertToEdoc.parseDocument(this.formHtml);
        }
        if (this.document == null) {
            MiscUtils.getLogger().error("There was a problem while parsing this eForm into a JSoup DOM. Exception needed?");
        }
        return document;
    }

    public String getRealPath() {
        return realPath;
    }

    public void setRealPath(String realPath) {
        this.realPath = realPath;
    }    

}
