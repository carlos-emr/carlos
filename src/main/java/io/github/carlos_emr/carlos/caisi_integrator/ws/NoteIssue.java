package io.github.carlos_emr.carlos.caisi_integrator.ws;

import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.io.Serializable;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "noteIssue", propOrder = { "codeType", "issueCode" })
public class NoteIssue implements Serializable
{
    private static final long serialVersionUID = 1L;
    @XmlSchemaType(name = "string")
    protected CodeType codeType;
    protected String issueCode;
    
    public CodeType getCodeType() {
        return this.codeType;
    }
    
    public void setCodeType(final CodeType codeType) {
        this.codeType = codeType;
    }
    
    public String getIssueCode() {
        return this.issueCode;
    }
    
    public void setIssueCode(final String issueCode) {
        this.issueCode = issueCode;
    }
}
