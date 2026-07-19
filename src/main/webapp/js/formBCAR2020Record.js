var demographicNo;
var formId;
var minYear = 1900;
var maxYear = 9900;
var maxPage1Height = 1485;
var maxPage2Height = 1265;
var maxPage3Height = 1185;
var maxPage4Height = 1400;
var maxPage5Height = 1400;
var maxPage6Height = 1250;
var dtCh = '/';

var provNo;

var page = 0;


function init(pageNo) {
    page = pageNo;
    if (page === 1) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage1Height));
    } else if (page === 2) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage2Height));
    } else if (page === 3) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage3Height));
    } else if (page === 4) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage4Height));
    } else if (page === 5) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage5Height));
    } else if (page === 6) {
        window.resizeTo(window.outerWidth, Math.min(screen.availHeight, maxPage6Height));
    } else {
        window.resizeTo(window.outerWidth, screen.availHeight);
    }

    demographicNo = $('#demographicNo').val();
    formId = $('#formId').val();
    provNo = $('#user').val();

    //Enable Calendars
    calendars(page);
    dialogs(page);

    if (page === 1) {
        // Load multi-select checkbox dropdowns
        var ethnicity = $("textarea[name='t_ethnicity']").val();
        $('#ethnicitySelectPicker').selectpicker('val', ethnicity.split(','));
        $('#ethnicitySelectPicker').on('changed.bs.select',
            function (e, clickedIndex, newValue, oldValue) {
                var pickerValue = $('#ethnicitySelectPicker').selectpicker('val')
                $("textarea[name='t_ethnicity']").val(pickerValue);
                $("textarea[name='t_ethnicity']").prop('title', pickerValue);
            });

        //Enable All No Links
        $('#presentPregnancyNo').click(function () {
            allNo('presentPregnancy');
            return false;
        });
        $('#medicalHistoryNo').click(function () {
            allNo('medicalHistory');
            return false;
        });
        $('#lifestyleSocialNo').click(function () {
            allNo('lifestyleSocial');
            return false;
        });
        $('#familyHistoryNo').click(function () {
            allNo('familyHistory');
            return false;
        });
        $('#initialExam1Norm').click(function () {
            allNo('initialExam1');
            return false;
        });
        $('#initialExam2Norm').click(function () {
            allNo('initialExam2');
            return false;
        });

        //Enable Toggle Rules and Default States for standard Yes/No/Details Fields
        toggleFieldRules('presentPregnancyBleeding');
        toggleFieldRules('presentPregnancyNausea');
        toggleFieldRules('presentPregnancyTravel');
        toggleFieldRules('presentPregnancyInfection');
        toggleFieldRules('presentPregnancyOther');

        toggleFieldRules('medicalHistorySurgery');
        toggleFieldRules('medicalHistoryAnestheticComplications');
        toggleFieldRules('medicalHistoryNeuro');
        toggleFieldRules('medicalHistoryResp');
        toggleFieldRules('medicalHistoryAbdo');
        toggleFieldRules('medicalHistoryGyne');
        toggleFieldRules('medicalHistoryOther');
        toggleFieldRules('medicalHistoryHematology');

        toggleFieldRules('lifestyleDiet');
        toggleFieldRules('lifestyleExercise');
        toggleFieldRules('lifestyleFinancial');
        toggleFieldRules('lifestyleHousing');
        toggleFieldRules('lifestyleTransportation');
        toggleFieldRules('lifestyleSafety');
        toggleFieldRules('lifestyleRelationships');
        toggleFieldRules('lifestyleOther');

        toggleFieldRules('familyHistoryAnestheticComp');
        toggleFieldRules('familyHistoryHypertension');
        toggleFieldRules('familyHistoryThromboembolic');
        toggleFieldRules('familyHistoryDiabetes');
        toggleFieldRules('familyHistoryMentalHealth');
        toggleFieldRules('familyHistorySubstanceUse');
        toggleFieldRules('familyHistoryOther');

        //Present Pregnancy - ART
        toggleFieldRulesEx('c_presentPregnancyARTNo', 'c_presentPregnancyARTYes', ['c_presentPregnancyARTOvaStim', 'c_presentPregnancyARTIUIOnly', 'c_presentPregnancyARTOvaStimIUI', 't_presentPregnancyARTIVFDetails', 't_presentPregnancyARTICSIDetails', 't_presentPregnancyARTOtherDetails', 'c_presentPregnancyARTIVF', 'c_presentPregnancyARTICSI', 'c_presentPregnancyARTOther']);
        toggleFieldRulesSingle('c_presentPregnancyARTICSI', ['t_presentPregnancyARTICSIDetails']);
        toggleFieldRulesSingle('c_presentPregnancyARTIVF', ['t_presentPregnancyARTIVFDetails']);
        toggleFieldRulesSingle('c_presentPregnancyARTOther', ['t_presentPregnancyARTOtherDetails']);
        toggleFieldRulesUnique(['c_presentPregnancyARTOvaStim', 'c_presentPregnancyARTIUIOnly', 'c_presentPregnancyARTOvaStimIUI', 'c_presentPregnancyARTIVF', 'c_presentPregnancyARTICSI', 'c_presentPregnancyARTOther']);

        //Family History - Inherited Conditions
        toggleFieldRulesEx('c_familyHistoryInheritedConditionsNo', 'c_familyHistoryInheritedConditionsYes', ['t_familyHistoryInheritedConditionsMother', 't_familyHistoryInheritedConditionsFather']);

        //Medical History - CV
        toggleFieldRulesEx('c_medicalHistoryCVNo', 'c_medicalHistoryCVYes', ['c_medicalHistoryCVHypert', 'c_medicalHistoryCVPrevHypert', 't_medicalHistoryCVOtherDetails', 'c_medicalHistoryCVOther']);
        toggleFieldRulesSingle('c_medicalHistoryCVOther', ['t_medicalHistoryCVOtherDetails']);
        toggleFieldRulesUnique(['c_medicalHistoryCVHypert', 'c_medicalHistoryCVPrevHypert', 'c_medicalHistoryCVOther']);

        //Medical History - Endocrine
        toggleFieldRulesEx('c_medicalHistoryEndocrineNo', 'c_medicalHistoryEndocrineYes', ['c_medicalHistoryEndocrineT1DM', 'c_medicalHistoryEndocrineT2DM', 'c_medicalHistoryEndocrinePrevGDM', 'c_medicalHistoryEndocrineThyroid', 't_medicalHistoryEndocrineOtherDetails', 'c_medicalHistoryEndocrineOther']);
        toggleFieldRulesSingle('c_medicalHistoryEndocrineOther', ['t_medicalHistoryEndocrineOtherDetails']);

        //Medical History - Mental Health
        toggleFieldRulesEx('c_medicalHistoryMentalNo', 'c_medicalHistoryMentalYes', ['c_medicalHistoryMHAnxiety', 'c_medicalHistoryMHDepression', 'c_medicalHistoryMHPrevPPD', 'c_medicalHistoryMHBipolar', 'c_medicalHistoryMHEatingDisorder', 'c_medicalHistoryMHMethadone', 'c_medicalHistoryMHSuboxone', 't_medicalHistoryMHOtherDetails', 'c_medicalHistoryMHSubstanceUse', 'c_medicalHistoryMHOther']);
        toggleFieldRulesSingle('c_medicalHistoryMHOther', ['t_medicalHistoryMHOtherDetails']);
        toggleFieldRulesSingle('c_medicalHistoryMHSubstanceUse', ['c_medicalHistoryMHMethadone', 'c_medicalHistoryMHSuboxone']);

        //Medical History - Infectious diseases
        toggleFieldRulesEx('c_medicalHistoryInfectiousNo', 'c_medicalHistoryInfectiousYes', ['c_medicalHistoryIDVaricella', 'c_medicalHistoryIDHSV', 't_medicalHistoryIDOtherDetails', 'c_medicalHistoryIDOther']);
        toggleFieldRulesSingle('c_medicalHistoryIDOther', ['t_medicalHistoryIDOtherDetails']);

        //Medical History - Immunizations
        toggleFieldRulesEx('c_medicalHistoryImmunizationsNo', 'c_medicalHistoryImmunizationsYes', ['d_medicalHistoryImmunizationsFluDate', 'd_medicalHistoryImmunizationsTDAPDate', 't_medicalHistoryImmunizationsOtherDetails', 'c_medicalHistoryImmunizationsTDAP', 'c_medicalHistoryImmunizationsOther', 'c_medicalHistoryImmunizationsFlu']);
        toggleFieldRulesSingle('c_medicalHistoryImmunizationsTDAP', ['d_medicalHistoryImmunizationsTDAPDate']);
        toggleFieldRulesSingle('c_medicalHistoryImmunizationsFlu', ['d_medicalHistoryImmunizationsFluDate']);
        toggleFieldRulesSingle('c_medicalHistoryImmunizationsOther', ['t_medicalHistoryImmunizationsOtherDetails']);

        //Lifestyle - Gender-based violence
        toggleFieldRulesEx('c_lifestyleGenderViolNo', 'c_lifestyleGenderViolYes', ['c_lifestyleGenderViolPartner', 'c_lifestyleGenderViolNonPartner']);

        //Substance Use - Alchohol
        toggleFieldRulesEx('c_substance3MoAlcoholNo', 'c_substance3MoAlcoholYes', ['t_substance3MoAlcoholNumDrinks', 'c_substance3MoAlcohol4DrinksNo', 'c_substance3MoAlcohol4DrinksYes']);
        toggleFieldRulesEx('c_substancePregAlcoholNo', 'c_substancePregAlcoholYes', ['t_substancePregAlcoholNumDrinks', 'c_substancePregAlcohol4DrinksNo', 'c_substancePregAlcohol4DrinksYes']);
        toggleFieldRulesUnique(['c_substance3MoAlcohol4DrinksNo', 'c_substance3MoAlcohol4DrinksYes']);
        toggleFieldRulesUnique(['c_substancePregAlcohol4DrinksNo', 'c_substancePregAlcohol4DrinksYes']);
        toggleFieldRulesEx('c_substanceQuitAlcoholNo', 'c_substanceQuitAlcoholYes', ['d_substanceQuitAlcoholDate']);

        //Substance Use - Tobacco
        toggleFieldRulesEx('c_substance3MoTobaccoNo', 'c_substance3MoTobaccoYes', ['t_substance3MoTobaccoNumCig']);
        toggleFieldRulesEx('c_substancePregTobaccoNo', 'c_substancePregTobaccoYes', ['t_substancePregTobaccoNumCig']);
        toggleFieldRulesUnique(['c_substance3MoTobaccoSecHndSmkNo', 'c_substance3MoTobaccoSecHndSmkYes']);
        toggleFieldRulesUnique(['c_substancePregTobaccoSecHndSmkNo', 'c_substancePregTobaccoSecHndSmkYes']);
        toggleFieldRulesEx('c_substanceQuitTobaccoNo', 'c_substanceQuitTobaccoYes', ['d_substanceQuitTobaccoDate']);

        //Substance Use - Cannabis
        toggleFieldRulesEx('c_substance3MoCannabisNo', 'c_substance3MoCannabisYes', ['c_substance3MoCannabisCBDOnlyNo', 'c_substance3MoCannabisCBDOnlyYes', 't_substance3MoCannabisNumUsed', 'c_substance3MoCannabisSmoke', 'c_substance3MoCannabisVapo', 'c_substance3MoCannabisEdi', 'c_substance3MoCannabisOther']);
        toggleFieldRulesEx('c_substancePregCannabisNo', 'c_substancePregCannabisYes', ['c_substancePregCannabisCBDOnlyNo', 'c_substancePregCannabisCBDOnlyYes', 't_substancePregCannabisNumUsed', 'c_substancePregCannabisSmoke', 'c_substancePregCannabisVapo', 'c_substancePregCannabisEdi', 'c_substancePregCannabisOther']);
        toggleFieldRulesUnique(['c_substance3MoCannabisCBDOnlyNo', 'c_substance3MoCannabisCBDOnlyYes']);
        toggleFieldRulesUnique(['c_substance3MoCannabisSmoke', 'c_substance3MoCannabisVapo', 'c_substance3MoCannabisEdi', 'c_substance3MoCannabisOther']);
        toggleFieldRulesUnique(['c_substancePregCannabisCBDOnlyNo', 'c_substancePregCannabisCBDOnlyYes']);
        toggleFieldRulesUnique(['c_substancePregCannabisSmoke', 'c_substancePregCannabisVapo', 'c_substancePregCannabisEdi', 'c_substancePregCannabisOther']);
        toggleFieldRulesEx('c_substanceQuitCannabisNo', 'c_substanceQuitCannabisYes', ['d_substanceQuitCannabisDate']);

        //Substance Use - Other
        toggleFieldRulesEx('c_substanceOthersNo', 'c_substanceOthersYes', ['c_substanceOthersCocaine', 'c_substanceOthersOpioids', 'c_substanceOthersMeth', 'c_substanceOthersIVDrugs', 'c_substanceOthersPrescDrugs', 't_substanceOthersOtherDetails', 'c_substanceOthersOther']);
        toggleFieldRulesSingle('c_substanceOthersOther', ['t_substanceOthersOtherDetails']);

        //Initial Physical Exam

        toggleFieldRulesEx('c_initialExamHeadNorm', 'c_initialExamHeadAbNorm', []);
        toggleFieldRulesEx('c_initialExamBreastsNorm', 'c_initialExamBreastsAbNorm', []);
        toggleFieldRulesEx('c_initialExamHeartNorm', 'c_initialExamHeartAbNorm', []);
        toggleFieldRulesEx('c_initialExamAbdomenNorm', 'c_initialExamAbdomenAbNorm', []);
        toggleFieldRulesEx('c_initialExamMusculoNorm', 'c_initialExamMusculoAbNorm', []);
        toggleFieldRulesEx('c_initialExamSkinNorm', 'c_initialExamSkinAbNorm', []);
        toggleFieldRulesSingle('c_initialExamSkinOther', ['t_initialExamSkinOtherDetails']);
        toggleFieldRulesEx('c_initialExamPelvicNorm', 'c_initialExamPelvicAbNorm', []);
        toggleFieldRulesMultiple(['c_initialExamPelvicNorm', 'c_initialExamPelvicAbNorm'], []);
        toggleFieldRulesEx('c_initialExamOtherNorm', 'c_initialExamOtherAbNorm', []);


        //Indigenous identity
        toggleFieldRulesSingle('c_indIdentFirstNations', ['c_indIdentStatus', 'c_indIdentNonStatus', 'c_indIdentLiveOnReserve', 'c_indIdentLiveOffReserve', 'c_indIdentLiveOnOffReserve']);
        toggleFieldRulesUnique(['c_indIdentStatus', 'c_indIdentNonStatus']);
        toggleFieldRulesUnique(['c_indIdentLiveOnReserve', 'c_indIdentLiveOffReserve', 'c_indIdentLiveOnOffReserve']);

        //Pregnancy Planned
        toggleFieldRulesUnique(['c_pregnancyPlannedYes', 'c_pregnancyPlannedNo']);

        //Comments
        toggleFieldRulesUnique(['c_commentsMD', 'c_commentsRM', 'c_commentsNP']);

        //Set Default States for custom rules
        if ($('input[name="t_biologicalFatherName"]').prop("value") == $('input[name="t_partnerName"]').prop("value")) {
            $('input[name="biologicalFatherSameCheck"]').prop("checked", true);
            $('input[name="t_biologicalFatherName"]').prop("readonly", true);
        }
        if ($('input[name="c_allergiesNone"]').prop("checked")) {
            $('input[name="t_allergies"]').prop("value", "");
            $('input[name="t_allergies"]').prop("disabled", true);
        }

        //Create toggle functions for custom rules that can't be applied using the toggle functions
        $('input[name="c_allergiesNone"]').change(function () {
            if ($('input[name="c_allergiesNone"]').prop("checked")) {
                $('input[name="t_allergies"]').prop("value", "");
                $('input[name="t_allergies"]').prop("disabled", true);
            } else {
                $('input[name="t_allergies"]').prop("disabled", false);
            }
        });
        $('input[name="biologicalFatherSameCheck"]').change(function () {
            if ($('input[name="biologicalFatherSameCheck"]').prop("checked")) {
                $('input[name="t_biologicalFatherName"]').prop("value", $('input[name="t_partnerName"]').prop("value"));
                $('input[name="t_biologicalFatherName"]').prop("readonly", true);
            } else {
                $('input[name="t_biologicalFatherName"]').prop("value", "");
                $('input[name="t_biologicalFatherName"]').prop("readonly", false);
            }
        });
        $('input[name="t_partnerName"]').change(function () {
            if ($('input[name="biologicalFatherSameCheck"]').prop("checked")) {
                $('input[name="t_biologicalFatherName"]').prop("value", $('input[name="t_partnerName"]').prop("value"));
            }
        });

        //DropDown Text-Input Initialization
        $('select.data-list-input').focus(function () {
            $(this).siblings('input.data-list-input').focus();
        });
        $('select.data-list-input').change(function () {
            $(this).siblings('input.data-list-input').val($(this).val());
        });
        $('input.data-list-input').change(function () {
            $(this).siblings('select.data-list-input').val('');
        });
    } else if (page === 2) {
        //EDD
        toggleFieldRulesUnique(['c_confirmedEDDUS', 'c_confirmedEDDIVF']);

        //Test Results
        toggleFieldRulesUnique(['c_investigationsRubellaImm', 'c_investigationsRubellaNonImm']);
        toggleFieldRulesUnique(['c_investigationsHIVNeg', 'c_investigationsHIVPos']);
        toggleFieldRulesUnique(['c_investigationsSyphilisNR', 'c_investigationsSyphilisR']);
        toggleFieldRulesUnique(['c_investigationsHBsAgNR', 'c_investigationsHBsAgR']);
        toggleFieldRulesUnique(['c_investigationsGonorrheaNeg', 'c_investigationsGonorrheaPos']);
        toggleFieldRulesUnique(['c_investigationsChlamydiaNeg', 'c_investigationsChlamydiaPos']);
        toggleFieldRulesUnique(['c_investigationsUrineNeg', 'c_investigationsUrinePos']);
        toggleFieldRulesUnique(['c_investigationsGDMGCTNeg', 'c_investigationsGDMGCTPos']);
        toggleFieldRulesUnique(['c_investigationsGDMGTTNeg', 'c_investigationsGDMGTTPos']);
        toggleFieldRulesUnique(['c_investigationsGBSNeg', 'c_investigationsGBSPos']);
        toggleFieldRulesSingle('c_prenatalGeneticOther', ['t_prenatalGeneticOtherDetails']);
        toggleFieldRulesSingleOpposite('c_prenatalGeneticDeclined', ['c_prenatalGeneticSIPS', 'c_prenatalGeneticIPS', 'c_prenatalGeneticQuad', 'c_prenatalGeneticCVS', 't_prenatalGeneticResults', 'c_prenatalGeneticNIPTMSP', 'c_prenatalGeneticNIPTSelf', 't_prenatalGeneticOtherDetails', 'c_prenatalGeneticOther', 'c_prenatalGeneticAmnio']);

        //Edinburgh
        toggleFieldRulesSingleOpposite('c_edinburgDeclined', ['d_edinburgDate', 't_edinburgGA', 't_edinburgTotalScore', 't_edinburgAnxietySubscore', 't_edinburgSelfharmSubscore', 't_edinburgFollowup']);

        //Perinatal Considerations
        toggleFieldRulesUnique(['c_considerationsPregnancySingleton', 'c_considerationsPregnancyTwin', 'c_considerationsPregnancyMultiple']);
        toggleFieldRulesUnique(['c_considerationsVBACEligNo', 'c_considerationsVBACEligYes', 'c_considerationsVBACEligNA']);
        toggleFieldRulesUnique(['c_considerationsVBACPlanNo', 'c_considerationsVBACPlanYes', 'c_considerationsVBACPlanNA']);
        toggleFieldRulesUnique(['c_considerationsBreastfeedNo', 'c_considerationsBreastfeedYes', 'c_considerationsBreastfeedUN']);

        //Sign Offs
        toggleFieldRulesUnique(['c_signOffsMD1', 'c_signOffsRM1', 'c_signOffsNP1']);
        toggleFieldRulesUnique(['c_signOffsMD2', 'c_signOffsRM2', 'c_signOffsNP2']);
        toggleFieldRulesUnique(['c_signOffsMD3', 'c_signOffsRM3', 'c_signOffsNP3']);
    } else if (page === 3) {
        //Sign Offs
        toggleFieldRulesUnique(['c_signOffsMD1', 'c_signOffsRM1', 'c_signOffsNP1']);
        toggleFieldRulesUnique(['c_signOffsMD2', 'c_signOffsRM2', 'c_signOffsNP2']);
        toggleFieldRulesUnique(['c_signOffsMD3', 'c_signOffsRM3', 'c_signOffsNP3']);
    } else if (page === 6) {
        // Attachments/Additional Info Page
        toggleFieldRulesSingle('c_attMedications', ['mt_attMedications', 'fetchMedications\\|mt_attMedications', 'fetchLongTermMedications\\|mt_attMedications', 'fetchOtherMeds']);
        toggleFieldRulesSingle('c_attAllergies', ['mt_attAllergies', 'fetchAllergies\\|mt_attAllergies']);
        toggleFieldRulesSingle('c_attAdditionalInfo', ['mt_attAdditionalInfo', 'fetchAddInfoSocHistory', 'fetchAddInfoFamHistory', 'fetchAddInfoMedHistory', 'fetchAddInfoConcerns', 'fetchAddInfoOtherMeds', 'fetchAddInfoReminders', 'fetchRiskFactors\\|mt_attAdditionalInfo']);
    }

}

