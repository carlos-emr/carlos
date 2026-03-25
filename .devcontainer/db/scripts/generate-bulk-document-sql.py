#!/usr/bin/env python3
"""
Generate bulk document SQL INSERT statements for all 3000 patients in development.sql.

Each patient gets 2-3 scanned/uploaded documents of varied types (consult letters,
radiology reports, pathology reports, insurance letters, old chart summaries, etc.).

This script reads patient names from development.sql and outputs replacement SQL
for the `document` and `ctl_document` INSERT statements.

Usage:
    python3 generate-bulk-document-sql.py
"""

import re
import sys
import os

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEV_SQL_PATH = os.path.join(SCRIPT_DIR, 'development.sql')

# Document type definitions: (doctype, docClass, docSubClass, pdf_filename)
DOC_TYPES = [
    ('consult', 'Consultant Report', 'Cardiology Encounter Report',
     'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf'),
    ('radiology', 'Diagnostic Imaging Report', 'Radiology Encounter Report',
     'FAKE_Radiology_Report.pdf'),
    ('pathology', 'Pathology Report', 'Anatomical Pathology Report',
     'FAKE_Pathology_Report.pdf'),
    ('lab', 'Cardio Respiratory Report', 'Clinical Biochemistry Encounter Report',
     'PATIENQ_TEST_FAKE_LabReport.pdf'),
    ('consult', 'Consultant Report', 'Endocrinology Encounter Report',
     'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf'),
    ('insurance', 'Other Letter', 'Coverage Authorization',
     'FAKE_Insurance_Letter.pdf'),
    ('oldchart', 'Other Letter', 'Chart Transfer Summary',
     'FAKE_OldChart_Summary.pdf'),
    ('legal', 'Other Letter', 'Patient Consent Form',
     'FAKE_Legal_Consent.pdf'),
    ('lab', 'Diagnostic Test Report', 'Cardiovascular Surgery Encounter Report',
     'TEST_PATIENT_LabReport.pdf'),
    ('radiology', 'Diagnostic Imaging Report', 'Dentistry Encounter Report',
     'FAKE_Radiology_Report.pdf'),
    ('others', 'Other Letter', 'General Clinical Correspondence',
     'FAKE_Miscellaneous_Document.pdf'),
    ('consult', 'Consultant Report', 'Chiropody / Podiatry Encounter Report',
     'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf'),
    ('pathology', 'Pathology Report', 'Clinical Biochemistry Encounter Report',
     'FAKE_Pathology_Report.pdf'),
    ('lab', 'Cardio Respiratory Report', 'Cardiovascular Surgery Encounter Report',
     'AMEND-LIFELABS__TEST_LabReport.pdf'),
    ('radiology', 'Diagnostic Imaging Report', 'Radiology Encounter Report',
     'FAKE_Radiology_Report.pdf'),
    ('insurance', 'Other Letter', 'Extended Health Benefits',
     'FAKE_Insurance_Letter.pdf'),
    ('lab', 'Diagnostic Test Report', 'Clinical Biochemistry Encounter Report',
     'TEST__FEP_LabReport.pdf'),
    ('others', 'Other Letter', 'General Clinical Correspondence',
     'FAKE_Miscellaneous_Document.pdf'),
    ('oldchart', 'Other Letter', 'Chart Transfer Summary',
     'FAKE_OldChart_Summary.pdf'),
    ('legal', 'Other Letter', 'Patient Consent Form',
     'FAKE_Legal_Consent.pdf'),
    ('consult', 'Consultant Report', 'Cardiology Encounter Report',
     'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf'),
    ('lab', 'Diagnostic Test Report', 'Clinical Biochemistry Encounter Report',
     'TEST__TEST6_LabReport.pdf'),
    ('radiology', 'Diagnostic Imaging Report', 'Dentistry Encounter Report',
     'FAKE_Radiology_Report.pdf'),
    ('pathology', 'Pathology Report', 'Anatomical Pathology Report',
     'FAKE_Pathology_Report.pdf'),
]

