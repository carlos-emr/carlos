<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<div class="flex flex-col">
    <div class="clinical-frailty-title-container">
        Clinical Frailty Score
    </div>
    <div class="h-full">
        <table id="clinical-frailty-score-table" class="h-full table table-sm table-striped">
            <tr>
                <th width="50%">Scale</th>
                <th width="25%"><span class="mandatory tt" data-bs-toggle="tooltip"
                                      data-field-name="clinical_frailty_score_patient"
                                      title="Clinical Frailty Scale - patient">Pt.</span></th>
                <th width="25%"><span class="tt" data-bs-toggle="tooltip"
                                      data-field-name="clinical_frailty_score_care_giver"
                                      title="Clinical Frailty Scale - caregiver" rel="cg"
                                      data-names="very_fit,well,well_w_comorbid,vulnerable,mildly_frail,moderately_frail,severely_frail,very_severely_ill,terminall_ill">CG</span>
                </th>
            </tr>
            <tr>
                <td class="tt" title="Robust, active, energetic and motivated, among the fittest for
                their age.">1. Very Fit
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="1"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="1"></td>
            </tr>
            <tr>
                <td class="tt" title="No active disease symptoms but less fit than category one. Very
                active only occasionally (e.g. Seasonally).">2. Well
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="2"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="2"></td>
            </tr>
            <tr>
                <td class="tt" height="60" title="Medical problems are well
                controlled, not regularly active (beyond routine walking).">3. Well With Rx'd co-morbid disease
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="3"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="3"></td>
            </tr>
            <tr>
                <td class="tt" title="Not dependent on others for daily help but
                symptoms limit activities.">4. Apparently vulnerable
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="4"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="4"></td>
            </tr>
            <tr>
                <td class="tt" title="More evident slowing and need help in high order IADLs
                (finances, transportation, heavy housework, medications, shopping,
                cooking). May need help walking outside alone. Suggest initiating
                discussion around care planning and encourage patient to assign an
                SDM and POA.">5. Mildly Frail
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="5"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="5"></td>
            </tr>
            <tr>
                <td class="tt" title="Needs help with all outside activities and keeping
                house, often problems with stairs. Needs help with bathing and may
                need minimal assistance with dressing. Suggest involving patient and
                SDM in goals of care planning in case of future acute health crises.">6. Moderately Frail
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="6"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="6"></td>
            </tr>
            <tr>
                <td class="tt" title="Completely dependent for personal care (due to
                physical and/or cognitive deficits). Stable and not at risk of dying within
                6 months. Consider the context of routine recommendations and
                discuss focusing on quality of life." width="55%">7. Severely Frail
                </td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="7"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="7"></td>
            </tr>
            <tr class="tt" title="Completely dependent, approaching end of life.
            Generally cannot recover even from a minor illness. Empower
            patient/SDM to consider palliative care as a valid treatment option.">
                <td>8. Very severely ill</td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="8"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="8"></td>
            </tr>
            <tr class="tt" title="Approaching end of life, life expectancy less than 6
            months, not otherwise evidently frail.">
                <td>9. Terminally ill</td>
                <td><input type="radio" name="clinical_frailty_score_patient" autocomplete="off" value="9"></td>
                <td><input type="radio" name="clinical_frailty_score_care_giver" autocomplete="off" value="9"></td>
            </tr>
        </table>

    </div>

</div>