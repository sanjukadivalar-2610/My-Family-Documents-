import { LanguageCode } from '../domain/models';

export interface AppStrings {
  languageCode: LanguageCode;
  appName: string;
  tagline: string;
  navHome: string;
  navFamily: string;
  navDocuments: string;
  navBackup: string;
  navSettings: string;

  // Auth & Onboarding
  welcomeTitle: string;
  welcomeSubtitle: string;
  signInWithGoogle: string;
  signInWithEmailTab: string;
  registerWithEmailTab: string;
  emailLabel: string;
  passwordLabel: string;
  signInButton: string;
  registerButton: string;
  signingIn: string;
  orDivider: string;
  invalidEmailError: string;
  passwordTooShortError: string;
  nameRequiredError: string;
  securityEncryptedBadge: string;
  securityFamilyIsolatedBadge: string;
  securityOfflinePwaBadge: string;
  createFamilyTab: string;
  joinFamilyTab: string;
  familyNameLabel: string;
  yourNameLabel: string;
  familyIdCodeLabel: string;
  createFamilyButton: string;
  joinFamilyButton: string;
  onboardingSubtitle: string;
  familyIdHint: string;

  // PIN Lock
  enterPinTitle: string;
  setupPinTitle: string;
  confirmPinTitle: string;
  pinSubtitle: string;
  pinMismatchError: string;
  wrongPinError: string;
  cancel: string;

  // Home Dashboard
  goodDayGreeting: string;
  familyIdPrefix: string;
  copyFamilyId: string;
  copiedFamilyId: string;
  totalMembers: string;
  totalDocuments: string;
  documentCategoriesCount: string;
  storageUsageTitle: string;
  expiringSoonCount: string;
  encryptedLocalCount: string;
  backupStatusTitle: string;
  syncedStatus: string;
  syncingStatus: string;
  offlineStatus: string;
  attentionStatus: string;
  lastBackupLabel: string;
  neverBackedUp: string;
  quickActions: string;
  quickUpload: string;
  scanDocument: string;
  addMember: string;
  loadSampleDocs: string;
  recentDocuments: string;
  categoriesOverviewTitle: string;
  viewAll: string;
  expiryAlertTitle: string;
  adminConsoleButton: string;
  roleAdmin: string;
  roleMember: string;

  // Family Management
  familyMembersTitle: string;
  editMember: string;
  memberNameLabel: string;
  relationLabel: string;
  linkedEmailLabel: string;
  selectPhoto: string;
  removeMember: string;
  removeMemberConfirm: string;
  save: string;
  memberCountSuffix: string;
  adminOnlyManageNote: string;

  // Documents
  documentsTitle: string;
  searchDocumentsHint: string;
  allCategories: string;
  allMembers: string;
  uploadDocumentTitle: string;
  uploadFileLabel: string;
  supportedFormatsHint: string;
  scanWithCamera: string;
  documentTitleLabel: string;
  categoryLabel: string;
  memberOwnerLabel: string;
  docNumberOptionalLabel: string;
  notesOptionalLabel: string;
  issueDateLabel: string;
  expiryDateLabel: string;
  clearDate: string;
  noExpirySet: string;
  expiredBadge: string;
  expiringSoonBadge: string;
  validBadge: string;
  encryptedBadge: string;
  cloudSyncedBadge: string;
  localOnlyBadge: string;
  sampleDocBadge: string;
  emptyDocumentsTitle: string;
  emptyDocumentsSubtitle: string;
  fileRequiredError: string;
  unsupportedFileError: string;

  // Document Detail & Camera
  documentPreviewTitle: string;
  renameDocument: string;
  editDates: string;
  downloadDocument: string;
  shareDocument: string;
  deleteDocument: string;
  deleteConfirmTitle: string;
  deleteConfirmMessage: string;
  confirmDelete: string;
  whoCanAccessTitle: string;
  permissionsSaved: string;
  downloadSuccess: string;
  shareSuccess: string;
  documentSaved: string;
  cameraPermissionHint: string;
  captureScan: string;
  retakeScan: string;
  useScan: string;
  uploadPhotoFallback: string;
  zoomIn: string;
  zoomOut: string;
  resetZoom: string;

  // Backup & PWA
  backupCenterTitle: string;
  manualBackupNow: string;
  restoreFromCloud: string;
  autoBackupTitle: string;
  autoBackupSubtitle: string;
  backupFrequencyLabel: string;
  daily: string;
  weekly: string;
  offlineModeBanner: string;
  backupCompletedMsg: string;
  restoreCompletedMsg: string;
  installPwaTitle: string;
  installPwaSubtitle: string;
  installPwaButton: string;
  pwaInstalledBadge: string;

  // Admin Panel
  adminPanelTitle: string;
  familyAccountSection: string;
  renameFamily: string;
  documentPermissionsSection: string;
  backupHealthSection: string;
  noOtherLinkedUsersNote: string;

  // Settings
  settingsTitle: string;
  languageSetting: string;
  themeSetting: string;
  lightMode: string;
  darkMode: string;
  systemMode: string;
  appPinLock: string;
  changePin: string;
  disablePin: string;
  expiryRemindersSetting: string;
  expiryRemindersSubtitle: string;
  checkExpiriesNow: string;
  backupSettingsLink: string;
  privacyPolicy: string;
  privacyPolicyBody: string;
  aboutApp: string;
  aboutAppBody: string;
  logout: string;
  logoutConfirm: string;
  close: string;