function allNo(section) {
    if (section === 'presentPregnancy') {
        check('c_presentPregnancyARTNo');
        check('c_presentPregnancyBleedingNo');
        check('c_presentPregnancyNauseaNo');
        check('c_presentPregnancyTravelNo');
        check('c_presentPregnancyInfectionNo');
        check('c_presentPregnancyOtherNo');
    } else if (section === 'familyHistory') {
        check('c_familyHistoryAnestheticCompNo');
        check('c_familyHistoryHypertensionNo');
        check('c_familyHistoryThromboembolicNo');
        check('c_familyHistoryDiabetesNo');
        check('c_familyHistoryMentalHealthNo');
        check('c_familyHistorySubstanceUseNo');
        check('c_familyHistoryInheritedConditionsNo');
        check('c_familyHistoryOtherNo');
    } else if (section === 'medicalHistory') {
        check('c_medicalHistorySurgeryNo');
        check('c_medicalHistoryAnestheticComplicationsNo');
        check('c_medicalHistoryNeuroNo');
        check('c_medicalHistoryRespNo');
        check('c_medicalHistoryCVNo');
        check('c_medicalHistoryAbdoNo');
        check('c_medicalHistoryGyneNo');
        check('c_medicalHistoryHematologyNo');
        check('c_medicalHistoryEndocrineNo');
        check('c_medicalHistoryMentalNo');
        check('c_medicalHistoryInfectiousNo');
        check('c_medicalHistoryImmunizationsNo');
        check('c_medicalHistoryOtherNo');

    } else if (section === 'lifestyleSocial') {
        check('c_lifestyleDietNo');
        check('c_lifestyleExerciseNo');
        check('c_lifestyleFinancialNo');
        check('c_lifestyleHousingNo');
        check('c_lifestyleTransportationNo');
        check('c_lifestyleSafetyNo');
        check('c_lifestyleGenderViolNo');
        check('c_lifestyleRelationshipsNo');
        check('c_lifestyleOtherNo');
    } else if (section === 'initialExam1') {
        check('c_initialExamHeadNorm');
        check('c_initialExamBreastsNorm');
        check('c_initialExamHeartNorm');
        check('c_initialExamAbdomenNorm');
        check('c_initialExamMusculoNorm');
    } else if (section === 'initialExam2') {
        check('c_initialExamSkinNorm');
        check('c_initialExamPelvicNorm');
        check('c_initialExamOtherNorm');
    }
}

