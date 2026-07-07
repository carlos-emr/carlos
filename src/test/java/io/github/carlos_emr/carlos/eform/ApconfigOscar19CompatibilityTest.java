package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EFormApConfig;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApconfigOscar19CompatibilityTest {

    private static final String EFORM_DEMOGRAPHIC_LIKE_PREDICATE =
            "demographic_no like '${eform_demographic}'";

    @Test
    void loadsCarlosAndOscar19ApconfigFixtures() throws Exception {
        EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        assertNotNull(oscar);
        assertNotNull(carlos);
        assertFalse(oscar.getDatabaseAPs().isEmpty());
        assertFalse(carlos.getDatabaseAPs().isEmpty());
    }

    @Test
    void eformValueQueriesPreserveOscar19WildcardSemantics() throws Exception {
        EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        for (String name : List.of(
                "_eform_values_first",
                "_eform_values_last",
                "_eform_values_first_all_json",
                "_eform_values_last_all_json",
                "_eform_values_count",
                "_eform_values_countname",
                "_eform_values_count_ref",
                "_eform_values_countname_ref",
                "_eform_values_count_refname",
                "_eform_values_countname_refname")) {
            String oscarSql = normalizeWhitespace(exactlyOneByName(oscar, name).getApSQL());
            String carlosSql = normalizeWhitespace(exactlyOneByName(carlos, name).getApSQL());

            assertTrue(oscarSql.contains(EFORM_DEMOGRAPHIC_LIKE_PREDICATE),
                    name + " should preserve the OSCAR19 wildcard predicate");
            assertTrue(carlosSql.contains(EFORM_DEMOGRAPHIC_LIKE_PREDICATE),
                    name + " should preserve the OSCAR19 wildcard predicate");
        }
    }

    @Test
    void addressFormattingMatchesOscar19ProvinceAbbreviationContract() throws Exception {
        EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        assertEquals(exactlyOneByName(oscar, "address").getApOutput(),
                exactlyOneByName(carlos, "address").getApOutput(),
                "address should match the OSCAR19 output contract");
        assertEquals(exactlyOneByName(oscar, "addressline").getApOutput(),
                exactlyOneByName(carlos, "addressline").getApOutput(),
                "addressline should match the OSCAR19 output contract");
        assertEquals(exactlyOneByName(oscar, "province").getApOutput(),
                exactlyOneByName(carlos, "province").getApOutput(),
                "province should match the OSCAR19 output contract");
    }

    @Test
    void duplicateApNamesAreEitherIdenticalOrIntentionallyReviewed() throws Exception {
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        Map<String, List<DatabaseAP>> grouped = carlos.getDatabaseAPs().stream()
                .collect(Collectors.groupingBy(DatabaseAP::getApName, LinkedHashMap::new, Collectors.toList()));

        assertTrue(grouped.containsKey("appt_date"), "Expected duplicate review coverage for appt_date");

        List<DatabaseAP> apptDateEntries = grouped.get("appt_date");
        assertEquals(2, apptDateEntries.size(), "appt_date should remain a reviewed duplicate pair");
        assertEquals(apptDateEntries.get(0).getApSQL(), apptDateEntries.get(1).getApSQL(),
                "appt_date duplicates should remain identical until intentionally reviewed");
    }

    private EFormApConfig loadConfig(String classpathLocation) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            assertNotNull(input, "Missing classpath resource: " + classpathLocation);
            JAXBContext context = JAXBContext.newInstance(EFormApConfig.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (EFormApConfig) unmarshaller.unmarshal(XmlUtils.createSecureJaxbSource(input));
        }
    }

    private DatabaseAP exactlyOneByName(EFormApConfig config, String apName) {
        List<DatabaseAP> matches = findByName(config, apName);
        assertEquals(1, matches.size(), "Expected exactly one AP named " + apName);
        return matches.get(0);
    }

    private List<DatabaseAP> findByName(EFormApConfig config, String apName) {
        return config.getDatabaseAPs().stream()
                .filter(ap -> apName.equalsIgnoreCase(ap.getApName()))
                .toList();
    }

    private String normalizeWhitespace(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }
}