  // Setup warning when Firebase env vars aren't configured
  firebaseNotConfiguredTitle: string;
  firebaseNotConfiguredBody: string;
}

export const EnglishStrings: AppStrings = {
  languageCode: 'en',
  appName: 'My Family Doc',
  tagline: 'All Your Family Documents in One Place.',
  navHome: 'Home',
  navFamily: 'Family',
  navDocuments: 'Documents',
  navBackup: 'Backup',
  navSettings: 'Settings',

  welcomeTitle: 'Your Private Family Vault',
  welcomeSubtitle:
    'Bank-grade AES-256 encrypted storage for Aadhaar, PAN, Passports, Property deeds, Medical records, and more.',
  signInWithGoogle: 'Continue with Google',
  signInWithEmailTab: 'Sign In',
  registerWithEmailTab: 'Create Account',
  emailLabel: 'Email Address',
  passwordLabel: 'Password (min 6 characters)',
  signInButton: 'Sign In to Vault',
  registerButton: 'Register Account',
  signingIn: 'Authenticating...',
  orDivider: 'OR',
  invalidEmailError: 'Please enter a valid email address.',
  passwordTooShortError: 'Password must be at least 6 characters.',
  nameRequiredError: 'Please enter your full name.',
  securityEncryptedBadge: 'AES-256 Encrypted',
  securityFamilyIsolatedBadge: 'Family Isolated',
  securityOfflinePwaBadge: 'Offline PWA',
  createFamilyTab: 'Create Family',
  joinFamilyTab: 'Join with Family ID',
  familyNameLabel: 'Family Name (e.g. Sharma Family)',
  yourNameLabel: 'Your Full Name',
  familyIdCodeLabel: 'Enter Family ID (e.g. MFD-1020-AB)',
  createFamilyButton: 'Create Family Vault',
  joinFamilyButton: 'Join Family Vault',
  onboardingSubtitle:
    'Set up a new family vault as Admin, or join an existing family using their Family ID.',
  familyIdHint: 'Share this Family ID only with trusted family members.',

  enterPinTitle: 'Unlock My Family Doc',
  setupPinTitle: 'Create 4-Digit Vault PIN',
  confirmPinTitle: 'Confirm Your 4-Digit PIN',
  pinSubtitle: 'Enter your 4-digit security PIN to access encrypted family documents.',
  pinMismatchError: 'PINs did not match. Please try again.',
  wrongPinError: 'Incorrect PIN. Please try again.',
  cancel: 'Cancel',

  goodDayGreeting: 'Welcome back,',
  familyIdPrefix: 'Family ID:',
  copyFamilyId: 'Copy ID',
  copiedFamilyId: 'Family ID copied to clipboard',
  totalMembers: 'Family Members',
  totalDocuments: 'Total Documents',
  documentCategoriesCount: 'Active Categories',
  storageUsageTitle: 'Vault Storage Usage',
  expiringSoonCount: 'Expiring Soon',
  encryptedLocalCount: 'Offline Encrypted',
  backupStatusTitle: 'Cloud Backup & Sync',
  syncedStatus: 'Synced & Protected',
  syncingStatus: 'Syncing Vault...',
  offlineStatus: 'Offline Mode (Local Vault Active)',
  attentionStatus: 'Backup Pending',
  lastBackupLabel: 'Last backup:',
  neverBackedUp: 'Not backed up yet',
  quickActions: 'Quick Actions',
  quickUpload: 'Upload Doc',
  scanDocument: 'Scan Camera',
  addMember: 'Add Member',
  loadSampleDocs: 'Load Sample Docs',
  recentDocuments: 'Recently Uploaded Documents',
  categoriesOverviewTitle: 'Document Categories',
  viewAll: 'View All',
  expiryAlertTitle: 'Document Expiry Alerts',
  adminConsoleButton: 'Admin Panel',
  roleAdmin: 'Admin',
  roleMember: 'Member',

  familyMembersTitle: 'Family Members',
  editMember: 'Edit Member Profile',
  memberNameLabel: 'Member Name',
  relationLabel: 'Relationship (e.g. Spouse, Child, Parent)',
  linkedEmailLabel: 'Linked Email (Optional)',
  selectPhoto: 'Choose Profile Photo',
  removeMember: 'Remove Member',
  removeMemberConfirm: 'Remove this member profile from the family list?',
  save: 'Save',
  memberCountSuffix: 'members',
  adminOnlyManageNote:
    'Only the Family Admin can add, remove, or change member roles and document permissions.',

  documentsTitle: 'Document Vault',
  searchDocumentsHint: 'Search by title, category, notes, or member...',
  allCategories: 'All Categories',
  allMembers: 'All Members',
  uploadDocumentTitle: 'Add Family Document',
  uploadFileLabel: 'Select PDF, JPG, JPEG, or PNG',
  supportedFormatsHint: 'Supported: PDF, JPG, JPEG, PNG (max 25 MB). Encrypted locally before sync.',
  scanWithCamera: 'Scan with Device Camera',
  documentTitleLabel: 'Document Title',
  categoryLabel: 'Document Category',
  memberOwnerLabel: 'Belongs to Member',
  docNumberOptionalLabel: 'Reference / Masked ID (Optional)',
  notesOptionalLabel: 'Description / Notes (Optional)',
  issueDateLabel: 'Issue Date',
  expiryDateLabel: 'Expiry Date',
  clearDate: 'Clear',
  noExpirySet: 'No expiry date',
  expiredBadge: 'Expired',
  expiringSoonBadge: 'Expiring Soon',
  validBadge: 'Valid',
  encryptedBadge: 'AES-256',
  cloudSyncedBadge: 'Cloud Synced',
  localOnlyBadge: 'Local Only',
  sampleDocBadge: 'SAMPLE',
  emptyDocumentsTitle: 'No documents found',
  emptyDocumentsSubtitle:
    'Upload a PDF, JPG, JPEG, or PNG, scan using your device camera, or load synthetic sample documents.',
  fileRequiredError: 'Please choose a PDF or image file to upload.',
  unsupportedFileError: 'Only PDF, JPG, JPEG, and PNG files are supported.',

  documentPreviewTitle: 'Decrypted Document Preview',
  renameDocument: 'Edit Document Details',
  editDates: 'Issue & Expiry Dates',
  downloadDocument: 'Download',
  shareDocument: 'Share',
  deleteDocument: 'Delete',
  deleteConfirmTitle: 'Delete Document?',
  deleteConfirmMessage:
    'This will permanently erase the encrypted local copy, cloud backup, and metadata record. This action cannot be undone.',
  confirmDelete: 'Delete',
  whoCanAccessTitle: 'Member Access Permissions',
  permissionsSaved: 'Document access permissions updated',
  downloadSuccess: 'Decrypted document downloaded',
  shareSuccess: 'Document shared successfully',
  documentSaved: 'Encrypted and saved to Family Vault',
  cameraPermissionHint:
    'Allow camera access in your browser to scan physical documents directly into your encrypted vault, or pick an image photo instead.',
  captureScan: 'Capture Document',
  retakeScan: 'Retake',
  useScan: 'Use Scan & Add Details',
  uploadPhotoFallback: 'Capture / Upload Photo',
  zoomIn: 'Zoom In',
  zoomOut: 'Zoom Out',
  resetZoom: 'Reset',

  backupCenterTitle: 'Backup & Cloud Sync',
  manualBackupNow: 'Backup Now',
  restoreFromCloud: 'Restore from Cloud',
  autoBackupTitle: 'Automatic Cloud Backup',
  autoBackupSubtitle:
    'Automatically encrypt and upload newly added documents to Firebase Cloud Storage.',
  backupFrequencyLabel: 'Backup Schedule',
  daily: 'Daily',
  weekly: 'Weekly',
  offlineModeBanner:
    'You are offline. Locally encrypted documents in your browser vault remain accessible and changes will sync automatically when back online.',
  backupCompletedMsg: 'Cloud backup sync completed',
  restoreCompletedMsg: 'Cloud restore completed',
  installPwaTitle: 'Install My Family Doc App',
  installPwaSubtitle:
    'Install this PWA on Chrome, Edge, or Android for an app-like experience and offline access.',
  installPwaButton: 'Install App',
  pwaInstalledBadge: 'Installed PWA',

  adminPanelTitle: 'Family Admin Panel',
  familyAccountSection: 'Family Account',
  renameFamily: 'Rename Family',
  documentPermissionsSection: 'Document Access Control',
  backupHealthSection: 'Vault Backup Health',
  noOtherLinkedUsersNote:
    'When additional family members join using your Family ID, you can toggle which documents each member can access here.',

  settingsTitle: 'Settings & Security',
  languageSetting: 'App Language',
  themeSetting: 'Appearance',
  lightMode: 'Light',
  darkMode: 'Dark',
  systemMode: 'System',
  appPinLock: 'App PIN Lock',
  changePin: 'Change PIN',
  disablePin: 'Disable PIN Lock',
  expiryRemindersSetting: 'Expiry Reminders',
  expiryRemindersSubtitle:
    'Notify when passports, licences, or policies expire within 30 days.',
  checkExpiriesNow: 'Check Expiries Now',
  backupSettingsLink: 'Backup & Sync Center',
  privacyPolicy: 'Privacy Policy',
  privacyPolicyBody:
    'My Family Doc encrypts sensitive documents locally in IndexedDB using Web Crypto AES-256-GCM before optional cloud sync to Firebase Storage. Cloud Firestore and Storage Security Rules strictly isolate each Family ID so no outside family can access your data.',
  aboutApp: 'About My Family Doc',
  aboutAppBody:
    'My Family Doc PWA v1.0\nAll Your Family Documents in One Place.\n\nCross-platform companion web application preserving the Android app models, AES-256-GCM local encryption, Cloud Firestore, and Firebase Storage security.',
  logout: 'Logout',
  logoutConfirm: 'Sign out of your account and lock this browser session?',
  close: 'Close',

  firebaseNotConfiguredTitle: 'Firebase Web Configuration Required',
  firebaseNotConfiguredBody:
    'Add your Firebase Web App credentials in web/.env (see web/.env.example) using Firebase Console -> Project Settings -> Web App, or set VITE_USE_FIREBASE_EMULATORS=true for local emulator testing.',
};

