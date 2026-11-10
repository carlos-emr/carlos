package io.github.carlos_emr.carlos.utility;

import java.io.StringReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import cds.OmdCdsDocument;
import cdsDt.DiabetesComplicationScreening;
import org.apache.xmlbeans.XmlOptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real vendored clinical schema bindings against the modern XMLBeans runtime. */
@Tag("integration")
@Tag("demographic")
class LegacyXmlBeansCompatibilityIntegrationTest {
    @TempDir
    Path directory;

    @Test
    void shouldRetainExamCodeAndDate_whenClinicalXmlIsSavedAndParsed() throws Exception {
        var document = clinicalDocument();
        var options = new XmlOptions().setSavePrettyPrint().setSavePrettyPrintIndent(3)
                .setSaveAggressiveNamespaces();
        var parsed = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                document.xmlText(options), options);
        assertClinicalValues(parsed);
    }

    @Test
    void shouldUseTheClinicalSchema_whenParsingAReaderOrFile() throws Exception {
        String xml = clinicalDocument().xmlText();
        var fromReader = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                new StringReader(xml));
        Path file = directory.resolve("synthetic-cds.xml");
        Files.writeString(file, xml);
        var fromFile = LegacyXmlBeans.parse(OmdCdsDocument.class, OmdCdsDocument.type,
                file.toFile(), new XmlOptions().setDocumentType(OmdCdsDocument.type));
        assertClinicalValues(fromReader);
        assertClinicalValues(fromFile);
    }

    @Test
    void shouldPreserveEnumAliases_whenLegacyBindingsSetACodedValue() {
        var holder = new ExamCodeHolder();
        holder.set(DiabetesComplicationScreening.ExamCode.X_67536_3);
        assertThat(holder.enumValue()).isEqualTo(DiabetesComplicationScreening.ExamCode.X_67536_3);
        assertThat(holder.getStringValue()).isEqualTo("67536-3");
    }

    @Test
    void shouldPreserveNamesAndIdentifiers_whenHrmXmlIsWrittenAndRead() throws Exception {
        var document = LegacyXmlBeans.newInstance(cdshrm.OmdCdsDocument.class, cdshrm.OmdCdsDocument.type);
        var demographics = document.addNewOmdCds().addNewPatientRecord().addNewDemographics();
        demographics.setChartNumber("12345678901234567");
        demographics.setUniqueVendorIdSequence("00123");
        var name = demographics.addNewNames().addNewLegalName();
        name.addNewFirstName().setPart("Synthetic Élodie 李");
        name.addNewLastName().setPart("Example");
        var copy = LegacyXmlBeans.parse(cdshrm.OmdCdsDocument.class, cdshrm.OmdCdsDocument.type,
                document.xmlText());
        var restored = copy.getOmdCds().getPatientRecord().getDemographics();
        assertThat(restored.getChartNumber()).isEqualTo("12345678901234567");
        assertThat(restored.getUniqueVendorIdSequence()).isEqualTo("00123");
        assertThat(restored.getNames().getLegalName().getFirstName().getPart()).isEqualTo("Synthetic Élodie 李");
        assertThat(restored.getNames().getLegalName().getLastName().getPart()).isEqualTo("Example");
    }

    @Test
    void shouldPreserveClinicalScores_whenRourkeXmlIsWrittenAndRead() throws Exception {
        var document = LegacyXmlBeans.newInstance(cdsrourke.PatientDocument.class, cdsrourke.PatientDocument.type);
        var patient = document.addNewPatient();
        patient.setAPGAR1(7);
        patient.setBREM(BigInteger.ONE);
        var copy = LegacyXmlBeans.parse(cdsrourke.PatientDocument.class, cdsrourke.PatientDocument.type,
                document.xmlText());
        assertThat(copy.getPatient().getAPGAR1()).isEqualTo(7);
        assertThat(copy.getPatient().getBREM()).isEqualTo(BigInteger.ONE);
    }

    private static OmdCdsDocument clinicalDocument() {
        var document = LegacyXmlBeans.newInstance(OmdCdsDocument.class, OmdCdsDocument.type);
        var exam = document.addNewOmdCds().addNewPatientRecord().addNewCareElements()
                .addNewDiabetesComplicationsScreening();
        var date = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        date.clear();
        date.set(2026, 7, 31);
        exam.setDate(date);
        exam.setExamCode(DiabetesComplicationScreening.ExamCode.X_67536_3);
        return document;
    }

    private static void assertClinicalValues(OmdCdsDocument document) {
        assertThat(document.schemaType()).isEqualTo(OmdCdsDocument.type);
        var exam = document.getOmdCds().getPatientRecord().getCareElementsArray(0)
                .getDiabetesComplicationsScreeningArray(0);
        assertThat(exam.getExamCode()).isEqualTo(DiabetesComplicationScreening.ExamCode.X_67536_3);
        assertThat(exam.getDate().get(GregorianCalendar.YEAR)).isEqualTo(2026);
        assertThat(exam.getDate().get(GregorianCalendar.MONTH)).isEqualTo(7);
        assertThat(exam.getDate().get(GregorianCalendar.DAY_OF_MONTH)).isEqualTo(31);
    }

    private static final class ExamCodeHolder extends LegacyXmlStringEnumerationHolder {
        private static final long serialVersionUID = 1L;

        private ExamCodeHolder() {
            super(DiabetesComplicationScreening.ExamCode.type, false);
        }
    }
}
