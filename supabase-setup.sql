-- Run this once in Supabase Dashboard > SQL Editor.
-- Creates a private bucket and enforces ownership in both database and storage.

create table if not exists public.java_files (
  id uuid primary key,
  owner_id uuid not null references auth.users(id) on delete cascade,
  file_name text not null check (char_length(file_name) between 1 and 180),
  storage_path text not null unique,
  size_bytes bigint not null check (size_bytes between 0 and 10485760),
  created_at timestamptz not null default now(),
  constraint java_file_extension check (lower(file_name) like '%.java'),
  constraint unique_owner_file_name unique (owner_id, file_name)
);

alter table public.java_files enable row level security;

drop policy if exists "Users can read their own Java files" on public.java_files;
create policy "Users can read their own Java files"
  on public.java_files for select to authenticated
  using (auth.uid() = owner_id);

drop policy if exists "Users can add their own Java files" on public.java_files;
create policy "Users can add their own Java files"
  on public.java_files for insert to authenticated
  with check (auth.uid() = owner_id and split_part(storage_path, '/', 1) = auth.uid()::text);

drop policy if exists "Users can delete their own Java files" on public.java_files;
create policy "Users can delete their own Java files"
  on public.java_files for delete to authenticated
  using (auth.uid() = owner_id);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('java-files', 'java-files', false, 10485760, array['text/plain', 'text/x-java-source', 'application/octet-stream'])
on conflict (id) do update set
  public = false,
  file_size_limit = 10485760,
  allowed_mime_types = array['text/plain', 'text/x-java-source', 'application/octet-stream'];

drop policy if exists "Users can upload Java files into their own folder" on storage.objects;
create policy "Users can upload Java files into their own folder"
  on storage.objects for insert to authenticated
  with check (bucket_id = 'java-files' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users can read Java files in their own folder" on storage.objects;
create policy "Users can read Java files in their own folder"
  on storage.objects for select to authenticated
  using (bucket_id = 'java-files' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "Users can delete Java files in their own folder" on storage.objects;
create policy "Users can delete Java files in their own folder"
  on storage.objects for delete to authenticated
  using (bucket_id = 'java-files' and (storage.foldername(name))[1] = auth.uid()::text);
