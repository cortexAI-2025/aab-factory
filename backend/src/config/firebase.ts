import admin from 'firebase-admin';


export function initializeFirebase(): void {
  if (admin.apps.length > 0) return;

  const credential = process.env.GOOGLE_APPLICATION_CREDENTIALS
    ? admin.credential.applicationDefault()
    : admin.credential.cert({
        projectId: process.env.FIREBASE_PROJECT_ID,
        privateKey: process.env.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n'),
        clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
      });

  admin.initializeApp({
    credential,
    storageBucket: process.env.FIREBASE_STORAGE_BUCKET,
  });
}

export const getFirestore = () => admin.firestore();
export const getAuth = () => admin.auth();
export const getStorage = () => admin.storage();
