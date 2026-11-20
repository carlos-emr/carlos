/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * The clinical phrase corpus: sentences a clinician would actually write, each chosen so that it
 * trips a different OWASP CRS signature family when the packaged nginx + ModSecurity front door
 * inspects it. One definition, shared by clinical-freetext (the consultation request and the
 * demographic Alert/Notes, rules 1100 and 1131) and waf-clinical-text-corpus (every other
 * route that has a prose exclusion).
 *
 * Each phrase is a sentence a clinician would actually write, measured through the packaged front
 * door to score over the CRS inbound threshold on its own. The rule ids are what the ModSecurity
 * audit log reported. The pasted link goes FIRST in its phrase on purpose: 931100 is anchored on the
 * start of the argument, so a link buried mid-sentence does not exercise attack-rfi. `leading`
 * marks the phrase whose argument must START with it for that reason: a caller that joins several
 * phrases into one value puts that phrase first.
 *
 * There is deliberately NO phrase carrying HTML markup. Unlike the note route (1010), the survey
 * exclusions keep the CRS XSS family ON every argument -- ClinicalProseWafExclusionRegressionTest
 * pins that -- so a "<span style=...>" pasted into a referral is expected to answer 403 behind the
 * front door, and a corpus that carried one would fail against a correct rule set.
 */
const PROSE_CORPUS = Object.freeze([
  { label: 'plain prose', text: 'Routine follow up. Patient doing well.', crs: 'none' },
  { label: 'sentence semicolon', text: 'Reviewed labs with the patient; find attached the CBC and lytes.', crs: '932100/932110 attack-rce' },
  { label: 'shell-shaped cost', text: 'Cost ${45} per month; patient declined the brand.', crs: '932130 attack-rce' },
  { label: 'either-or plan', text: 'Select one of the two and order 1,2 tests.', crs: '932115/942350 rce+sqli' },
  { label: 'relative file path', text: 'See scanned report ../../images/ecg.png for the tracing.', crs: '930100/930110 attack-lfi' },
  { label: 'pasted PACS link first', text: 'http://10.0.0.5/pacs/study?id=1&cmd=view reviewed prior imaging with the patient.', crs: '931100 attack-rfi + 932110 attack-rce', leading: true },
  { label: 'wound measurement', text: 'Wound <2cm, clean. <?> follow up in 1 week.', crs: '933100 attack-injection-php' },
].map((phrase) => Object.freeze(phrase)));

module.exports = { PROSE_CORPUS };
