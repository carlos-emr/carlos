package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNamePartQualifierCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNamePartTypeCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNamePrefixCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNamePurposeCode;
import io.github.carlos_emr.carlos.hospitalReportManager.xsd.PersonNameSuffixCode;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="personNameStandard", propOrder={"namePrefix", "legalName", "otherNames", "lastNameSuffix"})
public class PersonNameStandard {
    @XmlElement(name="NamePrefix")
    protected PersonNamePrefixCode namePrefix;
    @XmlElement(name="LegalName", required=true)
    protected LegalName legalName;
    @XmlElement(name="OtherNames")
    protected List<OtherNames> otherNames;
    @XmlElement(name="LastNameSuffix")
    protected PersonNameSuffixCode lastNameSuffix;

    public PersonNamePrefixCode getNamePrefix() {
        return this.namePrefix;
    }

    public void setNamePrefix(PersonNamePrefixCode value) {
        this.namePrefix = value;
    }

    public LegalName getLegalName() {
        return this.legalName;
    }

    public void setLegalName(LegalName value) {
        this.legalName = value;
    }

    public List<OtherNames> getOtherNames() {
        if (this.otherNames == null) {
            this.otherNames = new ArrayList<OtherNames>();
        }
        return this.otherNames;
    }

    public PersonNameSuffixCode getLastNameSuffix() {
        return this.lastNameSuffix;
    }

    public void setLastNameSuffix(PersonNameSuffixCode value) {
        this.lastNameSuffix = value;
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={"otherName"})
    public static class OtherNames {
        @XmlElement(name="OtherName")
        protected List<OtherName> otherName;
        @XmlAttribute(required=true)
        protected PersonNamePurposeCode namePurpose;

        public List<OtherName> getOtherName() {
            if (this.otherName == null) {
                this.otherName = new ArrayList<OtherName>();
            }
            return this.otherName;
        }

        public PersonNamePurposeCode getNamePurpose() {
            return this.namePurpose;
        }

        public void setNamePurpose(PersonNamePurposeCode value) {
            this.namePurpose = value;
        }

        @XmlAccessorType(value=XmlAccessType.FIELD)
        @XmlType(name="", propOrder={})
        public static class OtherName {
            @XmlElement(name="Part", required=true)
            protected String part;
            @XmlElement(name="PartType", required=true)
            protected PersonNamePartTypeCode partType;
            @XmlElement(name="PartQualifier")
            protected PersonNamePartQualifierCode partQualifier;

            public String getPart() {
                return this.part;
            }

            public void setPart(String value) {
                this.part = value;
            }

            public PersonNamePartTypeCode getPartType() {
                return this.partType;
            }

            public void setPartType(PersonNamePartTypeCode value) {
                this.partType = value;
            }

            public PersonNamePartQualifierCode getPartQualifier() {
                return this.partQualifier;
            }

            public void setPartQualifier(PersonNamePartQualifierCode value) {
                this.partQualifier = value;
            }
        }
    }

    /*
     * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
     */
    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={"firstName", "lastName", "otherName"})
    public static class LegalName {
        @XmlElement(name="FirstName", required=true)
        protected FirstName firstName;
        @XmlElement(name="LastName", required=true)
        protected LastName lastName;
        @XmlElement(name="OtherName")
        protected List<OtherName> otherName;
        @XmlAttribute(required=true)
        protected PersonNamePurposeCode namePurpose;

        public FirstName getFirstName() {
            return this.firstName;
        }

        public void setFirstName(FirstName value) {
            this.firstName = value;
        }

        public LastName getLastName() {
            return this.lastName;
        }

        public void setLastName(LastName value) {
            this.lastName = value;
        }

        public List<OtherName> getOtherName() {
            if (this.otherName == null) {
                this.otherName = new ArrayList<OtherName>();
            }
            return this.otherName;
        }

        public PersonNamePurposeCode getNamePurpose() {
            if (this.namePurpose == null) {
                return PersonNamePurposeCode.L;
            }
            return this.namePurpose;
        }

        public void setNamePurpose(PersonNamePurposeCode value) {
            this.namePurpose = value;
        }

        @XmlAccessorType(value=XmlAccessType.FIELD)
        @XmlType(name="", propOrder={})
        public static class OtherName {
            @XmlElement(name="Part", required=true)
            protected String part;
            @XmlElement(name="PartType", required=true)
            protected PersonNamePartTypeCode partType;
            @XmlElement(name="PartQualifier")
            protected PersonNamePartQualifierCode partQualifier;

            public String getPart() {
                return this.part;
            }

            public void setPart(String value) {
                this.part = value;
            }

            public PersonNamePartTypeCode getPartType() {
                return this.partType;
            }

            public void setPartType(PersonNamePartTypeCode value) {
                this.partType = value;
            }

            public PersonNamePartQualifierCode getPartQualifier() {
                return this.partQualifier;
            }

            public void setPartQualifier(PersonNamePartQualifierCode value) {
                this.partQualifier = value;
            }
        }

        @XmlAccessorType(value=XmlAccessType.FIELD)
        @XmlType(name="", propOrder={})
        public static class LastName {
            @XmlElement(name="Part", required=true)
            protected String part;
            @XmlElement(name="PartType", required=true)
            protected PersonNamePartTypeCode partType;
            @XmlElement(name="PartQualifier")
            protected PersonNamePartQualifierCode partQualifier;

            public String getPart() {
                return this.part;
            }

            public void setPart(String value) {
                this.part = value;
            }

            public PersonNamePartTypeCode getPartType() {
                return this.partType;
            }

            public void setPartType(PersonNamePartTypeCode value) {
                this.partType = value;
            }

            public PersonNamePartQualifierCode getPartQualifier() {
                return this.partQualifier;
            }

            public void setPartQualifier(PersonNamePartQualifierCode value) {
                this.partQualifier = value;
            }
        }

        @XmlAccessorType(value=XmlAccessType.FIELD)
        @XmlType(name="", propOrder={})
        public static class FirstName {
            @XmlElement(name="Part", required=true)
            protected String part;
            @XmlElement(name="PartType", required=true)
            protected PersonNamePartTypeCode partType;
            @XmlElement(name="PartQualifier")
            protected PersonNamePartQualifierCode partQualifier;

            public String getPart() {
                return this.part;
            }

            public void setPart(String value) {
                this.part = value;
            }

            public PersonNamePartTypeCode getPartType() {
                return this.partType;
            }

            public void setPartType(PersonNamePartTypeCode value) {
                this.partType = value;
            }

            public PersonNamePartQualifierCode getPartQualifier() {
                return this.partQualifier;
            }

            public void setPartQualifier(PersonNamePartQualifierCode value) {
                this.partQualifier = value;
            }
        }
    }
}
