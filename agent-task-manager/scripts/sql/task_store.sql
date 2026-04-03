CREATE SCHEMA IF NOT EXISTS agent_task_manager;

CREATE OR REPLACE FUNCTION agent_task_manager.touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS 'BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;';

CREATE TABLE IF NOT EXISTS agent_task_manager.agent_tasks (
  task_id text PRIMARY KEY,
  project_key text NOT NULL,
  source_repo text,
  task_kind text NOT NULL DEFAULT 'general',
  title text NOT NULL,
  status text NOT NULL,
  priority integer NOT NULL DEFAULT 100,
  owner_agent_id text,
  multi_agent_enabled boolean NOT NULL DEFAULT false,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  due_at timestamptz
);

CREATE INDEX IF NOT EXISTS agent_tasks_project_status_idx
  ON agent_task_manager.agent_tasks (project_key, status, priority, updated_at DESC);

CREATE INDEX IF NOT EXISTS agent_tasks_multi_agent_idx
  ON agent_task_manager.agent_tasks (multi_agent_enabled, updated_at DESC);

DROP TRIGGER IF EXISTS agent_tasks_touch_updated_at
  ON agent_task_manager.agent_tasks;

CREATE TRIGGER agent_tasks_touch_updated_at
BEFORE UPDATE ON agent_task_manager.agent_tasks
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.task_checkpoints (
  checkpoint_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  agent_id text NOT NULL,
  checkpoint_kind text NOT NULL DEFAULT 'progress',
  status text NOT NULL,
  summary text NOT NULL,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS task_checkpoints_task_created_idx
  ON agent_task_manager.task_checkpoints (task_id, created_at DESC);

CREATE INDEX IF NOT EXISTS task_checkpoints_agent_created_idx
  ON agent_task_manager.task_checkpoints (agent_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.task_events (
  event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  agent_id text,
  event_name text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS task_events_task_created_idx
  ON agent_task_manager.task_events (task_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.agent_leases (
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  agent_id text NOT NULL,
  lease_token text NOT NULL,
  session_id text,
  host_name text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  acquired_at timestamptz NOT NULL DEFAULT now(),
  heartbeat_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (task_id, agent_id)
);

CREATE INDEX IF NOT EXISTS agent_leases_expiry_idx
  ON agent_task_manager.agent_leases (expires_at ASC);

CREATE TABLE IF NOT EXISTS agent_task_manager.prompt_requests (
  request_id text PRIMARY KEY,
  project_key text NOT NULL,
  repo_path text NOT NULL,
  bridge_target text NOT NULL DEFAULT 'remote-headless',
  thread_key text NOT NULL DEFAULT '',
  requested_by text NOT NULL,
  requested_from text,
  target_agent_id text,
  execution_mode text NOT NULL,
  status text NOT NULL DEFAULT 'queued',
  prompt_text text NOT NULL,
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

ALTER TABLE agent_task_manager.prompt_requests
  ADD COLUMN IF NOT EXISTS bridge_target text NOT NULL DEFAULT 'remote-headless';

ALTER TABLE agent_task_manager.prompt_requests
  ADD COLUMN IF NOT EXISTS thread_key text NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS prompt_requests_status_updated_idx
  ON agent_task_manager.prompt_requests (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS prompt_requests_project_updated_idx
  ON agent_task_manager.prompt_requests (project_key, updated_at DESC);

CREATE INDEX IF NOT EXISTS prompt_requests_bridge_target_updated_idx
  ON agent_task_manager.prompt_requests (bridge_target, updated_at DESC);

DROP TRIGGER IF EXISTS prompt_requests_touch_updated_at
  ON agent_task_manager.prompt_requests;

CREATE TRIGGER prompt_requests_touch_updated_at
BEFORE UPDATE ON agent_task_manager.prompt_requests
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.agent_sessions (
  session_id text PRIMARY KEY,
  agent_id text NOT NULL,
  host_name text,
  client_name text,
  repo_path text,
  status text NOT NULL DEFAULT 'online',
  capabilities jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS agent_sessions_status_seen_idx
  ON agent_task_manager.agent_sessions (status, last_seen_at DESC);

DROP TRIGGER IF EXISTS agent_sessions_touch_updated_at
  ON agent_task_manager.agent_sessions;

CREATE TRIGGER agent_sessions_touch_updated_at
BEFORE UPDATE ON agent_task_manager.agent_sessions
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.bridge_automation_commands (
  command_request_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.agent_sessions(session_id) ON DELETE CASCADE,
  target_agent_id text NOT NULL,
  repo_path text,
  bridge_target text NOT NULL,
  command_id text NOT NULL,
  isolation_class text NOT NULL DEFAULT 'cooperative-only',
  requested_by text NOT NULL,
  requested_from text,
  status text NOT NULL DEFAULT 'queued',
  latest_summary text,
  command_arguments jsonb NOT NULL DEFAULT '{}'::jsonb,
  result_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  claimed_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS bridge_automation_commands_session_status_idx
  ON agent_task_manager.bridge_automation_commands (session_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS bridge_automation_commands_target_status_idx
  ON agent_task_manager.bridge_automation_commands (target_agent_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS bridge_automation_commands_touch_updated_at
  ON agent_task_manager.bridge_automation_commands;

CREATE TRIGGER bridge_automation_commands_touch_updated_at
BEFORE UPDATE ON agent_task_manager.bridge_automation_commands
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.prompt_runs (
  run_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id text NOT NULL REFERENCES agent_task_manager.prompt_requests(request_id) ON DELETE CASCADE,
  agent_session_id text REFERENCES agent_task_manager.agent_sessions(session_id) ON DELETE SET NULL,
  bridge_name text,
  thread_session_id text,
  status text NOT NULL DEFAULT 'queued',
  exit_code integer,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

ALTER TABLE agent_task_manager.prompt_runs
  ADD COLUMN IF NOT EXISTS thread_session_id text;

CREATE INDEX IF NOT EXISTS prompt_runs_request_updated_idx
  ON agent_task_manager.prompt_runs (request_id, updated_at DESC);

DROP TRIGGER IF EXISTS prompt_runs_touch_updated_at
  ON agent_task_manager.prompt_runs;

CREATE TRIGGER prompt_runs_touch_updated_at
BEFORE UPDATE ON agent_task_manager.prompt_runs
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.prompt_messages (
  message_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id text NOT NULL REFERENCES agent_task_manager.prompt_requests(request_id) ON DELETE CASCADE,
  run_id bigint REFERENCES agent_task_manager.prompt_runs(run_id) ON DELETE CASCADE,
  message_kind text NOT NULL,
  sender_name text,
  body text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS prompt_messages_request_created_idx
  ON agent_task_manager.prompt_messages (request_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.prompt_threads (
  thread_key text PRIMARY KEY,
  project_key text NOT NULL,
  repo_path text NOT NULL,
  bridge_target text NOT NULL,
  thread_session_id text,
  last_request_id text REFERENCES agent_task_manager.prompt_requests(request_id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_message_at timestamptz
);

DROP TRIGGER IF EXISTS prompt_threads_touch_updated_at
  ON agent_task_manager.prompt_threads;

CREATE TRIGGER prompt_threads_touch_updated_at
BEFORE UPDATE ON agent_task_manager.prompt_threads
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE INDEX IF NOT EXISTS prompt_threads_bridge_message_idx
  ON agent_task_manager.prompt_threads (bridge_target, last_message_at DESC, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_learning_sessions (
  session_id text PRIMARY KEY,
  bridge_session_id text REFERENCES agent_task_manager.agent_sessions(session_id) ON DELETE SET NULL,
  machine_id text NOT NULL,
  client_profile_id text,
  client_install_path text,
  server_target text,
  scenario_id text,
  status text NOT NULL DEFAULT 'active',
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS hytale_learning_sessions_scope_idx
  ON agent_task_manager.hytale_learning_sessions (
    machine_id,
    client_profile_id,
    server_target,
    scenario_id,
    updated_at DESC
  );

DROP TRIGGER IF EXISTS hytale_learning_sessions_touch_updated_at
  ON agent_task_manager.hytale_learning_sessions;

CREATE TRIGGER hytale_learning_sessions_touch_updated_at
BEFORE UPDATE ON agent_task_manager.hytale_learning_sessions
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_action_traces (
  trace_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE CASCADE,
  command_request_id text,
  action_kind text NOT NULL,
  command_id text,
  status text NOT NULL,
  summary text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_action_traces_session_created_idx
  ON agent_task_manager.hytale_action_traces (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_action_traces_command_idx
  ON agent_task_manager.hytale_action_traces (command_request_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_timeline_frames (
  frame_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE CASCADE,
  source_window text NOT NULL,
  artifact_kind text NOT NULL,
  storage_backend text NOT NULL DEFAULT 'mongo',
  storage_key text NOT NULL,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_timeline_frames_session_created_idx
  ON agent_task_manager.hytale_timeline_frames (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_timeline_frames_window_created_idx
  ON agent_task_manager.hytale_timeline_frames (source_window, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_visual_anchors (
  anchor_id text PRIMARY KEY,
  machine_id text NOT NULL,
  client_profile_id text,
  server_target text,
  scenario_id text,
  anchor_key text NOT NULL,
  source_window text NOT NULL,
  normalized_region jsonb NOT NULL DEFAULT '{}'::jsonb,
  description text NOT NULL,
  confidence double precision NOT NULL DEFAULT 0,
  storage_backend text NOT NULL DEFAULT 'mongo',
  storage_key text,
  last_validated_at timestamptz NOT NULL DEFAULT now(),
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_visual_anchors_scope_idx
  ON agent_task_manager.hytale_visual_anchors (
    machine_id,
    client_profile_id,
    server_target,
    scenario_id,
    anchor_key,
    updated_at DESC
  );

DROP TRIGGER IF EXISTS hytale_visual_anchors_touch_updated_at
  ON agent_task_manager.hytale_visual_anchors;

CREATE TRIGGER hytale_visual_anchors_touch_updated_at
BEFORE UPDATE ON agent_task_manager.hytale_visual_anchors
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_playbooks (
  playbook_id text PRIMARY KEY,
  machine_id text NOT NULL,
  client_profile_id text,
  server_target text,
  scenario_id text,
  name text NOT NULL,
  target_window text NOT NULL,
  actions jsonb NOT NULL DEFAULT '[]'::jsonb,
  expected_anchors jsonb NOT NULL DEFAULT '[]'::jsonb,
  failure_recovery jsonb NOT NULL DEFAULT '{}'::jsonb,
  approved boolean NOT NULL DEFAULT false,
  pinned boolean NOT NULL DEFAULT false,
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  approved_at timestamptz,
  approved_by text,
  pinned_at timestamptz,
  pinned_by text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_playbooks_scope_idx
  ON agent_task_manager.hytale_playbooks (
    machine_id,
    client_profile_id,
    server_target,
    scenario_id,
    updated_at DESC
  );

CREATE INDEX IF NOT EXISTS hytale_playbooks_execution_idx
  ON agent_task_manager.hytale_playbooks (approved, pinned, updated_at DESC);

DROP TRIGGER IF EXISTS hytale_playbooks_touch_updated_at
  ON agent_task_manager.hytale_playbooks;

CREATE TRIGGER hytale_playbooks_touch_updated_at
BEFORE UPDATE ON agent_task_manager.hytale_playbooks
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_promotion_decisions (
  decision_id text PRIMARY KEY,
  session_id text REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE SET NULL,
  subject_type text NOT NULL,
  subject_id text NOT NULL,
  semantic_kind text NOT NULL,
  decision_status text NOT NULL,
  summary text NOT NULL,
  promoted_document_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_promotion_decisions_session_created_idx
  ON agent_task_manager.hytale_promotion_decisions (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_promotion_decisions_subject_idx
  ON agent_task_manager.hytale_promotion_decisions (subject_type, subject_id, created_at DESC);

CREATE OR REPLACE VIEW agent_task_manager.task_overview AS
SELECT
  task.task_id,
  task.project_key,
  task.source_repo,
  task.task_kind,
  task.title,
  task.status,
  task.priority,
  task.owner_agent_id,
  task.multi_agent_enabled,
  task.created_at,
  task.updated_at,
  latest_checkpoint.agent_id AS latest_checkpoint_agent_id,
  latest_checkpoint.status AS latest_checkpoint_status,
  latest_checkpoint.summary AS latest_checkpoint_summary,
  latest_checkpoint.created_at AS latest_checkpoint_at,
  lease.agent_id AS active_lease_agent_id,
  lease.session_id AS active_lease_session_id,
  lease.expires_at AS active_lease_expires_at
FROM agent_task_manager.agent_tasks AS task
LEFT JOIN LATERAL (
  SELECT checkpoint.agent_id, checkpoint.status, checkpoint.summary, checkpoint.created_at
  FROM agent_task_manager.task_checkpoints AS checkpoint
  WHERE checkpoint.task_id = task.task_id
  ORDER BY checkpoint.created_at DESC
  LIMIT 1
) AS latest_checkpoint ON true
LEFT JOIN LATERAL (
  SELECT active_lease.agent_id, active_lease.session_id, active_lease.expires_at
  FROM agent_task_manager.agent_leases AS active_lease
  WHERE active_lease.task_id = task.task_id
    AND active_lease.expires_at > now()
  ORDER BY active_lease.expires_at DESC
  LIMIT 1
) AS lease ON true;

DROP VIEW IF EXISTS agent_task_manager.prompt_thread_overview;
DROP VIEW IF EXISTS agent_task_manager.prompt_request_overview;

CREATE OR REPLACE VIEW agent_task_manager.prompt_request_overview AS
SELECT
  request.request_id,
  request.project_key,
  request.repo_path,
  request.bridge_target,
  request.thread_key,
  request.requested_by,
  request.requested_from,
  request.target_agent_id,
  request.execution_mode,
  request.status,
  request.prompt_text,
  request.latest_summary,
  request.metadata,
  request.created_at,
  request.updated_at,
  request.completed_at,
  latest_run.run_id AS latest_run_id,
  latest_run.status AS latest_run_status,
  latest_run.summary AS latest_run_summary,
  latest_run.completed_at AS latest_run_completed_at,
  latest_message.message_kind AS latest_message_kind,
  latest_message.sender_name AS latest_message_sender_name,
  latest_message.created_at AS latest_message_at
FROM agent_task_manager.prompt_requests AS request
LEFT JOIN LATERAL (
  SELECT run.run_id, run.status, run.summary, run.completed_at
  FROM agent_task_manager.prompt_runs AS run
  WHERE run.request_id = request.request_id
  ORDER BY run.updated_at DESC, run.run_id DESC
  LIMIT 1
) AS latest_run ON true
LEFT JOIN LATERAL (
  SELECT message.message_kind, message.sender_name, message.created_at
  FROM agent_task_manager.prompt_messages AS message
  WHERE message.request_id = request.request_id
  ORDER BY message.created_at DESC, message.message_id DESC
  LIMIT 1
) AS latest_message ON true;

CREATE OR REPLACE VIEW agent_task_manager.prompt_thread_overview AS
SELECT
  thread.thread_key,
  thread.project_key,
  thread.repo_path,
  thread.bridge_target,
  thread.thread_session_id,
  thread.last_request_id,
  thread.created_at,
  thread.updated_at,
  thread.last_message_at,
  request.status AS latest_request_status,
  request.latest_summary AS latest_request_summary,
  request.prompt_text AS latest_prompt_text,
  request.updated_at AS latest_request_updated_at
FROM agent_task_manager.prompt_threads AS thread
LEFT JOIN agent_task_manager.prompt_requests AS request
  ON request.request_id = thread.last_request_id;

CREATE TABLE IF NOT EXISTS agent_task_manager.worker_tasks (
  worker_task_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  parent_worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE SET NULL,
  task_role text NOT NULL,
  title text NOT NULL,
  status text NOT NULL,
  assigned_agent_id text,
  assigned_transport text,
  attempt_count integer NOT NULL DEFAULT 0,
  max_attempts integer NOT NULL DEFAULT 3,
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_check_in_at timestamptz,
  completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS worker_tasks_task_status_idx
  ON agent_task_manager.worker_tasks (task_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS worker_tasks_agent_status_idx
  ON agent_task_manager.worker_tasks (assigned_agent_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS worker_tasks_touch_updated_at
  ON agent_task_manager.worker_tasks;

CREATE TRIGGER worker_tasks_touch_updated_at
BEFORE UPDATE ON agent_task_manager.worker_tasks
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.worker_task_leases (
  worker_task_id text PRIMARY KEY REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  agent_id text NOT NULL,
  session_id text,
  lease_token text NOT NULL,
  transport_kind text NOT NULL,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  acquired_at timestamptz NOT NULL DEFAULT now(),
  heartbeat_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL
);

CREATE INDEX IF NOT EXISTS worker_task_leases_expiry_idx
  ON agent_task_manager.worker_task_leases (expires_at ASC);

CREATE TABLE IF NOT EXISTS agent_task_manager.worker_checkins (
  check_in_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  worker_task_id text NOT NULL REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  agent_id text NOT NULL,
  status text NOT NULL,
  summary text NOT NULL,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS worker_checkins_worker_created_idx
  ON agent_task_manager.worker_checkins (worker_task_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.cleanup_reviews (
  cleanup_review_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  reviewer_agent_id text,
  status text NOT NULL,
  summary text,
  diff_artifact_id text,
  findings jsonb NOT NULL DEFAULT '[]'::jsonb,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS cleanup_reviews_task_status_idx
  ON agent_task_manager.cleanup_reviews (task_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS cleanup_reviews_touch_updated_at
  ON agent_task_manager.cleanup_reviews;

CREATE TRIGGER cleanup_reviews_touch_updated_at
BEFORE UPDATE ON agent_task_manager.cleanup_reviews
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.overseer_decisions (
  decision_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE SET NULL,
  decision_type text NOT NULL,
  status text NOT NULL,
  summary text NOT NULL,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS overseer_decisions_task_created_idx
  ON agent_task_manager.overseer_decisions (task_id, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.validation_reports (
  report_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  cleanup_review_id text REFERENCES agent_task_manager.cleanup_reviews(cleanup_review_id) ON DELETE SET NULL,
  status text NOT NULL,
  compliance_score numeric(5, 2) NOT NULL DEFAULT 0.00,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE INDEX IF NOT EXISTS validation_reports_task_status_idx
  ON agent_task_manager.validation_reports (task_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS validation_reports_touch_updated_at
  ON agent_task_manager.validation_reports;

CREATE TRIGGER validation_reports_touch_updated_at
BEFORE UPDATE ON agent_task_manager.validation_reports
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.validation_violations (
  violation_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  report_id text NOT NULL REFERENCES agent_task_manager.validation_reports(report_id) ON DELETE CASCADE,
  rule_id text NOT NULL,
  severity text NOT NULL,
  target_type text NOT NULL,
  target_name text NOT NULL,
  engine_source text NOT NULL,
  explanation text NOT NULL,
  remediation text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS validation_violations_report_idx
  ON agent_task_manager.validation_violations (report_id, severity, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.patch_decisions (
  patch_decision_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  validation_report_id text REFERENCES agent_task_manager.validation_reports(report_id) ON DELETE SET NULL,
  cleanup_review_id text REFERENCES agent_task_manager.cleanup_reviews(cleanup_review_id) ON DELETE SET NULL,
  diff_artifact_id text,
  status text NOT NULL,
  summary text NOT NULL,
  decision_by text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS patch_decisions_task_status_idx
  ON agent_task_manager.patch_decisions (task_id, status, updated_at DESC);

DROP TRIGGER IF EXISTS patch_decisions_touch_updated_at
  ON agent_task_manager.patch_decisions;

CREATE TRIGGER patch_decisions_touch_updated_at
BEFORE UPDATE ON agent_task_manager.patch_decisions
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.shared_task_context (
  context_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  context_key text NOT NULL,
  visibility text NOT NULL,
  summary text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS shared_task_context_task_idx
  ON agent_task_manager.shared_task_context (task_id, context_key, updated_at DESC);

DROP TRIGGER IF EXISTS shared_task_context_touch_updated_at
  ON agent_task_manager.shared_task_context;

CREATE TRIGGER shared_task_context_touch_updated_at
BEFORE UPDATE ON agent_task_manager.shared_task_context
FOR EACH ROW
EXECUTE FUNCTION agent_task_manager.touch_updated_at();

CREATE TABLE IF NOT EXISTS agent_task_manager.task_artifacts (
  artifact_id text PRIMARY KEY,
  task_id text NOT NULL REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE CASCADE,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE CASCADE,
  artifact_kind text NOT NULL,
  storage_backend text NOT NULL,
  storage_key text NOT NULL,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS task_artifacts_task_kind_idx
  ON agent_task_manager.task_artifacts (task_id, artifact_kind, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.computer_use_runners (
  runner_id text PRIMARY KEY,
  display_name text NOT NULL,
  host_name text NOT NULL,
  base_url text NOT NULL,
  launcher_path text,
  client_path text,
  status text NOT NULL DEFAULT 'online',
  current_lease_session_id text,
  supported_capture_modes jsonb NOT NULL DEFAULT '[]'::jsonb,
  capabilities jsonb NOT NULL DEFAULT '{}'::jsonb,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.computer_use_sessions (
  session_id text PRIMARY KEY,
  runner_id text NOT NULL REFERENCES agent_task_manager.computer_use_runners(runner_id) ON DELETE CASCADE,
  task_id text REFERENCES agent_task_manager.agent_tasks(task_id) ON DELETE SET NULL,
  worker_task_id text REFERENCES agent_task_manager.worker_tasks(worker_task_id) ON DELETE SET NULL,
  scenario_id text NOT NULL,
  server_target text,
  chart_id text,
  status text NOT NULL,
  latest_summary text,
  runner_session_key text,
  expected_artifacts jsonb NOT NULL DEFAULT '[]'::jsonb,
  pass_fail_gates jsonb NOT NULL DEFAULT '[]'::jsonb,
  artifact_policy jsonb NOT NULL DEFAULT '{}'::jsonb,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  completed_at timestamptz
);

CREATE TABLE IF NOT EXISTS agent_task_manager.computer_use_session_artifacts (
  artifact_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.computer_use_sessions(session_id) ON DELETE CASCADE,
  artifact_kind text NOT NULL,
  storage_backend text NOT NULL,
  storage_key text NOT NULL,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS computer_use_runner_status_idx
  ON agent_task_manager.computer_use_runners (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS computer_use_sessions_runner_status_idx
  ON agent_task_manager.computer_use_sessions (runner_id, status, updated_at DESC);

CREATE INDEX IF NOT EXISTS computer_use_session_artifacts_session_idx
  ON agent_task_manager.computer_use_session_artifacts (session_id, artifact_kind, created_at DESC);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_learning_sessions (
  session_id text PRIMARY KEY,
  bridge_session_id text REFERENCES agent_task_manager.agent_sessions(session_id) ON DELETE SET NULL,
  machine_id text NOT NULL,
  client_profile_id text NOT NULL,
  client_install_path text,
  server_target text,
  scenario_id text,
  status text NOT NULL DEFAULT 'recording',
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_action_traces (
  trace_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE CASCADE,
  command_request_id text REFERENCES agent_task_manager.bridge_automation_commands(command_request_id) ON DELETE SET NULL,
  action_kind text NOT NULL,
  command_id text,
  status text NOT NULL,
  summary text NOT NULL,
  trace_payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_timeline_frames (
  frame_id text PRIMARY KEY,
  session_id text NOT NULL REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE CASCADE,
  source_window text NOT NULL,
  artifact_kind text NOT NULL,
  storage_backend text NOT NULL,
  storage_key text NOT NULL,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_visual_anchors (
  anchor_id text PRIMARY KEY,
  machine_id text NOT NULL,
  client_profile_id text NOT NULL,
  server_target text,
  scenario_id text,
  anchor_key text NOT NULL,
  source_window text NOT NULL,
  normalized_region jsonb NOT NULL DEFAULT '{}'::jsonb,
  description text NOT NULL,
  confidence double precision NOT NULL DEFAULT 0,
  storage_backend text NOT NULL,
  storage_key text NOT NULL,
  last_validated_at timestamptz,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_playbooks (
  playbook_id text PRIMARY KEY,
  machine_id text NOT NULL,
  client_profile_id text NOT NULL,
  server_target text,
  scenario_id text,
  name text NOT NULL,
  target_window text NOT NULL,
  actions jsonb NOT NULL DEFAULT '[]'::jsonb,
  expected_anchors jsonb NOT NULL DEFAULT '[]'::jsonb,
  failure_recovery jsonb NOT NULL DEFAULT '{}'::jsonb,
  approved boolean NOT NULL DEFAULT false,
  pinned boolean NOT NULL DEFAULT false,
  latest_summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  approved_at timestamptz,
  approved_by text,
  pinned_at timestamptz,
  pinned_by text
);

CREATE TABLE IF NOT EXISTS agent_task_manager.hytale_promotion_decisions (
  decision_id text PRIMARY KEY,
  session_id text REFERENCES agent_task_manager.hytale_learning_sessions(session_id) ON DELETE SET NULL,
  subject_type text NOT NULL,
  subject_id text NOT NULL,
  semantic_kind text NOT NULL,
  decision_status text NOT NULL,
  summary text NOT NULL,
  promoted_document_id text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS hytale_learning_sessions_scope_idx
  ON agent_task_manager.hytale_learning_sessions (machine_id, client_profile_id, server_target, scenario_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS hytale_action_traces_session_idx
  ON agent_task_manager.hytale_action_traces (session_id, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_action_traces_command_idx
  ON agent_task_manager.hytale_action_traces (command_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_timeline_frames_session_idx
  ON agent_task_manager.hytale_timeline_frames (session_id, artifact_kind, created_at DESC);

CREATE INDEX IF NOT EXISTS hytale_visual_anchors_scope_idx
  ON agent_task_manager.hytale_visual_anchors (machine_id, client_profile_id, server_target, scenario_id, anchor_key, updated_at DESC);

CREATE INDEX IF NOT EXISTS hytale_playbooks_scope_idx
  ON agent_task_manager.hytale_playbooks (machine_id, client_profile_id, server_target, scenario_id, approved, pinned, updated_at DESC);

CREATE INDEX IF NOT EXISTS hytale_promotion_decisions_subject_idx
  ON agent_task_manager.hytale_promotion_decisions (subject_type, subject_id, created_at DESC);

CREATE OR REPLACE VIEW agent_task_manager.worker_task_overview AS
SELECT
  worker_task.worker_task_id,
  worker_task.task_id,
  worker_task.parent_worker_task_id,
  worker_task.task_role,
  worker_task.title,
  worker_task.status,
  worker_task.assigned_agent_id,
  worker_task.assigned_transport,
  worker_task.attempt_count,
  worker_task.max_attempts,
  worker_task.latest_summary,
  worker_task.created_at,
  worker_task.updated_at,
  worker_task.last_check_in_at,
  worker_task.completed_at,
  lease.session_id AS active_session_id,
  lease.expires_at AS active_lease_expires_at,
  latest_check_in.summary AS latest_check_in_summary,
  latest_check_in.status AS latest_check_in_status,
  latest_check_in.created_at AS latest_check_in_created_at
FROM agent_task_manager.worker_tasks AS worker_task
LEFT JOIN LATERAL (
  SELECT session_id, expires_at
  FROM agent_task_manager.worker_task_leases AS active_lease
  WHERE active_lease.worker_task_id = worker_task.worker_task_id
    AND active_lease.expires_at > now()
  LIMIT 1
) AS lease ON true
LEFT JOIN LATERAL (
  SELECT summary, status, created_at
  FROM agent_task_manager.worker_checkins AS check_in
  WHERE check_in.worker_task_id = worker_task.worker_task_id
  ORDER BY created_at DESC
  LIMIT 1
) AS latest_check_in ON true;