function unCheck(fieldName) {
    if ($('input[name="' + fieldName + 'Yes"]').prop("checked")) {
        $('input[name="' + fieldName + 'Yes"]').prop("checked", false).change();
    }
}

function check(fieldName) {
    if (!$('input[name="' + fieldName + '"]').prop("checked")) {
        $('input[name="' + fieldName + '"]').prop("checked", true).change();
    }
}

/*
toggleFieldRulesUnique applies a unique rule so that only one field in the fieldList can be selected
at any one time. 
 */
function toggleFieldRulesUnique(fieldList) {
    fieldList.forEach(function (fieldName, index) {
        $('input[name="' + fieldName + '"]').change(function () {
            var $this = $(this);
            if ($this.prop("checked")) {
                fieldList.forEach(function (fieldName, index) {
                    if ($('input[name="' + fieldName + '"]').prop("checked")) {
                        $('input[name="' + fieldName + '"]').not($this).prop("checked", false).change();
                    }
                });
            }
        });
    });
}

/*
toggleFieldRulesSingle expands upon toggleFieldRules to allow you to specify a single checkbox field
and a list of all of the fields you would like to be toggled (enabled/disabled based on the checkField).
The rules that are applied are:
- If checkField is checked, the fields in the fieldList are Enabled. 
- If checkField is unchecked, the field in the fieldList are wiped and disabled.
 */
