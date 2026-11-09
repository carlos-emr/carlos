/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

/** Shared wire values; neither persistence nor exporters depend on report orchestration. */
public final class BcBillingConstants {
    private BcBillingConstants() { }
    public static final String REP_INVOICE = "REP_INVOICE";
    public static final String REP_PAYREF = "REP_PAYREF";
    public static final String REP_PAYREF_SUM = "REP_PAYREF_SUM";
    public static final String REP_ACCOUNT_REC = "REP_ACCOUNT_REC";
    public static final String REP_REJ = "REP_REJ";
    public static final String REP_WO = "REP_WO";
    public static final String REP_MSPREM = "REP_MSPREM";
    public static final String REP_MSPREM_NEW = "REP_MSPREM_NEW";
    public static final String REP_MSPREMSUM = "REP_MSPREMSUM";
    public static final String REP_MSPREMSUM_PRACTSUM = "REP_MSPREMSUM_PRACTSUM";
    public static final String REP_MSPREMSUM_S23 = "REP_MSPREMSUM_S23";
    public static final String REJECTED = "R";
    public static final String NOTSUBMITTED = "O";
    public static final String SUBMITTED = "B";
    public static final String SETTLED = "S";
    public static final String DELETED = "D";
    public static final String HELD = "Z";
    public static final String DATACENTERCHANGED = "C";
    public static final String PAIDWITHEXP = "E";
    public static final String REFUSED = "F";
    public static final String BADDEBT = "X";
    public static final String WCB = "W";
    public static final String CAPITATED = "H";
    public static final String DONOTBILL = "N";
    public static final String BILLPATIENT = "P";
    public static final String COLLECTION = "T";
    public static final String PAIDPRIVATE = "A";
    public static final String BILLTYPE_PRI = "Pri";
    public static final String BILLTYPE_MSP = "MSP";
    public static final String BILLTYPE_ICBC = "ICBC";
    public static final String BILLTYPE_WCB = "WCB";
    public static final String PAYTYPE_CASH = "1";
    public static final String PAYTYPE_CHEQUE = "2";
    public static final String PAYTYPE_VISA = "3";
    public static final String PAYTYPE_MC = "4";
    public static final String PAYTYPE_AMEX = "5";
    public static final String PAYTYPE_ELECTRONIC = "6";
    public static final String PAYTYPE_DEBIT = "7";
    public static final String PAYTYPE_OTHER = "8";
    public static final String PAYTYPE_NA = "9";
    public static final String PAYTYPE_IA = "10";
    public static final String DATE_FORMAT = "yyyyMMdd";
}
