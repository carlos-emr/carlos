package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EFormApConfig;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
