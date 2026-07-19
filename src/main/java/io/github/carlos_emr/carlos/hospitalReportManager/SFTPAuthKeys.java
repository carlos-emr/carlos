/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.hospitalReportManager;

public class SFTPAuthKeys {

    //the encryption is based on Advanced Encryption Standard (AES) Algorithm aes-128-ecb
    //thus we know about the decryption key
    public static final String OMDdecryptionKey1 = "43783C6B48804FD9E765BCA632341183";

    //the second key, given after passing 2.2
    public static final String OMDdecryptionKey2 = "2B0CD4AB381891EB02F7A9B9D19864E8";
}
