-- Affiliate orgs (DNS segment under *.affiliates.castalia.institute) + user ↔ org membership.
-- Assumes Supabase Auth (auth.users). Safe if public.profiles exists; will add default_affiliate_id there.
-- Apply with: supabase db push  (or paste into SQL editor)

-- ---------------------------------------------------------------------------
-- 1) Tables
-- ---------------------------------------------------------------------------

create table if not exists public.affiliate_organizations (
  id uuid primary key default gen_random_uuid (),
  slug text not null
    check (
      slug = lower (slug)
      and slug ~ '^[a-z0-9]([a-z0-9._-]*[a-z0-9])?$' :: text
    ),
  name text not null,
  status text not null default 'active'
    check (status in ('active', 'suspended', 'archived')),
  metadata jsonb not null default '{}',
  created_at timestamptz not null default now (),
  updated_at timestamptz not null default now (),
  constraint affiliate_organizations_slug_key unique (slug)
);

create index if not exists idx_affiliate_organizations_status on public.affiliate_organizations (status);

create table if not exists public.affiliate_memberships (
  id uuid primary key default gen_random_uuid (),
  user_id uuid not null references auth.users (id) on delete cascade,
  affiliate_id uuid not null references public.affiliate_organizations (id) on delete cascade,
  role text not null default 'member'
    check (role in ('owner', 'admin', 'editor', 'member', 'viewer')),
  created_at timestamptz not null default now (),
  unique (user_id, affiliate_id)
);

create index if not exists idx_affiliate_memberships_user on public.affiliate_memberships (user_id);
create index if not exists idx_affiliate_memberships_affiliate on public.affiliate_memberships (affiliate_id);

-- Optional: one preferred org for the signed-in user in profile UI
do $profiles_default$
begin
  if
    exists (
      select
        1
      from
        information_schema.tables
      where
        table_schema = 'public' and
        table_name = 'profiles'
    )
  then
    if not exists (
      select
        1
      from
        information_schema.columns
      where
        table_schema = 'public' and
        table_name = 'profiles' and
        column_name = 'default_affiliate_id'
    ) then
      alter table public.profiles
        add column default_affiliate_id uuid references public.affiliate_organizations (id) on delete set null;
    end if;
  end if;
end;
$profiles_default$;

-- ---------------------------------------------------------------------------
-- 2) updated_at (reuse common trigger name if you already have it)
-- ---------------------------------------------------------------------------

create or replace function public.aff_organizations_touch_updated_at()
returns trigger
language plpgsql
as $fn$
begin
  new.updated_at = now();
  return new;
end;
$fn$;

drop trigger if exists trg_affiliate_organizations_updated on public.affiliate_organizations;
create trigger trg_affiliate_organizations_updated
  before update on public.affiliate_organizations
  for each row
  execute procedure public.aff_organizations_touch_updated_at();

-- ---------------------------------------------------------------------------
-- 3) RLS
-- ---------------------------------------------------------------------------

alter table public.affiliate_organizations enable row level security;
alter table public.affiliate_memberships enable row level security;

-- Orgs: members (any role) can read; owner/admin can update; insert delete via service or elevated policy
drop policy if exists "affiliate_orgs_select_member" on public.affiliate_organizations;
create policy "affiliate_orgs_select_member" on public.affiliate_organizations
  for select
  to authenticated
  using (
    exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_organizations.id and
        m.user_id = (select auth.uid())
    )
  );

drop policy if exists "affiliate_orgs_update_admins" on public.affiliate_organizations;
create policy "affiliate_orgs_update_admins" on public.affiliate_organizations
  for update
  to authenticated
  using (
    exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_organizations.id and
        m.user_id = (select auth.uid()) and
        m.role in ('owner', 'admin')
    )
  )
  with check (
    exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_organizations.id and
        m.user_id = (select auth.uid()) and
        m.role in ('owner', 'admin')
    )
  );

-- Memberships: every member can list everyone in the same orgs they belong to; users always see their own row
drop policy if exists "affiliate_memberships_select" on public.affiliate_memberships;
create policy "affiliate_memberships_select" on public.affiliate_memberships
  for select
  to authenticated
  using (
    user_id = (select auth.uid())
    or exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_memberships.affiliate_id and
        m.user_id = (select auth.uid())
    )
  );

-- Admins/owners can insert and delete memberships (invite/remove); editors cannot by default
drop policy if exists "affiliate_memberships_write_admins" on public.affiliate_memberships;
create policy "affiliate_memberships_write_admins" on public.affiliate_memberships
  for all
  to authenticated
  using (
    exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_memberships.affiliate_id and
        m.user_id = (select auth.uid()) and
        m.role in ('owner', 'admin')
    )
  )
  with check (
    exists (
      select
        1
      from
        public.affiliate_memberships m
      where
        m.affiliate_id = affiliate_memberships.affiliate_id and
        m.user_id = (select auth.uid()) and
        m.role in ('owner', 'admin')
    )
  );

-- No insert into affiliate_organizations from clients without a separate policy: use service role
--   or add a "bootstrap" function. Uncomment if you want authenticated users to create a draft org:
-- create policy "affiliate_orgs_insert_authenticated" on public.affiliate_organizations
--   for insert to authenticated with check ( true );

-- ---------------------------------------------------------------------------
-- 4) Grants (new tables in migrations do not get dashboard defaults; RLS still applies)
-- ---------------------------------------------------------------------------

grant select, update on public.affiliate_organizations to authenticated;
grant select, insert, update, delete on public.affiliate_memberships to authenticated;

-- Service role: bootstrap orgs + first owner row (bypasses RLS only if policies allow; service_role bypasses RLS in API)
grant all on public.affiliate_organizations to service_role;
grant all on public.affiliate_memberships to service_role;

-- Optional comment for app authors
comment on table public.affiliate_memberships is
  'Links auth.users to affiliate_organizations; role drives RLS and admin UIs.';
