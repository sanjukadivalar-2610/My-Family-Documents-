export interface DocumentCategoryMeta {
  id: string;
  storageKey: string;
  labelEn: string;
  labelKn: string;
  tintColor: string;
  iconKey:
    | 'badge'
    | 'credit-card'
    | 'vote'
    | 'receipt'
    | 'car'
    | 'flight'
    | 'school'
    | 'bank'
    | 'property'
    | 'medical'
    | 'award'
    | 'folder';
}

export const DOCUMENT_CATEGORIES: DocumentCategoryMeta[] = [
  {
    id: 'AADHAAR',
    storageKey: 'Aadhaar Card',
    labelEn: 'Aadhaar Card',
    labelKn: 'ಆಧಾರ್ ಕಾರ್ಡ್',
    tintColor: '#2563EB',
    iconKey: 'badge',
  },
  {
    id: 'PAN',
    storageKey: 'PAN Card',
    labelEn: 'PAN Card',
    labelKn: 'ಪ್ಯಾನ್ ಕಾರ್ಡ್',
    tintColor: '#0284C7',
    iconKey: 'credit-card',
  },
  {
    id: 'VOTER_ID',
    storageKey: 'Voter ID',
    labelEn: 'Voter ID',
    labelKn: 'ಮತದಾರರ ಗುರುತಿನ ಚೀಟಿ',
    tintColor: '#4F46E5',
    iconKey: 'vote',
  },
  {
    id: 'RATION_CARD',
    storageKey: 'Ration Card',
    labelEn: 'Ration Card',
    labelKn: 'ರೇಷನ್ ಕಾರ್ಡ್',
    tintColor: '#0D9488',
    iconKey: 'receipt',
  },
  {
    id: 'DRIVING_LICENCE',
    storageKey: 'Driving Licence',
    labelEn: 'Driving Licence',
    labelKn: 'ಚಾಲನಾ ಪರವಾನಗಿ',
    tintColor: '#D97706',
    iconKey: 'car',
  },
  {
    id: 'PASSPORT',
    storageKey: 'Passport',
    labelEn: 'Passport',
    labelKn: 'ಪಾಸ್‌ಪೋರ್ಟ್',
    tintColor: '#1D4ED8',
    iconKey: 'flight',
  },
  {
    id: 'EDUCATION',
    storageKey: 'Education Documents',
    labelEn: 'Education Documents',
    labelKn: 'ಶೈಕ್ಷಣಿಕ ದಾಖಲೆಗಳು',
    tintColor: '#7C3AED',
    iconKey: 'school',
  },
  {
    id: 'BANK',
    storageKey: 'Bank Documents',
    labelEn: 'Bank Documents',
    labelKn: 'ಬ್ಯಾಂಕ್ ದಾಖಲೆಗಳು',
    tintColor: '#059669',
    iconKey: 'bank',
  },
  {
    id: 'PROPERTY',
    storageKey: 'Property Documents',
    labelEn: 'Property Documents',
    labelKn: 'ಆಸ್ತಿ ದಾಖಲೆಗಳು',
    tintColor: '#B45309',
    iconKey: 'property',
  },
  {
    id: 'MEDICAL',
    storageKey: 'Medical Documents',
    labelEn: 'Medical Documents',
    labelKn: 'ವೈದ್ಯಕೀಯ ದಾಖಲೆಗಳು',
    tintColor: '#DC2626',
    iconKey: 'medical',
  },
  {
    id: 'CERTIFICATES',
    storageKey: 'Certificates',
    labelEn: 'Certificates',
    labelKn: 'ಪ್ರಮಾಣಪತ್ರಗಳು',
    tintColor: '#0891B2',
    iconKey: 'award',
  },
  {
    id: 'OTHER',
    storageKey: 'Other Documents',
    labelEn: 'Other Documents',
    labelKn: 'ಇತರೆ ದಾಖಲೆಗಳು',
    tintColor: '#475569',
    iconKey: 'folder',
  },
];

export function getCategoryByKey(key: string): DocumentCategoryMeta {
  const normalized = key.trim().toLowerCase();
  return (
    DOCUMENT_CATEGORIES.find((cat) => cat.storageKey.toLowerCase() === normalized) ??
    DOCUMENT_CATEGORIES[DOCUMENT_CATEGORIES.length - 1]
  );
}

export function getLocalizedCategoryLabel(cat: DocumentCategoryMeta, lang: string): string {
  return lang === 'kn' ? cat.labelKn : cat.labelEn;
}
