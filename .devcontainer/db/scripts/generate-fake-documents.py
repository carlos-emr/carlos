#!/usr/bin/env python3
"""
Generate fake watermarked PDF documents for CARLOS EMR development environment.

These PDFs replace real lab reports with clearly fake, watermarked versions
that are appropriate for development/testing purposes only.

Usage:
    python3 generate-fake-documents.py [output_directory]

Default output: .devcontainer/db/db_data/documents/
"""

import os
import sys
import struct
import zlib
from datetime import datetime

# Output directory
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUTPUT_DIR = os.path.join(SCRIPT_DIR, '..', 'db_data', 'documents')


class SimplePDFWriter:
    """Minimal PDF writer that creates multi-page documents with text content
    and a diagonal watermark, using only Python standard library."""

    def __init__(self):
        self.objects = []  # (obj_number, content_bytes)
        self.pages = []
        self.next_obj = 1

    def _alloc_obj(self):
        n = self.next_obj
        self.next_obj += 1
        return n

    def _make_stream(self, obj_num, text_content, extra_dict=""):
        compressed = zlib.compress(text_content.encode('latin-1'))
        header = f"{obj_num} 0 obj\n<< /Length {len(compressed)} /Filter /FlateDecode {extra_dict}>>\nstream\n"
        footer = b"\nendstream\nendobj\n"
        return header.encode('latin-1') + compressed + footer

    def add_page(self, lines, watermark_text="FAKE - DEVELOPMENT USE ONLY",
                 page_width=612, page_height=792):
        """Add a page with text lines and a diagonal watermark."""
        page_obj = self._alloc_obj()
        content_obj = self._alloc_obj()

        # Build content stream
        stream_parts = []

        # Watermark: large gray diagonal text
        stream_parts.append("q")  # save graphics state
        stream_parts.append("0.85 0.85 0.85 rg")  # light gray fill
        stream_parts.append("BT")
        stream_parts.append("/F1 48 Tf")
        # Rotate ~35 degrees diagonally across page
        # cos(35)=0.819, sin(35)=0.574
        stream_parts.append("0.819 0.574 -0.574 0.819 30 200 Tm")
        stream_parts.append(f"({self._escape_pdf(watermark_text)}) Tj")
        stream_parts.append("ET")
        # Second watermark line higher
        stream_parts.append("BT")
        stream_parts.append("/F1 48 Tf")
        stream_parts.append("0.819 0.574 -0.574 0.819 30 500 Tm")
        stream_parts.append(f"({self._escape_pdf(watermark_text)}) Tj")
        stream_parts.append("ET")
        stream_parts.append("Q")  # restore graphics state

        # Horizontal rule near top
        stream_parts.append("q 0 0 0 RG 0.5 w")
        stream_parts.append(f"72 {page_height - 85} m {page_width - 72} {page_height - 85} l S")
        stream_parts.append("Q")

        # Text content - use Tm (text matrix) for absolute positioning
        # since Td is relative and accumulates offsets
        y = page_height - 72  # start near top
        for line in lines:
            if y < 72:
                break  # stop if we run off the page

            stream_parts.append("BT")
            if line.startswith("##"):
                # Section header - bold/larger
                stream_parts.append(f"/F2 14 Tf")
                text = line.lstrip("# ").strip()
                y_step = 18
            elif line.startswith("#"):
                # Title - large
                stream_parts.append(f"/F2 18 Tf")
                text = line.lstrip("# ").strip()
                y_step = 22
            elif line.startswith("  "):
                # Indented detail
                stream_parts.append(f"/F1 9 Tf")
                text = line
                y_step = 12
            elif line == "":
                # Empty line - just add spacing
                stream_parts.append("ET")
                y -= 8
                continue
            else:
                stream_parts.append(f"/F1 10 Tf")
                text = line
                y_step = 14

            stream_parts.append("0 0 0 rg")  # black text
            stream_parts.append(f"1 0 0 1 72 {y} Tm")  # absolute position
            stream_parts.append(f"({self._escape_pdf(text)}) Tj")
            stream_parts.append("ET")
            y -= y_step

        # Footer
        stream_parts.append("BT /F1 8 Tf 0.5 0.5 0.5 rg")
        stream_parts.append(f"1 0 0 1 72 36 Tm (CARLOS EMR - FAKE DEVELOPMENT DOCUMENT - NOT FOR CLINICAL USE) Tj")
        stream_parts.append("ET")

        content_str = "\n".join(stream_parts)

        self.pages.append((page_obj, content_obj))
        self.objects.append((page_obj, 'PAGE_PLACEHOLDER'))
        self.objects.append((content_obj, content_str))

    def _escape_pdf(self, text):
        """Escape special PDF string characters."""
        return text.replace('\\', '\\\\').replace('(', '\\(').replace(')', '\\)')

    def write(self, filepath):
        """Write the complete PDF to a file."""
        # Allocate catalog, pages, and font objects
        catalog_obj = self._alloc_obj()
        pages_obj = self._alloc_obj()
        font1_obj = self._alloc_obj()  # Helvetica (regular)
        font2_obj = self._alloc_obj()  # Helvetica-Bold

        # Build output
        output = b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n"
        offsets = {}

        def write_obj(obj_num, content):
            nonlocal output
            offsets[obj_num] = len(output)
            output += content if isinstance(content, bytes) else content.encode('latin-1')

        # Write catalog
        write_obj(catalog_obj,
                  f"{catalog_obj} 0 obj\n<< /Type /Catalog /Pages {pages_obj} 0 R >>\nendobj\n")

        # Write pages object
        kids = " ".join(f"{p[0]} 0 R" for p in self.pages)
        write_obj(pages_obj,
                  f"{pages_obj} 0 obj\n<< /Type /Pages /Kids [{kids}] /Count {len(self.pages)} >>\nendobj\n")

        # Write fonts
        write_obj(font1_obj,
                  f"{font1_obj} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        write_obj(font2_obj,
                  f"{font2_obj} 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")

        # Write pages and their content streams
        for page_obj, content_obj in self.pages:
            # Content stream
            content_str = None
            for obj_num, data in self.objects:
                if obj_num == content_obj:
                    content_str = data
                    break

            compressed = zlib.compress(content_str.encode('latin-1'))
            offsets[content_obj] = len(output)
            header = f"{content_obj} 0 obj\n<< /Length {len(compressed)} /Filter /FlateDecode >>\nstream\n"
            output += header.encode('latin-1') + compressed + b"\nendstream\nendobj\n"

            # Page object
            offsets[page_obj] = len(output)
            page_dict = (
                f"{page_obj} 0 obj\n"
                f"<< /Type /Page /Parent {pages_obj} 0 R "
                f"/MediaBox [0 0 612 792] "
                f"/Contents {content_obj} 0 R "
                f"/Resources << /Font << /F1 {font1_obj} 0 R /F2 {font2_obj} 0 R >> >> "
                f">>\nendobj\n"
            )
            output += page_dict.encode('latin-1')

        # Cross-reference table
        xref_offset = len(output)
        all_objs = sorted(offsets.keys())
        max_obj = max(all_objs)
        output += f"xref\n0 {max_obj + 1}\n".encode('latin-1')
        output += b"0000000000 65535 f \n"
        for i in range(1, max_obj + 1):
            if i in offsets:
                output += f"{offsets[i]:010d} 00000 n \n".encode('latin-1')
            else:
                output += b"0000000000 00000 f \n"

        # Trailer
        output += (
            f"trailer\n<< /Root {catalog_obj} 0 R /Size {max_obj + 1} >>\n"
            f"startxref\n{xref_offset}\n%%EOF\n"
        ).encode('latin-1')

        with open(filepath, 'wb') as f:
            f.write(output)

        return len(output)


