const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs/promises');
const path=require('node:path');
const os=require('node:os');
const {assertSigningConfiguration,signAndVerifyAab}=require('../dist/services/build-security');
const {generateAndroidProject}=require('../dist/services/buildService');
test('user keystore never silently falls back to the system key',()=>assert.throws(()=>assertSigningConfiguration('user'),/no fallback/));
test('system signing requires explicit authorization',()=>{ delete process.env.SIGNING_AUTHORIZED; assert.throws(()=>assertSigningConfiguration('system'),/Owner-authorized/); });
test('non-AAB, unsigned and symlink artifacts cannot pass delivery',async t=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'aab-sign-'));t.after(()=>fs.rm(root,{recursive:true,force:true}));
 await assert.rejects(signAndVerifyAab(path.join(root,'wrong.apk')),/AAB only/);
 const file=path.join(root,'unsigned.aab');await fs.writeFile(file,'not an aab');
 await assert.rejects(signAndVerifyAab(file),/withheld/);
 const link=path.join(root,'link.aab');await fs.symlink(file,link);await assert.rejects(signAndVerifyAab(link),/Invalid AAB/);
});
test('generated source preserves visible sections, escapes Kotlin and respects SDK configuration',async t=>{
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'aab-source-'));t.after(()=>fs.rm(root,{recursive:true,force:true}));
 const wrapper=path.join(root,'cached.jar');await fs.writeFile(wrapper,'fixture');process.env.GRADLE_WRAPPER_JAR_PATH=wrapper;
 const project=path.join(root,'project');
 await generateAndroidProject(project,{config:{appName:'Example',packageName:'com.example.test',minSdkVersion:23,targetSdkVersion:34},sections:[{title:'Hello ${Runtime}',content:'Line\nnext',isVisible:true},{title:'Hidden',isVisible:false}]});
 const code=await fs.readFile(path.join(project,'app/src/main/java/com/example/test/MainActivity.kt'),'utf8');
 assert.ok(code.includes('Hello \\${Runtime}'));assert.ok(code.includes('Line\\nnext'));assert.ok(!code.includes('Hidden'));assert.ok(code.includes('setContentView'));
 assert.match(await fs.readFile(path.join(project,'app/build.gradle.kts'),'utf8'),/minSdk = 23/);
 await assert.rejects(generateAndroidProject(path.join(root,'bad'),{config:{packageName:'../escape'}}),/package name/);
});
