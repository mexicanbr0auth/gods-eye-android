#!/usr/bin/env node
/**
 * Sincroniza dist/ -> android/app/src/main/assets/dist
 * Usado pelo build Android e pelo GitHub Actions.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, '..');
const dist = path.join(root, 'dist');
const target = path.join(root, 'android/app/src/main/assets/dist');

if (!fs.existsSync(dist)) {
  console.error(`[sync-android] dist/ não encontrado em ${dist}. Rode 'npm run build' primeiro.`);
  process.exit(1);
}

fs.rmSync(target, { recursive: true, force: true });
fs.mkdirSync(path.dirname(target), { recursive: true });

function copyRecursive(src, dest) {
  const stat = fs.statSync(src);
  if (stat.isDirectory()) {
    fs.mkdirSync(dest, { recursive: true });
    for (const entry of fs.readdirSync(src)) {
      copyRecursive(path.join(src, entry), path.join(dest, entry));
    }
  } else {
    fs.mkdirSync(path.dirname(dest), { recursive: true });
    fs.copyFileSync(src, dest);
  }
}

copyRecursive(dist, target);
console.log(`[sync-android] OK: ${dist} -> ${target}`);

// Garante que file:///android_asset/dist/index.html exista
const index = path.join(target, 'index.html');
if (!fs.existsSync(index)) {
  console.error(`[sync-android] index.html não encontrado em ${index}`);
  process.exit(1);
}