def generate_lab_report_cbc(filepath):
    """Generate fake CBC lab report."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE LABORATORY REPORT - Complete Blood Count (CBC)",
        "",
        "## CARLOS EMR Development Lab - FAKE DATA",
        "",
        "Patient: FAKE-Test FAKE-Patient          DOB: 1985-06-15",
        "Specimen ID: DEV-00001-FAKE              Collected: 2025-06-24",
        "Ordering Provider: Dr. FAKE-Development  Reported: 2025-06-24",
        "",
        "## HEMATOLOGY - Complete Blood Count",
        "",
        "  Test                    Result      Units       Reference Range",
        "  ---------------------------------------------------------------",
        "  White Blood Cell Count  7.2         x10^9/L     4.0 - 11.0",
        "  Red Blood Cell Count    4.85        x10^12/L    4.50 - 5.50",
        "  Hemoglobin              148         g/L         135 - 175",
        "  Hematocrit              0.44        L/L         0.40 - 0.50",
        "  MCV                     90.7        fL          80.0 - 100.0",
        "  MCH                     30.5        pg          27.0 - 33.0",
        "  MCHC                    336         g/L         320 - 360",
        "  RDW                     13.1        %           11.5 - 14.5",
        "  Platelet Count          245         x10^9/L     150 - 400",
        "  MPV                     9.8         fL          7.0 - 11.0",
        "",
        "## DIFFERENTIAL",
        "",
        "  Neutrophils             62          %           40 - 75",
        "  Lymphocytes             28          %           20 - 45",
        "  Monocytes               6           %           2 - 10",
        "  Eosinophils             3           %           0 - 6",
        "  Basophils               1           %           0 - 2",
        "",
        "Interpretation: All values within normal reference ranges.",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


def generate_lab_report_metabolic(filepath):
    """Generate fake metabolic panel lab report."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE LABORATORY REPORT - Comprehensive Metabolic Panel",
        "",
        "## CARLOS EMR Development Lab - FAKE DATA",
        "",
        "Patient: FAKE-Test FAKE-Patient          DOB: 1985-06-15",
        "Specimen ID: DEV-00002-FAKE              Collected: 2025-06-24",
        "Ordering Provider: Dr. FAKE-Development  Reported: 2025-06-24",
        "",
        "## CHEMISTRY - Comprehensive Metabolic Panel",
        "",
        "  Test                    Result      Units       Reference Range",
        "  ---------------------------------------------------------------",
        "  Glucose (Fasting)       5.2         mmol/L      3.3 - 5.5",
        "  BUN (Urea)              5.8         mmol/L      2.5 - 8.0",
        "  Creatinine              88          umol/L      62 - 106",
        "  eGFR                    >90         mL/min      >60",
        "  Sodium                  141         mmol/L      136 - 145",
        "  Potassium               4.3         mmol/L      3.5 - 5.1",
        "  Chloride                103         mmol/L      98 - 107",
        "  CO2 (Bicarbonate)       25          mmol/L      22 - 29",
        "  Calcium                 2.38        mmol/L      2.15 - 2.55",
        "  Total Protein           72          g/L         60 - 80",
        "  Albumin                 42          g/L         35 - 50",
        "  Bilirubin (Total)       12          umol/L      3 - 17",
        "  ALP                     68          U/L         40 - 130",
        "  AST                     24          U/L         10 - 40",
        "  ALT                     28          U/L         7 - 56",
        "",
        "Interpretation: All values within normal reference ranges.",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


