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
<div class="flex border-bottom-gray">
    <label for="eGFR" class="tt" data-toggle="tooltip" title="eGFR">
        <svg height="10" width="10" data-html="true">
            <circle cx="5" cy="5" r="4" stroke="black" stroke-width="1" fill="white"/>
        </svg>
        <input type="checkbox" class="action-required" name="action_required_crcl" data-score="1">
        <strong class="highlight" data-field-name="crcl">CrCl (Creatine Clearance mL/sec)</strong>
    </label>
    <input type="number" step="0.1" name="crcl" id="eGFR" title="eGFR" min="1.5" max="2.3"
           class="bottom-line ecares-input h-si inline-block ml-2" style="width:10%;">
    <!-- expected units 1.5-2.3 millilitres per second (mL/sec) -->
</div>