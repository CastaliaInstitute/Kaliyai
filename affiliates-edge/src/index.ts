/**
 * Edge auth for {course}.{org}.affiliates.castalia.institute (and {org}.affiliates only).
 * One org password: course hostnames under the same org share the KV key org:{slug}.
 * KV: PBKDF2-SHA-256 hashes. Session cookie to skip repeat Basic auth.
 */

const SUFFIX = "affiliates.castalia.institute";
const COOKIE = "affiliates_session";
const ITER = 100_000;
const SESSION_DAYS = 7;
const AUTH_KEY_PREFIX = "org:";

type AuthRecord = { v: "p256-100k-v1"; salt: string; hash: string };

type Env = { AFFILIATE_KV: KVNamespace; SESSION_HMAC_KEY: string };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const host = new URL(request.url).hostname.toLowerCase();

    const org = orgSlugFromHost(host);
    if (!org) {
      return new Response("Not an affiliates host", { status: 404, headers: { "content-type": "text/plain; charset=utf-8" } });
    }

    const rec = await readAuthRecord(env.AFFILIATE_KV, org);
    if (!rec) {
      return new Response(
        "This affiliate is not registered yet. Set a password: cd affiliates-edge && npm run set-password (see package.json).",
        { status: 503, headers: { "content-type": "text/plain; charset=utf-8" } }
      );
    }

    if (rec.v !== "p256-100k-v1" || !rec.salt || !rec.hash) {
      return new Response("Invalid auth record", { status: 500, headers: { "content-type": "text/plain; charset=utf-8" } });
    }

    if (await validSessionAsync(request, org, env.SESSION_HMAC_KEY)) {
      return fetch(request);
    }

    const creds = basicCredentials(request);
    if (creds) {
      const ok = await verifyPassword(creds.password, rec);
      if (ok) {
        const r = await fetch(request);
        return withSession(r, org, env.SESSION_HMAC_KEY, new URL(request.url));
      }
    }

    return unauthorized();
  },
};

function orgSlugFromHost(host: string): string | null {
  if (!host.endsWith(SUFFIX) || host.length < SUFFIX.length + 2) return null;
  if (host[host.length - SUFFIX.length - 1] !== ".") return null;
  const parts = host.split(".");
  const i = parts.indexOf("affiliates");
  if (i < 1) return null;
  return parts[i - 1]!.toLowerCase();
}

function basicCredentials(request: Request): { user: string; password: string } | null {
  const h = request.headers.get("Authorization");
  if (!h || !h.toLowerCase().startsWith("basic ")) return null;
  let raw: string;
  try {
    raw = atob(h.slice(6).trim());
  } catch {
    return null;
  }
  const c = raw.indexOf(":");
  if (c < 0) return { user: raw, password: "" };
  return { user: raw.slice(0, c), password: raw.slice(c + 1) };
}

function unauthorized(): Response {
  return new Response("Authentication required for this affiliate site.", {
    status: 401,
    headers: {
      "www-authenticate": 'Basic realm="affiliate", charset="UTF-8"',
      "content-type": "text/plain; charset=utf-8",
    },
  });
}

async function readAuthRecord(kv: KVNamespace, org: string): Promise<AuthRecord | null> {
  const raw = await kv.get(AUTH_KEY_PREFIX + org, "text");
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthRecord;
  } catch {
    return null;
  }
}

const enc = new TextEncoder();

async function verifyPassword(password: string, rec: AuthRecord): Promise<boolean> {
  const saltB = b64ToBytes(rec.salt);
  const want = b64ToBytes(rec.hash);
  const keyMaterial = await crypto.subtle.importKey("raw", enc.encode(password), { name: "PBKDF2" }, false, ["deriveBits"]);
  const out = new Uint8Array(
    await crypto.subtle.deriveBits(
      { name: "PBKDF2", salt: saltB, iterations: ITER, hash: "SHA-256" },
      keyMaterial,
      256
    )
  );
  if (out.length !== want.length) return false;
  return timingSafeEqualBuf(out, want);
}

function b64ToBytes(b64: string): Uint8Array {
  const bin = atob(b64);
  const a = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) a[i] = bin.charCodeAt(i) & 0xff;
  return a;
}

function timingSafeEqualBuf(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) return false;
  let o = 0;
  for (let i = 0; i < a.length; i++) o |= a[i]! ^ b[i]!;
  return o === 0;
}

function getCookie(request: Request, name: string): string | null {
  const s = request.headers.get("Cookie");
  if (!s) return null;
  for (const p of s.split(";")) {
    const t = p.trim();
    const eq = t.indexOf("=");
    if (eq < 0) continue;
    if (t.slice(0, eq) === name) return decodeURIComponent(t.slice(eq + 1));
  }
  return null;
}

/** payload = base64url( org:exp64 ) + "." + base64url( hmacSha256( secret, payloadBytes ) ) */
async function makeSessionToken(org: string, secret: string): Promise<string> {
  const exp = Math.floor(Date.now() / 1000) + SESSION_DAYS * 86400;
  const head = `${org}:${exp}`;
  const headB = enc.encode(head);
  const p1 = b64u(headB);
  const mKey = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", mKey, headB));
  return `${p1}.${b64u(sig)}`;
}

function b64u(b: Uint8Array): string {
  let s = btoa(String.fromCharCode(...b));
  return s.replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function b64uToBytes(s: string): Uint8Array | null {
  let x = s.replace(/-/g, "+").replace(/_/g, "/");
  const pad = x.length % 4;
  if (pad) x += "====".slice(0, 4 - pad);
  try {
    return b64ToBytes(x);
  } catch {
    return null;
  }
}

async function validSessionAsync(request: Request, org: string, secret: string): Promise<boolean> {
  const t = getCookie(request, COOKIE);
  if (!t) return false;
  return verifySessionToken(t, org, secret);
}

async function verifySessionToken(token: string, org: string, secret: string): Promise<boolean> {
  const dot = token.indexOf(".");
  if (dot < 0) return false;
  const p1 = token.slice(0, dot);
  const p2s = token.slice(dot + 1);
  const headB = b64uToBytes(p1);
  const wantSig = b64uToBytes(p2s);
  if (!headB || !wantSig) return false;
  let head: string;
  try {
    head = new TextDecoder().decode(headB);
  } catch {
    return false;
  }
  const c = head.lastIndexOf(":");
  if (c < 0) return false;
  const o = head.slice(0, c);
  const expN = head.slice(c + 1);
  if (o.toLowerCase() !== org) return false;
  const exp = parseInt(expN, 10);
  if (Number.isNaN(exp) || exp < Math.floor(Date.now() / 1000)) return false;
  const mKey = await crypto.subtle.importKey("raw", enc.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", mKey, headB));
  if (sig.length !== wantSig.length) return false;
  return timingSafeEqualBuf(sig, wantSig);
}

async function withSession(res: Response, org: string, sessionSecret: string, url: URL): Promise<Response> {
  const token = await makeSessionToken(org, sessionSecret);
  const headers = new Headers(res.headers);
  const isSecure = url.protocol === "https:";
  const cookie = [
    `${COOKIE}=${token}`,
    "Path=/",
    "HttpOnly",
    "SameSite=Lax",
    isSecure ? "Secure" : "",
    `Max-Age=${SESSION_DAYS * 86400}`,
  ]
    .filter(Boolean)
    .join("; ");
  headers.append("Set-Cookie", cookie);
  return new Response(res.body, { status: res.status, statusText: res.statusText, headers });
}