function toggleFieldRulesSingle(checkField, fieldList) {
    //Set Default State
    if (!$('input[name="' + checkField + '"]').prop("checked")) {
        fieldList.forEach(disableField);
    }

    //Create Toggle Functions
    $('input[name="' + checkField + '"]').change(function () {
        toggleField(checkField, fieldList);
    });
}

/*
disableField wraps all of the code to disable a field (whether it be a button, text or other field) into a reusable function.
fieldName: is the field to be disabled
 */
function disableField(fieldName) {
    var fieldList = $('[name=' + fieldName + '],#' + fieldName);

    // Wipe the contents of any matching control thats not a button
    fieldList.not(':input[type=button]').prop("value", "");

    // Uncheck any checkbox 
    fieldList.find('[type=checkbox]').prop("checked", false);

    // Disable any input
    fieldList.prop("disabled", true);
}

/*
enableField wraps all of the code to enable a field (whether it be a button, text or other field) into a reusable function.
fieldName: is the field to be enabled
 */
function enableField(fieldName) {
    $('input[name="' + fieldName + '"],#' + fieldName).prop("disabled", false);
}

/*
toggleField will enable/disable fields from the fieldList based on a checkField.
checkField: the checkField that toggles the fields
fieldList: the list of fields to be toggled
 */
