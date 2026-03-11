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

CREATE INDEX IF NOT EXISTS prompt_requests_status_updated_idx
  ON agent_task_manager.prompt_requests (status, updated_at DESC);

CREATE INDEX IF NOT EXISTS prompt_requests_project_updated_idx
  ON agent_task_manager.prompt_requests (project_key, updated_at DESC);

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

CREATE TABLE IF NOT EXISTS agent_task_manager.prompt_runs (
  run_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  request_id text NOT NULL REFERENCES agent_task_manager.prompt_requests(request_id) ON DELETE CASCADE,
  agent_session_id text REFERENCES agent_task_manager.agent_sessions(session_id) ON DELETE SET NULL,
  bridge_name text,
  status text NOT NULL DEFAULT 'queued',
  exit_code integer,
  summary text,
  metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  completed_at timestamptz
);

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

CREATE OR REPLACE VIEW agent_task_manager.prompt_request_overview AS
SELECT
  request.request_id,
  request.project_key,
  request.repo_path,
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
