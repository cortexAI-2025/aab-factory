import { execFile } from 'child_process';
import { promisify } from 'util';
import path from 'path';
import fs from 'fs/promises';
import { v4 as uuidv4 } from 'uuid';
import { getFirestore, getStorage } from '../config/firebase';
import admin from 'firebase-admin';

const execFileAsync = promisify(execFile);

interface BuildParams {
  projectId: string;
  userId: string;
  projectData: Record<string, unknown>;
  keystoreType: 'system' | 'user';
  userKeystoreUrl?: string;
}

interface BuildRecord {
  buildId: string;
  projectId: string;
  userId: string;
  status: 'QUEUED' | 'BUILDING' | 'SUCCESS' | 'FAILED';
  downloadUrl: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  estimatedMinutes: number;
  queuePosition: number;
}

/**
 * Queues an AAB build job.
 * In production this would push to a build queue (e.g., BullMQ / Cloud Tasks).
 * Here we execute it asynchronously to keep the response fast.
 */
export async function queueBuild(params: BuildParams): Promise<{
  buildId: string;
  status: string;
  estimatedMinutes: number;
  queuePosition: number;
}> {
  const db = getFirestore();
  const buildId = uuidv4();
  const now = new Date().toISOString();

  // Count pending/building jobs to estimate queue position
  const pending = await db.collection('builds')
    .where('status', 'in', ['QUEUED', 'BUILDING'])
    .count()
    .get();
  const queuePosition = (pending.data().count ?? 0) + 1;
  const estimatedMinutes = Math.ceil(queuePosition * 2.5); // ~2.5 min per build

  const buildRecord: BuildRecord = {
    buildId,
    projectId: params.projectId,
    userId: params.userId,
    status: 'QUEUED',
    downloadUrl: null,
    errorMessage: null,
    createdAt: now,
    completedAt: null,
    estimatedMinutes,
    queuePosition,
  };

  await db.collection('builds').doc(buildId).set(buildRecord);

  // Update project build status
  await db.collection('users').doc(params.userId)
    .collection('projects').doc(params.projectId)
    .update({ buildStatus: 'QUEUED', updatedAt: now });

  // Increment build counter for free plan tracking
  await db.collection('users').doc(params.userId).update({
    buildsUsedThisMonth: admin.firestore.FieldValue.increment(1),
    updatedAt: now,
  });

  // Execute build asynchronously (non-blocking)
  executeBuildAsync(buildId, params).catch(err => {
    console.error(`Build ${buildId} failed:`, err);
  });

  return { buildId, status: 'QUEUED', estimatedMinutes, queuePosition };
}

/**
 * Executes the Gradle AAB build process.
 * Generates a temporary Android project, runs bundleRelease, uploads AAB.
 */
async function executeBuildAsync(buildId: string, params: BuildParams): Promise<void> {
  const db = getFirestore();
  const buildRef = db.collection('builds').doc(buildId);
  const projectRef = db.collection('users').doc(params.userId)
    .collection('projects').doc(params.projectId);

  const workDir = path.join('/tmp', 'aab-builds', buildId);

  try {
    // Mark as building
    await buildRef.update({ status: 'BUILDING', updatedAt: new Date().toISOString() });
    await projectRef.update({ buildStatus: 'BUILDING' });

    // Create build workspace
    await fs.mkdir(workDir, { recursive: true });

    // Generate Android project from template
    await generateAndroidProject(workDir, params.projectData);

    // Run Gradle bundleRelease
    const keystorePath = process.env.KEYSTORE_PATH!;
    const keystorePass = process.env.KEYSTORE_PASSWORD!;
    const keyAlias = process.env.KEY_ALIAS!;
    const keyPass = process.env.KEY_PASSWORD!;

    await execFileAsync('./gradlew', [
      'bundleRelease',
      `-Pandroid.injected.signing.store.file=${keystorePath}`,
      `-Pandroid.injected.signing.store.password=${keystorePass}`,
      `-Pandroid.injected.signing.key.alias=${keyAlias}`,
      `-Pandroid.injected.signing.key.password=${keyPass}`,
      '--no-daemon',
    ], {
      cwd: workDir,
      timeout: 8 * 60 * 1000, // 8 minutes max
      env: {
        ...process.env,
        ANDROID_HOME: process.env.ANDROID_HOME,
        JAVA_HOME: process.env.JAVA_HOME,
      },
    });

    // Upload AAB to Firebase Storage
    const aabPath = path.join(workDir, 'app/build/outputs/bundle/release/app-release.aab');
    const downloadUrl = await uploadAabToStorage(aabPath, params.userId, params.projectId, buildId);

    // Mark success
    const completedAt = new Date().toISOString();
    await buildRef.update({ status: 'SUCCESS', downloadUrl, completedAt });
    await projectRef.update({ buildStatus: 'SUCCESS', lastBuildUrl: downloadUrl });

  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Unknown build error';
    const completedAt = new Date().toISOString();
    await buildRef.update({ status: 'FAILED', errorMessage, completedAt });
    await projectRef.update({ buildStatus: 'FAILED' });
  } finally {
    // Cleanup workspace
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => {});
  }
}