def generate_lab_report_amended(filepath):
    """Generate fake amended lab report."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE LABORATORY REPORT - AMENDED",
        "",
        "## CARLOS EMR Development Lab - FAKE DATA",
        "## *** AMENDED REPORT - REPLACES PREVIOUS VERSION ***",
        "",
        "Patient: FAKE-Test FAKE-Patient          DOB: 1985-06-15",
        "Specimen ID: DEV-00003-FAKE              Collected: 2025-06-29",
        "Ordering Provider: Dr. FAKE-Development  Reported: 2025-06-29",
        "",
        "Amendment Reason: Specimen re-analyzed due to quality control flag.",
        "Original Report Date: 2025-06-28",
        "",
        "## CHEMISTRY - Lipid Panel (AMENDED VALUES)",
        "",
        "  Test                    Result      Units       Reference Range",
        "  ---------------------------------------------------------------",
        "  Total Cholesterol       5.1         mmol/L      < 5.2",
        "  HDL Cholesterol         1.45        mmol/L      > 1.0",
        "  LDL Cholesterol         3.0         mmol/L      < 3.4",
        "  Triglycerides           1.42        mmol/L      < 1.7",
        "  Total/HDL Ratio         3.5                     < 5.0",
        "  Non-HDL Cholesterol     3.65        mmol/L      < 4.3",
        "",
        "## CHEMISTRY - Glucose",
        "",
        "  Glucose (Fasting)       5.4         mmol/L      3.3 - 5.5",
        "  HbA1c                   5.6         %           4.0 - 6.0",
        "",
        "Amendment Note: HDL value corrected from 1.25 to 1.45 mmol/L",
        "after instrument recalibration. All other values confirmed.",
        "",
        "Reviewed by: Dr. FAKE-Pathologist (FAKE credentials)",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


def generate_lab_report_iron(filepath):
    """Generate fake iron studies lab report."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE LABORATORY REPORT - Iron Studies",
        "",
        "## CARLOS EMR Development Lab - FAKE DATA",
        "",
        "Patient: FAKE-Test FAKE-Patient          DOB: 1985-06-15",
        "Specimen ID: DEV-00004-FAKE              Collected: 2025-06-28",
        "Ordering Provider: Dr. FAKE-Development  Reported: 2025-06-28",
        "",
        "## CHEMISTRY - Iron Studies",
        "",
        "  Test                    Result      Units       Reference Range",
        "  ---------------------------------------------------------------",
        "  Serum Iron              18.5        umol/L      9.0 - 30.0",
        "  TIBC                    58          umol/L      45 - 72",
        "  Transferrin Saturation  32          %           20 - 55",
        "  Ferritin                85          ug/L        30 - 400",
        "",
        "## HEMATOLOGY - Reticulocyte Count",
        "",
        "  Reticulocyte Count      1.2         %           0.5 - 2.5",
        "  Reticulocyte Abs Count  58          x10^9/L     25 - 75",
        "",
        "## ADDITIONAL TESTS",
        "",
        "  Vitamin B12             320         pmol/L      148 - 590",
        "  Folate (Serum)          28          nmol/L      7 - 45",
        "",
        "Interpretation: Iron stores and related parameters within",
        "normal reference ranges. No evidence of iron deficiency.",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


