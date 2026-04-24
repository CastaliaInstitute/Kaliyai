---
layout: castalia
title: anubis.castalia.institute — DNS & Cloudflare
---

# Custom domain: anubis.castalia.institute

The public [Kali AI / Anubis](index.html) site is published with **GitHub Pages** and uses the hostname **anubis.castalia.institute** with **Cloudflare** in front. Nothing in the Pages build or `docs/` reads or contains API keys; Gemini keys are only in [GitHub secrets / Codespace secrets](codespaces.html) and local, gitignored `.env` files.

## GitHub

- Repository: [CastaliaInstitute/anubis](https://github.com/CastaliaInstitute/anubis)
- The repo includes `docs/CNAME` with `anubis.castalia.institute` so the Pages artifact advertises the hostname.
- **Settings → Pages** — or use **`gh`**: [`./scripts/gh-pages-custom-domain.sh`](https://github.com/CastaliaInstitute/anubis/blob/main/scripts/gh-pages-custom-domain.sh) (sets the custom domain via `PUT /repos/.../pages` with `build_type: workflow`). If the API says **"The certificate does not exist yet"**, set **CNAME in Cloudflare first**, wait for GitHub’s DNS check, then **Enforce HTTPS** in the UI, or re-run the API with `"https_enforced": true` (see script comments). Do not bundle `https_enforced` and other fields in the *first* request before a cert can be issued. Repo **admin** is required for the API.

## Monorepo index (castalia.institute)

The [castalia.institute](https://github.com/CastaliaInstitute/castalia.institute) repo has a [master list of GitHub Pages custom domains](https://github.com/CastaliaInstitute/castalia.institute/blob/main/docs/CASTALIA_GITHUB_PAGES_SETUP.md) (including anubis). **Authoritative DNS for this deployment is Cloudflare,** not AWS Route 53. Add the CNAME below (or run `./scripts/add-anubis-cname-cloudflare.sh` with a **Zone → DNS:Edit** API token). Older “Route 53” docs in the monorepo are legacy for other cutovers; do not use them for the live `castalia.institute` zone.

## Cloudflare (castalia.institute) — this is the live path

Use a **CNAME** record in the [Cloudflare](https://dash.cloudflare.com) zone for `castalia.institute`:

| Type | Name | Target | Proxy |
|------|------|--------|--------|
| CNAME | `anubis` | `castaliainstitute.github.io` | Proxied (orange cloud) |

- Target must be the **user/org Pages hostname** for the org that hosts the site (`castaliainstitute.github.io` for the `CastaliaInstitute` org). If GitHub’s DNS check fails, use the value shown in **Settings → Pages → Custom domain** (often a short verification step the first time).
- **SSL/TLS** mode: **Full (strict)** once the edge cert to GitHub is working.

**API / tokens:** if you use the Cloudflare API to manage DNS, keep the token in your org’s vault (e.g. the same place other Castalia Institute / Castalia.Institute credentials live) — not in this repository.

### Add the CNAME with the API (fastest, repeatable)

1. [Create an API token](https://developers.cloudflare.com/fundamentals/api/get-started/create-token/) with **Zone → DNS: Edit** (zone scope: `castalia.institute`), or a single-zone token with the same permission.
2. In a shell (from the **anubis** repo):
   ```bash
   export CLOUDFLARE_API_TOKEN="…"
   ./scripts/add-anubis-cname-cloudflare.sh
   ```
3. This creates or updates **CNAME** `anubis` → `castaliainstitute.github.io` with **Proxied: on**. If your Wrangler/CLI user only has **zone (read)**, the token must be one with **DNS write** for that zone.
4. Verify: `dig +short anubis.castalia.institute CNAME` (after propagation, often under a minute on Cloudflare).

## Canonical URL

- Jekyll `url` / `baseurl` in `_config.yml` are set to `https://anubis.castalia.institute` with an empty `baseurl` so permalinks match the custom domain.