# Description templates per doctype
DESC_TEMPLATES = {
    'consult': 'Consultant Report {first} {last}',
    'radiology': 'Imaging Report {first} {last}',
    'pathology': 'Pathology Report {first} {last}',
    'lab': 'Lab Report {first} {last}',
    'insurance': 'Insurance Letter {first} {last}',
    'oldchart': 'Chart Transfer {first} {last}',
    'legal': 'Consent Form {first} {last}',
    'others': 'Correspondence {first} {last}',
}

# Base dates spread across 2024-2025 (one per type slot, cycled)
BASE_DATES = [
    '2024-01-15', '2024-02-20', '2024-03-12', '2024-04-08',
    '2024-05-22', '2024-06-14', '2024-07-03', '2024-08-19',
    '2024-09-10', '2024-10-25', '2024-11-07', '2024-12-02',
    '2025-01-18', '2025-02-11', '2025-03-27', '2025-04-05',
    '2025-05-16', '2025-06-09', '2025-07-21', '2025-08-13',
    '2025-09-04', '2025-10-30', '2025-11-14', '2025-11-28',
]


def extract_patient_names(sql_path):
    """Extract (demo_no, first_name, last_name) from development.sql."""
    patients = []
    with open(sql_path, 'r') as f:
        in_demo = False
        for line in f:
            if 'INSERT INTO `demographic` VALUES' in line:
                in_demo = True
                continue
            if in_demo:
                stripped = line.strip()
                if not stripped.startswith('('):
                    if stripped == '' or stripped.startswith('INSERT') or stripped.startswith('--'):
                        in_demo = False
                        continue
                    continue
                # Parse the row - split on commas but respect quotes
                m = re.match(r"\((\d+),'[^']*','([^']*)','([^']*)'", stripped)
                if m:
                    demo_no = int(m.group(1))
                    first_name = m.group(2)
                    last_name = m.group(3)
                    patients.append((demo_no, first_name, last_name))
    return patients


