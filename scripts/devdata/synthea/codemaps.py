# SPDX-License-Identifier: GPL-2.0-or-later
# Copyright (C) 2026 CARLOS Contributors
"""Code maps from Synthea's sample CSV to CARLOS (#6148): conditions, medicines, vaccines, measurements.

Keyed by Synthea's own description text, so every entry is readable and a sample description with no
entry here is left out (and counted) rather than guessed. ICD-9 codes are ones the Ontario `icd9`
table carries (dotless, as CARLOS stores them). Medicine names are Canadian generic names; where the
sample names a product not sold in Canada, the entry names the usual Canadian equivalent and says so
(`canadian`), and the README lists it as a clean-up rule.
"""

# --- Conditions: description -> (label shown in the chart, ICD-9 for dxresearch or None, kind) ---------
# kind: "chronic" = Ongoing Concerns while active; "history" = Medical History (with its year);
#       "skip" = acute, dental or minor injury, not carried over.
CONDITIONS = {
    "Essential hypertension (disorder)": ("Hypertension", "4019", "chronic"),
    "Anemia (disorder)": ("Anemia", "2859", "chronic"),
    "Ischemic heart disease (disorder)": ("Coronary artery disease", "4140", "chronic"),
    "Metabolic syndrome X (disorder)": ("Metabolic syndrome", "2779", "chronic"),
    "Diabetes mellitus type 2 (disorder)": ("Type 2 diabetes", "25000", "chronic"),
    "Hypertriglyceridemia (disorder)": ("Hypertriglyceridemia", "2721", "chronic"),
    "Osteoporosis (disorder)": ("Osteoporosis", "7330", "chronic"),
    "Chronic sinusitis (disorder)": ("Chronic sinusitis", "4739", "chronic"),
    "Dependent drug abuse (disorder)": ("Substance use disorder", "3049", "chronic"),
    "Disorder of kidney due to diabetes mellitus (disorder)": ("Diabetic kidney disease", "25040", "chronic"),
    "Hyperlipidemia (disorder)": ("Hyperlipidemia", "2724", "chronic"),
    "Chronic intractable migraine without aura (disorder)": ("Chronic migraine", "3469", "chronic"),
    "Transformed migraine (disorder)": ("Chronic migraine", "3469", "chronic"),
    # Stage numbers are dropped: the sample's stage often disagrees with its own creatinine (rule R6).
    "Chronic kidney disease stage 1 (disorder)": ("Chronic kidney disease", "585", "chronic"),
    "Chronic kidney disease stage 2 (disorder)": ("Chronic kidney disease", "585", "chronic"),
    "Chronic kidney disease stage 3 (disorder)": ("Chronic kidney disease", "585", "chronic"),
    "Chronic kidney disease stage 4 (disorder)": ("Chronic kidney disease", "585", "chronic"),
    "Microalbuminuria due to type 2 diabetes mellitus (disorder)": ("Microalbuminuria (type 2 diabetes)", None, "chronic"),
    "Proteinuria due to type 2 diabetes mellitus (disorder)": ("Proteinuria (type 2 diabetes)", None, "chronic"),
    "Neuropathy due to type 2 diabetes mellitus (disorder)": ("Diabetic neuropathy", "25060", "chronic"),
    "Osteoarthritis of knee (disorder)": ("Osteoarthritis of knee", "7159", "chronic"),
    "Osteoarthritis of hip (disorder)": ("Osteoarthritis of hip", "7159", "chronic"),
    "Recurrent urinary tract infection (disorder)": ("Recurrent urinary tract infections", "5990", "chronic"),
    "Sleep apnea (disorder)": ("Sleep apnea", "78057", "chronic"),
    "Sleep disorder (disorder)": ("Sleep disorder", "7805", "chronic"),
    "Asthma (disorder)": ("Asthma", "4939", "chronic"),
    "Malignant neoplasm of breast (disorder)": ("Breast cancer", "1749", "chronic"),
    "Primary fibromyalgia syndrome (disorder)": ("Fibromyalgia", "7291", "chronic"),
    "Seizure disorder (disorder)": ("Seizure disorder", "3459", "chronic"),
    "Adolescent idiopathic scoliosis (disorder)": ("Scoliosis", "7373", "chronic"),
    "Alzheimer's disease (disorder)": ("Alzheimer disease", "3310", "chronic"),
    "Aortic valve stenosis (disorder)": ("Aortic stenosis", "4241", "chronic"),
    "Child attention deficit disorder (disorder)": ("ADHD", "31400", "chronic"),
    "Chronic obstructive bronchitis (disorder)": ("COPD", "496", "chronic"),
    "Idiopathic atrophic hypothyroidism (disorder)": ("Hypothyroidism", "2449", "chronic"),
    "Leukemia  disease (disorder)": ("Leukemia", "2089", "chronic"),
    "Polyp of colon (disorder)": ("Colon polyp", "2113", "history"),
    "Myocardial infarction (disorder)": ("Heart attack (myocardial infarction)", "412", "history"),
    "Acute non-ST segment elevation myocardial infarction (disorder)": ("Heart attack (NSTEMI)", "412", "history"),
    "Acute ST segment elevation myocardial infarction (disorder)": ("Heart attack (STEMI)", "412", "history"),
    "Acute pulmonary embolism (disorder)": ("Pulmonary embolism", None, "history"),
    "Pneumonia (disorder)": ("Pneumonia", None, "history"),
    "Disease caused by severe acute respiratory syndrome coronavirus 2 (disorder)": ("COVID-19", None, "history"),
    "Overdose (disorder)": ("Drug overdose", None, "history"),
    "Eclampsia in pregnancy (disorder)": ("Eclampsia in pregnancy", None, "history"),
    "Fracture of bone (disorder)": ("Fracture", None, "history"),
    "Fracture of forearm (disorder)": ("Fracture of forearm", None, "history"),
    "Osteoporotic fracture of bone (disorder)": ("Osteoporotic fracture", None, "history"),
    "Gunshot wound (disorder)": ("Gunshot wound", None, "history"),
    "Bullet wound (disorder)": ("Gunshot wound", None, "history"),
}
SKIP_CONDITION_WORDS = (
    "gingiv", "dental", "teeth", "molars", "tooth", "torus palatinus", "alveolitis of jaw",
    "viral sinusitis", "pharyngitis", "acute bronchitis", "sore throat", "acute bacterial sinusitis",
    "sinusitis (disorder)", "cystitis", "laceration", "sprain", "concussion", "injury of", "hypoxemia",
    "hyperglycemia",
)

