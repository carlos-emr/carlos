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
 * What {@link PatientConsentManager#saveChartConsent} did with a chart's consent choice for one
 * consent type.
 *
 * @since 2026-09-29
 */
public enum ChartConsentOutcome {

    /** The choice was applied, including an explicit confirmation when one was asked for. */
    APPLIED,

    /**
     * Nothing was changed: the deciding consent record is no longer the one the chart page showed,
     * so the posted choice was made against out-of-date information.
     */
    STALE,

    /** The opt-in was applied, but the explicit confirmation asked for with it was not recorded. */
    EXPLICIT_NOT_RECORDED,

    /** The request carried no choice, so there was nothing to apply. */
    NO_CHANGE
}