def generate_document_sql(patients):
    """Generate INSERT statements for document and ctl_document tables."""
    doc_rows = []
    ctl_rows = []
    doc_no = 1  # start document numbering from 1

    num_types = len(DOC_TYPES)
    num_dates = len(BASE_DATES)

    for demo_no, first_name, last_name in patients:
        # Each patient gets 2 or 3 docs based on demo_no
        num_docs = 3 if (demo_no % 3 == 1) else 2

        for d in range(num_docs):
            # Pick document type deterministically
            type_idx = (demo_no * 3 + d) % num_types
            doctype, doc_class, doc_subclass, pdf_file = DOC_TYPES[type_idx]

            # Pick date deterministically
            date_idx = (demo_no * 3 + d) % num_dates
            obs_date = BASE_DATES[date_idx]
            update_dt = f'{obs_date} 10:{(demo_no % 12):02d}:{(d * 15):02d}'

            # Description
            desc = DESC_TEMPLATES[doctype].format(first=first_name, last=last_name)

            # document row: 27 fields
            row = (
                f"({doc_no},"
                f"'{doctype}',"
                f"'{doc_class}',"
                f"'{doc_subclass}',"
                f"'{_esc(desc)}',"
                f"'',"  # docxml
                f"'{pdf_file}',"
                f"999998,"  # doccreator
                f"'',"  # responsible
                f"'',"  # source
                f"'',"  # sourceFacility
                f"10034,"  # program_id
                f"'{update_dt}',"  # updatedatetime
                f"'A',"  # status
                f"'application/pdf',"  # contenttype
                f"'{update_dt}',"  # contentdatetime
                f"0,"  # public1
                f"'{obs_date}',"  # observationdate
                f"NULL,"  # reviewer
                f"NULL,"  # reviewdatetime
                f"1,"  # number_of_pages
                f"0,"  # appointment_no
                f"0,"  # restrictToProgram
                f"0,"  # abnormal
                f"NULL,"  # receivedDate
                f"NULL,"  # report_media
                f"NULL)"  # sent_date_time
            )
            doc_rows.append(row)

            # ctl_document row: (module, module_id, document_no, status)
            ctl_rows.append(f"('demographic',{demo_no},{doc_no},'A')")

            doc_no += 1

    # Add the 6 existing provider/desktop documents (renumbered after patient docs)
    provider_pdfs = [
        'PATIENQ_TEST_FAKE_LabReport.pdf',
        'TEST_PATIENT_LabReport.pdf',
        'AMEND-LIFELABS__TEST_LabReport.pdf',
        'TEST__FEP_LabReport.pdf',
        'TEST__TEST6_LabReport.pdf',
        'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf',
    ]
    provider_descs = [
        'Desktop Resource - CBC Report Template',
        'Desktop Resource - Metabolic Panel Template',
        'Desktop Resource - Amended Report Template',
        'Desktop Resource - Iron Studies Template',
        'Desktop Resource - Thyroid Panel Template',
        'Desktop Resource - Referral Letter Template',
    ]
    for i, (pdf_file, desc) in enumerate(zip(provider_pdfs, provider_descs)):
        row = (
            f"({doc_no},"
            f"'others',"
            f"'Other Letter',"
            f"'Desktop Resource',"
            f"'{desc}',"
            f"'',"
            f"'{pdf_file}',"
            f"999998,"
            f"'',"
            f"'',"
            f"'',"
            f"10034,"
            f"'2025-06-24 16:54:07',"
            f"'A',"
            f"'application/pdf',"
            f"'2025-06-24 16:54:07',"
            f"0,"
            f"'2025-06-24',"
            f"NULL,"
            f"NULL,"
            f"1,"
            f"0,"
            f"0,"
            f"0,"
            f"NULL,"
            f"NULL,"
            f"NULL)"
        )
        doc_rows.append(row)
        ctl_rows.append(f"('providers',999998,{doc_no},'A')")
        doc_no += 1

    total_docs = doc_no - 1
    return doc_rows, ctl_rows, total_docs


def _esc(s):
    """Escape single quotes for SQL."""
    return s.replace("'", "''")


def main():
    print("Extracting patient names from development.sql...")
    patients = extract_patient_names(DEV_SQL_PATH)
    print(f"  Found {len(patients)} patients")

    print("Generating document SQL...")
    doc_rows, ctl_rows, total_docs = generate_document_sql(patients)
    print(f"  Generated {total_docs} documents ({len(doc_rows)} doc rows, {len(ctl_rows)} ctl rows)")

    # Count distribution
    type_counts = {}
    for demo_no, first_name, last_name in patients:
        num_docs = 3 if (demo_no % 3 == 1) else 2
        for d in range(num_docs):
            type_idx = (demo_no * 3 + d) % len(DOC_TYPES)
            dt = DOC_TYPES[type_idx][0]
            type_counts[dt] = type_counts.get(dt, 0) + 1
    print("  Document type distribution:")
    for dt, count in sorted(type_counts.items()):
        print(f"    {dt}: {count}")

    # Write the SQL to replace in development.sql
    doc_sql = "INSERT INTO `document` VALUES\n" + ",\n".join(doc_rows) + ";\n"
    ctl_sql = "INSERT INTO `ctl_document` VALUES\n" + ",\n".join(ctl_rows) + ";\n"

    output_path = os.path.join(SCRIPT_DIR, 'bulk-documents.sql')
    with open(output_path, 'w') as f:
        f.write("-- Auto-generated bulk document data for all 3000 patients\n")
        f.write(f"-- {total_docs} total documents\n\n")
        f.write(doc_sql)
        f.write("\n")
        f.write(ctl_sql)

    print(f"\n  Written to: {output_path}")
    print(f"  Total documents: {total_docs}")
    print(f"  Patient documents: {total_docs - 6}")
    print(f"  Provider/desktop documents: 6")


if __name__ == '__main__':
    main()
