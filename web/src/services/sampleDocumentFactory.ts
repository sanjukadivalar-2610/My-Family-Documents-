import { DocumentCategoryMeta, getCategoryByKey } from '../domain/documentCategory';

export interface SampleDocumentTemplate {
  title: string;
  categoryKey: string;
  fileType: 'png' | 'pdf';
  fileName: string;
  maskedId: string;
  notes: string;
  issueOffsetDays: number;
  expiryOffsetDays: number;
}

const DAY_MS = 86_400_000;

export function getSampleTemplates(memberName: string): SampleDocumentTemplate[] {
  return [
    {
      title: `${memberName} - Sample Aadhaar`,
      categoryKey: 'Aadhaar Card',
      fileType: 'png',
      fileName: 'sample_aadhaar_card.png',
      maskedId: 'DEMO-0000-1001',
      notes: 'Synthetic sample card for development testing only.',
      issueOffsetDays: -1200,
      expiryOffsetDays: 0,
    },
    {
      title: `${memberName} - Sample PAN Card`,
      categoryKey: 'PAN Card',
      fileType: 'png',
      fileName: 'sample_pan_card.png',
      maskedId: 'DEMOP0001A',
      notes: 'Synthetic sample PAN card for testing.',
      issueOffsetDays: -900,
      expiryOffsetDays: 0,
    },
    {
      title: `${memberName} - Sample Passport`,
      categoryKey: 'Passport',
      fileType: 'pdf',
      fileName: 'sample_passport.pdf',
      maskedId: 'Z9990001',
      notes: 'Expiring soon demo document to test expiry reminders.',
      issueOffsetDays: -3635,
      expiryOffsetDays: 18,
    },
    {
      title: `${memberName} - Sample Driving Licence`,
      categoryKey: 'Driving Licence',
      fileType: 'pdf',
      fileName: 'sample_driving_licence.pdf',
      maskedId: 'KA01DEMO0001',
      notes: 'Synthetic sample licence PDF.',
      issueOffsetDays: -1800,
      expiryOffsetDays: 420,
    },
    {
      title: 'Family Sample Ration Card',
      categoryKey: 'Ration Card',
      fileType: 'pdf',
      fileName: 'sample_ration_card.pdf',
      maskedId: 'RC-DEMO-4402',
      notes: 'Synthetic household ration card sample.',
      issueOffsetDays: -730,
      expiryOffsetDays: 900,
    },
    {
      title: `${memberName} - Health Policy Sample`,
      categoryKey: 'Medical Documents',
      fileType: 'pdf',
      fileName: 'sample_medical_policy.pdf',
      maskedId: 'MED-DEMO-771',
      notes: 'Synthetic health insurance certificate.',
      issueOffsetDays: -350,
      expiryOffsetDays: 15,
    },
  ];
}

export function computeSampleTimestamps(
  template: SampleDocumentTemplate,
  nowMillis: number = Date.now()
): { issueDateMillis: number; expiryDateMillis: number } {
  const issueDateMillis = nowMillis + template.issueOffsetDays * DAY_MS;
  const expiryDateMillis =
    template.expiryOffsetDays === 0 ? 0 : nowMillis + template.expiryOffsetDays * DAY_MS;
  return { issueDateMillis, expiryDateMillis };
}