export const KannadaStrings: AppStrings = {
  languageCode: 'kn',
  appName: 'ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್',
  tagline: 'ನಿಮ್ಮ ಕುಟುಂಬದ ಎಲ್ಲಾ ದಾಖಲೆಗಳು ಒಂದೇ ಸ್ಥಳದಲ್ಲಿ.',
  navHome: 'ಮುಖಪುಟ',
  navFamily: 'ಕುಟುಂಬ',
  navDocuments: 'ದಾಖಲೆಗಳು',
  navBackup: 'ಬ್ಯಾಕಪ್',
  navSettings: 'ಸೆಟ್ಟಿಂಗ್ಸ್',

  welcomeTitle: 'ನಿಮ್ಮ ಖಾಸಗಿ ಕುಟುಂಬ ವಾಲ್ಟ್',
  welcomeSubtitle:
    'ಆಧಾರ್, ಪ್ಯಾನ್, ಪಾಸ್‌ಪೋರ್ಟ್, ಆಸ್ತಿ ಮತ್ತು ವೈದ್ಯಕೀಯ ದಾಖಲೆಗಳಿಗೆ ಬ್ಯಾಂಕ್-ಮಟ್ಟದ AES-256 ಎನ್‌ಕ್ರಿಪ್ಟೆಡ್ ಸುರಕ್ಷತೆ.',
  signInWithGoogle: 'Google ಮೂಲಕ ಮುಂದುವರಿಸಿ',
  signInWithEmailTab: 'ಸೈನ್ ಇನ್',
  registerWithEmailTab: 'ಖಾತೆ ರಚಿಸಿ',
  emailLabel: 'ಇಮೇಲ್ ವಿಳಾಸ',
  passwordLabel: 'ಪಾಸ್‌ವರ್ಡ್ (ಕನಿಷ್ಠ 6 ಅಕ್ಷರಗಳು)',
  signInButton: 'ವಾಲ್ಟ್‌ಗೆ ಸೈನ್ ಇನ್ ಮಾಡಿ',
  registerButton: 'ಖಾತೆ ನೋಂದಾಯಿಸಿ',
  signingIn: 'ಪರಿಶೀಲಿಸಲಾಗುತ್ತಿದೆ...',
  orDivider: 'ಅಥವಾ',
  invalidEmailError: 'ದಯವಿಟ್ಟು ಮಾನ್ಯವಾದ ಇಮೇಲ್ ವಿಳಾಸವನ್ನು ನಮೂದಿಸಿ.',
  passwordTooShortError: 'ಪಾಸ್‌ವರ್ಡ್ ಕನಿಷ್ಠ 6 ಅಕ್ಷರಗಳನ್ನು ಹೊಂದಿರಬೇಕು.',
  nameRequiredError: 'ದಯವಿಟ್ಟು ನಿಮ್ಮ ಪೂರ್ಣ ಹೆಸರನ್ನು ನಮೂದಿಸಿ.',
  securityEncryptedBadge: 'AES-256 ಎನ್‌ಕ್ರಿಪ್ಟ್',
  securityFamilyIsolatedBadge: 'ಕುಟುಂಬ ಪ್ರತ್ಯೇಕತೆ',
  securityOfflinePwaBadge: 'ಆಫ್‌ಲೈನ್ PWA',
  createFamilyTab: 'ಕುಟುಂಬ ರಚಿಸಿ',
  joinFamilyTab: 'ಕುಟುಂಬ ID ಮೂಲಕ ಸೇರಿ',
  familyNameLabel: 'ಕುಟುಂಬದ ಹೆಸರು (ಉದಾ. ಶರ್ಮಾ ಕುಟುಂಬ)',
  yourNameLabel: 'ನಿಮ್ಮ ಪೂರ್ಣ ಹೆಸರು',
  familyIdCodeLabel: 'ಕುಟುಂಬ ID ನಮೂದಿಸಿ (ಉದಾ. MFD-1020-AB)',
  createFamilyButton: 'ಕುಟುಂಬ ವಾಲ್ಟ್ ರಚಿಸಿ',
  joinFamilyButton: 'ಕುಟುಂಬ ವಾಲ್ಟ್ ಸೇರಿ',
  onboardingSubtitle:
    'ನಿರ್ವಾಹಕರಾಗಿ ಹೊಸ ಕುಟುಂಬ ವಾಲ್ಟ್ ರಚಿಸಿ ಅಥವಾ ಕುಟುಂಬ ID ಬಳಸಿ ಅಸ್ತಿತ್ವದಲ್ಲಿರುವ ಕುಟುಂಬಕ್ಕೆ ಸೇರಿ.',
  familyIdHint: 'ಈ ಕುಟುಂಬ ID ಯನ್ನು ವಿಶ್ವಾಸಾರ್ಹ ಕುಟುಂಬ ಸದಸ್ಯರೊಂದಿಗೆ ಮಾತ್ರ ಹಂಚಿಕೊಳ್ಳಿ.',

  enterPinTitle: 'ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್ ಅನ್‌ಲಾಕ್ ಮಾಡಿ',
  setupPinTitle: '4-ಅಂಕಿಯ ವಾಲ್ಟ್ PIN ರಚಿಸಿ',
  confirmPinTitle: 'ನಿಮ್ಮ 4-ಅಂಕಿಯ PIN ದೃಢೀಕರಿಸಿ',
  pinSubtitle: 'ಎನ್‌ಕ್ರಿಪ್ಟ್ ಮಾಡಿದ ಕುಟುಂಬ ದಾಖಲೆಗಳನ್ನು ತೆರೆಯಲು 4-ಅಂಕಿಯ PIN ನಮೂದಿಸಿ.',
  pinMismatchError: 'PIN ಹೊಂದಾಣಿಕೆಯಾಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.',
  wrongPinError: 'ತಪ್ಪಾದ PIN. ದಯವಿಟ್ಟು ಮತ್ತೆ ಪ್ರಯತ್ನಿಸಿ.',
  cancel: 'ರದ್ದುಮಾಡಿ',

  goodDayGreeting: 'ಮತ್ತೆ ಸ್ವಾಗತ,',
  familyIdPrefix: 'ಕುಟುಂಬ ID:',
  copyFamilyId: 'ID ನಕಲಿಸಿ',
  copiedFamilyId: 'ಕುಟುಂಬ ID ನಕಲಿಸಲಾಗಿದೆ',
  totalMembers: 'ಕುಟುಂಬ ಸದಸ್ಯರು',
  totalDocuments: 'ಒಟ್ಟು ದಾಖಲೆಗಳು',
  documentCategoriesCount: 'ಸಕ್ರಿಯ ವರ್ಗಗಳು',
  storageUsageTitle: 'ವಾಲ್ಟ್ ಸ್ಟೋರೇಜ್ ಬಳಕೆ',
  expiringSoonCount: 'ಶೀಘ್ರದಲ್ಲೇ ಮುಕ್ತಾಯ',
  encryptedLocalCount: 'ಆಫ್‌ಲೈನ್ ಎನ್‌ಕ್ರಿಪ್ಟೆಡ್',
  backupStatusTitle: 'ಕ್ಲೌಡ್ ಬ್ಯಾಕಪ್ ಮತ್ತು ಸಿಂಕ್',
  syncedStatus: 'ಸಿಂಕ್ ಮತ್ತು ಸುರಕ್ಷಿತವಾಗಿದೆ',
  syncingStatus: 'ವಾಲ್ಟ್ ಸಿಂಕ್ ಆಗುತ್ತಿದೆ...',
  offlineStatus: 'ಆಫ್‌ಲೈನ್ ಮೋಡ್ (ಸ್ಥಳೀಯ ವಾಲ್ಟ್ ಸಕ್ರಿಯ)',
  attentionStatus: 'ಬ್ಯಾಕಪ್ ಬಾಕಿ ಇದೆ',
  lastBackupLabel: 'ಕೊನೆಯ ಬ್ಯಾಕಪ್:',
  neverBackedUp: 'ಇನ್ನೂ ಬ್ಯಾಕಪ್ ಮಾಡಲಾಗಿಲ್ಲ',
  quickActions: 'ತ್ವರಿತ ಕ್ರಿಯೆಗಳು',
  quickUpload: 'ಅಪ್‌ಲೋಡ್ ಮಾಡಿ',
  scanDocument: 'ಕ್ಯಾಮೆರಾ ಸ್ಕ್ಯಾನ್',
  addMember: 'ಸದಸ್ಯರನ್ನು ಸೇರಿಸಿ',
  loadSampleDocs: 'ಮಾದರಿ ದಾಖಲೆಗಳು',
  recentDocuments: 'ಇತ್ತೀಚೆಗೆ ಅಪ್‌ಲೋಡ್ ಮಾಡಿದ ದಾಖಲೆಗಳು',
  categoriesOverviewTitle: 'ದಾಖಲೆ ವರ್ಗಗಳು',
  viewAll: 'ಎಲ್ಲಾ ನೋಡಿ',
  expiryAlertTitle: 'ದಾಖಲೆ ಮುಕ್ತಾಯ ಎಚ್ಚರಿಕೆಗಳು',
  adminConsoleButton: 'ನಿರ್ವಾಹಕ ಫಲಕ',
  roleAdmin: 'ನಿರ್ವಾಹಕ',
  roleMember: 'ಸದಸ್ಯ',

  familyMembersTitle: 'ಕುಟುಂಬ ಸದಸ್ಯರು',
  editMember: 'ಸದಸ್ಯರ ಪ್ರೊಫೈಲ್ ತಿದ್ದುಪಡಿ',
  memberNameLabel: 'ಸದಸ್ಯರ ಹೆಸರು',
  relationLabel: 'ಸಂಬಂಧ (ಉದಾ. ಪತಿ/ಪತ್ನಿ, ಮಗು, ಪೋಷಕರು)',
  linkedEmailLabel: 'ಲಿಂಕ್ ಮಾಡಿದ ಇಮೇಲ್ (ಐಚ್ಛಿಕ)',
  selectPhoto: 'ಪ್ರೊಫೈಲ್ ಫೋಟೋ ಆಯ್ಕೆಮಾಡಿ',
  removeMember: 'ಸದಸ್ಯರನ್ನು ತೆಗೆದುಹಾಕಿ',
  removeMemberConfirm: 'ಈ ಸದಸ್ಯರ ಪ್ರೊಫೈಲ್ ಅನ್ನು ಕುಟುಂಬ ಪಟ್ಟಿಯಿಂದ ತೆಗೆದುಹಾಕುವುದೇ?',
  save: 'ಉಳಿಸಿ',
  memberCountSuffix: 'ಸದಸ್ಯರು',
  adminOnlyManageNote:
    'ಕುಟುಂಬದ ನಿರ್ವಾಹಕರು ಮಾತ್ರ ಸದಸ್ಯರನ್ನು ಸೇರಿಸಲು, ತೆಗೆಯಲು ಮತ್ತು ದಾಖಲೆ ಅನುಮತಿಗಳನ್ನು ಬದಲಾಯಿಸಬಹುದು.',

  documentsTitle: 'ದಾಖಲೆ ವಾಲ್ಟ್',
  searchDocumentsHint: 'ಹೆಸರು, ವರ್ಗ, ಟಿಪ್ಪಣಿ ಅಥವಾ ಸದಸ್ಯರಿಂದ ಹುಡುಕಿ...',
  allCategories: 'ಎಲ್ಲಾ ವರ್ಗಗಳು',
  allMembers: 'ಎಲ್ಲಾ ಸದಸ್ಯರು',
  uploadDocumentTitle: 'ಕುಟುಂಬ ದಾಖಲೆ ಸೇರಿಸಿ',
  uploadFileLabel: 'PDF, JPG, JPEG, ಅಥವಾ PNG ಆಯ್ಕೆಮಾಡಿ',
  supportedFormatsHint: 'ಬೆಂಬಲಿತ: PDF, JPG, JPEG, PNG (ಗರಿಷ್ಠ 25 MB). ಸಿಂಕ್ ಮೊದಲು ಸ್ಥಳೀಯವಾಗಿ ಎನ್‌ಕ್ರಿಪ್ಟ್ ಆಗುತ್ತದೆ.',
  scanWithCamera: 'ಕ್ಯಾಮೆರಾ ಮೂಲಕ ಸ್ಕ್ಯಾನ್ ಮಾಡಿ',
  documentTitleLabel: 'ದಾಖಲೆಯ ಶೀರ್ಷಿಕೆ',
  categoryLabel: 'ದಾಖಲೆ ವರ್ಗ',
  memberOwnerLabel: 'ಯಾರ ದಾಖಲೆ',
  docNumberOptionalLabel: 'ಉಲ್ಲೇಖ / ಮರೆಮಾಡಿದ ಸಂಖ್ಯೆ (ಐಚ್ಛಿಕ)',
  notesOptionalLabel: 'ವಿವರಣೆ / ಟಿಪ್ಪಣಿಗಳು (ಐಚ್ಛಿಕ)',
  issueDateLabel: 'ನೀಡಿದ ದಿನಾಂಕ',
  expiryDateLabel: 'ಮುಕ್ತಾಯ ದಿನಾಂಕ',
  clearDate: 'ತೆರವುಗೊಳಿಸಿ',
  noExpirySet: 'ಮುಕ್ತಾಯ ದಿನಾಂಕ ಇಲ್ಲ',
  expiredBadge: 'ಅವಧಿ ಮುಗಿದಿದೆ',
  expiringSoonBadge: 'ಶೀಘ್ರದಲ್ಲೇ ಮುಕ್ತಾಯ',
  validBadge: 'ಮಾನ್ಯವಾಗಿದೆ',
  encryptedBadge: 'AES-256',
  cloudSyncedBadge: 'ಕ್ಲೌಡ್ ಸಿಂಕ್',
  localOnlyBadge: 'ಸ್ಥಳೀಯ ಮಾತ್ರ',
  sampleDocBadge: 'ಮಾದರಿ',
  emptyDocumentsTitle: 'ಯಾವುದೇ ದಾಖಲೆಗಳು ಕಂಡುಬಂದಿಲ್ಲ',
  emptyDocumentsSubtitle:
    'PDF, JPG, JPEG ಅಥವಾ PNG ಅಪ್‌ಲೋಡ್ ಮಾಡಿ, ಕ್ಯಾಮೆರಾದಿಂದ ಸ್ಕ್ಯಾನ್ ಮಾಡಿ ಅಥವಾ ಮಾದರಿ ದಾಖಲೆಗಳನ್ನು ಲೋಡ್ ಮಾಡಿ.',
  fileRequiredError: 'ದಯವಿಟ್ಟು ಅಪ್‌ಲೋಡ್ ಮಾಡಲು PDF ಅಥವಾ ಚಿತ್ರವನ್ನು ಆಯ್ಕೆಮಾಡಿ.',
  unsupportedFileError: 'PDF, JPG, JPEG ಮತ್ತು PNG ಫೈಲ್‌ಗಳನ್ನು ಮಾತ್ರ ಬೆಂಬಲಿಸಲಾಗುತ್ತದೆ.',

  documentPreviewTitle: 'ಡೀಕ್ರಿಪ್ಟ್ ಮಾಡಿದ ದಾಖಲೆ ಮುನ್ನೋಟ',
  renameDocument: 'ದಾಖಲೆ ವಿವರಗಳನ್ನು ತಿದ್ದುಪಡಿ ಮಾಡಿ',
  editDates: 'ನೀಡಿದ ಮತ್ತು ಮುಕ್ತಾಯ ದಿನಾಂಕಗಳು',
  downloadDocument: 'ಡೌನ್‌ಲೋಡ್',
  shareDocument: 'ಹಂಚಿಕೊಳ್ಳಿ',
  deleteDocument: 'ಅಳಿಸಿ',
  deleteConfirmTitle: 'ದಾಖಲೆಯನ್ನು ಅಳಿಸುವುದೇ?',
  deleteConfirmMessage:
    'ಇದು ಎನ್‌ಕ್ರಿಪ್ಟ್ ಮಾಡಿದ ಸ್ಥಳೀಯ ಫೈಲ್, ಕ್ಲೌಡ್ ಬ್ಯಾಕಪ್ ಮತ್ತು ದಾಖಲೆಯನ್ನು ಶಾಶ್ವತವಾಗಿ ಅಳಿಸುತ್ತದೆ.',
  confirmDelete: 'ಅಳಿಸಿ',
  whoCanAccessTitle: 'ಸದಸ್ಯರ ಪ್ರವೇಶ ಅನುಮತಿಗಳು',
  permissionsSaved: 'ದಾಖಲೆ ಪ್ರವೇಶ ಅನುಮತಿಗಳನ್ನು ನವೀಕರಿಸಲಾಗಿದೆ',
  downloadSuccess: 'ಡೀಕ್ರಿಪ್ಟ್ ಮಾಡಿದ ದಾಖಲೆ ಡೌನ್‌ಲೋಡ್ ಆಗಿದೆ',
  shareSuccess: 'ದಾಖಲೆಯನ್ನು ಯಶಸ್ವಿಯಾಗಿ ಹಂಚಲಾಗಿದೆ',
  documentSaved: 'ಎನ್‌ಕ್ರಿಪ್ಟ್ ಮಾಡಿ ಕುಟುಂಬ ವಾಲ್ಟ್‌ಗೆ ಉಳಿಸಲಾಗಿದೆ',
  cameraPermissionHint:
    'ದಾಖಲೆಗಳನ್ನು ನೇರವಾಗಿ ಸ್ಕ್ಯಾನ್ ಮಾಡಲು ಬ್ರೌಸರ್‌ನಲ್ಲಿ ಕ್ಯಾಮೆರಾ ಅನುಮತಿ ನೀಡಿ ಅಥವಾ ಫೋಟೋ ಆಯ್ಕೆಮಾಡಿ.',
  captureScan: 'ಸ್ಕ್ಯಾನ್ ಸೆರೆಹಿಡಿಯಿರಿ',
  retakeScan: 'ಮತ್ತೆ ತೆಗೆಯಿರಿ',
  useScan: 'ಸ್ಕ್ಯಾನ್ ಬಳಸಿ ಮತ್ತು ವಿವರ ಸೇರಿಸಿ',
  uploadPhotoFallback: 'ಫೋಟೋ ತೆಗೆಯಿರಿ / ಅಪ್‌ಲೋಡ್',
  zoomIn: 'ಜೂಮ್ ಇನ್',
  zoomOut: 'ಜೂಮ್ ಔಟ್',
  resetZoom: 'ಮರುಹೊಂದಿಸಿ',

  backupCenterTitle: 'ಬ್ಯಾಕಪ್ ಮತ್ತು ಕ್ಲೌಡ್ ಸಿಂಕ್',
  manualBackupNow: 'ಈಗಲೇ ಬ್ಯಾಕಪ್ ಮಾಡಿ',
  restoreFromCloud: 'ಕ್ಲೌಡ್‌ನಿಂದ ಮರುಸ್ಥಾಪಿಸಿ',
  autoBackupTitle: 'ಸ್ವಯಂಚಾಲಿತ ಕ್ಲೌಡ್ ಬ್ಯಾಕಪ್',
  autoBackupSubtitle:
    'ಹೊಸ ದಾಖಲೆಗಳನ್ನು ಸ್ವಯಂಚಾಲಿತವಾಗಿ ಎನ್‌ಕ್ರಿಪ್ಟ್ ಮಾಡಿ Firebase ಸ್ಟೋರೇಜ್‌ಗೆ ಅಪ್‌ಲೋಡ್ ಮಾಡಿ.',
  backupFrequencyLabel: 'ಬ್ಯಾಕಪ್ ವೇಳಾಪಟ್ಟಿ',
  daily: 'ದೈನಂದಿನ',
  weekly: 'ಸಾಪ್ತಾಹಿಕ',
  offlineModeBanner:
    'ನೀವು ಆಫ್‌ಲೈನ್‌ನಲ್ಲಿರುವಿರಿ. ಬ್ರೌಸರ್ ವಾಲ್ಟ್‌ನಲ್ಲಿರುವ ಎನ್‌ಕ್ರಿಪ್ಟ್ ದಾಖಲೆಗಳು ಲಭ್ಯವಿವೆ ಮತ್ತು ಆನ್‌ಲೈನ್ ಆದಾಗ ಸಿಂಕ್ ಆಗುತ್ತವೆ.',
  backupCompletedMsg: 'ಬ್ಯಾಕಪ್ ಸಿಂಕ್ ಪೂರ್ಣಗೊಂಡಿದೆ',
  restoreCompletedMsg: 'ಕ್ಲೌಡ್ ಮರುಸ್ಥಾಪನೆ ಪೂರ್ಣಗೊಂಡಿದೆ',
  installPwaTitle: 'ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್ ಆ್ಯಪ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ',
  installPwaSubtitle:
    'ಆಫ್‌ಲೈನ್ ಪ್ರವೇಶ ಮತ್ತು ಆ್ಯಪ್ ಅನುಭವಕ್ಕಾಗಿ Chrome, Edge ಅಥವಾ Android ನಲ್ಲಿ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ.',
  installPwaButton: 'ಆ್ಯಪ್ ಇನ್‌ಸ್ಟಾಲ್ ಮಾಡಿ',
  pwaInstalledBadge: 'ಇನ್‌ಸ್ಟಾಲ್ ಆದ PWA',

  adminPanelTitle: 'ಕುಟುಂಬ ನಿರ್ವಾಹಕ ಫಲಕ',
  familyAccountSection: 'ಕುಟುಂಬ ಖಾತೆ',
  renameFamily: 'ಕುಟುಂಬದ ಹೆಸರು ಬದಲಿಸಿ',
  documentPermissionsSection: 'ದಾಖಲೆ ಪ್ರವೇಶ ನಿಯಂತ್ರಣ',
  backupHealthSection: 'ವಾಲ್ಟ್ ಬ್ಯಾಕಪ್ ಸ್ಥಿತಿ',
  noOtherLinkedUsersNote:
    'ಇತರ ಕುಟುಂಬ ಸದಸ್ಯರು ನಿಮ್ಮ ಕುಟುಂಬ ID ಬಳಸಿ ಸೇರಿದಾಗ, ಪ್ರತಿ ದಾಖಲೆಗೆ ಅವರ ಪ್ರವೇಶವನ್ನು ಇಲ್ಲಿ ನಿಯಂತ್ರಿಸಬಹುದು.',

  settingsTitle: 'ಸೆಟ್ಟಿಂಗ್ಸ್ ಮತ್ತು ಸುರಕ್ಷತೆ',
  languageSetting: 'ಅಪ್ಲಿಕೇಶನ್ ಭಾಷೆ',
  themeSetting: 'ಥೀಮ್',
  lightMode: 'ಲೈಟ್',
  darkMode: 'ಡಾರ್ಕ್',
  systemMode: 'ಸಿಸ್ಟಮ್',
  appPinLock: 'ಅಪ್ಲಿಕೇಶನ್ PIN ಲಾಕ್',
  changePin: 'PIN ಬದಲಾಯಿಸಿ',
  disablePin: 'PIN ಲಾಕ್ ಆಫ್ ಮಾಡಿ',
  expiryRemindersSetting: 'ಮುಕ್ತಾಯ ಜ್ಞಾಪನೆಗಳು',
  expiryRemindersSubtitle: 'ದಾಖಲೆಗಳು 30 ದಿನಗಳಲ್ಲಿ ಮುಕ್ತಾಯವಾಗುವ ಮೊದಲು ಸೂಚನೆ ನೀಡಿ.',
  checkExpiriesNow: 'ಮುಕ್ತಾಯಗಳನ್ನು ಪರಿಶೀಲಿಸಿ',
  backupSettingsLink: 'ಬ್ಯಾಕಪ್ ಮತ್ತು ಸಿಂಕ್ ಸೆಟ್ಟಿಂಗ್ಸ್',
  privacyPolicy: 'ಗೌಪ್ಯತಾ ನೀತಿ',
  privacyPolicyBody:
    'ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್ IndexedDB ನಲ್ಲಿ Web Crypto AES-256-GCM ಎನ್‌ಕ್ರಿಪ್ಶನ್ ಬಳಸುತ್ತದೆ. ಪ್ರತಿ ಕುಟುಂಬದ ಡೇಟಾವನ್ನು Firebase ಭದ್ರತಾ ನಿಯಮಗಳಿಂದ ಪ್ರತ್ಯೇಕಿಸಲಾಗಿದೆ.',
  aboutApp: 'ಅಪ್ಲಿಕೇಶನ್ ಬಗ್ಗೆ',
  aboutAppBody:
    'ಮೈ ಫ್ಯಾಮಿಲಿ ಡಾಕ್ PWA v1.0\nನಿಮ್ಮ ಕುಟುಂಬದ ಎಲ್ಲಾ ದಾಖಲೆಗಳು ಒಂದೇ ಸ್ಥಳದಲ್ಲಿ.\n\nAES-256-GCM ಸ್ಥಳೀಯ ಎನ್‌ಕ್ರಿಪ್ಶನ್, Cloud Firestore ಮತ್ತು Firebase Storage ನೊಂದಿಗೆ ನಿರ್ಮಿಸಲಾಗಿದೆ.',
  logout: 'ಲಾಗೌಟ್',
  logoutConfirm: 'ನಿಮ್ಮ ಖಾತೆಯಿಂದ ಸೈನ್ ಔಟ್ ಆಗಿ ಈ ಸೆಷನ್ ಲಾಕ್ ಮಾಡುವುದೇ?',
  close: 'ಮುಚ್ಚಿ',

  firebaseNotConfiguredTitle: 'Firebase ವೆಬ್ ಕಾನ್ಫಿಗರೇಶನ್ ಅಗತ್ಯವಿದೆ',
  firebaseNotConfiguredBody:
    'web/.env ಫೈಲ್‌ನಲ್ಲಿ ನಿಮ್ಮ Firebase Web App ವಿವರಗಳನ್ನು ಸೇರಿಸಿ (web/.env.example ನೋಡಿ), ಅಥವಾ ಸ್ಥಳೀಯ ಎಮ್ಯುಲೇಟರ್ ಪರೀಕ್ಷೆಗಾಗಿ VITE_USE_FIREBASE_EMULATORS=true ಹೊಂದಿಸಿ.',
};

export function stringsForLanguage(code: LanguageCode): AppStrings {
  return code === 'kn' ? KannadaStrings : EnglishStrings;
}
