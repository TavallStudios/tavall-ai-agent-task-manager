const csrfToken = document.querySelector('meta[name="_csrf"]')?.content ?? "";
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content ?? "X-CSRF-TOKEN";
const username = document.querySelector('meta[name="atm-username"]')?.content ?? "unknown";
const appBase = document.querySelector('meta[name="app-base"]')?.content ?? "/";

const state = {
  knownRepos: [],
  selectedThreadKey: null,
  selectedTaskId: null,
};

function apiHeaders() {
  return {
    "Content-Type": "application/json",
    [csrfHeader]: csrfToken,
  };
}

function appUrl(path) {
  const base = appBase.endsWith("/") ? appBase.slice(0, -1) : appBase;
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return `${base}${normalizedPath}`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function statusPill(status) {
  const css = ["failed", "cancelled"].includes(status) ? "failed"
    : ["completed", "succeeded"].includes(status) ? "completed"
    : "";
  return `<span class="status-pill ${css}">${escapeHtml(status || "unknown")}</span>`;
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString();
}

function normalizePath(path) {
  return String(path ?? "").replaceAll("\\", "/").replace(/\/+$/, "");
}

function repoNameFromPath(path) {
  const value = normalizePath(path);
  if (!value) {
    return "unknown repo";
  }
  const parts = value.split("/");
  return parts[parts.length - 1] || value;
}

function bridgeTargetLabel(value) {
  return value === "local-ide" ? "local IDE" : "remote server";
}

function bridgeOnlineLabel(session) {
  if (session.online) {
    return "online";
  }
  return session.status || "offline";
}

function messageRole(message) {
  if (message.messageKind === "prompt") {
    return "user";
  }
  if (message.messageKind === "agent-message" || message.messageKind === "final-response") {
    return "assistant";
  }
  return "system";
}

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

function renderBridgeSessions(items) {
  const root = document.getElementById("bridge-session-list");
  if (!root) {
    return;
  }
  if (!items.length) {
    root.innerHTML = '<div class="empty-state">No bridge sessions have registered yet.</div>';
    return;
  }

  root.innerHTML = items.map((session) => `
    <article class="detail-card bridge-session-card">
      <div class="item-top">
        <strong>${escapeHtml(session.clientName || session.agentId || "bridge")}</strong>
        ${statusPill(bridgeOnlineLabel(session))}
      </div>
      <div class="muted">${escapeHtml(bridgeTargetLabel(session.bridgeTarget || "remote-headless"))} · ${escapeHtml(session.transport || "unknown transport")}</div>
      <div class="item-meta muted">
        <span>${escapeHtml(session.hostName || "-")}</span>
        <span>${formatDate(session.lastSeenAt)}</span>
      </div>
      <div class="muted">${escapeHtml(session.repoPath || "all repos")}</div>
      <div class="muted">${escapeHtml(session.sessionId)}</div>
    </article>
  `).join("");
}

function renderThreadList(items) {
  const root = document.getElementById("thread-list");
  if (!items.length) {
    root.innerHTML = '<div class="empty-state">No chat threads matched the current filter.</div>';
    return;
  }

  root.innerHTML = items.map((item) => `
    <article class="list-item ${item.threadKey === state.selectedThreadKey ? "active" : ""}" data-thread-key="${escapeHtml(item.threadKey)}">
      <div class="item-top">
        <strong>${escapeHtml(repoNameFromPath(item.repoPath))} · ${escapeHtml(bridgeTargetLabel(item.bridgeTarget))}</strong>
        ${statusPill(item.latestRequestStatus)}
      </div>
      <div class="muted">${escapeHtml(item.latestPromptPreview || item.latestRequestSummary || "No prompts yet.")}</div>
      <div class="item-meta muted">
        <span>${escapeHtml(item.threadSessionId || "new thread")}</span>
        <span>${formatDate(item.lastMessageAt || item.updatedAt)}</span>
      </div>
    </article>
  `).join("");

  root.querySelectorAll("[data-thread-key]").forEach((element) => {
    element.addEventListener("click", async () => {
      state.selectedThreadKey = element.dataset.threadKey;
      await loadThreadDetail();
      await refreshThreadList();
    });
  });
}

function renderTaskList(items) {
  const root = document.getElementById("task-list");
  if (!items.length) {
    root.innerHTML = '<div class="empty-state">No tasks matched the current filters.</div>';
    return;
  }

  root.innerHTML = items.map((item) => `
    <article class="list-item ${item.taskId === state.selectedTaskId ? "active" : ""}" data-task-id="${escapeHtml(item.taskId)}">
      <div class="item-top">
        <strong>${escapeHtml(item.taskId)}</strong>
        ${statusPill(item.status)}
      </div>
      <div>${escapeHtml(item.title)}</div>
      <div class="item-meta muted">
        <span>${escapeHtml(item.projectKey)} · priority ${escapeHtml(item.priority)}</span>
        <span>${formatDate(item.updatedAt)}</span>
      </div>
    </article>
  `).join("");

  root.querySelectorAll("[data-task-id]").forEach((element) => {
    element.addEventListener("click", async () => {
      state.selectedTaskId = element.dataset.taskId;
      await loadTaskDetail();
      await refreshTaskList();
    });
  });
}

function renderThreadDetail(payload) {
  const title = document.getElementById("thread-detail-title");
  const root = document.getElementById("thread-detail");

  if (!payload) {
    title.textContent = "Select a chat thread";
    root.innerHTML = "Choose a chat thread to inspect the conversation.";
    root.classList.add("empty-state");
    return;
  }

  title.textContent = `${repoNameFromPath(payload.thread.repoPath)} · ${bridgeTargetLabel(payload.thread.bridgeTarget)}`;
  root.classList.remove("empty-state");

  const requests = payload.requests.map((request) => `
    <div class="detail-card compact-card">
      <div class="item-top">
        <strong>${escapeHtml(request.requestId)}</strong>
        ${statusPill(request.status)}
      </div>
      <div class="item-meta muted">
        <span>${escapeHtml(request.executionMode)}</span>
        <span>${formatDate(request.updatedAt)}</span>
      </div>
    </div>
  `).join("");

  const messages = payload.messages
    .filter((message, index, items) => {
      if (message.messageKind !== "final-response") {
        return true;
      }
      const previous = items[index - 1];
      return !previous || previous.body !== message.body;
    })
    .map((message) => `
      <article class="chat-message ${messageRole(message)}">
        <div class="chat-meta">
          <strong>${escapeHtml(message.senderName || message.messageKind)}</strong>
          <span>${formatDate(message.createdAt)}</span>
        </div>
        <div class="chat-kind">${escapeHtml(message.messageKind)}</div>
        <pre>${escapeHtml(message.body)}</pre>
      </article>
    `).join("");

  root.innerHTML = `
    <div class="detail-card">
      <div class="item-top">
        <strong>${escapeHtml(payload.thread.repoPath)}</strong>
        ${statusPill(payload.thread.latestRequestStatus)}
      </div>
      <div class="item-meta muted">
        <span>${escapeHtml(payload.thread.threadSessionId || "new local thread")}</span>
        <span>${escapeHtml(payload.thread.threadKey)}</span>
      </div>
      <div class="muted">Bridge target: ${escapeHtml(bridgeTargetLabel(payload.thread.bridgeTarget))}</div>
    </div>
    <div>
      <h3>Conversation</h3>
      <div class="chat-shell">${messages || '<div class="empty-state">No messages recorded yet.</div>'}</div>
    </div>
    <div>
      <h3>Requests</h3>
      <div class="list-shell">${requests || '<div class="empty-state">No requests recorded yet.</div>'}</div>
    </div>
  `;
}

function renderTaskDetail(payload) {
  const title = document.getElementById("task-detail-title");
  const root = document.getElementById("task-detail");

  if (!payload) {
    title.textContent = "Select a task";
    root.innerHTML = "Choose a task to inspect checkpoints.";
    root.classList.add("empty-state");
    return;
  }

  title.textContent = payload.task.taskId;
  root.classList.remove("empty-state");

  const checkpoints = payload.checkpoints.length
    ? payload.checkpoints.map((checkpoint) => `
        <div class="checkpoint-card">
          <div class="item-top">
            <strong>${escapeHtml(checkpoint.agentId)}</strong>
            ${statusPill(checkpoint.status)}
          </div>
          <div>${escapeHtml(checkpoint.summary)}</div>
          <div class="item-meta muted">
            <span>${escapeHtml(checkpoint.checkpointKind)}</span>
            <span>${formatDate(checkpoint.createdAt)}</span>
          </div>
        </div>
      `).join("")
    : '<div class="empty-state">No checkpoints recorded yet.</div>';

  root.innerHTML = `
    <div class="detail-card">
      <div class="item-top">
        <strong>${escapeHtml(payload.task.title)}</strong>
        ${statusPill(payload.task.status)}
      </div>
      <div class="item-meta muted">
        <span>${escapeHtml(payload.task.projectKey)}</span>
        <span>${formatDate(payload.task.updatedAt)}</span>
      </div>
      <div class="muted">Owner: ${escapeHtml(payload.task.ownerAgentId || "-")} · Lease: ${escapeHtml(payload.task.activeLeaseAgentId || "-")}</div>
    </div>
    <div>
      <h3>Checkpoints</h3>
      ${checkpoints}
    </div>
  `;
}

function renderRepoSelectionMeta(repo) {
  const meta = document.getElementById("repo-selection-meta");
  if (!repo) {
    meta.textContent = "Select a repo. Agent Task Manager will assign the internal project key automatically.";
    return;
  }
  const bridgeTarget = document.getElementById("bridge-target")?.value ?? "local-ide";
  meta.textContent = `Path: ${repo.repoPath} · Internal key: ${repo.projectKey} · Default thread: ${bridgeTarget}:${normalizePath(repo.repoPath).toLowerCase()}`;
}

function selectedRepo() {
  const repoPath = document.getElementById("repo-path").value;
  return state.knownRepos.find((repo) => repo.repoPath === repoPath) ?? null;
}

function renderRepoOptions(items) {
  state.knownRepos = items;
  const select = document.getElementById("repo-path");
  const previousValue = select.value;
  select.innerHTML = '<option value="">Select a repository</option>' + items.map((repo) => `
    <option value="${escapeHtml(repo.repoPath)}">${escapeHtml(repo.displayName)} · ${escapeHtml(repo.locationLabel)}</option>
  `).join("");

  const nextValue = items.some((repo) => repo.repoPath === previousValue)
      ? previousValue
      : preferredRepoPath(items);
  select.value = nextValue;
  renderRepoSelectionMeta(selectedRepo());
}

function preferredRepoPath(items) {
  const preferred = items.find((repo) => repo.repoPath === "/srv/Companions")
      ?? items.find((repo) => repo.locationLabel === "remote")
      ?? items[0];
  return preferred?.repoPath ?? "";
}

async function loadRepoCatalog() {
  const payload = await fetchJson(appUrl("/api/repos"));
  renderRepoOptions(payload.items);
}

async function loadRuntimeStatus() {
  const payload = await fetchJson(appUrl("/api/runtime/status"));
  document.getElementById("task-count").textContent = payload.taskCount;
  document.getElementById("queued-prompts").textContent = payload.queuedPromptCount;
  document.getElementById("multi-agent-mode").textContent = payload.multiAgentEnabled ? "enabled" : "disabled";
  document.getElementById("redis-namespace").textContent = payload.redisNamespace;
  document.getElementById("bridge-status").textContent =
    payload.bridgeEnabled
      ? `${payload.bridgeOnline ? "online" : payload.bridgeSessionStatus || "offline"} · ${payload.bridgeAgentId || "-"}`
      : "disabled";
  document.getElementById("bridge-active-request").textContent =
    payload.bridgeActiveRequestId || "idle";
}

async function loadBridgeSessions() {
  const payload = await fetchJson(`${appUrl("/api/bridge/sessions")}?limit=12`);
  renderBridgeSessions(payload.items);
}

async function refreshThreadList() {
  const bridgeTarget = document.getElementById("thread-bridge-filter").value.trim();
  const query = new URLSearchParams({ limit: "25", bridgeTarget });
  const payload = await fetchJson(`${appUrl("/api/threads")}?${query.toString()}`);
  renderThreadList(payload.items);
}

async function refreshTaskList() {
  const project = document.getElementById("task-project-filter").value.trim();
  const status = document.getElementById("task-status-filter").value.trim();
  const query = new URLSearchParams({ limit: "25", project, status });
  const payload = await fetchJson(`${appUrl("/api/tasks")}?${query.toString()}`);
  renderTaskList(payload.items);
}

async function loadThreadDetail() {
  if (!state.selectedThreadKey) {
    renderThreadDetail(null);
    return;
  }
  const query = new URLSearchParams({ threadKey: state.selectedThreadKey });
  const payload = await fetchJson(`${appUrl("/api/threads/detail")}?${query.toString()}`);
  renderThreadDetail(payload);
}

async function loadTaskDetail() {
  if (!state.selectedTaskId) {
    renderTaskDetail(null);
    return;
  }
  const payload = await fetchJson(appUrl(`/api/tasks/${encodeURIComponent(state.selectedTaskId)}`));
  renderTaskDetail(payload);
}

async function submitPrompt(event) {
  event.preventDefault();
  const status = document.getElementById("prompt-submit-status");
  status.textContent = "Submitting prompt request...";
  const repo = selectedRepo();
  if (!repo) {
    status.textContent = "Select a repository first.";
    return;
  }

  const body = {
    repoPath: repo.repoPath,
    bridgeTarget: document.getElementById("bridge-target").value,
    executionMode: document.getElementById("execution-mode").value,
    promptText: document.getElementById("prompt-text").value.trim(),
  };

  try {
    const payload = await fetchJson(appUrl("/api/prompt-requests"), {
      method: "POST",
      headers: apiHeaders(),
      body: JSON.stringify(body),
    });
    document.getElementById("prompt-submit-status").textContent =
      `Queued ${payload.requestId} for ${username}.`;
    document.getElementById("prompt-text").value = "";
    state.selectedThreadKey = payload.threadKey;
    document.getElementById("thread-bridge-filter").value = payload.bridgeTarget;
    await Promise.all([loadRuntimeStatus(), refreshThreadList(), loadThreadDetail()]);
  } catch (error) {
    status.textContent = error.message;
  }
}

async function refreshAll() {
  try {
    await Promise.all([
      loadRuntimeStatus(),
      loadBridgeSessions(),
      refreshThreadList(),
      refreshTaskList(),
      loadThreadDetail(),
      loadTaskDetail(),
    ]);
  } catch (error) {
    console.error(error);
  }
}

document.getElementById("prompt-form")?.addEventListener("submit", submitPrompt);
document.getElementById("repo-path")?.addEventListener("change", () => renderRepoSelectionMeta(selectedRepo()));
document.getElementById("bridge-target")?.addEventListener("change", () => renderRepoSelectionMeta(selectedRepo()));
document.getElementById("thread-bridge-filter")?.addEventListener("change", refreshThreadList);
document.getElementById("task-project-filter")?.addEventListener("change", refreshTaskList);
document.getElementById("task-status-filter")?.addEventListener("change", refreshTaskList);

loadRepoCatalog().then(refreshAll).catch((error) => {
  console.error(error);
});
setInterval(refreshAll, 5000);