# --- Medicines: description -> entry ---------------------------------------------------------------------
# Entry: name (customName, max 60), generic (GN), strength, form, route, ATC, sig (takemin, takemax,
# freqcode, prn), instructions, unit, long_term, cls (for the one-per-class rule), canadian (a note when
# the sample's product is replaced by the usual Canadian one).


def med(name, generic, strength, atc, sig, instructions, form="TAB", route="PO", unit="tab", long_term=True,
        cls=None, canadian=None):
    takemin, takemax, freq, prn = sig
    return dict(name=name, generic=generic, strength=strength, atc=atc, takemin=takemin, takemax=takemax,
                freq=freq, prn=prn, instructions=instructions, form=form, route=route, unit=unit,
                long_term=long_term, cls=cls, canadian=canadian)


OD, BID, TID, QID, QHS = (1, 1, "OD", 0), (1, 1, "BID", 0), (1, 1, "TID", 0), (1, 1, "QID", 0), (1, 1, "QHS", 0)
PRN = (1, 2, "Q6H", 1)
_CONTRA = "Take 1 tablet once a day at the same time each day"
MEDICINES = {
    "Acetaminophen 325 MG Oral Tablet": med("acetaminophen 325 mg", "acetaminophen", "325 mg", "N02BE01", PRN,
                                             "Take 1-2 tablets every 4-6 hours as needed for pain (max 4 g/day)", long_term=False),
    "Acetaminophen 500 MG Oral Tablet": med("acetaminophen 500 mg", "acetaminophen", "500 mg", "N02BE01", PRN,
                                             "Take 1-2 tablets every 6 hours as needed for pain (max 4 g/day)", long_term=False),
    "24 HR metoprolol succinate 50 MG Extended Release Oral Tablet": med(
        "metoprolol ER 50 mg", "metoprolol succinate", "50 mg", "C07AB02", OD, "Take 1 tablet once a day", cls="beta-blocker"),
    "24 HR metoprolol succinate 100 MG Extended Release Oral Tablet": med(
        "metoprolol ER 100 mg", "metoprolol succinate", "100 mg", "C07AB02", OD, "Take 1 tablet once a day", cls="beta-blocker"),
    "24 HR metoprolol succinate 200 MG Extended Release Oral Tablet": med(
        "metoprolol ER 200 mg", "metoprolol succinate", "200 mg", "C07AB02", OD, "Take 1 tablet once a day", cls="beta-blocker"),
    "carvedilol 3.125 MG Oral Tablet": med("carvedilol 3.125 mg", "carvedilol", "3.125 mg", "C07AG02", BID,
                                            "Take 1 tablet twice a day with food", cls="beta-blocker"),
    "carvedilol 25 MG Oral Tablet": med("carvedilol 25 mg", "carvedilol", "25 mg", "C07AG02", BID,
                                         "Take 1 tablet twice a day with food", cls="beta-blocker"),
    "Nitroglycerin 0.4 MG/ACTUAT Mucosal Spray": med(
        "nitroglycerin 0.4 mg/spray", "nitroglycerin", "0.4 mg/spray", "C01DA02", (1, 2, "Q5MIN", 1),
        "1-2 sprays under the tongue for chest pain; may repeat every 5 minutes, up to 3 doses; call 911 if pain persists",
        form="SPRAY", route="SL", unit="spray"),
    "nitroglycerin 0.4 MG Sublingual Tablet": med(
        "nitroglycerin 0.4 mg SL", "nitroglycerin", "0.4 mg", "C01DA02", (1, 1, "Q5MIN", 1),
        "Dissolve 1 tablet under the tongue for chest pain; may repeat every 5 minutes, up to 3 doses; call 911 if pain persists",
        route="SL"),
    "lisinopril 5 MG Oral Tablet": med("lisinopril 5 mg", "lisinopril", "5 mg", "C09AA03", OD, "Take 1 tablet once a day", cls="raas"),
    "lisinopril 10 MG Oral Tablet": med("lisinopril 10 mg", "lisinopril", "10 mg", "C09AA03", OD, "Take 1 tablet once a day", cls="raas"),
    "lisinopril 20 MG Oral Tablet": med("lisinopril 20 mg", "lisinopril", "20 mg", "C09AA03", OD, "Take 1 tablet once a day", cls="raas"),
    "lisinopril 40 MG Oral Tablet": med("lisinopril 40 mg", "lisinopril", "40 mg", "C09AA03", OD, "Take 1 tablet once a day", cls="raas"),
    "losartan potassium 50 MG Oral Tablet": med("losartan 50 mg", "losartan", "50 mg", "C09CA01", OD, "Take 1 tablet once a day", cls="raas"),
    "valsartan 160 MG Oral Tablet": med("valsartan 160 mg", "valsartan", "160 mg", "C09CA03", OD, "Take 1 tablet once a day", cls="raas"),
    "Hydrochlorothiazide 25 MG Oral Tablet": med("hydrochlorothiazide 25 mg", "hydrochlorothiazide", "25 mg", "C03AA03", OD,
                                                  "Take 1 tablet once a day in the morning"),
    "amLODIPine 2.5 MG Oral Tablet": med("amlodipine 2.5 mg", "amlodipine", "2.5 mg", "C08CA01", OD, "Take 1 tablet once a day"),
    "Clopidogrel 75 MG Oral Tablet": med("clopidogrel 75 mg", "clopidogrel", "75 mg", "B01AC04", OD, "Take 1 tablet once a day", cls="p2y12"),
    "prasugrel 10 MG Oral Tablet": med("prasugrel 10 mg", "prasugrel", "10 mg", "B01AC22", OD, "Take 1 tablet once a day", cls="p2y12"),
    "ticagrelor 90 MG Oral Tablet": med("ticagrelor 90 mg", "ticagrelor", "90 mg", "B01AC24", BID, "Take 1 tablet twice a day", cls="p2y12"),
    "aspirin 81 MG Oral Tablet": med("ASA 81 mg", "acetylsalicylic acid", "81 mg", "B01AC06", OD, "Take 1 tablet once a day", cls="asa"),
    "aspirin 81 MG Oral Capsule [Vazalore]": med("ASA 81 mg", "acetylsalicylic acid", "81 mg", "B01AC06", OD, "Take 1 capsule once a day",
                                                 form="CAP", unit="cap", cls="asa"),
    "aspirin 325 MG Oral Tablet": med("ASA 325 mg", "acetylsalicylic acid", "325 mg", "B01AC06", OD, "Take 1 tablet once a day", cls="asa"),
    "Simvastatin 10 MG Oral Tablet": med("simvastatin 10 mg", "simvastatin", "10 mg", "C10AA01", QHS, "Take 1 tablet at bedtime", cls="statin"),
    "Simvastatin 20 MG Oral Tablet": med("simvastatin 20 mg", "simvastatin", "20 mg", "C10AA01", QHS, "Take 1 tablet at bedtime", cls="statin"),
    "simvastatin 40 MG Oral Tablet": med("simvastatin 40 mg", "simvastatin", "40 mg", "C10AA01", QHS, "Take 1 tablet at bedtime", cls="statin"),
    "atorvastatin 10 MG Oral Tablet": med("atorvastatin 10 mg", "atorvastatin", "10 mg", "C10AA05", OD, "Take 1 tablet once a day", cls="statin"),
    "atorvastatin 40 MG Oral Tablet [Lipitor]": med("atorvastatin 40 mg", "atorvastatin", "40 mg", "C10AA05", OD, "Take 1 tablet once a day", cls="statin"),
    "rosuvastatin calcium 10 MG Oral Tablet": med("rosuvastatin 10 mg", "rosuvastatin", "10 mg", "C10AA07", OD, "Take 1 tablet once a day", cls="statin"),
    "rosuvastatin calcium 20 MG Oral Tablet": med("rosuvastatin 20 mg", "rosuvastatin", "20 mg", "C10AA07", OD, "Take 1 tablet once a day", cls="statin"),
    "rosuvastatin calcium 40 MG Oral Tablet": med("rosuvastatin 40 mg", "rosuvastatin", "40 mg", "C10AA07", OD, "Take 1 tablet once a day", cls="statin"),
    "Alendronic acid 10 MG Oral Tablet": med("alendronate 10 mg", "alendronate", "10 mg", "M05BA04", OD,
                                              "Take 1 tablet once a day in the morning with a full glass of water, 30 minutes before food; stay upright"),
    "Ibuprofen 200 MG Oral Tablet": med("ibuprofen 200 mg", "ibuprofen", "200 mg", "M01AE01", PRN,
                                         "Take 1-2 tablets every 6-8 hours as needed with food", long_term=False, cls="nsaid"),
    "Ibuprofen 400 MG Oral Tablet [Ibu]": med("ibuprofen 400 mg", "ibuprofen", "400 mg", "M01AE01", (1, 1, "Q6H", 1),
                                               "Take 1 tablet every 6-8 hours as needed with food", long_term=False, cls="nsaid"),
    "Naproxen sodium 220 MG Oral Tablet": med("naproxen sodium 220 mg", "naproxen", "220 mg", "M01AE02", (1, 1, "BID", 1),
                                               "Take 1 tablet twice a day as needed with food", long_term=False, cls="nsaid"),
    "Naproxen 500 MG Oral Tablet": med("naproxen 500 mg", "naproxen", "500 mg", "M01AE02", BID, "Take 1 tablet twice a day with food", cls="nsaid"),
    "Vitamin B12 5 MG/ML Injectable Solution": med(
        "cyanocobalamin 1000 mcg/mL inj", "cyanocobalamin", "1000 mcg/mL", "B03BA01", (1, 1, "QMONTH", 0),
        "Inject 1 mL into the muscle once a month", form="INJ", route="IM", unit="mL",
        canadian="Vitamin B12 5 mg/mL (US) -> cyanocobalamin 1000 mcg/mL, the Canadian strength"),
    "24 HR Metformin hydrochloride 500 MG Extended Release Oral Tablet": med(
        "metformin ER 500 mg", "metformin", "500 mg", "A10BA02", OD, "Take 1 tablet once a day with supper"),
    "insulin isophane  human 70 UNT/ML / insulin  regular  human 30 UNT/ML Injectable Suspension [Humulin]": med(
        "insulin NPH/regular 30/70 (Humulin 30/70)", "insulin human NPH/regular", "100 units/mL", "A10AD01", (None, None, "BID", 0),
        "Inject under the skin before breakfast and before supper, dose as directed", form="INJ", route="SC", unit="unit",
        canadian="Humulin 70/30 (US label) -> Humulin 30/70 (Canadian label, the same insulin)"),
    "Etonogestrel 68 MG Drug Implant": med("etonogestrel 68 mg implant", "etonogestrel", "68 mg", "G03AC08", (1, 1, "ONCE", 0),
                                           "Implant under the skin of the upper arm; effective for 3 years", form="IMPL", route="SUBDERMAL",
                                           unit="impl", cls="contraceptive"),
    "1 ML medroxyPROGESTERone acetate 150 MG/ML Injection": med(
        "medroxyprogesterone 150 mg/mL inj", "medroxyprogesterone acetate", "150 mg/mL", "G03AC06", (1, 1, "Q12W", 0),
        "Inject 1 mL into the muscle every 12 weeks", form="INJ", route="IM", unit="mL", cls="contraceptive"),
    "1 ML medroxyprogesterone acetate 150 MG/ML Injection": med(
        "medroxyprogesterone 150 mg/mL inj", "medroxyprogesterone acetate", "150 mg/mL", "G03AC06", (1, 1, "Q12W", 0),
        "Inject 1 mL into the muscle every 12 weeks", form="INJ", route="IM", unit="mL", cls="contraceptive"),
    "{7 (ethinyl estradiol 0.01 MG Oral Tablet) / 84 (ethinyl estradiol 0.03 MG / levonorgestrel 0.15 MG Oral Tablet) } Pack [Seasonique]": med(
        "levonorgestrel/EE 0.15/0.03 mg (91-day)", "levonorgestrel/ethinyl estradiol", "0.15 mg/0.03 mg", "G03AA07", OD,
        _CONTRA + " (91-day pack)", cls="contraceptive"),
    "{21 (ethinyl estradiol 0.03 MG / levonorgestrel 0.15 MG Oral Tablet) / 7 (inert ingredients 1 MG Oral Tablet) } Pack [Levora 0.15/30 28 Day]": med(
        "levonorgestrel/EE 0.15/0.03 mg", "levonorgestrel/ethinyl estradiol", "0.15 mg/0.03 mg", "G03AA07", OD, _CONTRA, cls="contraceptive"),
    "{28 (norethindrone 0.35 MG Oral Tablet) } Pack [Jolivette 28 Day]": med(
        "norethindrone 0.35 mg", "norethindrone", "0.35 mg", "G03AC01", OD, _CONTRA, cls="contraceptive"),
    "{28 (norethindrone 0.35 MG Oral Tablet) } Pack [Camila 28 Day]": med(
        "norethindrone 0.35 mg", "norethindrone", "0.35 mg", "G03AC01", OD, _CONTRA, cls="contraceptive"),
    "{7 (ethinyl estradiol 0.035 MG / norgestimate 0.18 MG Oral Tablet) / 7 (ethinyl estradiol 0.035 MG / norgestimate 0.215 MG Oral Tablet) / 7 (ethinyl estradiol 0.035 MG / norgestimate 0.25 MG Oral Tablet) / 7 (inert ingredients 1 MG Oral Tablet) } Pack [Trinessa 28 Day]": med(
        "norgestimate/EE triphasic", "norgestimate/ethinyl estradiol", "triphasic", "G03AA11", OD, _CONTRA, cls="contraceptive"),
    "{24 (drospirenone 3 MG / ethinyl estradiol 0.02 MG Oral Tablet) / 4 (inert ingredients 1 MG Oral Tablet) } Pack [Yaz 28 Day]": med(
        "drospirenone/EE 3/0.02 mg", "drospirenone/ethinyl estradiol", "3 mg/0.02 mg", "G03AA12", OD, _CONTRA, cls="contraceptive"),
    "{5 (dienogest 2 MG / estradiol valerate 2 MG Oral Tablet) / 17 (dienogest 3 MG / estradiol valerate 2 MG Oral Tablet) / 2 (estradiol valerate 1 MG Oral Tablet) / 2 (estradiol valerate 3 MG Oral Tablet) / 2 (inert ingredients 1 MG Oral Tablet) } Pack [Natazia 28 Day]": med(
        "levonorgestrel/EE 0.15/0.03 mg", "levonorgestrel/ethinyl estradiol", "0.15 mg/0.03 mg", "G03AA07", OD, _CONTRA, cls="contraceptive",
        canadian="Natazia (dienogest/estradiol valerate, not sold in Canada) -> levonorgestrel/ethinyl estradiol"),
    "168 HR Ethinyl Estradiol 0.00146 MG/HR / norelgestromin 0.00625 MG/HR Transdermal System": med(
        "norelgestromin/EE patch (Evra)", "norelgestromin/ethinyl estradiol", "150 mcg/35 mcg per day", "G03AA13", (1, 1, "QWEEK", 0),
        "Apply 1 patch once a week for 3 weeks, then 1 week without a patch", form="PATCH", route="TOP", unit="patch", cls="contraceptive"),
    "21 DAY ethinyl estradiol 0.000625 MG/HR / etonogestrel 0.005 MG/HR Vaginal System [NuvaRing]": med(
        "etonogestrel/EE vaginal ring (NuvaRing)", "etonogestrel/ethinyl estradiol", "0.12 mg/0.015 mg per day", "G02BB01", (1, 1, "Q4W", 0),
        "Insert 1 ring for 3 weeks, then remove for 1 week", form="RING", route="VAG", unit="ring", cls="contraceptive"),
    "levonorgestrel 0.000833 MG/HR Intrauterine System [Mirena]": med(
        "levonorgestrel 52 mg IUS (Mirena)", "levonorgestrel", "52 mg", "G02BA03", (1, 1, "ONCE", 0),
        "Intrauterine system; effective for up to 8 years", form="IUS", route="IU", unit="ius", cls="contraceptive"),
    "levonorgestrel 0.000813 MG/HR Intrauterine System [Liletta]": med(
        "levonorgestrel 52 mg IUS (Mirena)", "levonorgestrel", "52 mg", "G02BA03", (1, 1, "ONCE", 0),
        "Intrauterine system; effective for up to 8 years", form="IUS", route="IU", unit="ius", cls="contraceptive",
        canadian="Liletta (not sold in Canada) -> Mirena, the same 52 mg levonorgestrel system"),
    "0.4 ML Enoxaparin sodium 100 MG/ML Prefilled Syringe": med(
        "enoxaparin 40 mg/0.4 mL", "enoxaparin", "40 mg/0.4 mL", "B01AB05", OD, "Inject 40 mg under the skin once a day",
        form="INJ", route="SC", unit="syr", long_term=False),
    "1 ML Enoxaparin sodium 150 MG/ML Prefilled Syringe": med(
        "enoxaparin 150 mg/1 mL", "enoxaparin", "150 mg/1 mL", "B01AB05", OD, "Inject 150 mg under the skin once a day",
        form="INJ", route="SC", unit="syr", long_term=False),
    "Chlorpheniramine Maleate 2 MG/ML Oral Solution": med(
        "chlorpheniramine 2 mg/5 mL", "chlorpheniramine", "2 mg/5 mL", "R06AB04", (5, 10, "Q6H", 1),
        "Take 5-10 mL every 4-6 hours as needed", form="SOLN", unit="mL", long_term=False, cls="antihistamine",
        canadian="2 mg/mL (US) -> 2 mg/5 mL, the Canadian syrup"),
    "diphenhydrAMINE Hydrochloride 25 MG Oral Tablet": med(
        "diphenhydramine 25 mg", "diphenhydramine", "25 mg", "R06AA02", (1, 1, "QHS", 1), "Take 1 tablet at bedtime as needed",
        long_term=False, cls="antihistamine"),
    "Terfenadine 60 MG Oral Tablet": med(
        "fexofenadine 60 mg", "fexofenadine", "60 mg", "R06AX26", BID, "Take 1 tablet twice a day", cls="antihistamine",
        canadian="terfenadine (withdrawn in the 1990s) -> fexofenadine"),
    "Milnacipran hydrochloride 100 MG Oral Tablet": med(
        "duloxetine 60 mg", "duloxetine", "60 mg", "N06AX21", OD, "Take 1 capsule once a day", form="CAP", unit="cap",
        cls="snri", canadian="milnacipran (not sold in Canada) -> duloxetine 60 mg for fibromyalgia"),
    "duloxetine 20 MG Delayed Release Oral Capsule": med(
        "duloxetine 30 mg", "duloxetine", "30 mg", "N06AX21", OD, "Take 1 capsule once a day", form="CAP", unit="cap",
        cls="snri", canadian="duloxetine 20 mg (not sold in Canada) -> 30 mg"),
    "pregabalin 100 MG Oral Capsule": med("pregabalin 100 mg", "pregabalin", "100 mg", "N03AX16", BID, "Take 1 capsule twice a day",
                                          form="CAP", unit="cap"),
    "Penicillin V Potassium 500 MG Oral Tablet": med("penicillin V potassium 500 mg", "penicillin V", "500 mg", "J01CE02", QID,
                                                      "Take 1 tablet 4 times a day for 10 days", long_term=False),
    "Amoxicillin 250 MG / Clavulanate 125 MG Oral Tablet": med(
        "amoxicillin/clavulanate 250/125 mg", "amoxicillin/clavulanic acid", "250 mg/125 mg", "J01CR02", TID,
        "Take 1 tablet 3 times a day with food for 10 days", long_term=False),
    "azithromycin 250 MG Oral Tablet [Zithromax]": med("azithromycin 250 mg", "azithromycin", "250 mg", "J01FA10", OD,
                                                        "Take 2 tablets on day 1, then 1 tablet once a day for 4 days", long_term=False),
    "cephalexin 500 MG Oral Tablet": med("cephalexin 500 mg", "cephalexin", "500 mg", "J01DB01", QID,
                                          "Take 1 tablet 4 times a day for 7 days", long_term=False),
    "doxycycline hyclate 100 MG": med("doxycycline 100 mg", "doxycycline", "100 mg", "J01AA02", BID,
                                      "Take 1 tablet twice a day with a full glass of water for 7 days", long_term=False),
    "albuterol 5 MG/ML Inhalation Solution": med(
        "salbutamol 5 mg/mL nebules", "salbutamol", "5 mg/mL", "R03AC02", (1, 1, "QID", 1),
        "Inhale by nebulizer up to 4 times a day as needed", form="NEB", route="INH", unit="neb", cls="saba",
        canadian="albuterol (US name) -> salbutamol"),
    "NDA020503 200 ACTUAT Albuterol 0.09 MG/ACTUAT Metered Dose Inhaler": med(
        "salbutamol 100 mcg/puff inhaler", "salbutamol", "100 mcg/puff", "R03AC02", (1, 2, "Q4H", 1),
        "Inhale 1-2 puffs every 4-6 hours as needed", form="MDI", route="INH", unit="puff", cls="saba",
        canadian="albuterol 90 mcg (US) -> salbutamol 100 mcg/puff (Canadian)"),
    "NDA021457 200 ACTUAT albuterol 0.09 MG/ACTUAT Metered Dose Inhaler [ProAir]": med(
        "salbutamol 100 mcg/puff inhaler", "salbutamol", "100 mcg/puff", "R03AC02", (1, 2, "Q4H", 1),
        "Inhale 1-2 puffs every 4-6 hours as needed", form="MDI", route="INH", unit="puff", cls="saba",
        canadian="albuterol 90 mcg (US) -> salbutamol 100 mcg/puff (Canadian)"),
    "120 ACTUAT fluticasone propionate 0.044 MG/ACTUAT Metered Dose Inhaler [Flovent]": med(
        "fluticasone 50 mcg/puff inhaler", "fluticasone propionate", "50 mcg/puff", "R03BA05", (2, 2, "BID", 0),
        "Inhale 2 puffs twice a day; rinse mouth after use", form="MDI", route="INH", unit="puff", cls="ics",
        canadian="Flovent 44 mcg (US) -> Flovent HFA 50 mcg (Canadian)"),
    "120 ACTUAT fluticasone propionate 0.11 MG/ACTUAT Metered Dose Inhaler [Flovent]": med(
        "fluticasone 125 mcg/puff inhaler", "fluticasone propionate", "125 mcg/puff", "R03BA05", (2, 2, "BID", 0),
        "Inhale 2 puffs twice a day; rinse mouth after use", form="MDI", route="INH", unit="puff", cls="ics",
        canadian="Flovent 110 mcg (US) -> Flovent HFA 125 mcg (Canadian)"),
    "60 ACTUAT Fluticasone propionate 0.25 MG/ACTUAT / salmeterol 0.05 MG/ACTUAT Dry Powder Inhaler": med(
        "fluticasone/salmeterol 250/50 Diskus", "fluticasone propionate/salmeterol", "250 mcg/50 mcg", "R03AK06", BID,
        "Inhale 1 dose twice a day; rinse mouth after use", form="DPI", route="INH", unit="inh", cls="ics"),
    "carbamazepine 20 MG/ML Oral Suspension [Tegretol]": med(
        "carbamazepine 100 mg/5 mL suspension", "carbamazepine", "100 mg/5 mL", "N03AF01", (10, 10, "BID", 0),
        "Take 10 mL twice a day", form="SUSP", unit="mL"),
    "ferrous sulfate 325 MG Oral Tablet": med("ferrous sulfate 300 mg", "ferrous sulfate", "300 mg", "B03AA07", OD,
                                               "Take 1 tablet once a day", canadian="325 mg (US) -> 300 mg (Canadian)"),
    "1 ML Epoetin Alfa 4000 UNT/ML Injection [Epogen]": med(
        "epoetin alfa 4000 units/mL", "epoetin alfa", "4000 units/mL", "B03XA01", (1, 1, "QWEEK", 0),
        "Inject under the skin once a week", form="INJ", route="SC", unit="mL"),
    "24 HR tacrolimus 1 MG Extended Release Oral Tablet [Envarsus]": med(
        "tacrolimus ER 1 mg (Envarsus PA)", "tacrolimus", "1 mg", "L04AD02", OD, "Take once a day in the morning, dose as directed"),
    "Abuse-Deterrent 12 HR Oxycodone Hydrochloride 15 MG Extended Release Oral Tablet": med(
        "oxycodone CR 15 mg", "oxycodone", "15 mg", "N02AA05", (1, 1, "Q12H", 0), "Take 1 tablet every 12 hours; do not crush"),
    "Acetaminophen 300 MG / Codeine Phosphate 15 MG Oral Tablet": med(
        "acetaminophen/codeine 300/15 mg", "acetaminophen/codeine", "300 mg/15 mg", "N02AJ06", PRN,
        "Take 1-2 tablets every 4-6 hours as needed for pain", long_term=False),
    "Acetaminophen 300 MG / HYDROcodone Bitartrate 5 MG Oral Tablet": med(
        "acetaminophen/codeine 300/30 mg", "acetaminophen/codeine", "300 mg/30 mg", "N02AJ06", PRN,
        "Take 1-2 tablets every 4-6 hours as needed for pain", long_term=False,
        canadian="hydrocodone/acetaminophen (not sold in Canada) -> acetaminophen/codeine 300/30 mg"),
    "Acetaminophen 300 MG / Hydrocodone Bitartrate 5 MG Oral Tablet": med(
        "acetaminophen/codeine 300/30 mg", "acetaminophen/codeine", "300 mg/30 mg", "N02AJ06", PRN,
        "Take 1-2 tablets every 4-6 hours as needed for pain", long_term=False,
        canadian="hydrocodone/acetaminophen (not sold in Canada) -> acetaminophen/codeine 300/30 mg"),
    "Galantamine 4 MG Oral Tablet": med("galantamine 4 mg", "galantamine", "4 mg", "N06DA04", BID, "Take 1 tablet twice a day with food"),
    "Levothyroxine Sodium 0.075 MG Oral Tablet": med("levothyroxine 75 mcg", "levothyroxine", "75 mcg", "H03AA01", OD,
                                                     "Take 1 tablet once a day on an empty stomach"),
    "NDA020800 0.3 ML Epinephrine 1 MG/ML Auto-Injector": med(
        "epinephrine 0.3 mg auto-injector", "epinephrine", "0.3 mg", "C01CA24", (1, 1, "PRN", 1),
        "Inject into the outer thigh for a severe allergic reaction, then call 911", form="INJ", route="IM", unit="dose"),
    "Tamoxifen 10 MG Oral Tablet": med("tamoxifen 10 mg", "tamoxifen", "10 mg", "L02BA01", BID, "Take 1 tablet twice a day"),
    "neratinib 40 MG Oral Tablet": med("neratinib 40 mg", "neratinib", "40 mg", "L01EH02", (6, 6, "OD", 0),
                                       "Take 6 tablets once a day with food"),
    "palbociclib 100 MG Oral Capsule": med("palbociclib 100 mg", "palbociclib", "100 mg", "L01EF01", OD,
                                           "Take 1 capsule once a day for 21 days, then 7 days off", form="CAP", unit="cap"),
}
# Hospital-only or office-use products: never a prescription in the chart.
SKIP_MEDICINE_WORDS = (
    "sodium fluoride", "heparin", "bivalirudin", "morphine sulfate 1 MG/ML", "propofol", "eptifibatide",
    "ondansetron 2 MG/ML", "protamine", "midazolam", "rocuronium", "remifentanil", "isoflurane", "cefazolin",
    "clopidogrel 300", "tropicamide", "dextromethorphan",
)

