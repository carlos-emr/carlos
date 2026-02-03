/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.form.util;

import java.util.HashMap;
import java.util.Map;

public class LanguageUtil {
    private static Map<String, String> languageMap;

    static {
        languageMap = new HashMap<>();
        languageMap.put("ENG", "English");
        languageMap.put("FRA", "French");
        languageMap.put("AAR", "Afar");
        languageMap.put("AFR", "Afrikaans");
        languageMap.put("AKA", "Akan");
        languageMap.put("SQI", "Albanian");
        languageMap.put("ASE", "American Sign Language (ASL)");
        languageMap.put("AMH", "Amharic");
        languageMap.put("ARA", "Arabic");
        languageMap.put("ARG", "Aragonese");
        languageMap.put("HYE", "Armenian");
        languageMap.put("ASM", "Assamese");
        languageMap.put("AVA", "Avaric");
        languageMap.put("AYM", "Aymara");
        languageMap.put("AZE", "Azerbaijani");
        languageMap.put("BAM", "Bambara");
        languageMap.put("BAK", "Bashkir");
        languageMap.put("EUS", "Basque");
        languageMap.put("BEL", "Belarusian");
        languageMap.put("BEN", "Bengali");
        languageMap.put("BIS", "Bislama");
        languageMap.put("BOS", "Bosnian");
        languageMap.put("BRE", "Breton");
        languageMap.put("BUL", "Bulgarian");
        languageMap.put("MYA", "Burmese");
        languageMap.put("CAT", "Catalan");
        languageMap.put("KHM", "Central Khmer");
        languageMap.put("CHA", "Chamorro");
        languageMap.put("CHE", "Chechen");
        languageMap.put("YUE", "Chinese Cantonese");
        languageMap.put("CMN", "Chinese Mandarin");
        languageMap.put("CHV", "Chuvash");
        languageMap.put("COR", "Cornish");
        languageMap.put("COS", "Corsican");
        languageMap.put("CRE", "Cree");
        languageMap.put("HRV", "Croatian");
        languageMap.put("CES", "Czech");
        languageMap.put("DAN", "Danish");
        languageMap.put("DIV", "Dhivehi");
        languageMap.put("NLD", "Dutch");
        languageMap.put("DZO", "Dzongkha");
        languageMap.put("EST", "Estonian");
        languageMap.put("EWE", "Ewe");
        languageMap.put("FAO", "Faroese");
        languageMap.put("FIJ", "Fijian");
        languageMap.put("FIL", "Filipino");
        languageMap.put("FIN", "Finnish");
        languageMap.put("FUL", "Fulah");
        languageMap.put("GLG", "Galician");
        languageMap.put("LUG", "Ganda");
        languageMap.put("KAT", "Georgian");
        languageMap.put("DEU", "German");
        languageMap.put("GRN", "Guarani");
        languageMap.put("GUJ", "Gujarati");
        languageMap.put("HAT", "Haitian");
        languageMap.put("HAU", "Hausa");
        languageMap.put("HEB", "Hebrew");
        languageMap.put("HER", "Herero");
        languageMap.put("HIN", "Hindi");
        languageMap.put("HMO", "Hiri Motu");
        languageMap.put("HUN", "Hungarian");
        languageMap.put("ISL", "Icelandic");
        languageMap.put("IBO", "Igbo");
        languageMap.put("IND", "Indonesian");
        languageMap.put("IKU", "Inuktitut");
        languageMap.put("IPK", "Inupiaq");
        languageMap.put("GLE", "Irish");
        languageMap.put("ITA", "Italian");
        languageMap.put("JPN", "Japanese");
        languageMap.put("JAV", "Javanese");
        languageMap.put("KAL", "Kalaallisut");
        languageMap.put("KAN", "Kannada");
        languageMap.put("KAU", "Kanuri");
        languageMap.put("KAS", "Kashmiri");
        languageMap.put("KAZ", "Kazakh");
        languageMap.put("KIK", "Kikuyu");
        languageMap.put("KIN", "Kinyarwanda");
        languageMap.put("KIR", "Kirghiz");
        languageMap.put("KOM", "Komi");
        languageMap.put("KON", "Kongo");
        languageMap.put("KOR", "Korean");
        languageMap.put("KUA", "Kuanyama");
        languageMap.put("KUR", "Kurdish");
        languageMap.put("LAO", "Lao");
        languageMap.put("LAV", "Latvian");
        languageMap.put("LIM", "Limburgan");
        languageMap.put("LIN", "Lingala");
        languageMap.put("LIT", "Lithuanian");
        languageMap.put("LUB", "Luba-Katanga");
        languageMap.put("LTZ", "Luxembourgish");
        languageMap.put("MKD", "Macedonian");
        languageMap.put("MLG", "Malagasy");
        languageMap.put("MSA", "Malay");
        languageMap.put("MAL", "Malayalam");
        languageMap.put("MLT", "Maltese");
        languageMap.put("GLV", "Manx");
        languageMap.put("MRI", "Maori");
        languageMap.put("MAR", "Marathi");
        languageMap.put("MAH", "Marshallese");
        languageMap.put("ELL", "Greek");
        languageMap.put("MON", "Mongolian");
        languageMap.put("NAU", "Nauru");
        languageMap.put("NAV", "Navajo");
        languageMap.put("NDO", "Ndonga");
        languageMap.put("NEP", "Nepali");
        languageMap.put("NDE", "North Ndebele");
        languageMap.put("SME", "Northern Sami");
        languageMap.put("NOR", "Norwegian");
        languageMap.put("NOB", "Norwegian Bokmål");
        languageMap.put("NNO", "Norwegian Nynorsk");
        languageMap.put("NYA", "Nyanja");
        languageMap.put("OCI", "Occitan (post 1500)");
        languageMap.put("OJI", "Ojibwa");
        languageMap.put("OJC", "Oji-cree");
        languageMap.put("ORI", "Oriya");
        languageMap.put("ORM", "Oromo");
        languageMap.put("OSS", "Ossetian");
        languageMap.put("PAN", "Panjabi");
        languageMap.put("FAS", "Persian");
        languageMap.put("POL", "Polish");
        languageMap.put("POR", "Portuguese");
        languageMap.put("PUS", "Pashto");
        languageMap.put("QUE", "Quechua");
        languageMap.put("RON", "Romanian");
        languageMap.put("ROH", "Romansh");
        languageMap.put("RUN", "Rundi");
        languageMap.put("RUS", "Russian");
        languageMap.put("SMO", "Samoan");
        languageMap.put("SAG", "Sango");
        languageMap.put("SRD", "Sardinian");
        languageMap.put("GLA", "Scottish Gaelic");
        languageMap.put("SRP", "Serbian");
        languageMap.put("SNA", "Shona");
        languageMap.put("III", "Sichuan Yi");
        languageMap.put("SND", "Sindhi");
        languageMap.put("SIN", "Sinhala");
        languageMap.put("SGN", "Other Sign Language");
        languageMap.put("SLK", "Slovak");
        languageMap.put("SLV", "Slovenian");
        languageMap.put("SOM", "Somali");
        languageMap.put("NBL", "South Ndebele");
        languageMap.put("SOT", "Southern Sotho");
        languageMap.put("SPA", "Spanish");
        languageMap.put("SUN", "Sundanese");
        languageMap.put("SWA", "Swahili (macrolanguage)");
        languageMap.put("SSW", "Swati");
        languageMap.put("SWE", "Swedish");
        languageMap.put("TGL", "Tagalog");
        languageMap.put("TAH", "Tahitian");
        languageMap.put("TGK", "Tajik");
        languageMap.put("TAM", "Tamil");
        languageMap.put("TAT", "Tatar");
        languageMap.put("TEL", "Telugu");
        languageMap.put("THA", "Thai");
        languageMap.put("BOD", "Tibetan");
        languageMap.put("TIR", "Tigrinya");
        languageMap.put("TON", "Tonga (Tonga Islands)");
        languageMap.put("TSO", "Tsonga");
        languageMap.put("TSN", "Tswana");
        languageMap.put("TUR", "Turkish");
        languageMap.put("TUK", "Turkmen");
        languageMap.put("TWI", "Twi");
        languageMap.put("UIG", "Uighur");
        languageMap.put("UKR", "Ukrainian");
        languageMap.put("URD", "Urdu");
        languageMap.put("UZB", "Uzbek");
        languageMap.put("VEN", "Venda");
        languageMap.put("VIE", "Vietnamese");
        languageMap.put("WLN", "Walloon");
        languageMap.put("CYM", "Welsh");
        languageMap.put("FRY", "Western Frisian");
        languageMap.put("WOL", "Wolof");
        languageMap.put("XHO", "Xhosa");
        languageMap.put("YID", "Yiddish");
        languageMap.put("YOR", "Yoruba");
        languageMap.put("ZHA", "Zhuang");
        languageMap.put("ZUL", "Zulu");
        languageMap.put("OTH", "Other");
        languageMap.put("UN", "Unknown");
    }

    public static String getLanguage(String languageCode) {
        String language = languageMap.get(languageCode);
        if (language == null) {
            return languageCode;
        } else {
            return language;
        }
    }
}
