/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

// The Edinburgh Postnatal Depression Scale as published, for issue #5050: the English of the original scale and the
// validated French version, item by item, each item's answers in the order the BC-AR pages show them (the first
// answer scores 0 on items 1, 2 and 4 and 3 on the others). scripts/bcar-epds-wording.test.js holds the bundles to
// these words and scripts/form-bc-epds-wording-playwright-checks.js holds the rendered pages to them.

// Cox JL, Holden JM, Sagovsky R (1987), British Journal of Psychiatry 150:782-786.
const ENGLISH = {
    1: ['1. I have been able to laugh and see the funny side of things',
        ['As much as I always could', 'Not quite so much now', 'Definitely not so much now', 'Not at all']],
    2: ['2. I have looked forward with enjoyment to things',
        ['As much as I ever did', 'Rather less than I used to', 'Definitely less than I used to', 'Hardly at all']],
    3: ['3. I have blamed myself unnecessarily when things went wrong',
        ['Yes, most of the time', 'Yes, some of the time', 'Not very often', 'No, never']],
    4: ['4. I have been anxious or worried for no good reason',
        ['No, not at all', 'Hardly ever', 'Yes, sometimes', 'Yes, very often']],
    5: ['5. I have felt scared or panicky for no very good reason',
        ['Yes, quite a lot', 'Yes, sometimes', 'No, not much', 'No, not at all']],
    6: ['6. Things have been getting on top of me',
        ["Yes, most of the time I haven't been able to cope at all", "Yes, sometimes I haven't been coping as well as usual",
            'No, most of the time I have coped quite well', 'No, I have been coping as well as ever']],
    7: ['7. I have been so unhappy that I have had difficulty sleeping',
        ['Yes, most of the time', 'Yes, sometimes', 'Not very often', 'No, not at all']],
    8: ['8. I have felt sad or miserable',
        ['Yes, most of the time', 'Yes, quite often', 'Not very often', 'No, not at all']],
    9: ['9. I have been so unhappy that I have been crying',
        ['Yes, most of the time', 'Yes, quite often', 'Only occasionally', 'No, never']],
    10: ['10. The thought of harming myself has occurred to me',
        ['Yes, quite often', 'Sometimes', 'Hardly ever', 'Never']],
};

// Guedeney N, Fermanian J (1998), European Psychiatry 13(2):83-89, as reproduced word for word
// by Alberta Health Services, the Government of Western Australia Department of Health and the
// Association Depression Postpartale Suisse (sources listed in oscarResources_fr.properties).
const FRENCH = {
    1: ["1. J'ai pu rire et prendre les choses du bon côté",
        ["Aussi souvent que d'habitude", 'Pas tout-à-fait autant',
            'Vraiment beaucoup moins souvent ces jours-ci', 'Absolument pas']],
    2: ["2. Je me suis sentie confiante et joyeuse, en pensant à l'avenir",
        ["Autant que d'habitude", "Plutôt moins que d'habitude", "Vraiment moins que d'habitude",
            'Pratiquement pas']],
    3: ["3. Je me suis reprochée, sans raisons, d'être responsable quand les choses allaient mal",
        ['Oui, la plupart du temps', 'Oui, parfois', 'Pas très souvent', 'Non, jamais']],
    4: ['4. Je me suis sentie inquiète ou soucieuse sans motifs',
        ['Non, pas du tout', 'Presque jamais', 'Oui, parfois', 'Oui, très souvent']],
    5: ['5. Je me suis sentie effrayée ou paniquée sans vraiment de raisons',
        ['Oui, vraiment souvent', 'Oui, parfois', 'Non, pas très souvent', 'Non, pas du tout']],
    6: ["6. J'ai eu tendance à me sentir dépassée par les évènements",
        ['Oui, la plupart du temps, je me suis sentie incapable de faire face aux situations',
            "Oui, parfois, je ne me suis pas sentie aussi capable de faire face que d'habitude",
            "Non, j'ai pu faire face à la plupart des situations",
            "Non, je me suis sentie aussi efficace que d'habitude"]],
    7: ["7. Je me suis sentie si malheureuse que j'ai eu des problèmes de sommeil",
        ['Oui, la plupart du temps', 'Oui, parfois', 'Pas très souvent', 'Non, pas du tout']],
    8: ['8. Je me suis sentie triste ou peu heureuse',
        ['Oui, la plupart du temps', 'Oui, très souvent', 'Pas très souvent', 'Non, pas du tout']],
    9: ["9. Je me suis sentie si malheureuse que j'en ai pleuré",
        ['Oui, la plupart du temps', 'Oui, très souvent', 'Seulement de temps en temps', 'Non, jamais']],
    10: ["10. Il m'est arrivé de penser à me faire du mal",
        ['Oui, très souvent', 'Parfois', 'Presque jamais', 'Jamais']],
};

// Items whose first answer scores 0; every other item is reverse scored (first answer scores 3).
const FORWARD_SCORED = new Set([1, 2, 4]);

module.exports = { ENGLISH, FRENCH, FORWARD_SCORED };
