/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * Maintained by the CARLOS EMR Project.
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

/**
 * The consent section of a chart save, for one consent type: the choice that was posted, and the
 * consent record the page was showing when staff made it.
 *
 * <p>Every chart save re-posts the choice that was shown, even when staff changed only an
 * unrelated field. The shown record lets the save tell a choice made against the current record
 * from one made against a record a colleague has since changed.</p>
 *
 * @param choice            the posted choice
 * @param explicitRequested whether "Patient confirmed consent directly" was ticked; only used with
 *                          {@link Choice#OPT_IN}
 * @param shownSent         whether the page sent the shown record at all; an older form, or
 *                          another caller, does not, and is then not checked
 * @param shownId           id of the consent record the page showed, or {@code null} when it
 *                          showed none
 * @param shownOptOut       whether the shown record was an opt-out, or {@code null} when the page
 *                          showed none
 * @since 2026-09-29
 */
public record ChartConsentRequest(Choice choice, boolean explicitRequested, boolean shownSent,
                                  Integer shownId, Boolean shownOptOut) {

    /** The consent choice a chart save posts for one consent type. */
    public enum Choice {
        OPT_IN,
        OPT_OUT,
        /** The Clear button: remove the patient's record of this consent. */
        CLEAR,
        /** Nothing was posted for this consent type. */
        NONE
    }
}
