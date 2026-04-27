-- Demo affiliate: Aurnova (Castalia Aurnova MSAI / Cybersecurity AI). Slug must match
-- DNS + KV: aurnova.affiliates.castalia.institute and org:aurnova in affiliates-edge.
-- Add user memberships in the app (service role or first-owner RPC); no auth.users in seeds.

insert into public.affiliate_organizations (slug, name, status, metadata)
values
  (
    'aurnova',
    'Aurnova',
    'active',
    jsonb_build_object(
      'demo', true,
      'program', 'Aurnova MSAI',
      'concentration', 'Cybersecurity AI'
    )
  )
on conflict (slug) do update
set
  name = excluded.name,
  status = excluded.status,
  metadata = public.affiliate_organizations.metadata || excluded.metadata,
  updated_at = now();

comment on table public.affiliate_organizations is
  'Affiliate partner org; slug matches DNS (e.g. aurnova in aurnova.affiliates.castalia.institute).';