# --- Vaccines: description -> (CARLOS prevention_type, display name, route, dose) --------------------------
IMMUNIZATIONS = {
    "Influenza  split virus  trivalent  PF": ("Inf", "Influenza vaccine", "IM", "0.5 mL"),
    "Td (adult)  5 Lf tetanus toxoid  preservative free  adsorbed": ("Td", "Td adsorbed (adult)", "IM", "0.5 mL"),
    "COVID-19  mRNA  LNP-S  PF  30 mcg/0.3 mL dose": ("COVID-19", "COVID-19 mRNA vaccine", "IM", "0.3 mL"),
    "COVID-19  mRNA  LNP-S  PF  100 mcg/0.5mL dose or 50 mcg/0.25mL dose": ("COVID-19", "COVID-19 mRNA vaccine", "IM", "0.5 mL"),
    "COVID-19 vaccine  vector-nr  rS-Ad26  PF  0.5 mL": ("COVID-19", "COVID-19 vaccine", "IM", "0.5 mL"),
    "zoster vaccine  live": ("LZV", "Live zoster vaccine", "SC", "0.65 mL"),
    "Pneumococcal conjugate PCV 13": ("Pneu-C-13", "Pneumococcal 13-valent conjugate", "IM", "0.5 mL"),
    "pneumococcal polysaccharide vaccine  23 valent": ("Pneu-P-23", "Pneumococcal polysaccharide 23-valent", "IM", "0.5 mL"),
    "Hep A  adult": ("HA", "Hepatitis A vaccine (adult)", "IM", "1 mL"),
    "Hep B  adult": ("HB", "Hepatitis B vaccine (adult)", "IM", "1 mL"),
    "meningococcal MCV4P": ("Men-C-ACYW-135", "Meningococcal ACYW-135 conjugate", "IM", "0.5 mL"),
}

