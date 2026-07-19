package ca.ontario.health.hcv;

import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;

@XmlType(name = "responseID")
@XmlEnum
public enum ResponseID
{
    INFO_NOT_AVAIL("INFO_NOT_AVAIL"), 
    LOST_STATE_ND("LOST_STATE_ND"), 
    LOST_STATE("LOST_STATE"), 
    DAMAGED_STATE_ND("DAMAGED_STATE_ND"), 
    DAMAGED_STATE("DAMAGED_STATE"), 
    IS_VOID_NEVER_ISS_ND("IS_VOID_NEVER_ISS_ND"), 
    IS_VOID_NEVER_ISS("IS_VOID_NEVER_ISS"), 
    IS_CANCELLED_OR_VOIDED_ND("IS_CANCELLED_OR_VOIDED_ND"), 
    IS_CANCELLED_OR_VOIDED("IS_CANCELLED_OR_VOIDED"), 
    IS_STOLEN_ND("IS_STOLEN_ND"), 
    IS_STOLEN("IS_STOLEN"), 
    INVALID_VERSION_CODE_ND("INVALID_VERSION_CODE_ND"), 
    INVALID_VERSION_CODE("INVALID_VERSION_CODE"), 
    RETURNED_MAIL_ND("RETURNED_MAIL_ND"), 
    RETURNED_MAIL("RETURNED_MAIL"), 
    IS_THC_ND("IS_THC_ND"), 
    IS_THC("IS_THC"), 
    IS_RQ_HAS_EXPIRED_ND("IS_RQ_HAS_EXPIRED_ND"), 
    IS_RQ_HAS_EXPIRED("IS_RQ_HAS_EXPIRED"), 
    IS_RQ_FUTURE_ISSUE("IS_RQ_FUTURE_ISSUE"), 
    IS_RQ_FUTURE_ISSUE_ND("IS_RQ_FUTURE_ISSUE_ND"), 
    HAS_NOTICE_ND("HAS_NOTICE_ND"), 
    HAS_NOTICE("HAS_NOTICE"), 
    IS_ON_ACTIVE_ROSTER_ND("IS_ON_ACTIVE_ROSTER_ND"), 
    IS_ON_ACTIVE_ROSTER("IS_ON_ACTIVE_ROSTER"), 
    NOT_ON_ACTIVE_ROSTER_ND("NOT_ON_ACTIVE_ROSTER_ND"), 
    NOT_ON_ACTIVE_ROSTER("NOT_ON_ACTIVE_ROSTER"), 
    IS_NOT_ELIGIBLE_ND("IS_NOT_ELIGIBLE_ND"), 
    IS_NOT_ELIGIBLE("IS_NOT_ELIGIBLE"), 
    IS_IN_DISTRIBUTED_STATUS("IS_IN_DISTRIBUTED_STATUS"), 
    @XmlEnumValue("FAILED_MOD10")
    FAILED_MOD_10("FAILED_MOD10"), 
    NOT_10_DIGITS("NOT_10_DIGITS"), 
    SUCCESS("SUCCESS");
    
    private final String value;
    
    private ResponseID(final String v) {
        this.value = v;
    }
    
    public String value() {
        return this.value;
    }
    
    public static ResponseID fromValue(final String v) {
        for (final ResponseID c : values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }
}
