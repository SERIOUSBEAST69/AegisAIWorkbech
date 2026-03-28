import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const includeRoots = [
  'backend/src/main/java',
  'backend/src/main/resources',
  'src',
  'electron',
  'python-service',
];

const includeExt = new Set(['.java', '.kt', '.js', '.ts', '.vue', '.py', '.yml', '.yaml', '.properties']);
const ignoreDirs = new Set(['node_modules', 'target', 'dist', '.git']);

const riskyPatterns = [
  /\b(log|logger)\.(debug|info|warn|error|trace)\s*\([^\n]*(password|passwd|token|secret|authorization|cookie|jwt)/i,
  /console\.(log|debug|info|warn|error)\s*\([^\n]*(password|passwd|token|secret|authorization|cookie|jwt)/i,
  /print\s*\([^\n]*(password|passwd|token|secret|authorization|cookie|jwt)/i,
];

const allowPatterns = [
  /\*{3,}/,
  /masked/i,
  /redact/i,
  /sanit/i,
  /example/i,
  /dummy/i,
];

function walk(dir, files = []) {
  if (!fs.existsSync(dir)) return files;
  for (const item of fs.readdirSync(dir, { withFileTypes: true })) {
    if (ignoreDirs.has(item.name)) continue;
    const full = path.join(dir, item.name);
    if (item.isDirectory()) {
      walk(full, files);
    } else if (includeExt.has(path.extname(item.name))) {
      files.push(full);
    }
  }
  return files;
}

const findings = [];
for (const relRoot of includeRoots) {
  const absRoot = path.join(root, relRoot);
  const files = walk(absRoot);
  for (const file of files) {
    const content = fs.readFileSync(file, 'utf8');
    const lines = content.split(/\r?\n/);
    lines.forEach((line, i) => {
      const matchesRisk = riskyPatterns.some((p) => p.test(line));
      if (!matchesRisk) return;
      const allowed = allowPatterns.some((p) => p.test(line));
      if (allowed) return;
      findings.push({
        file: path.relative(root, file).replace(/\\/g, '/'),
        line: i + 1,
        text: line.trim().slice(0, 220),
      });
    });
  }
}

if (findings.length > 0) {
  console.error('Sensitive logging violations detected:');
  for (const finding of findings) {
    console.error(`- ${finding.file}:${finding.line} ${finding.text}`);
  }
  process.exit(1);
}

console.log('Sensitive logging scan passed.');