/**
 * Generates a minimal Android project structure from project data.
 * In production, use a pre-built template Android project and inject values.
 */
async function generateAndroidProject(
  workDir: string,
  projectData: Record<string, unknown>
): Promise<void> {
  const config = projectData.config as Record<string, unknown>;
  const appName = (config?.appName as string) ?? 'MyApp';
  const packageName = (config?.packageName as string) ?? 'com.example.myapp';
  const primaryColor = (config?.primaryColor as string) ?? '#6C63FF';
  const versionName = (config?.versionName as string) ?? '1.0.0';
  const versionCode = (config?.versionCode as number) ?? 1;

  // In real implementation: copy template project and substitute values
  // For now, write the critical Gradle files
  await fs.mkdir(path.join(workDir, 'app'), { recursive: true });

  const settingsGradle = `rootProject.name = "${appName}"\ninclude(":app")\n`;
  const buildGradle = `
plugins {
    id("com.android.application") version "8.3.2"
    id("org.jetbrains.kotlin.android") version "1.9.23"
}
android {
    namespace = "${packageName}"
    compileSdk = 34
    defaultConfig {
        applicationId = "${packageName}"
        minSdk = 26
        targetSdk = 34
        versionCode = ${versionCode}
        versionName = "${versionName}"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
`;

  await fs.writeFile(path.join(workDir, 'settings.gradle.kts'), settingsGradle);
  await fs.writeFile(path.join(workDir, 'app/build.gradle.kts'), buildGradle);
  await fs.writeFile(path.join(workDir, 'app/proguard-rules.pro'), '# Default ProGuard rules\n');

  // Minimal AndroidManifest
  const manifest = `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:label="${appName}" android:theme="@style/Theme.AppCompat">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>`;

  const srcDir = path.join(workDir, `app/src/main/java/${packageName.replace(/\./g, '/')}`);
  await fs.mkdir(srcDir, { recursive: true });
  await fs.mkdir(path.join(workDir, 'app/src/main/res/values'), { recursive: true });
  await fs.writeFile(path.join(workDir, 'app/src/main/AndroidManifest.xml'), manifest);
}

/**
 * Uploads the built AAB file to Firebase Storage and returns a signed download URL.
 */
async function uploadAabToStorage(
  aabPath: string,
  userId: string,
  projectId: string,
  buildId: string
): Promise<string> {
  const storage = getStorage();
  const bucket = storage.bucket();
  const destination = `builds/${userId}/${projectId}/${buildId}/app-release.aab`;

  await bucket.upload(aabPath, {
    destination,
    metadata: {
      contentType: 'application/octet-stream',
      metadata: {
        userId,
        projectId,
        buildId,
      },
    },
  });

  const [url] = await bucket.file(destination).getSignedUrl({
    action: 'read',
    expires: Date.now() + 7 * 24 * 60 * 60 * 1000, // 7 days
  });

  return url;
}

/**
 * Returns the status of a specific build (only if it belongs to the requesting user).
 */
export async function getBuildStatus(buildId: string, userId: string): Promise<BuildRecord | null> {
  const db = getFirestore();
  const doc = await db.collection('builds').doc(buildId).get();
  if (!doc.exists) return null;

  const data = doc.data() as BuildRecord;
  if (data.userId !== userId) return null; // Security: only owner can view

  return data;
}

/**
 * Lists all builds for a user.
 */
export async function listBuilds(userId: string): Promise<BuildRecord[]> {
  const db = getFirestore();
  const snapshot = await db.collection('builds')
    .where('userId', '==', userId)
    .orderBy('createdAt', 'desc')
    .limit(20)
    .get();

  return snapshot.docs.map(doc => doc.data() as BuildRecord);
}
