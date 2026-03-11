const csrfToken = document.querySelector('meta[name="_csrf"]')?.content ?? "";
const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content ?? "X-CSRF-TOKEN";
const username = document.querySelector('meta[name="atm-username"]')?.content ?? "unknown";
const appBase = document.querySelector('meta[name="app-base"]')?.content ?? "/";

const state = {
  selectedPromptId: null,
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

async function fetchJson(url, options = {}) {
  const response = await fetch(url, options);
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

function renderPromptList(items) {
  const root = document.getElementById("prompt-list");
  if (!items.length) {
    root.innerHTML = '<div class="empty-state">No prompt requests matched the current filter.</div>';
    return;
  }

  root.innerHTML = items.map((item) => `
    <article class="list-item ${item.requestId === state.selectedPromptId ? "active" : ""}" data-prompt-id="${escapeHtml(item.requestId)}">
      <div class="item-top">
        <strong>${escapeHtml(item.projectKey)} :: ${escapeHtml(item.executionMode)}</strong>
        ${statusPill(item.status)}
      </div>
      <div class="muted">${escapeHtml(item.promptPreview)}</div>
      <div class="item-meta muted">
        <span>${escapeHtml(item.requestedBy)} from ${escapeHtml(item.requestedFrom || "browser")}</span>
        <span>${formatDate(item.updatedAt)}</span>
      </div>
    </article>
  `).join("");

  root.querySelectorAll("[data-prompt-id]").forEach((element) => {
    element.addEventListener("click", async () => {
      state.selectedPromptId = element.dataset.promptId;
      await loadPromptDetail();
      await refreshPromptList();
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

function renderPromptDetail(payload) {
  const title = document.getElementById("prompt-detail-title");
  const root = document.getElementById("prompt-detail");

  if (!payload) {
    title.textContent = "Select a prompt request";
    root.innerHTML = "Choose a prompt request to inspect status and messages.";
    root.classList.add("empty-state");
    return;
  }

  title.textContent = payload.request.requestId;
  root.classList.remove("empty-state");
  const runs = payload.runs.length
    ? payload.runs.map((run) => `
        <div class="detail-card">
          <div class="item-top">
            <strong>Run ${escapeHtml(run.runId)}</strong>
            ${statusPill(run.status)}
          </div>
          <div class="muted">${escapeHtml(run.summary || "-")}</div>
          <div class="item-meta muted">
            <span>${escapeHtml(run.bridgeName || "unassigned")}</span>
            <span>${formatDate(run.updatedAt)}</span>
          </div>
        </div>
      `).join("")
    : '<div class="empty-state">No runs have been attached yet.</div>';

  const messages = payload.messages.length
    ? payload.messages.map((message) => `
        <div class="message-card">
          <div class="item-top">
            <strong>${escapeHtml(message.senderName || message.messageKind)}</strong>
            <span class="muted">${formatDate(message.createdAt)}</span>
          </div>
          <div class="muted">${escapeHtml(message.messageKind)}</div>
          <pre>${escapeHtml(message.body)}</pre>
        </div>
      `).join("")
    : '<div class="empty-state">No output messages have been recorded yet.</div>';

  root.innerHTML = `
    <div class="detail-card">
      <div class="item-top">
        <strong>${escapeHtml(payload.request.projectKey)} · ${escapeHtml(payload.request.executionMode)}</strong>
        ${statusPill(payload.request.status)}
      </div>
      <div class="muted">${escapeHtml(payload.request.repoPath)}</div>
      <pre>${escapeHtml(payload.request.promptText)}</pre>
    </div>
    <div>
      <h3>Runs</h3>
      ${runs}
    </div>
    <div>
      <h3>Messages</h3>
      ${messages}
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

async function refreshPromptList() {
  const status = document.getElementById("prompt-status-filter").value.trim();
  const query = new URLSearchParams({ limit: "25", status });
  const payload = await fetchJson(`${appUrl("/api/prompt-requests")}?${query.toString()}`);
  renderPromptList(payload.items);
}

async function refreshTaskList() {
  const project = document.getElementById("task-project-filter").value.trim();
  const status = document.getElementById("task-status-filter").value.trim();
  const query = new URLSearchParams({ limit: "25", project, status });
  const payload = await fetchJson(`${appUrl("/api/tasks")}?${query.toString()}`);
  renderTaskList(payload.items);
}

async function loadPromptDetail() {
  if (!state.selectedPromptId) {
    renderPromptDetail(null);
    return;
  }
  const payload = await fetchJson(appUrl(`/api/prompt-requests/${encodeURIComponent(state.selectedPromptId)}`));
  renderPromptDetail(payload);
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

  const body = {
    projectKey: document.getElementById("project-key").value.trim(),
    repoPath: document.getElementById("repo-path").value.trim(),
    executionMode: document.getElementById("execution-mode").value,
    requestedFrom: document.getElementById("requested-from").value.trim(),
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
    state.selectedPromptId = payload.requestId;
    await Promise.all([loadRuntimeStatus(), refreshPromptList(), loadPromptDetail()]);
  } catch (error) {
    status.textContent = error.message;
  }
}

async function refreshAll() {
  try {
    await Promise.all([
      loadRuntimeStatus(),
      refreshPromptList(),
      refreshTaskList(),
      loadPromptDetail(),
      loadTaskDetail(),
    ]);
  } catch (error) {
    console.error(error);
  }
}

document.getElementById("prompt-form")?.addEventListener("submit", submitPrompt);
document.getElementById("prompt-status-filter")?.addEventListener("change", refreshPromptList);
document.getElementById("task-project-filter")?.addEventListener("change", refreshTaskList);
document.getElementById("task-status-filter")?.addEventListener("change", refreshTaskList);

refreshAll();
setInterval(refreshAll, 5000);
