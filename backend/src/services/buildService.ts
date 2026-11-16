import path from 'path';
import fs from 'fs/promises';
import { v4 as uuidv4 } from 'uuid';
import { getFirestore, getStorage } from '../config/firebase';
import admin from 'firebase-admin';

import { assertSigningConfiguration, buildInSandbox, signAndVerifyAab } from './build-security';
let activeBuilds = 0;

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
  sha256?: string;
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
  // Reject unsupported key imports and implicit system signing before charging quota.
  assertSigningConfiguration(params.keystoreType);
  if (activeBuilds >= 1) throw new Error('Build capacity reached; retry after the current build');
  activeBuilds++;
  try {
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
      console.error(`Build ${buildId} failed: ${err instanceof Error ? err.name : "BuildError"}`);
    }).finally(() => { activeBuilds--; });

    return { buildId, status: 'QUEUED', estimatedMinutes, queuePosition };
  } catch (error) { activeBuilds--; throw error; }
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

  const baseDir = process.env.AAB_WORKSPACE_DIR || '/tmp/aab-builds';
  await fs.mkdir(baseDir, { recursive: true, mode: 0o700 });
  const workDir = await fs.mkdtemp(path.join(baseDir, buildId + '-'));

  let signingDir: string | undefined;
  try {
    // Mark as building
    await buildRef.update({ status: 'BUILDING', updatedAt: new Date().toISOString() });
    await projectRef.update({ buildStatus: 'BUILDING' });

    // Create build workspace
    await fs.mkdir(workDir, { recursive: true });

    // Generate Android project from template
    await generateAndroidProject(workDir, params.projectData);

    // No signing, Firebase, Stripe or AI credentials enter the build container.
    await buildInSandbox(workDir, buildId);

    // Upload AAB to Firebase Storage
    const aabPath = path.join(workDir, 'app/build/outputs/bundle/release/app-release.aab');
    // Seal the untrusted output after container destruction. Never sign a path
    // whose parent was replaced by a symlink inside the imported workspace.
    const resolved = await fs.realpath(aabPath);
    const workspace = await fs.realpath(workDir);
    if (!resolved.startsWith(workspace + path.sep) || (await fs.lstat(aabPath)).isSymbolicLink()) {
      throw new Error('AAB escapes the isolated workspace');
    }
    signingDir = await fs.mkdtemp(path.join('/tmp', 'aab-signing-'));
    const sealedAab = path.join(signingDir, 'app-release.aab');
    await fs.copyFile(resolved, sealedAab);
    await fs.chmod(sealedAab, 0o600);
    const sha256 = await signAndVerifyAab(sealedAab);
    const downloadUrl = await uploadAabToStorage(sealedAab, params.userId, params.projectId, buildId);

    // Mark success
    const completedAt = new Date().toISOString();
    await buildRef.update({ status: 'SUCCESS', downloadUrl, sha256, completedAt });
    await projectRef.update({ buildStatus: 'SUCCESS', lastBuildUrl: downloadUrl });

  } catch (err) {
    const errorMessage = err instanceof Error ? err.message : 'Unknown build error';
    const completedAt = new Date().toISOString();
    await buildRef.update({ status: 'FAILED', errorMessage, completedAt });
    await projectRef.update({ buildStatus: 'FAILED' });
  } finally {
    if (signingDir) await fs.rm(signingDir, {recursive: true, force: true}).catch(() => {});
    // Cleanup workspace
    await fs.rm(workDir, { recursive: true, force: true }).catch(() => {});
  }
}

/**
 * Generates a self-contained Android project ready for `./gradlew bundleRelease`.
 *
 * Writes every file Gradle requires so the build worker never needs a pre-cloned
 * base project on disk:
 *   - gradlew script (chmod +x)
 *   - gradle/wrapper/gradle-wrapper.properties
 *   - root build.gradle.kts + settings.gradle.kts
 *   - app/build.gradle.kts + proguard-rules.pro
 *   - AndroidManifest.xml
 *   - MainActivity.kt (minimal Activity so Kotlin compiles)
 *   - res/values/strings.xml + colors.xml
 *
 * NOTE: gradle-wrapper.jar cannot be generated — it must be present on the
 * build server at GRADLE_WRAPPER_JAR_PATH (default: /opt/gradle-wrapper.jar).
 * Set that env var to the path of a cached wrapper jar on your CI/CD image.
 */
