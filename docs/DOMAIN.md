---
layout: default
title: anubis.castalia.institute — DNS & Cloudflare
---

# Custom domain: anubis.castalia.institute

The public [Kali AI / Anubis](index.html) site is published with **GitHub Pages** and uses the hostname **anubis.castalia.institute** with **Cloudflare** in front. Nothing in the Pages build or `docs/` reads or contains API keys; Gemini keys are only in [GitHub secrets / Codespace secrets](codespaces.html) and local, gitignored `.env` files.

## GitHub

- Repository: [CastaliaInstitute/anubis](https://github.com/CastaliaInstitute/anubis)
- The repo includes `docs/CNAME` with `anubis.castalia.institute` so the Pages artifact advertises the hostname.
- **Settings → Pages** — in `CastaliaInstitute/anubis` add **Custom domain** `anubis.castalia.institute`, save, then **Enforce HTTPS** once Cloudflare and GitHub show the domain as valid. (A repo with admin rights may be required; the Pages REST `PATCH` call returns 404 if your token is not an org admin.)

## Cloudflare (castalia.institute)

Use a **CNAME** record in the [Cloudflare](https://dash.cloudflare.com) zone for `castalia.institute`:

| Type | Name | Target | Proxy |
|------|------|--------|--------|
| CNAME | `anubis` | `castaliainstitute.github.io` | Proxied (orange cloud) |

- Target must be the **user/org Pages hostname** for the org that hosts the site (`castaliainstitute.github.io` for the `CastaliaInstitute` org). If GitHub’s DNS check fails, use the value shown in **Settings → Pages → Custom domain** (often a short verification step the first time).
- **SSL/TLS** mode: **Full (strict)** once the edge cert to GitHub is working.

**API / tokens:** if you use the Cloudflare API to manage DNS, keep the token in your org’s vault (e.g. the same place other Castalia Institute / Castalia.Institute credentials live) — not in this repository.

## Canonical URL

- Jekyll `url` / `baseurl` in `_config.yml` are set to `https://anubis.castalia.institute` with an empty `baseurl` so permalinks match the custom domain.
