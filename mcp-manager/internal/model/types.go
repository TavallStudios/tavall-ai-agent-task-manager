package model

import "time"

type DocumentFormat string

const (
	DocumentFormatJSON DocumentFormat = "json"
	DocumentFormatTOML DocumentFormat = "toml"
)

type ValidationSeverity string

const (
	ValidationSeverityInfo    ValidationSeverity = "info"
	ValidationSeverityWarning ValidationSeverity = "warning"
	ValidationSeverityError   ValidationSeverity = "error"
)

type SourceDocument struct {
	ID              string
	Path            string
	SourceKind      string
	Scope           string
	Format          DocumentFormat
	ServerRootKey   string
	Writable        bool
	DiscoveredAt    time.Time
	TopLevel        map[string]any
	Servers         []ManagedServer
	ManagerMetadata map[string]any
}

type DocumentSummary struct {
	ID            string         `json:"id"`
	Path          string         `json:"path"`
	SourceKind    string         `json:"sourceKind"`
	Scope         string         `json:"scope"`
	Format        DocumentFormat `json:"format"`
	ServerCount   int            `json:"serverCount"`
	Writable      bool           `json:"writable"`
	DiscoveredAt  time.Time      `json:"discoveredAt"`
	HasManagerCfg bool           `json:"hasManagerConfig"`
}

type ManagedProfile struct {
	DocumentID  string              `json:"documentId"`
	Path        string              `json:"path"`
	SourceKind  string              `json:"sourceKind"`
	Scope       string              `json:"scope"`
	Format      DocumentFormat      `json:"format"`
	GeneratedAt time.Time           `json:"generatedAt"`
	Servers     []ManagedServer     `json:"servers"`
	Validation  []ValidationMessage `json:"validation"`
}

type BackendRegistry struct {
	Path          string           `json:"path"`
	CentralServer string           `json:"centralServer"`
	Connectors    []ManagedBackend `json:"connectors"`
}

type ManagedServer struct {
	Name          string            `json:"name"`
	PluginID      string            `json:"pluginId"`
	Enabled       bool              `json:"enabled"`
	Required      bool              `json:"required"`
	TransportKind string            `json:"transportKind"`
	Command       string            `json:"command"`
	Args          []string          `json:"args"`
	URL           string            `json:"url"`
	Env           map[string]string `json:"env"`
	Settings      map[string]any    `json:"settings"`
	Preset        string            `json:"preset"`
	Scope         string            `json:"scope"`
	Source        string            `json:"source"`
	HealthStatus  string            `json:"healthStatus"`
	Extra         map[string]any    `json:"extra"`
}

type ManagedTool struct {
	Key             string `json:"key"`
	Name            string `json:"name"`
	DisplayName     string `json:"displayName"`
	Summary         string `json:"summary"`
	Category        string `json:"category"`
	Source          string `json:"source"`
	SettingsHint    string `json:"settingsHint"`
	BackendID       string `json:"backendId,omitempty"`
	BackendName     string `json:"backendName,omitempty"`
	OwnerDocumentID string `json:"ownerDocumentId"`
	OwnerServerName string `json:"ownerServerName"`
	OwnerPluginID   string `json:"ownerPluginId"`
	OwnerEnabled    bool   `json:"ownerEnabled"`
}

type ManagedBackend struct {
	ID            string            `json:"id"`
	DisplayName   string            `json:"displayName"`
	Enabled       bool              `json:"enabled"`
	TransportKind string            `json:"transportKind"`
	Command       string            `json:"command"`
	Args          []string          `json:"args"`
	URL           string            `json:"url"`
	Env           map[string]string `json:"env"`
	Source        string            `json:"source"`
	HealthStatus  string            `json:"healthStatus"`
	ToolCache     []ManagedTool     `json:"toolCache"`
}

type ValidationMessage struct {
	Severity ValidationSeverity `json:"severity"`
	Path     string             `json:"path"`
	Message  string             `json:"message"`
}

type HistoryEntry struct {
	ID         string    `json:"id"`
	DocumentID string    `json:"documentId"`
	Path       string    `json:"path"`
	BackupPath string    `json:"backupPath"`
	CreatedAt  time.Time `json:"createdAt"`
}

type RootSettings struct {
	Roots      []string `json:"roots"`
	ManagedHub string   `json:"managedHub"`
}
