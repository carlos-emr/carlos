package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "codeType")
@XmlEnum
public enum CodeType
{
    @XmlEnumValue("ICD9")
    ICD_9("ICD9"), 
    @XmlEnumValue("ICD10")
    ICD_10("ICD10"), 
    CUSTOM_ISSUE("CUSTOM_ISSUE"), 
    DRUG("DRUG"), 
    PREVENTION("PREVENTION"), 
    SYSTEM("SYSTEM"), 
    SNOMED("SNOMED"), 
    SNOMED_CORE("SNOMED_CORE");
    
    private final String value;
    
    private CodeType(final String value) {
        this.value = value;
    }
    
    public String value() {
        return this.value;
    }
    
    public static CodeType fromValue(final String s) {
        for (final CodeType codeType : values()) {
            if (codeType.value.equals(s)) {
                return codeType;
            }
        }
        throw new IllegalArgumentException(s);
    }
}