# --- Measurements: LOINC -> (CARLOS type, factor to Canadian units, decimals, label for notes, unit) ------
# Blood pressure (8480-6 + 8462-4) is joined into one BP row as "sys/dia". eGFR is recomputed from
# creatinine (rule R6), never copied.
MEASUREMENTS = {
    "29463-7": ("WT", 1, 1, "Wt", "kg"),
    "8302-2": ("HT", 1, 1, "Ht", "cm"),
    "39156-5": ("BMI", 1, 1, "BMI", ""),
    "8867-4": ("HR", 1, 0, "HR", "/min"),
    "9279-1": ("RESP", 1, 0, "RR", "/min"),
    "8310-5": ("TEMP", 1, 1, "Temp", "C"),
    "2708-6": ("02", 1, 0, "O2 sat", "%"),
    "4548-4": ("A1C", 1, 1, "HbA1c", "%"),
    "18262-6": ("LDL", 1 / 38.67, 2, "LDL", "mmol/L"),
    "2085-9": ("HDL", 1 / 38.67, 2, "HDL", "mmol/L"),
    "2093-3": ("TCHL", 1 / 38.67, 2, "Total cholesterol", "mmol/L"),
    "2571-8": ("TG", 1 / 88.57, 2, "Triglycerides", "mmol/L"),
    "2339-0": ("FBS", 1 / 18.016, 1, "Glucose", "mmol/L"),
    "2345-7": ("FBS", 1 / 18.016, 1, "Glucose", "mmol/L"),
    "2160-0": ("SCR", 88.4, 0, "Creatinine", "umol/L"),
    "38483-4": ("SCR", 88.4, 0, "Creatinine", "umol/L"),
    "14959-1": ("ACR", 1 / 8.84, 1, "Urine ACR", "mg/mmol"),
    "2823-3": ("Kpl", 1, 1, "Potassium", "mmol/L"),
    "6298-4": ("Kpl", 1, 1, "Potassium", "mmol/L"),
    "2951-2": ("Napl", 1, 0, "Sodium", "mmol/L"),
    "2947-0": ("Napl", 1, 0, "Sodium", "mmol/L"),
    "718-7": ("Hb", 10, 0, "Hemoglobin", "g/L"),
    "6690-2": ("WBC", 1, 1, "WBC", "x10^9/L"),
    "777-3": ("PLT", 1, 0, "Platelets", "x10^9/L"),
    "787-2": ("MCV", 1, 0, "MCV", "fL"),
    "1742-6": ("ALT", 1, 0, "ALT", "U/L"),
    "1920-8": ("AST", 1, 0, "AST", "U/L"),
    "6768-6": ("ALP", 1, 0, "ALP", "U/L"),
    "1751-7": ("ALB", 10, 0, "Albumin", "g/L"),
    "1975-2": ("BILI", 17.1, 0, "Bilirubin", "umol/L"),
    "6301-6": ("INR", 1, 1, "INR", ""),
    "3016-3": ("TSH", 1, 2, "TSH", "mU/L"),
    "2276-4": ("Fer", 1, 0, "Ferritin", "ug/L"),
    "1988-5": ("CRP", 1, 1, "CRP", "mg/L"),
    "49765-1": ("CA", 0.2495, 2, "Calcium", "mmol/L"),
    "17861-6": ("CA", 0.2495, 2, "Calcium", "mmol/L"),
    "6299-2": ("BUN", 0.357, 1, "Urea", "mmol/L"),
    "3094-0": ("BUN", 0.357, 1, "Urea", "mmol/L"),
    "21377-7": ("MG", 0.4114, 2, "Magnesium", "mmol/L"),
    "19123-9": ("MG", 0.4114, 2, "Magnesium", "mmol/L"),
}
# Where two LOINC codes give the same CARLOS type on one day, the first listed here wins.
PREFERRED = ("2160-0", "2345-7", "2823-3", "2951-2", "17861-6", "3094-0")