def generate_lab_report_thyroid(filepath):
    """Generate fake thyroid panel lab report."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE LABORATORY REPORT - Thyroid Function Panel",
        "",
        "## CARLOS EMR Development Lab - FAKE DATA",
        "",
        "Patient: FAKE-Test FAKE-Patient          DOB: 1985-06-15",
        "Specimen ID: DEV-00005-FAKE              Collected: 2025-06-28",
        "Ordering Provider: Dr. FAKE-Development  Reported: 2025-06-28",
        "",
        "## ENDOCRINOLOGY - Thyroid Function",
        "",
        "  Test                    Result      Units       Reference Range",
        "  ---------------------------------------------------------------",
        "  TSH                     2.15        mIU/L       0.27 - 4.20",
        "  Free T4 (FT4)          15.8        pmol/L      12.0 - 22.0",
        "  Free T3 (FT3)          4.9         pmol/L      3.1 - 6.8",
        "",
        "## IMMUNOLOGY - Thyroid Antibodies",
        "",
        "  Anti-TPO Antibodies     < 10        IU/mL       < 35",
        "  Anti-Thyroglobulin Ab   < 20        IU/mL       < 115",
        "",
        "Interpretation: Thyroid function within normal limits.",
        "No evidence of autoimmune thyroid disease.",
        "",
        "Clinical Note: Routine thyroid screening. No abnormalities",
        "detected. Recommend follow-up in 12 months if clinically",
        "indicated.",
        "",
        "Reported by: FAKE Laboratory Technologist",
        "Verified by: Dr. FAKE-Pathologist",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


def generate_referral_letter(filepath):
    """Generate fake specialist referral/consultation letter."""
    pdf = SimplePDFWriter()
    pdf.add_page([
        "# FAKE CONSULTATION / REFERRAL LETTER",
        "",
        "## CARLOS EMR Development Clinic - FAKE DATA",
        "",
        "Date: June 24, 2025",
        "",
        "From: Dr. FAKE-Referring FAKE-Physician",
        "      FAKE Family Medicine Clinic",
        "      123 FAKE Street, Toronto, ON M5V 1A1",
        "      Phone: 555-555-0000   Fax: 555-555-0001",
        "",
        "To:   Dr. FAKE-Specialist FAKE-Consultant",
        "      FAKE Cardiology Associates",
        "      456 FAKE Avenue, Toronto, ON M5V 2B2",
        "",
        "Re:   FAKE-Test FAKE-Patient",
        "      DOB: 1985-06-15    HIN: 0000-000-000 (FAKE)",
        "",
        "## Reason for Referral",
        "",
        "Routine cardiovascular assessment for a FAKE patient",
        "presenting with fabricated symptoms for development testing.",
        "",
        "## History of Present Illness",
        "",
        "This 40-year-old FAKE patient presents with fictional",
        "symptoms created for EMR system testing. The patient reports",
        "no real complaints as this is a development test record.",
        "",
        "## Current Medications (ALL FAKE)",
        "",
        "  - Fake-a-statin 10mg PO daily",
        "  - Fake-opril 5mg PO daily",
        "  - Test-amin D 1000 IU PO daily",
        "",
        "## Assessment",
        "",
        "FAKE patient in stable condition. This document exists",
        "solely for EMR development and testing purposes.",
        "",
        "Thank you for seeing this FAKE patient.",
        "",
        "Dr. FAKE-Referring FAKE-Physician",
        "FAKE Medical License #DEV-00000",
        "",
        "THIS IS A FAKE DOCUMENT FOR DEVELOPMENT PURPOSES ONLY.",
        "DO NOT USE FOR ANY CLINICAL DECISION MAKING.",
    ])
    return pdf.write(filepath)


# Map filenames to generator functions
DOCUMENT_GENERATORS = {
    'PATIENQ_TEST_FAKE_LabReport.pdf': generate_lab_report_cbc,
    'TEST_PATIENT_LabReport.pdf': generate_lab_report_metabolic,
    'AMEND-LIFELABS__TEST_LabReport.pdf': generate_lab_report_amended,
    'TEST__FEP_LabReport.pdf': generate_lab_report_iron,
    'TEST__TEST6_LabReport.pdf': generate_lab_report_thyroid,
    'TESTPAT-FN-ONE__TESTPAT-LN-REFERRAL_LabReport.pdf': generate_referral_letter,
}


def main():
    output_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUTPUT_DIR
    output_dir = os.path.abspath(output_dir)

    os.makedirs(output_dir, exist_ok=True)

    print(f"Generating fake documents in: {output_dir}")
    print()

    for filename, generator in DOCUMENT_GENERATORS.items():
        filepath = os.path.join(output_dir, filename)
        size = generator(filepath)
        print(f"  Created: {filename} ({size:,} bytes)")

    print()
    print(f"Generated {len(DOCUMENT_GENERATORS)} fake watermarked PDF documents.")
    print("All documents contain 'FAKE - DEVELOPMENT USE ONLY' watermarks.")


if __name__ == '__main__':
    main()
