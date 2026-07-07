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
            String oscarSql = findByName(oscar, name).get(0).getApSQL();
            String carlosSql = findByName(carlos, name).get(0).getApSQL();

            assertTrue(oscarSql.contains("demographic_no like ''"));
            assertTrue(carlosSql.contains("demographic_no like ''"));
        }
    }

    @Test
    void addressFormattingMatchesOscar19ProvinceAbbreviationContract() throws Exception {
        EFormApConfig oscar = loadConfig("oscar/eform/oscar19-apconfig.xml");
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        assertEquals(findByName(oscar, "address").get(0).getApOutput(),
                findByName(carlos, "address").get(0).getApOutput());
        assertEquals(findByName(oscar, "addressline").get(0).getApOutput(),
                findByName(carlos, "addressline").get(0).getApOutput());
        assertEquals(findByName(oscar, "province").get(0).getApOutput(),
                findByName(carlos, "province").get(0).getApOutput());
    }

    @Test
    void duplicateApNamesAreEitherIdenticalOrIntentionallyReviewed() throws Exception {
        EFormApConfig carlos = loadConfig("oscar/eform/apconfig.xml");

        Map<String, List<DatabaseAP>> grouped = carlos.getDatabaseAPs().stream()
                .collect(Collectors.groupingBy(DatabaseAP::getApName, LinkedHashMap::new, Collectors.toList()));

        assertEquals(2, grouped.get("appt_date").size());
        assertEquals(grouped.get("appt_date").get(0).getApSQL(), grouped.get("appt_date").get(1).getApSQL());
    }

    private EFormApConfig loadConfig(String classpathLocation) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(classpathLocation)) {
            assertNotNull(input, "Missing classpath resource: " + classpathLocation);
            JAXBContext context = JAXBContext.newInstance(EFormApConfig.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (EFormApConfig) unmarshaller.unmarshal(XmlUtils.createSecureJaxbSource(input));
        }
    }

    private List<DatabaseAP> findByName(EFormApConfig config, String apName) {
        return config.getDatabaseAPs().stream()
                .filter(ap -> apName.equalsIgnoreCase(ap.getApName()))
                .toList();
    }
}
