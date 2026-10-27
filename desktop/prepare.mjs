import { createHash } from 'node:crypto';
import { execFileSync, spawnSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, rmSync, copyFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const destination = path.join(root, 'build', 'desktop', 'win32-x64');

function javaHome() {
  const java = process.platform === 'win32' ? 'java.exe' : 'java';
  const javac = process.platform === 'win32' ? 'javac.exe' : 'javac';
  const candidates = [process.env.JAVA_HOME, process.env.JDK_HOME, '/usr/lib/jvm/default-java', '/usr/lib/jvm/java-21-openjdk-amd64'];
  const settings = spawnSync(java, ['-XshowSettings:properties', '-version'], { encoding: 'utf8' }).stderr || '';
  candidates.push(settings.match(/java\.home = (.+)/)?.[1]?.trim());
  return candidates.find(home => {
    if (!home || !existsSync(path.join(home, 'bin', javac))) return false;
    const result = spawnSync(path.join(home, 'bin', java), ['-version'], { encoding: 'utf8' });
    return result.status === 0 && /version "21[.\"]/.test(result.stderr);
  });
}

async function prepareRuntime() {
  const runtime = path.join(destination, 'runtime');
  if (existsSync(path.join(runtime, 'bin', 'java.exe'))) return;

  const response = await fetch('https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jre&os=windows&vendor=eclipse');
  if (!response.ok) throw new Error(`Adoptium: HTTP ${response.status}`);
  const asset = (await response.json())[0]?.binary?.package;
  if (!asset) throw new Error('Нет Java 21 для Windows x64');

  const cache = path.join(root, 'build', 'desktop', 'downloads');
  mkdirSync(cache, { recursive: true });
  const archive = path.join(cache, asset.name);
  if (!existsSync(archive)) {
    console.log(`Загружаем Java 21: ${asset.name}`);
    const download = await fetch(asset.link);
    if (!download.ok) throw new Error(`Загрузка Java: HTTP ${download.status}`);
    writeFileSync(archive, Buffer.from(await download.arrayBuffer()));
  }
  if (createHash('sha256').update(readFileSync(archive)).digest('hex') !== asset.checksum) {
    rmSync(archive);
    throw new Error('Контрольная сумма Java не совпала. Повторите подготовку.');
  }

  const unpacked = path.join(destination, 'unpacked');
  rmSync(unpacked, { recursive: true, force: true });
  mkdirSync(unpacked, { recursive: true });
  const useUnzip = process.platform !== 'win32';
  const unpacker = useUnzip ? 'unzip' : path.join(process.env.SystemRoot, 'System32', 'tar.exe');
  execFileSync(unpacker,
    useUnzip ? ['-q', archive, '-d', unpacked] : ['-xf', archive, '-C', unpacked], { stdio: 'inherit' });
  const folder = readdirSync(unpacked, { withFileTypes: true }).find(entry => entry.isDirectory());
  if (!folder) throw new Error('Архив Java не содержит runtime');
  rmSync(runtime, { recursive: true, force: true });
  renameSync(path.join(unpacked, folder.name), runtime);
  rmSync(unpacked, { recursive: true });
}

try {
  const home = javaHome();
  if (!home) throw new Error('Для сборки нужен JDK 21. Укажите его каталог в JAVA_HOME.');
  const options = { cwd: root, stdio: 'inherit', env: { ...process.env, JAVA_HOME: home } };
  if (process.platform === 'win32') {
    execFileSync('cmd.exe', ['/d', '/s', '/c', 'gradlew.bat --no-daemon --max-workers=2 :penalty-calculation-service:bootJar'], options);
  } else {
    execFileSync(path.join(root, 'gradlew'), ['--no-daemon', '--max-workers=2', ':penalty-calculation-service:bootJar'], options);
  }
  mkdirSync(path.join(destination, 'backend'), { recursive: true });
  copyFileSync(path.join(root, 'penalty-calculation-service', 'build', 'libs', 'app.jar'), path.join(destination, 'backend', 'app.jar'));
  await prepareRuntime();
  console.log(`Готово: ${path.relative(root, destination)}`);
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