function toggleField(checkField, fieldList) {
    if ($('input[name="' + checkField + '"]').prop("checked")) {
        fieldList.forEach(enableField);
    } else {
        fieldList.forEach(disableField);
    }
}

/*
toggleFieldRulesMultiple expands upon toggleFieldRulesSingle to allow you to specify a list of checkbox fields
and a list of all of the fields you would like to be toggled (enabled/disabled based on the checkFields).
The rules that are applied are:
- If *any* checkField is checked, the fields in the fieldList are Enabled. 
- If *all* checkFields are unchecked, the field in the fieldList are wiped and disabled.
 */
function toggleFieldRulesMultiple(checkFieldList, fieldList) {
    //Set Default State
    // Check if any of the fields in the checkFieldList are checked
    if (!checkFieldList.some(checkField => $('input[name="' + checkField + '"]').prop("checked"))) {
        fieldList.forEach(disableField);
    }

    //Create Toggle Functions
    checkFieldList.forEach(function (checkField) {
        $('input[name="' + checkField + '"]').change(function () {
            toggleField(checkField, fieldList);
        });
    });
}

/*
toggleFieldRulesSingleOpposite is the opposite of toggleFieldRulesSingle, in that if a checkField is checked
the fields in the field list are disabled/wiped/unchecked.
- If checkField is checked, the fields in the fieldList are disabled/wiped/unchecked. 
- If checkField is unchecked, the field in the fieldList are enabled.
 */
function toggleFieldRulesSingleOpposite(checkField, fieldList) {
    //Set Default State
    if ($('input[name="' + checkField + '"]').prop("checked")) {
        fieldList.forEach(function (fieldName, index) {
            $('input[type="text"][name="' + fieldName + '"]').prop("value", "");
            $('#' + fieldName).prop("value", "");
            $('input[name="' + fieldName + '"]').prop("disabled", true);
            $('#' + fieldName).prop("disabled", true);
            $('input[name="' + fieldName + '"]').prop("checked", false);
        });
    }

    //Create Toggle Functions
    $('input[name="' + checkField + '"]').change(function () {
        if ($('input[name="' + checkField + '"]').prop("checked")) {
            fieldList.forEach(function (fieldName, index) {
                $('input[type="text"][name="' + fieldName + '"]').prop("value", "");
                $('#' + fieldName).prop("value", "");
                $('input[name="' + fieldName + '"]').prop("disabled", true);
                $('#' + fieldName).prop("disabled", true);
                $('input[name="' + fieldName + '"]').prop("checked", false);
            });
        } else {
            fieldList.forEach(function (fieldName, index) {
                $('input[name="' + fieldName + '"]').prop("disabled", false).change();
                $('#' + fieldName).prop("disabled", false);
            });
        }
    });
}

/*
toggleFieldRulesEx expands upon toggleFieldRules to allow you to specify the No Checkbox field, 
the Yes Checkbox field and a list of all of the fields you would like to be toggled (enabled/disabled based on the Yes/no).
The rules applied are:
- Only Yes or No can be selected at any one time
- If Yes is selected, the fields in the fieldList are enabled. 
- If nothing is selected or No is selected, the field in the fieldList are wiped and disabled.
 */
function toggleFieldRulesEx(fieldNo, fieldYes, fieldList) {
    //Set Default State
    if (!$('input[name="' + fieldYes + '"]').prop("checked")) {
        fieldList.forEach(function (fieldName, index) {
            $('input[type="text"][name="' + fieldName + '"]').prop("value", "");
            $('input[name="' + fieldName + '"]').prop("disabled", true);
            $('input[name="' + fieldName + '"]').prop("checked", false);
        });
    }

    //Create Toggle Functions
    $('input[name="' + fieldYes + '"]').change(function () {
        if ($('input[name="' + fieldYes + '"]').prop("checked")) {
            $('input[name="' + fieldNo + '"]').prop("checked", false);
            fieldList.forEach(function (fieldName, index) {
                $('input[name="' + fieldName + '"]').prop("disabled", false);
                $('input[name="' + fieldName + '"]').prop("checked", false).change();
            });
        } else {
            fieldList.forEach(function (fieldName, index) {
                $('input[type="text"][name="' + fieldName + '"]').prop("value", "");
                $('input[name="' + fieldName + '"]').prop("disabled", true);
                $('input[name="' + fieldName + '"]').prop("checked", false);
            });
        }
    });

    $('input[name="' + fieldNo + '"]').change(function () {
        if ($('input[name="' + fieldNo + '"]').prop("checked")) {
            $('input[name="' + fieldYes + '"]').prop("checked", false);
            fieldList.forEach(function (fieldName, index) {
                $('input[type="text"][name="' + fieldName + '"]').prop("value", "");
                $('input[name="' + fieldName + '"]').prop("disabled", true);
                $('input[name="' + fieldName + '"]').prop("checked", false);
            });
        }
    });
}


/*
toggleFieldRules applies the standard toggle options on BCAR2020, which are:
- Only Yes or No can be selected at any one time
- If Yes is selected, the Details field is enabled. 
- If nothing is selected or No is selected, the Details field is wiped and disabled.

This function takes the generic fieldName and applies the rules based on that. 
Eg. fieldNameYes, fieldNameNo, fieldNameDetails.

The Field names must be in this standard format for this function to work.
 */
