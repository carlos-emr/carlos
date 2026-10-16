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
/**
 * Per-provider eChart display settings and the OSCAR 19 style eChart header bar.
 *
 * <p>OSCAR 19 let a clinic choose what its eChart shows (Administration &gt; eChart &gt; eChart
 * Display Settings). CARLOS makes those choices personal: each provider sets them in the "eChart"
 * section of Preferences ({@code provider/providerpreference.jsp}), stored one {@code property} row
 * per setting. {@link io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreference} names
 * the settings and their defaults; the
 * {@link io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreferencePersister} writes the
 * section and the {@link io.github.carlos_emr.carlos.encounter.display.EchartDisplayPreferenceResolver}
 * reads it for every chart surface that honours a setting (header bar, note-panel timer, left
 * navigation Family/Referring Doctor panels, Documents folders, Ocean placeholder).</p>
 *
 * <p>Classes here only read the patient record and the provider's rows; callers have already
 * authorized the viewer for the chart. Values handed to JSPs are raw text, encoded at output.</p>
 *
 * @since 2026-10-16
 */
package io.github.carlos_emr.carlos.encounter.display;
