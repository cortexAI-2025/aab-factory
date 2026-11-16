import { execFile } from 'child_process';
import { promisify } from 'util';
import * as fs from 'fs/promises';
import path from 'path';
import { createHash } from 'crypto';

const run = promisify(execFile);
export function assertSigningConfiguration(type: 'system' | 'user'): void {
  if (type !== 'system') throw new Error('User keystore import is not implemented; no fallback to a system key');
  if (process.env.SIGNING_AUTHORIZED !== 'true') throw new Error('Owner-authorized signing is required');
  for (const key of ['KEYSTORE_PATH', 'KEYSTORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD', 'AAB_BUILDER_IMAGE', 'BUNDLETOOL_JAR']) {
    if (!process.env[key]) throw new Error(`Missing build configuration: ${key}`);
  }
  if (!path.isAbsolute(process.env.KEYSTORE_PATH!)) throw new Error('KEYSTORE_PATH must be absolute');
}

/** The generated project never runs in the credential-bearing API process. */
export async function buildInSandbox(workDir: string, buildId: string): Promise<void> {
  const name = `aab-${buildId}`;
  // A dedicated uid owns the generated tree; credentials remain on the API host.
  if (process.getuid!() === 0) {
    const own = async (dir: string): Promise<void> => {
      for (const entry of await fs.readdir(dir, {withFileTypes:true})) {
        const file = path.join(dir, entry.name);
        if (entry.isDirectory()) await own(file);
        await fs.chown(file, 1000, 1000);
      }
      await fs.chown(dir, 1000, 1000);
    };
    await own(workDir);
  } else if (process.getuid!() !== 1000) throw new Error('Build host must prepare uid 1000 workspaces');
  let diskExceeded = false;
  const size = async (dir: string): Promise<number> => {
    let bytes = 0;
    for (const e of await fs.readdir(dir, {withFileTypes:true})) {
      const file = path.join(dir, e.name);
      if (e.isDirectory()) bytes += await size(file);
      else if (e.isFile()) bytes += (await fs.lstat(file)).size;
    }
    return bytes;
  };
  let checking = false;
  const watchdog = setInterval(async () => {
    if (checking) return;
    checking = true;
    try {
      if (await size(workDir) > 4 * 1024 ** 3) {
        diskExceeded = true;
        await run('docker', ['rm', '--force', name], {timeout:15_000, env:{PATH:process.env.PATH}});
      }
    } catch { /* container exit/cleanup may race the watchdog */ }
    finally { checking = false; }
  }, 1000);
  try {
    await run('docker', ['run', '--rm', '--name', name,
      '--user', '1000:1000', '--read-only', '--cap-drop=ALL',
      '--security-opt=no-new-privileges:true', '--init', '--memory=4g', '--memory-swap=4g',
      '--cpus=2', '--pids-limit=512', '--network=none',
      '--ulimit', 'fsize=2147483648:2147483648', '--tmpfs', '/tmp:size=2g,exec,mode=1777',
      '--volume', `${workDir}:/workspace:rw`, '--workdir', '/workspace',
      '--env', 'HOME=/tmp/home', '--env', 'GRADLE_USER_HOME=/tmp/gradle',
      '--entrypoint', '/bin/sh', process.env.AAB_BUILDER_IMAGE!,
      '-c', 'exec ./gradlew bundleRelease --no-daemon --console=plain'], {
      timeout: 8 * 60 * 1000, maxBuffer: 2 * 1024 * 1024,
      env: { PATH: process.env.PATH },
    });
    if (diskExceeded) throw new Error('Build disk limit exceeded');
  } catch {
    // Do not persist execFile's error: it may include source-controlled log data.
    throw new Error('Isolated AAB build failed; check worker prerequisites and project configuration');
  } finally {
    clearInterval(watchdog);
    await run('docker', ['rm', '--force', name], {timeout: 15_000, env: {PATH: process.env.PATH}}).catch(() => {});
  }
}

/** Trusted signing happens after the untrusted container is destroyed. */
export async function signAndVerifyAab(file: string): Promise<string> {
  if (path.extname(file) !== '.aab') throw new Error('AAB Factory produces AAB only');
  const stat = await fs.lstat(file);
  if (!stat.isFile() || stat.isSymbolicLink() || stat.size === 0 || stat.size > 200 * 1024 * 1024) throw new Error('Invalid AAB artifact');
  const env = { PATH: process.env.PATH, JAVA_HOME: process.env.JAVA_HOME, LANG: 'C', LC_ALL: 'C',
    AAB_STORE_PASS: process.env.KEYSTORE_PASSWORD, AAB_KEY_PASS: process.env.KEY_PASSWORD };
  try {
    await run('jarsigner', ['-keystore', process.env.KEYSTORE_PATH!, '-storepass:env', 'AAB_STORE_PASS',
      '-keypass:env', 'AAB_KEY_PASS', file, process.env.KEY_ALIAS!], {timeout: 60_000, env});
    const result = await run('jarsigner', ['-J-Duser.language=en', '-verify', '-verbose', file], {timeout: 60_000, env: {PATH: process.env.PATH, JAVA_HOME: process.env.JAVA_HOME, LANG: 'C'}});
    const output = result.stdout + result.stderr;
    if (!output.includes('jar verified.') || /unsigned entries|jar is unsigned/i.test(output)) throw new Error('Invalid signature');
    await run('java', ['-jar', process.env.BUNDLETOOL_JAR!, 'validate', `--bundle=${file}`], {timeout: 60_000, env: {PATH: process.env.PATH, JAVA_HOME: process.env.JAVA_HOME}});
  } catch { throw new Error('AAB signing or verification failed; artifact withheld'); }
  return createHash('sha256').update(await fs.readFile(file)).digest('hex');
}