function toggleFieldRules(fieldName) {
    //Set Default State
    if (!$('input[name$="' + fieldName + 'Yes"]').prop("checked")) {
        $('input[type$="text"][name$="' + fieldName + 'Details"]').prop("value", "");
        $('input[name$="' + fieldName + 'Details"]').prop("disabled", true);
    }

    //Create toggle functions
    $('input[name$="' + fieldName + 'Yes"]').change(function () {
        if ($('input[name$="' + fieldName + 'Yes"]').prop("checked")) {
            $('input[name$="' + fieldName + 'No"]').prop("checked", false);
            $('input[name$="' + fieldName + 'Details"]').prop("disabled", false);
        } else {
            $('input[type="text"][name$="' + fieldName + 'Details"]').prop("value", "");
            $('input[name$="' + fieldName + 'Details"]').prop("disabled", true);
        }
    });
    $('input[name$="' + fieldName + 'No"]').change(function () {
        if ($('input[name$="' + fieldName + 'No"]').prop("checked")) {
            $('input[name$="' + fieldName + 'Yes"]').prop("checked", false);
            $('input[type="text"][name$="' + fieldName + 'Details"]').prop("value", "");
            $('input[name$="' + fieldName + 'Details"]').prop("disabled", true);
        }
    });
}

function reset() {
    document.forms[0].target = "";
    document.forms[0].action = "../form/BCAR2020.do";
}

function onSave() {
    document.forms[0].method.value = "save";
    var ret1 = validate();
    var ret = checkAllDates();
    if (ret == true && ret1 == true) {
        reset();
    }
    if (ret && ret1) {
        window.onunload = null;
    }
    return ret && ret1;
}

function onSaveExit() {
    document.forms[0].method.value = "saveAndExit";
    var ret1 = validate();
    var ret = checkAllDates();
    if (ret == true && ret1 == true) {
        reset();
    }
    return ret && ret1;
}

/* 
 * Validation for various fields 
*/
function validate() {
    return true;
}

function daysInArray(n) {
    for (var i = 1; i <= n; i++) {
        this[i] = 31;
        if (i === 4 || i === 6 || i === 9 || i === 11) {
            this[i] = 30;
        }
        if (i === 2) {
            this[i] = 29;
        }
    }
    return this;
}

function daysInFebruary(year) {
    // February has 29 days in any year evenly divisible by four,
    // EXCEPT for centurial years which are not also divisible by 400.
    return (((year % 4 == 0) && ((!(year % 100 == 0)) || (year % 400 == 0))) ? 29 : 28);
}

function stripCharsInBag(s, bag) {
    var returnString = "";

    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (bag.indexOf(c) === -1) {
            returnString += c;
        }
    }

    return returnString;
}

function isInteger(s) {
    var isInt = true;

    for (var i = 0; i < s.length; i++) {
        var c = s.charAt(i);
        if (((c < "0") || (c > "9"))) {
            isInt = false;
        }
    }

    return isInt;
}

function isNumber(value) {
    return value != null && value.trim().length > 0 && !isNaN(value);
}

function isDate(dtStr) {
    var daysInMonth = daysInArray(12);
    var pos1 = dtStr.indexOf(dtCh);
    var pos2 = dtStr.indexOf(dtCh, pos1 + 1);
    var strMonth = dtStr.substring(0, pos1);
    var strDay = dtStr.substring(pos1 + 1, pos2);
    var strYear = dtStr.substring(pos2 + 1);

    if (strDay.charAt(0) === "0" && strDay.length > 1) strDay = strDay.substring(1);
    if (strMonth.charAt(0) === "0" && strMonth.length > 1) strMonth = strMonth.substring(1);
    for (var i = 1; i <= 3; i++) {
        if (strYear.charAt(0) === "0" && strYear.length > 1) {
            strYear = strYear.substring(1);
        }
    }
    var month = parseInt(strMonth);
    var day = parseInt(strDay);
    var year = parseInt(strYear);

    if (pos1 === -1 || pos2 === -1) {
        return "format";
    }
    if (month < 1 || month > 12) {
        return "month";
    }
    if (day < 1 || day > 31 || (month === 2 && day > daysInFebruary(year)) || day > daysInMonth[month]) {
        return "day";
    }
    if (strYear.length !== 4 || year === 0 || year < minYear || year > maxYear) {
        return "year";
    }
    if (dtStr.indexOf(dtCh, pos2 + 1) !== -1 || isInteger(stripCharsInBag(dtStr, dtCh)) === false) {
        return "date";
    }

    return true;
}

function valDate(dateBox, ds = dtCh) {
    if (dateBox) {
        try {
            var dateString = dateBox.value;
            if (dateString === "") {
                return true;
            }
            var dt = dateString.split(ds);
            var d = dt[0];
            var m = dt[1];
            var y = dt[2];
            var orderString = m + '/' + d + '/' + y;
            var pass = isDate(orderString);

            if (pass !== true) {
                alert('Invalid ' + pass + ' in field ' + dateBox.title);
                dateBox.focus();
                return false;
            }
        } catch (ex) {
            alert('Invalid Date in field ' + dateBox.title);
            dateBox.focus();
            return false;
        }
    }

    return true;
}

function checkAllDates() {
    var valid = true;

    if (page === 1) {
        if (valDate(document.forms[0].d_contraceptiveLastUsed) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_LMP) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_EDDByLMP) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_datingUS) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_EDDByUS) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_obHistoryDate1) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_obHistoryDate2) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_obHistoryDate3) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_obHistoryDate4) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_obHistoryDate5) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_medicalHistoryImmunizationsFluDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_medicalHistoryImmunizationsTDAPDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_substanceQuitAlcoholDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_substanceQuitTobaccoDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_substanceQuitCannabisDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_initialExamDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_initialExamPelvicSTITest) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_initialExamPelvicPapTest) == false) {
            valid = false;
        }
    } else if (page === 2) {
        if (valDate(document.forms[0].d_imagingDate1) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_imagingDate2) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_imagingDate3) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_imagingDate4) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_confirmedEDD) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_investigationsAntibody1) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_investigationsRhIgDate1) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_investigationsAntibody2) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_investigationsRhIgDate2) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_edinburgDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_investigationsGBSDate) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate1) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate2) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate3) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate4) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate5) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate6) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate7) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate8) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate9) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate10) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate11) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate12) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate13) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate14) == false) {
            valid = false;
        }
    } else if (page === 3) {
        if (valDate(document.forms[0].d_confirmedEDD) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate15) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate16) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate17) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate18) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate19) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate20) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate21) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate22) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate23) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate24) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate25) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate26) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate27) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate28) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate29) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate30) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate31) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate32) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate33) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate34) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate35) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate36) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate37) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate38) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate39) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate40) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate41) == false) {
            valid = false;
        } else if (valDate(document.forms[0].d_prenatalVisitDate42) == false) {
            valid = false;
        }
    }

    return valid;
}

