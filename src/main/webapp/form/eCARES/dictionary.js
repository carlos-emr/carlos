/*
 *
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved. *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version. *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. * * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA. *
 *
 * <OSCAR TEAM>
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

const ECGA_DICTIONARY = [
    {
        field_name: 'action_required_crcl',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_cognition',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_emotional',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_motivation',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_communication',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_sleep',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_pain',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_immunizations',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_advance_directive_in_place',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_code_status',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_control_of_life_events',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_usual_activities',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_exercise',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_smoke',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_strength',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_balance',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_mobility',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_nutrition',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_elimination',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_adls',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_iadls',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_enough_income',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_socially_engaged',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_marital',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_lives',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_home',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_steps',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_supports',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_caregiver_relationship',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'action_required_caregiver_stress',
        field_type: 'checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'chief_lifelong_occupation',
        field_type: 'Textbox',
        data_type: 'Text',
        mandatory: false,
        score: false
    },
    {
        field_name: 'education',
        field_type: 'Textbox',
        data_type: 'select',
        mandatory: false,
        score: false
    },
    {
        field_name: 'crcl',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Cognition',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Delirium',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Mini-Cog',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: true,
        score: true
    },
    {
        field_name: 'MOCA',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: true,
        score: true
    },
    {
        field_name: 'FAST',
        field_type: 'Select',
        data_type: 'Number',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Low Mood',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Depression',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Anxiety',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Fatigue',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Hallucination',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Delusion',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Other',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Motivation',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Health Attitude',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Speech',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Hearing',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Vision',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Sleep',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Daytime Drowsiness',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Pain',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Zoster',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Influenza',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Pneumococcal',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Tetanus and Diphtheria',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Hep A',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Hep B',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Advance Directive in Place',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'code_status',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Control of Life Events',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Usual Activities',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Exercise',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Smoke',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: true
    },
    {
        field_name: 'Strength',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Upper Proximal',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Upper Distal',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Lower Proximal',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Lower Distal',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Balance',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Falls',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Falls Number',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Walk Outside',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Walking',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Transfers',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Bed',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Aid',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Five Times Sit to Stand Time',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Five Times Sit to Stand Attempt',
        field_type: 'Select',
        data_type: 'Number',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Five Times Sit to Stand Crossed Arms',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Weight',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Appetite',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Bowel',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Constipation',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Bladder',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Catheter',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Feeding',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Bathing',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Dressing',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Toileting',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Cooking',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Cleaning',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Shopping',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Meds',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Driving',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Banking',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: true
    },
    {
        field_name: 'Enough Income',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: true
    },
    {
        field_name: 'Socially Engaged',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: true
    },
    {
        field_name: 'Marital',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Lives',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: true
    },
    {
        field_name: 'Home',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Steps',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Supports',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Requires more support',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Caregiver Relationship',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Caregiver Stress',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Caregiver Occupation',
        field_type: 'Textbox',
        data_type: 'Text',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Problems',
        field_type: 'Textbox',
        data_type: 'Text',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Adj Required',
        field_type: 'Checkbox',
        data_type: 'Boolean',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Associated Medications',
        field_type: 'Textbox',
        data_type: 'Text',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Assessor',
        field_type: 'Textbox',
        data_type: 'Text',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Date',
        field_type: 'Textbox',
        data_type: 'Date',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Deficit based Frailty Score',
        field_type: 'Textbox',
        data_type: 'Number',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Clinical Frailty Score Patient',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Clinical Frailty Score Care Giver',
        field_type: 'radio',
        data_type: 'discrete',
        mandatory: false,
        score: false
    },
    {
        field_name: 'Num Problem',
        field_type: 'n/a',
        data_type: 'Number',
        mandatory: false,
        score: true
    },
    {
        field_name: 'Num Medication',
        field_type: 'n/a',
        data_type: 'Number',
        mandatory: false,
        score: true
    },
    {
        field_name: 'PHN',
        field_type: 'n/a',
        data_type: 'Number',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Patient Name',
        field_type: 'n/a',
        data_type: 'Text',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Gender/Sex',
        field_type: 'n/a',
        data_type: 'Text',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Date of Birth',
        field_type: 'n/a',
        data_type: 'Text',
        mandatory: true,
        score: false
    },
    {
        field_name: 'EMR File Number',
        field_type: 'n/a',
        data_type: 'Number',
        mandatory: true,
        score: false
    },
    {
        field_name: 'Feedback Comments',
        field_type: 'Textarea',
        data_type: 'Text',
        mandatory: false,
        score: false
    },
]


const ECGA_DICTIONARY_FILTERED = ECGA_DICTIONARY.filter(d => {
    // We don't show this anywhere in the form
    if (d.field_name === 'Five Times Sit to Stand Attempt') {
        return false
    }
    return true
})


const ECGA_MANDATORY = ECGA_DICTIONARY_FILTERED.filter(d => {

    //These must be loaded in from oscar itself or no longer needed
    const skip = {
        problems: true,
        associated_medications: true,
        phn: true,
        patient_name: true,
        'gender/sex': true,
        date_of_birth: true,
        emr_file_number: true,
        encounter_notes: true
    }
    const name = d.field_name.toLowerCase().replace(/\s/g, "_")
    if (skip[name] === true) {
        return false
    }

    return d.mandatory === true
})

const ECGA_SCORE = ECGA_DICTIONARY.filter(d => {

    if (d.score === true) {
        return true
    }

    return false
})
