CREATE TABLE IF NOT EXISTS agent_task_manager.codex_sessions (
  session_id text PRIMARY KEY,
  user_id text NOT NULL,
  title text NOT NULL,
  client_surface text NOT NULL,
  lifecycle_state text NOT NULL,
  runtime_state text NOT NULL,
  output_release_state text NOT NULL,
  runtime_id text,
  active_turn_id text,
  remotely_resumable boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_session_workspace_bindings (
  session_id text PRIMARY KEY REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  project_key text,
  repo_path text,
  workspace_root text NOT NULL,
  workspace_scope text NOT NULL,
  working_directory text NOT NULL,
  profile_key text NOT NULL,
  approval_policy text NOT NULL,
  sandbox_mode text NOT NULL,
  utility_session boolean NOT NULL DEFAULT false,
  config_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb,
  mcp_server_snapshot jsonb NOT NULL DEFAULT '[]'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_runtime_connections (
  runtime_id text PRIMARY KEY,
  session_id text NOT NULL UNIQUE REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  connection_state text NOT NULL,
  transport_kind text NOT NULL,
  auth_mode text NOT NULL,
  preferred_model text NOT NULL DEFAULT 'gpt-5.3-codex',
  endpoint_description text NOT NULL,
  schema_version text NOT NULL,
  thread_id text,
  last_turn_id text,
  last_heartbeat_at timestamptz,
  last_disconnect_reason text,
  diagnostics_ref text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_runtime_leases (
  session_id text PRIMARY KEY REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  owner_device_id text,
  owner_host_name text,
  lease_state text NOT NULL,
  handoff_allowed boolean NOT NULL DEFAULT true,
  remotely_resumable boolean NOT NULL DEFAULT true,
  lease_expires_at timestamptz,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_device_presence (
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  device_id text NOT NULL,
  device_name text NOT NULL,
  client_surface text NOT NULL,
  host_name text,
  presence_state text NOT NULL,
  runtime_owner boolean NOT NULL DEFAULT false,
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (session_id, device_id)
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_session_turns (
  turn_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  requested_by text NOT NULL,
  requested_mode text NOT NULL,
  status text NOT NULL,
  prompt_text text NOT NULL,
  required_receipt_kinds jsonb NOT NULL DEFAULT '[]'::jsonb,
  allow_file_edits boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_session_events (
  event_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  event_type text NOT NULL,
  event_source text NOT NULL,
  schema_version text NOT NULL,
  runtime_id text,
  sequence_number bigint,
  raw_notification_name text,
  attributes jsonb NOT NULL DEFAULT '{}'::jsonb,
  summary text NOT NULL,
  occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_tool_receipts (
  receipt_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  tool_name text NOT NULL,
  receipt_kind text NOT NULL,
  receipt_status text NOT NULL,
  summary text NOT NULL,
  payload_ref text,
  recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_verifier_results (
  verifier_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  verifier_status text NOT NULL,
  blocking boolean NOT NULL DEFAULT true,
  summary text NOT NULL,
  evidence_uri text,
  payload_ref text,
  recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_output_snapshots (
  output_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  approved boolean NOT NULL DEFAULT false,
  release_state text NOT NULL,
  summary text NOT NULL,
  content_ref text,
  recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_patch_artifacts (
  patch_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  repo_path text NOT NULL,
  base_revision text,
  head_revision text,
  summary text NOT NULL,
  diff_preview text NOT NULL,
  artifact_body_ref text,
  changed_files jsonb NOT NULL DEFAULT '[]'::jsonb,
  recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_file_focus_requests (
  request_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  file_path text NOT NULL,
  line_number integer,
  column_number integer,
  reason text NOT NULL,
  launch_hint text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.codex_memory_context_refs (
  reference_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.codex_sessions(session_id) ON DELETE CASCADE,
  turn_id text REFERENCES agent_task_manager.codex_session_turns(turn_id) ON DELETE SET NULL,
  memory_kind text NOT NULL,
  source_type text NOT NULL,
  summary text NOT NULL,
  body_preview text,
  payload_ref text,
  recorded_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS codex_sessions_updated_idx
  ON agent_task_manager.codex_sessions (updated_at DESC);

CREATE INDEX IF NOT EXISTS codex_events_session_occurred_idx
  ON agent_task_manager.codex_session_events (session_id, occurred_at ASC);

CREATE INDEX IF NOT EXISTS codex_events_runtime_sequence_idx
  ON agent_task_manager.codex_session_events (runtime_id, sequence_number ASC);

CREATE INDEX IF NOT EXISTS codex_presence_session_seen_idx
  ON agent_task_manager.codex_device_presence (session_id, last_seen_at DESC);

CREATE INDEX IF NOT EXISTS codex_turns_session_updated_idx
  ON agent_task_manager.codex_session_turns (session_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS codex_outputs_session_recorded_idx
  ON agent_task_manager.codex_output_snapshots (session_id, recorded_at DESC);