/* 
 * Calculators for various fields 
*/
function appendNotify(field) {
    var fieldMessage = "";
    var promptMessage = "";
    switch (field.name) {
        case "t_medications":
            fieldMessage = "See attached page for medications.";
            promptMessage = "Click on the attachments link at the top of this page to add and customize a list of medications.";
            break;
        case "t_allergies":
            fieldMessage = "See attached page for allergies.";
            promptMessage = "Click on the attachments link at the top of this page to add and customize a list of allergies.";
            break;
    }

    $('input[type="text"][name=' + field.name + ']').val(fieldMessage);
    alert(promptMessage);
}

function calculateBmi(field) {
    var height = $('input[type="text"][name="t_initialExamHT"]').prop("value");
    var weight = $('input[type="text"][name="t_initialExamWT"]').prop("value");

    if (isNumber(height) && isNumber(weight)) {
        height = parseFloat(height) / 100;
        weight = parseFloat(weight);
        if (height && height !== 0 && weight && weight !== 0) {
            field.value = Math.round(weight * 10 / height / height) / 10;
        }
    } else {
        alert("Please enter a valid Height and Pre-preg. Wt before calculating BMI.");
    }
}

function calcAgeAtEDD(EDD, DOB, field) {
    var DOB_array = DOB.split("/");
    var age = 0;

    if (EDD == "") {
        alert("Please enter 'Confirmed EDD' in Section 12 of Part 2 (Page 1) before calculating the 'Age at EDD'.");
    } else if (DOB.length != 10) {
        alert("Please enter a date of birth first.");
    } else {
        var EDD_array = EDD.split("/");
        age = EDD_array[2] - DOB_array[2];
        if (EDD_array[1] < DOB_array[1]) {
            age--;
        } else if (EDD_array[1] == DOB_array[1] && EDD_array[0] < DOB_array[0]) {
            age--;
        }
        field.value = age;
    }
    recheckForm();
}

function calculateByLMP(field, ds = dtCh) {
    if (document.forms[0].d_LMP.value != "" && valDate(document.forms[0].d_LMP) == true) {
        var str_date = document.forms[0].d_LMP.value;

        var dt = str_date.split(ds);
        var dd = dt[0];
        var mm = eval(dt[1] - 1);
        var yyyy = dt[2];

        var calDate = new Date(yyyy, mm, dd);

        calDate.setTime(eval(calDate.getTime() + (280 * 86400000)));

        varMonth1 = calDate.getMonth() + 1;
        varMonth1 = varMonth1 > 9 ? varMonth1 : ("0" + varMonth1);
        varDate1 = calDate.getDate() > 9 ? calDate.getDate() : ("0" + calDate.getDate());
        field.value = varDate1 + '/' + varMonth1 + '/' + calDate.getFullYear();
    } else {
        alert("Please enter a valid 'LMP' in Section 3 before calculating the 'EDD by LMP'.");
    }
    recheckForm();
}

function dayDifference(day1, day2) {
    return (day2 - day1) / (1000 * 60 * 60 * 24);
}

function getGestationalAgeDays(edd, comparison) {
    // Get the GA based on two dates, the confirmed edd and the date you are checking against (comparison, which may simply be todays date)
    var numberOfDays = -1;

    if (edd.length === 10) {
        var year = edd.substring(6, 10);
        var month = edd.substring(3, 5);
        var day = edd.substring(0, 2);
        var monthString = month.substring(0, 1) === '0' ? month.substring(1, 2) : month;

        var eddDate = new Date(year, parseInt(monthString) - 1, day);
        eddDate.setHours(8);
        eddDate.setMinutes(0);
        eddDate.setSeconds(0);
        eddDate.setMilliseconds(0);

        var startDate = new Date();
        startDate.setTime(eddDate.getTime() - (280 * 1000 * 60 * 60 * 24));
        startDate.setHours(8);

        if (comparison != null && comparison.length === 10) {
            var comparisonYear = comparison.substring(6, 10);
            var comparisonmonth = comparison.substring(3, 5);
            var comparisonday = comparison.substring(0, 2);
            var comparisonmonthString = comparisonmonth.substring(0, 1) === '0' ? comparisonmonth.substring(1, 2) : comparisonmonth;

            var comparisonDate = new Date(comparisonYear, parseInt(comparisonmonthString) - 1, comparisonday);
            comparisonDate.setHours(8);
            comparisonDate.setMinutes(0);
            comparisonDate.setSeconds(0);
            comparisonDate.setMilliseconds(0);

            if (comparisonDate > startDate) {
                var days = dayDifference(startDate, comparisonDate);
                days = Math.round(days);
                numberOfDays = days;
            }
        } else {
            var today = new Date();
            today.setHours(8);
            today.setMinutes(0);
            today.setSeconds(0);
            today.setMilliseconds(0);

            if (today > startDate) {
                var days = dayDifference(startDate, today);
                days = Math.round(days);
                numberOfDays = days;
            }
        }
    }

    return parseInt(numberOfDays);
}

function getGAByFieldDate(resultField, eddField, comparisonField) {
    var edd_str = $('input[name="' + eddField + '"]').val();
    var comparison_str = $('input[name="' + comparisonField + '"]').val();
    if (comparison_str.length < 10 || edd_str.length < 10) {
        alert("Please enter valid dates for '" + $('input[name="' + comparisonField + '"]').attr('title') + "' and '" + $('input[name="' + eddField + '"]').attr('title') + "' before calculating the '" + $('input[name="' + resultField + '"]').attr('title') + "'.");
    } else {
        var daysDifference = getGestationalAgeDays(edd_str, comparison_str)
        var offset;
        var weeks = 0;
        var result;

        if (daysDifference > 0) {
            weeks = parseInt(daysDifference / 7);
            offset = parseInt(daysDifference % 7);
        }

        result = weeks + "w/" + offset + "d";

        $('input[name="' + resultField + '"]').val(result);
    }
}

/* 
 * Setup Calendars
 */
function setupCalendar(field) {
    Calendar.setup({
        onUpdate: function () {
            recheckForm()
        }, inputField: field, ifFormat: '%d/%m/%Y', showsTime: false, button: field + '_cal', singleClick: true, step: 1
    });
}

