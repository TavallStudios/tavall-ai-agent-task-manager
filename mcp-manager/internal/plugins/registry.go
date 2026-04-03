package plugins

import (
	"os"
	"path/filepath"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

type FieldType string

const (
	FieldTypeString FieldType = "string"
	FieldTypeBool   FieldType = "bool"
	FieldTypeSelect FieldType = "select"
)

type FieldOption struct {
	Value string `json:"value"`
	Label string `json:"label"`
}

type FieldSchema struct {
	Name        string        `json:"name"`
	Label       string        `json:"label"`
	Description string        `json:"description"`
	Type        FieldType     `json:"type"`
	Default     any           `json:"default"`
	Options     []FieldOption `json:"options,omitempty"`
}

type Preset struct {
	ID          string         `json:"id"`
	Label       string         `json:"label"`
	Description string         `json:"description"`
	Values      map[string]any `json:"values"`
}

type Definition struct {
	ID          string        `json:"id"`
	DisplayName string        `json:"displayName"`
	Description string        `json:"description"`
	Fields      []FieldSchema `json:"fields"`
	Presets     []Preset      `json:"presets"`
}

type Registry struct {
	definitions map[string]Definition
}

func NewRegistry() *Registry {
	return &Registry{
		definitions: map[string]Definition{
			"generic": {
				ID:          "generic",
				DisplayName: "Generic MCP Server",
				Description: "Use the existing command, URL, and env values without plugin-specific augmentation.",
			},
			"agent-task-manager": {
				ID:          "agent-task-manager",
				DisplayName: "AgentTaskManager",
				Description: "Manages the AgentTaskManager launcher, remote MCP bridge settings, auth toggles, clean-code mode, and tool-bundle presets without replacing runtime-injected required MCP wiring.",
				Fields: []FieldSchema{
					{Name: "connectionMode", Label: "Connection mode", Description: "Switch between the local stdio launcher and the remote HTTP MCP endpoint.", Type: FieldTypeSelect, Default: "local", Options: []FieldOption{{Value: "local", Label: "Local install"}, {Value: "remote", Label: "Remote MCP"}}},
					{Name: "localLauncherPath", Label: "Local launcher path", Description: "Repo-local launcher or custom local ATM stdio script.", Type: FieldTypeString, Default: defaultAgentTaskManagerLauncher()},
					{Name: "remoteExecutionEnabled", Label: "Remote execution enabled", Description: "Controls AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED.", Type: FieldTypeBool, Default: false},
					{Name: "remoteBaseUrl", Label: "Remote base URL", Description: "Writes AGENT_TASK_MANAGER_MCP_BASE_URL when set.", Type: FieldTypeString, Default: ""},
					{Name: "mcpEndpoint", Label: "MCP endpoint", Description: "Writes AGENT_TASK_MANAGER_MCP_ENDPOINT.", Type: FieldTypeString, Default: "/mcp"},
					{Name: "username", Label: "Remote user", Description: "Writes AGENT_TASK_MANAGER_USERNAME.", Type: FieldTypeString, Default: ""},
					{Name: "password", Label: "Remote password", Description: "Writes AGENT_TASK_MANAGER_PASSWORD.", Type: FieldTypeString, Default: ""},
					{Name: "downstreamCentralServer", Label: "Downstream central server", Description: "Controls AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER.", Type: FieldTypeString, Default: ""},
					{Name: "noAuthEnabled", Label: "No auth transport", Description: "Controls AGENT_TASK_MANAGER_MCP_NO_AUTH_ENABLED.", Type: FieldTypeBool, Default: false},
					{Name: "cleanCodeMode", Label: "Clean code mode", Description: "Managed metadata used by future ATM-aware clients.", Type: FieldTypeSelect, Default: "balanced", Options: []FieldOption{{Value: "minimal", Label: "Minimal"}, {Value: "balanced", Label: "Balanced"}, {Value: "strict", Label: "Strict"}}},
					{Name: "toolBundlePreset", Label: "Tool bundle preset", Description: "Named tool combination stored in manager metadata.", Type: FieldTypeSelect, Default: "repo_context_only", Options: []FieldOption{{Value: "repo_context_only", Label: "Repo context only"}, {Value: "clean_java_validation", Label: "Clean Java validation"}, {Value: "full_atm", Label: "Full ATM"}}},
				},
				Presets: []Preset{
					{ID: "repo_context_only", Label: "Repo context only", Description: "Favor local repository inspection and retrieval without extra validation emphasis.", Values: map[string]any{"connectionMode": "local", "cleanCodeMode": "minimal", "toolBundlePreset": "repo_context_only", "remoteExecutionEnabled": false, "mcpEndpoint": "/mcp", "localLauncherPath": defaultAgentTaskManagerLauncher()}},
					{ID: "clean_java_validation", Label: "Clean Java validation", Description: "Bias the local ATM profile toward clean Java validation work.", Values: map[string]any{"connectionMode": "local", "cleanCodeMode": "strict", "toolBundlePreset": "clean_java_validation", "remoteExecutionEnabled": false, "mcpEndpoint": "/mcp", "localLauncherPath": defaultAgentTaskManagerLauncher()}},
					{ID: "full_atm", Label: "Full ATM", Description: "Keep the full AgentTaskManager operator profile with local-first defaults and a remote escape hatch.", Values: map[string]any{"connectionMode": "local", "cleanCodeMode": "balanced", "toolBundlePreset": "full_atm", "remoteExecutionEnabled": false, "mcpEndpoint": "/mcp", "localLauncherPath": defaultAgentTaskManagerLauncher()}},
				},
			},
			"chrome-devtools": {
				ID:          "chrome-devtools",
				DisplayName: "Chrome DevTools MCP",
				Description: "Manages the chrome-devtools-mcp launcher flags that actually control browser attach behavior and telemetry settings.",
				Fields: []FieldSchema{
					{Name: "packageName", Label: "Package", Description: "The npm package or tag used to launch the MCP.", Type: FieldTypeString, Default: "chrome-devtools-mcp@latest"},
					{Name: "autoConnect", Label: "Auto-connect", Description: "Adds or removes --autoConnect.", Type: FieldTypeBool, Default: true},
					{Name: "usageStatisticsEnabled", Label: "Usage statistics", Description: "Adds or removes --no-usage-statistics.", Type: FieldTypeBool, Default: false},
					{Name: "performanceCruxEnabled", Label: "Performance CrUX", Description: "Adds or removes --no-performance-crux.", Type: FieldTypeBool, Default: false},
					{Name: "browserExecutablePath", Label: "Browser executable path", Description: "Writes --executablePath=...", Type: FieldTypeString, Default: ""},
				},
			},
		},
	}
}

func (registry *Registry) Catalog() []Definition {
	return []Definition{
		registry.definitions["agent-task-manager"],
		registry.definitions["chrome-devtools"],
		registry.definitions["generic"],
	}
}

func (registry *Registry) Get(id string) Definition {
	definition, ok := registry.definitions[id]
	if ok {
		return definition
	}
	return registry.definitions["generic"]
}

func (registry *Registry) Resolve(server model.ManagedServer) Definition {
	if server.PluginID != "" {
		return registry.Get(server.PluginID)
	}
	if strings.EqualFold(server.Name, "agent-task-manager") || strings.Contains(strings.ToLower(server.Command), "agent-task-manager") {
		return registry.Get("agent-task-manager")
	}
	if strings.Contains(strings.ToLower(server.Name), "chrome-devtools") || strings.Contains(strings.ToLower(strings.Join(server.Args, " ")), "chrome-devtools-mcp") {
		return registry.Get("chrome-devtools")
	}
	for key := range server.Env {
		if strings.HasPrefix(strings.ToUpper(key), "AGENT_TASK_MANAGER_") {
			return registry.Get("agent-task-manager")
		}
	}
	return registry.Get("generic")
}

func (registry *Registry) Apply(server model.ManagedServer) model.ManagedServer {
	definition := registry.Resolve(server)
	server.PluginID = definition.ID
	switch definition.ID {
	case "agent-task-manager":
		server = hydrateAgentTaskManagerSettings(server)
	case "chrome-devtools":
		server = hydrateChromeDevToolsSettings(server)
	}
	if server.Settings == nil {
		server.Settings = map[string]any{}
	}
	for _, field := range definition.Fields {
		if _, exists := server.Settings[field.Name]; !exists {
			server.Settings[field.Name] = field.Default
		}
	}
	if definition.ID == "agent-task-manager" {
		server = applyAgentTaskManagerSettings(server)
	}
	if definition.ID == "chrome-devtools" {
		server = applyChromeDevToolsSettings(server)
	}
	return server
}

func (registry *Registry) Validate(server model.ManagedServer) []model.ValidationMessage {
	definition := registry.Resolve(server)
	messages := make([]model.ValidationMessage, 0, 4)
	if server.Name == "" {
		messages = append(messages, model.ValidationMessage{Severity: model.ValidationSeverityError, Path: "server.name", Message: "Server name is required."})
	}
	if server.Command == "" && server.URL == "" {
		messages = append(messages, model.ValidationMessage{Severity: model.ValidationSeverityError, Path: "server.transport", Message: "Each server needs either a command or a URL."})
	}
	if definition.ID == "agent-task-manager" {
		if _, exists := server.Env["AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS"]; exists {
			messages = append(messages, model.ValidationMessage{
				Severity: model.ValidationSeverityError,
				Path:     "server.env.AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS",
				Message:  "The standalone manager must not own ATM required MCP server injection. Keep that deterministic in AgentTaskManager runtime code.",
			})
		}
		if server.Required {
			messages = append(messages, model.ValidationMessage{
				Severity: model.ValidationSeverityWarning,
				Path:     "server.required",
				Message:  "Marking ATM as required here does not replace ATM runtime-owned required MCP injection.",
			})
		}
	}
	return messages
}

func cloneEnv(source map[string]string) map[string]string {
	if source == nil {
		return map[string]string{}
	}
	result := make(map[string]string, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}

func boolString(value any) (string, bool) {
	switch typed := value.(type) {
	case bool:
		if typed {
			return "true", true
		}
		return "false", true
	case string:
		normalized := strings.TrimSpace(strings.ToLower(typed))
		if normalized == "true" || normalized == "false" {
			return normalized, true
		}
		return "", false
	default:
		return "", false
	}
}

func defaultAgentTaskManagerLauncher() string {
	workingDirectory, err := os.Getwd()
	if err != nil {
		return "scripts/agent-task-manager-mcp-stdio.cmd"
	}
	candidates := []string{
		filepath.Join(workingDirectory, "scripts", "agent-task-manager-mcp-stdio.cmd"),
		filepath.Join(filepath.Dir(workingDirectory), "scripts", "agent-task-manager-mcp-stdio.cmd"),
		filepath.Join(workingDirectory, "scripts", "agent-task-manager-mcp-stdio.sh"),
		filepath.Join(filepath.Dir(workingDirectory), "scripts", "agent-task-manager-mcp-stdio.sh"),
	}
	for _, candidate := range candidates {
		if _, err := os.Stat(candidate); err == nil {
			return candidate
		}
	}
	return "scripts/agent-task-manager-mcp-stdio.cmd"
}
