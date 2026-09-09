import { mkdir, writeFile } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

export const FAKE_PREFIX = "FAKE";

const documents = [
  {
    fileName: "FAKE_Avery_Patient_Bloodwork.pdf",
    profileName: "FAKE Avery Patient",
    folders: ["FAKE Test Results"],
    title: "FAKE Bloodwork Report",
    lines: [
      "Patient: FAKE Avery Patient",
      "Provider: FAKE Maple Clinic",
      "Result: Synthetic values only - no clinical meaning",
      "Generated for myCarlos durable-vault testing",
    ],
  },
  {
    fileName: "FAKE_Avery_Patient_Cardiology_Letter.pdf",
    profileName: "FAKE Avery Patient",
    folders: ["FAKE Specialist Letters"],
    title: "FAKE Cardiology Letter",
    lines: [
      "Patient: FAKE Avery Patient",
      "Provider: FAKE Heart Centre",
      "Assessment: Synthetic evaluation content",
      "This document must never be treated as medical advice",
    ],
  },
  {
    fileName: "FAKE_Morgan_Patient_Imaging_Report.pdf",
    profileName: "FAKE Morgan Patient",
    folders: ["FAKE Imaging"],
    title: "FAKE Imaging Report",
    lines: [
      "Patient: FAKE Morgan Patient",
      "Provider: FAKE Riverside Imaging",
      "Finding: Synthetic normal study",
      "Generated for myCarlos durable-vault testing",
    ],
  },
  {
    fileName: "FAKE_Morgan_Patient_Prescription.pdf",
    profileName: "FAKE Morgan Patient",
    folders: ["FAKE Prescriptions"],
    title: "FAKE Prescription Record",
    lines: [
      "Patient: FAKE Morgan Patient",
      "Provider: FAKE Maple Clinic",
      "Medication: FAKE medication - do not dispense",
      "Generated for myCarlos durable-vault testing",
    ],
  },
];

const duplicate = {
  fileName: "FAKE_Avery_Patient_Bloodwork_DUPLICATE.pdf",
  duplicateOf: documents[0].fileName,
  profileName: documents[0].profileName,
  folders: documents[0].folders,
};

function escapePdfText(value) {
  return value.replaceAll("\\", "\\\\").replaceAll("(", "\\(").replaceAll(")", "\\)");
}

function createPdf(title, lines) {
  const text = [
    "BT",
    "/F1 16 Tf",
    "72 740 Td",
    `(${escapePdfText(title)}) Tj`,
    "/F1 11 Tf",
    ...lines.flatMap((line) => ["0 -24 Td", `(${escapePdfText(line)}) Tj`]),
    "ET",
  ].join("\n");
  const objects = [
    "<< /Type /Catalog /Pages 2 0 R >>",
    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
    "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    `<< /Length ${Buffer.byteLength(text)} >>\nstream\n${text}\nendstream`,
  ];
  let pdf = "%PDF-1.4\n%FAKE synthetic fixture\n";
  const offsets = [0];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(pdf));
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xrefOffset = Buffer.byteLength(pdf);
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += "0000000000 65535 f \n";
  pdf += offsets.slice(1).map((offset) => `${String(offset).padStart(10, "0")} 00000 n \n`).join("");
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return Buffer.from(pdf);
}

export async function generateFakeDevData(outputDirectory) {
  await mkdir(outputDirectory, { recursive: true });
  const generated = [];
  let duplicateBytes;

  for (const document of documents) {
    if (!document.fileName.startsWith(`${FAKE_PREFIX}_`) || !document.profileName.startsWith(`${FAKE_PREFIX} `)) {
      throw new Error("Fake fixture names must start with the FAKE prefix");
    }
    const bytes = createPdf(document.title, document.lines);
    await writeFile(path.join(outputDirectory, document.fileName), bytes);
    generated.push(document.fileName);
    if (document.fileName === duplicate.duplicateOf) duplicateBytes = bytes;
  }

  await writeFile(path.join(outputDirectory, duplicate.fileName), duplicateBytes);
  generated.push(duplicate.fileName);
  const manifest = {
    warning: "FAKE synthetic development data only. Contains no patient information.",
    passphraseGuidance: "Choose a unique throwaway passphrase; no credential is stored in these fixtures.",
    profiles: [
      { name: "FAKE Avery Patient", folders: ["FAKE Test Results", "FAKE Specialist Letters"] },
      { name: "FAKE Morgan Patient", folders: ["FAKE Imaging", "FAKE Prescriptions"] },
    ],
    documents: [...documents.map(({ lines: _lines, title: _title, ...document }) => document), duplicate],
  };
  await writeFile(
    path.join(outputDirectory, "FAKE_MANIFEST.json"),
    `${JSON.stringify(manifest, null, 2)}\n`,
  );
  generated.push("FAKE_MANIFEST.json");
  return generated;
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
  const outputDirectory = fileURLToPath(new URL("../dev-data", import.meta.url));
  const generated = await generateFakeDevData(outputDirectory);
  console.log(`Generated ${generated.length} FAKE development files in ${outputDirectory}`);
}