function calendars(page) {
    if (page === 1) {
        setupCalendar('d_contraceptiveLastUsed');
        setupCalendar('d_LMP');
        setupCalendar('d_EDDByLMP');
        setupCalendar('d_datingUS');
        setupCalendar('d_EDDByUS');
        setupCalendar('d_obHistoryDate1');
        setupCalendar('d_obHistoryDate2');
        setupCalendar('d_obHistoryDate3');
        setupCalendar('d_obHistoryDate4');
        setupCalendar('d_obHistoryDate5');
        setupCalendar('d_medicalHistoryImmunizationsFluDate');
        setupCalendar('d_medicalHistoryImmunizationsTDAPDate');
        setupCalendar('d_substanceQuitAlcoholDate');
        setupCalendar('d_substanceQuitTobaccoDate');
        setupCalendar('d_substanceQuitCannabisDate');
        setupCalendar('d_initialExamDate');
        setupCalendar('d_initialExamPelvicSTITest');
        setupCalendar('d_initialExamPelvicPapTest');
    } else if (page === 2) {
        setupCalendar('d_imagingDate1');
        setupCalendar('d_imagingDate2');
        setupCalendar('d_imagingDate3');
        setupCalendar('d_imagingDate4');
        setupCalendar('d_confirmedEDD');
        setupCalendar('d_investigationsAntibody1');
        setupCalendar('d_investigationsRhIgDate1');
        setupCalendar('d_investigationsAntibody2');
        setupCalendar('d_investigationsRhIgDate2');
        setupCalendar('d_edinburgDate');
        setupCalendar('d_investigationsGBSDate');
        setupCalendar('d_prenatalVisitDate1');
        setupCalendar('d_prenatalVisitDate2');
        setupCalendar('d_prenatalVisitDate3');
        setupCalendar('d_prenatalVisitDate4');
        setupCalendar('d_prenatalVisitDate5');
        setupCalendar('d_prenatalVisitDate6');
        setupCalendar('d_prenatalVisitDate7');
        setupCalendar('d_prenatalVisitDate8');
        setupCalendar('d_prenatalVisitDate9');
        setupCalendar('d_prenatalVisitDate10');
        setupCalendar('d_prenatalVisitDate11');
        setupCalendar('d_prenatalVisitDate12');
        setupCalendar('d_prenatalVisitDate13');
        setupCalendar('d_prenatalVisitDate14');
    } else if (page === 3) {
        setupCalendar('d_confirmedEDD');
        setupCalendar('d_prenatalVisitDate15');
        setupCalendar('d_prenatalVisitDate16');
        setupCalendar('d_prenatalVisitDate17');
        setupCalendar('d_prenatalVisitDate18');
        setupCalendar('d_prenatalVisitDate19');
        setupCalendar('d_prenatalVisitDate20');
        setupCalendar('d_prenatalVisitDate21');
        setupCalendar('d_prenatalVisitDate22');
        setupCalendar('d_prenatalVisitDate23');
        setupCalendar('d_prenatalVisitDate24');
        setupCalendar('d_prenatalVisitDate25');
        setupCalendar('d_prenatalVisitDate26');
        setupCalendar('d_prenatalVisitDate27');
        setupCalendar('d_prenatalVisitDate28');
        setupCalendar('d_prenatalVisitDate29');
        setupCalendar('d_prenatalVisitDate30');
        setupCalendar('d_prenatalVisitDate31');
        setupCalendar('d_prenatalVisitDate32');
        setupCalendar('d_prenatalVisitDate33');
        setupCalendar('d_prenatalVisitDate34');
        setupCalendar('d_prenatalVisitDate35');
        setupCalendar('d_prenatalVisitDate36');
        setupCalendar('d_prenatalVisitDate37');
        setupCalendar('d_prenatalVisitDate38');
        setupCalendar('d_prenatalVisitDate39');
        setupCalendar('d_prenatalVisitDate40');
        setupCalendar('d_prenatalVisitDate41');
        setupCalendar('d_prenatalVisitDate42');

    }
}

function onPrint() {
    $("#print-dialog").dialog("open");
    recheckForm();
    return false;
}

var loggedInProviderNo;

function printSelectAll() {
    $('input[name="print_pr1"]').prop("checked", true);
    $('input[name="print_pr2"]').prop("checked", true);
    $('input[name="print_pr3"]').prop("checked", true);
    $('input[name="print_att"]').prop("checked", true);
}

function dialogs(page) {
    loggedInProviderNo = $('#user').val();
    demographicNo = $('#demographicNo').val();

    $("#print-dialog").dialog({
        autoOpen: false,
        height: 350,
        width: 400,
        modal: true,
        buttons: {
            "Print": function () {
                $(this).dialog("close");

                $("#printPg1").val($("#print_pr1").prop('checked'));
                $("#printPg2").val($("#print_pr2").prop('checked'));
                $("#printPg3").val($("#print_pr3").prop('checked'));
                $("#printPg4").val($("#print_pr4").prop('checked'));
                $("#printPg5").val($("#print_pr5").prop('checked'));
                $("#printPg6").val($("#print_att").prop('checked'));

                if ($("#printPg1").val() === "true" || $("#printPg2").val() === "true" || $("#printPg3").val() === "true" || $("#printPg4").val() === "true" || $("#printPg5").val() === "true" || $("#printPg6").val() === "true") {
                    document.forms[0].action = "../form/BCAR2020.do?method=print";
                    $("#printBtn").click();
                }

            },
            Cancel: function () {
                $(this).dialog("close");
            }
        },
        close: function () {

        }
    });

}

function onPageChange(pageNo) {
    var url = pageNo !== '6' ? 'formBCAR2020pg' + pageNo + '.jsp?demographic_no=' + demographicNo + '&formId=' + formId + '&provNo=' + provNo : 'formBCAR2020Attachments.jsp?demographic_no=' + demographicNo + '&formId=' + formId + '&provNo=' + provNo;

    var result = false;
    var isValid = validate();
    var datesValid = checkAllDates();
    if (isValid === true && datesValid === true) {
        reset();
        if ($('form').hasClass('dirty') && confirm("Would you like to save the changes made on this page first?")) {
            result = true;
            document.forms[0].method.value = "save";
            document.forms[0].forwardTo.value = pageNo;
            document.forms[0].submit();
        } else {
            location.href = url;
        }
    }

    return result;
}

const wtEnglish2Metric = function (obj) {
    let weight = obj.value;
    if (!weight) {
        alert("Enter a value in pounds.");
    }
    if (isNumber(weight)) {
        let weightM = Math.round(weight * 10 * 0.4536) / 10;
        if (confirm("Are you sure you want to change " + weight + " pounds to " + weightM + "kg?")) {
            obj.value = weightM;
        }
    }
}
