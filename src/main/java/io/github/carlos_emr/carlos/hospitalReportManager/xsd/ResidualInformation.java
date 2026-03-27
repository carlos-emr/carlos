package io.github.carlos_emr.carlos.hospitalReportManager.xsd;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.CollapsedStringAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/*
 * This class specifies class file version 49.0 but uses Java 6 signatures.  Assumed Java 6.
 */
@XmlAccessorType(value=XmlAccessType.FIELD)
@XmlType(name="residualInformation", propOrder={"dataElement"})
public class ResidualInformation {
    @XmlElement(name="DataElement", required=true)
    protected List<DataElement> dataElement;

    public List<DataElement> getDataElement() {
        if (this.dataElement == null) {
            this.dataElement = new ArrayList<DataElement>();
        }
        return this.dataElement;
    }

    @XmlAccessorType(value=XmlAccessType.FIELD)
    @XmlType(name="", propOrder={"name", "description", "dataType", "content"})
    public static class DataElement {
        @XmlElement(name="Name", required=true)
        @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
        @XmlSchemaType(name="token")
        protected String name;
        @XmlElement(name="Description")
        protected String description;
        @XmlElement(name="DataType", required=true)
        @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
        @XmlSchemaType(name="token")
        protected String dataType;
        @XmlElement(name="Content", required=true)
        @XmlJavaTypeAdapter(value=CollapsedStringAdapter.class)
        @XmlSchemaType(name="token")
        protected String content;

        public String getName() {
            return this.name;
        }

        public void setName(String value) {
            this.name = value;
        }

        public String getDescription() {
            return this.description;
        }

        public void setDescription(String value) {
            this.description = value;
        }

        public String getDataType() {
            return this.dataType;
        }

        public void setDataType(String value) {
            this.dataType = value;
        }

        public String getContent() {
            return this.content;
        }

        public void setContent(String value) {
            this.content = value;
        }
    }
}