function escapePdfText(text: string): string {
  return text.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

/**
 * Generates a valid single-page PDF 1.4 byte stream without external dependencies.
 */
export function buildSamplePdfBytes(
  template: SampleDocumentTemplate,
  memberName: string
): Uint8Array {
  const cat: DocumentCategoryMeta = getCategoryByKey(template.categoryKey);
  const lines = [
    '0.09 0.145 0.329 rg',
    '0 740 595 102 re f',
    '0.22 0.74 0.97 rg',
    '0 730 595 10 re f',
    'BT /F1 20 Tf 1 1 1 rg 36 785 Td (MY FAMILY DOC - SAMPLE RECORD) Tj ET',
    'BT /F1 11 Tf 0.73 0.9 0.99 rg 36 762 Td (AES-256 Encrypted Synthetic Demo Document) Tj ET',
    `BT /F1 16 Tf 0.06 0.09 0.16 rg 36 675 Td (${escapePdfText(template.title)}) Tj ET`,
    `BT /F1 12 Tf 0.2 0.25 0.33 rg 36 635 Td (Category: ${escapePdfText(cat.labelEn)}) Tj ET`,
    `BT /F1 12 Tf 0.2 0.25 0.33 rg 36 610 Td (Member: ${escapePdfText(memberName)}) Tj ET`,
    `BT /F1 12 Tf 0.2 0.25 0.33 rg 36 585 Td (Demo Reference: ${escapePdfText(template.maskedId)}) Tj ET`,
    `BT /F1 11 Tf 0.2 0.25 0.33 rg 36 555 Td (Notes: ${escapePdfText(template.notes)}) Tj ET`,
    '0.88 0.95 1 rg 36 440 523 70 re f',
    'BT /F1 11 Tf 0.09 0.145 0.329 rg 52 480 Td (SAMPLE DOCUMENT FOR DEVELOPMENT & TESTING ONLY) Tj ET',
    'BT /F1 10 Tf 0.2 0.25 0.33 rg 52 458 Td (Does not contain real Aadhaar, PAN, or banking credentials.) Tj ET',
  ];
  const stream = lines.join('\n');
  const objs: string[] = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n',
    '4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n',
    `5 0 obj\n<< /Length ${stream.length} >>\nstream\n${stream}\nendstream\nendobj\n`,
  ];

  let pdf = '%PDF-1.4\n';
  const offsets: number[] = [0];
  for (const obj of objs) {
    offsets.push(pdf.length);
    pdf += obj;
  }
  const xrefStart = pdf.length;
  pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`;
  for (let i = 1; i < offsets.length; i++) {
    pdf += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF`;

  return new TextEncoder().encode(pdf);
}

export async function renderSampleBytes(
  template: SampleDocumentTemplate,
  memberName: string
): Promise<Uint8Array> {
  if (template.fileType === 'pdf' || typeof document === 'undefined') {
    return buildSamplePdfBytes(template, memberName);
  }

  const canvas = document.createElement('canvas');
  canvas.width = 1080;
  canvas.height = 680;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return buildSamplePdfBytes(template, memberName);
  }

  ctx.fillStyle = '#F8FAFC';
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // Navy banking card background
  ctx.fillStyle = '#172554';
  ctx.beginPath();
  ctx.roundRect(40, 40, 1000, 600, 36);
  ctx.fill();

  // Sky accent header
  ctx.fillStyle = '#38BDF8';
  ctx.beginPath();
  ctx.roundRect(40, 40, 1000, 110, [36, 36, 0, 0]);
  ctx.fill();

  ctx.fillStyle = '#0F172A';
  ctx.font = 'bold 34px system-ui, sans-serif';
  ctx.fillText('MY FAMILY DOC • SAMPLE ONLY', 80, 108);

  const cat = getCategoryByKey(template.categoryKey);
  ctx.fillStyle = '#FFFFFF';
  ctx.font = 'bold 42px system-ui, sans-serif';
  ctx.fillText(cat.labelEn, 80, 235);

  ctx.fillStyle = '#E0F2FE';
  ctx.font = '30px system-ui, sans-serif';
  ctx.fillText(`Holder: ${memberName}`, 80, 315);
  ctx.fillText(`Demo Ref: ${template.maskedId}`, 80, 375);

  ctx.fillStyle = '#38BDF8';
  ctx.font = 'bold 26px system-ui, sans-serif';
  ctx.fillText('SYNTHETIC TEST DOCUMENT • NOT A REAL GOVT ID', 80, 565);

  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/png'));
  if (!blob) return buildSamplePdfBytes(template, memberName);
  return new Uint8Array(await blob.arrayBuffer());
}