export async function generateAndroidProject(
  workDir: string,
  projectData: Record<string, unknown>
): Promise<void> {
  const config = projectData.config as Record<string, unknown>;
  const appName    = sanitizeString(config?.appName   as string, 'MyApp');
  const packageName = config?.packageName as string;
  if (typeof packageName !== 'string' || !/^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*){2,}$/.test(packageName)) {
    throw new Error('Invalid Android package name');
  }
  const primaryColor = sanitizeColor(config?.primaryColor as string, '#6C63FF');
  const accentColor  = sanitizeColor(config?.accentColor  as string, '#00D4AA');
  const versionName  = sanitizeString(config?.versionName as string, '1.0.0');
  const minSdk = Number(config?.minSdkVersion ?? 26);
  const targetSdk = Number(config?.targetSdkVersion ?? 34);
  if (!Number.isInteger(minSdk) || !Number.isInteger(targetSdk) || minSdk < 21 || targetSdk > 34 || minSdk > targetSdk) {
    throw new Error('Template image supports Android SDK up to 34; configure another tested toolchain for newer targets');
  }
  const versionCode  = Number.isInteger(config?.versionCode) ? (config.versionCode as number) : 1;

  // ── Directory tree ────────────────────────────────────────────────────────
  const packagePath = packageName.replace(/\./g, '/');
  const srcDir      = path.join(workDir, `app/src/main/java/${packagePath}`);
  const resDir      = path.join(workDir, 'app/src/main/res/values');
  const wrapperDir  = path.join(workDir, 'gradle/wrapper');

  await Promise.all([
    fs.mkdir(srcDir,     { recursive: true }),
    fs.mkdir(resDir,     { recursive: true }),
    fs.mkdir(wrapperDir, { recursive: true }),
  ]);

  // ── gradlew shell script ──────────────────────────────────────────────────
  const gradlewScript = `#!/bin/sh
# Gradle start-up script generated by AAB Factory build worker
CLASSPATH="gradle/wrapper/gradle-wrapper.jar"
exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
`;
  const gradlewPath = path.join(workDir, 'gradlew');
  await fs.writeFile(gradlewPath, gradlewScript);
  await fs.chmod(gradlewPath, 0o755);

  // ── Copy gradle-wrapper.jar from the server cache ─────────────────────────
  const wrapperJarSrc = process.env.GRADLE_WRAPPER_JAR_PATH ?? '/opt/gradle-wrapper.jar';
  const wrapperJarDst = path.join(wrapperDir, 'gradle-wrapper.jar');
  try {
    await fs.copyFile(wrapperJarSrc, wrapperJarDst);
  } catch {
    throw new Error(
      `gradle-wrapper.jar not found at ${wrapperJarSrc}. ` +
      'Set GRADLE_WRAPPER_JAR_PATH to the cached jar on the build server.'
    );
  }

  // ── gradle-wrapper.properties ─────────────────────────────────────────────
  await fs.writeFile(path.join(wrapperDir, 'gradle-wrapper.properties'), [
    'distributionBase=GRADLE_USER_HOME',
    'distributionPath=wrapper/dists',
    'distributionUrl=https\\://services.gradle.org/distributions/gradle-8.6-bin.zip',
    'networkTimeout=10000',
    'validateDistributionUrl=true',
    'zipStoreBase=GRADLE_USER_HOME',
    'zipStorePath=wrapper/dists',
  ].join('\n'));

  // ── Root build.gradle.kts ─────────────────────────────────────────────────
  await fs.writeFile(path.join(workDir, 'build.gradle.kts'), `
plugins {
    id("com.android.application") version "8.3.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.23" apply false
}
`.trimStart());

  // ── settings.gradle.kts ──────────────────────────────────────────────────
  await fs.writeFile(path.join(workDir, 'settings.gradle.kts'), `
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "${escapeKotlinString(appName)}"
include(":app")
`.trimStart());

  // ── app/build.gradle.kts ─────────────────────────────────────────────────
  await fs.writeFile(path.join(workDir, 'app/build.gradle.kts'), `
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "${packageName}"
    compileSdk = ${targetSdk}
    defaultConfig {
        applicationId = "${packageName}"
        minSdk = ${minSdk}
        targetSdk = ${targetSdk}
        versionCode = ${versionCode}
        versionName = "${versionName}"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
`.trimStart());

  await fs.writeFile(path.join(workDir, 'app/proguard-rules.pro'),
    '-keepattributes SourceFile,LineNumberTable\n');

  // ── AndroidManifest.xml ───────────────────────────────────────────────────
  await fs.writeFile(path.join(workDir, 'app/src/main/AndroidManifest.xml'), `<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="@string/app_name"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>`);

  // ── MainActivity.kt ───────────────────────────────────────────────────────
  await fs.writeFile(path.join(srcDir, 'MainActivity.kt'), `package ${packageName}

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ScrollView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24,24,24,24) }
${renderSections(projectData)}
        setContentView(ScrollView(this).apply { addView(content) })
    }
}
`);

  // ── res/values/strings.xml ────────────────────────────────────────────────
  await fs.writeFile(path.join(resDir, 'strings.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">${escapeXmlString(appName)}</string>
</resources>`);

  // ── res/values/colors.xml ─────────────────────────────────────────────────
  await fs.writeFile(path.join(resDir, 'colors.xml'), `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">${primaryColor}</color>
    <color name="accent">${accentColor}</color>
</resources>`);
}

/** Strips characters that would break Kotlin string literals or shell injection. */
function sanitizeString(value: string | undefined, fallback: string): string {
  if (!value || typeof value !== 'string') return fallback;
  return value.replace(/[^\w\s\-_.]/g, '').trim() || fallback;
}

/** Validates a hex colour; returns fallback if invalid. */
function sanitizeColor(value: string | undefined, fallback: string): string {
  if (value && /^#[0-9A-Fa-f]{6}$/.test(value)) return value;
  return fallback;
}

/** Escapes special characters for Kotlin string literals. */
function escapeKotlinString(s: string): string {
  return s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\$/g, '\\$').replace(/\r/g, '\\r').replace(/\n/g, '\\n');
}

/** Escapes special characters for XML text content. */
function escapeXmlString(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&apos;');
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

/** Basic text rendering; interactive sections still require dedicated renderers. */
function renderSections(project: Record<string, unknown>): string {
  const sections = Array.isArray(project.sections) ? project.sections : [];
  return sections.filter(s => s && s.isVisible !== false).slice(0, 100).flatMap(s =>
    [s.title, s.subtitle, s.content].filter(v => typeof v === 'string' && v.length > 0).map(v =>
      `        content.addView(TextView(this).apply { text = "${escapeKotlinString(v.slice(0, 4000))}"; textSize = 18f })`
    )
  ).join('\n');
}
