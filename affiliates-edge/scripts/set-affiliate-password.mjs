#!/usr/bin/env node
/**
 * Set one affiliate password in KV (PBKDF2-SHA-256, 100k iters — must match src/index.ts).
 * Usage: AFFILIATE_KV_ID=xxx npm run set-password -- <org-slug> <plain-password>
 *    or:  npm run set-password -- <org-slug> <plain-password>   (reads id from wrangler.toml)
 * Requires: wrangler CLI on PATH, logged in, or CLOUDFLARE_API_TOKEN in env.
 */
import { randomBytes, pbkdf2Sync, createHash } from "node:crypto";
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { execFileSync } from "node:child_process";
import { createRequire } from "node:module";

const ITER = 100_000;
const KEY_LEN = 32;
const __dirname = dirname(fileURLToPath(import.meta.url));

const args = process.argv.slice(2).filter((a) => a !== "--");
if (args.length < 2) {
  console.error("Usage: npm run set-password -- <org-slug> <password>");
  process.exit(1);
}

const [org, password] = args;
if (!/^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$/i.test(org)) {
  console.error("org-slug must be a simple label (letters, numbers, - _ .) ");
  process.exit(1);
}

const nsId = process.env.AFFILIATE_KV_ID || readKvIdFromWrangler();
if (!nsId || nsId === "replace-with-kv-namespace-id") {
  console.error("Set AFFILIATE_KV_ID or put your KV namespace id in wrangler.toml (id = \"...\").");
  process.exit(1);
}

const salt = randomBytes(16);
const hash = pbkdf2Sync(password, salt, ITER, KEY_LEN, "sha256");
const record = {
  v: "p256-100k-v1",
  salt: salt.toString("base64"),
  hash: hash.toString("base64"),
};

const key = "org:" + org.toLowerCase();
const value = JSON.stringify(record);

const root = join(__dirname, "..");
const require = createRequire(join(root, "package.json"));
let wranglerBin;
try {
  wranglerBin = require.resolve("wrangler/bin/wrangler.js", { paths: [root] });
} catch {
  console.error("Run: npm install (in affiliates-edge/)");
  process.exit(1);
}

execFileSync(process.execPath, [wranglerBin, "kv", "key", "put", "--namespace-id", nsId, key, value], {
  stdio: "inherit",
  cwd: root,
});
const fp = createHash("sha256").update(hash).digest("hex").slice(0, 12);
console.log(`OK: KV key ${key} (password not logged; hash fingerprint: ${fp}...)`);

function readKvIdFromWrangler() {
  const p = join(root, "wrangler.toml");
  if (!existsSync(p)) return "";
  const t = readFileSync(p, "utf8");
  const parts = t.split("[[kv_namespaces]]");
  for (const block of parts) {
    if (!/binding\s*=\s*"AFFILIATE_KV"/.test(block)) continue;
    const m = block.match(/id\s*=\s*"([^"]+)"/);
    if (m && m[1].length > 20 && !m[1].includes("replace")) return m[1];
  }
  return "";
}
