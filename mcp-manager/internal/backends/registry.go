package backends

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

const FileName = "tavall-ai-backends.json"

type fileModel struct {
	Version       int                    `json:"version"`
	CentralServer string                 `json:"centralServer"`
	Connectors    []model.ManagedBackend `json:"connectors"`
}

func DefaultPathForDocument(documentPath string) string {
	return filepath.Join(filepath.Dir(documentPath), FileName)
}

func Load(path string) (model.BackendRegistry, error) {
	absolutePath, err := filepath.Abs(path)
	if err != nil {
		return model.BackendRegistry{}, fmt.Errorf("resolve backend registry path: %w", err)
	}
	content, err := os.ReadFile(absolutePath)
	if err != nil {
		if os.IsNotExist(err) {
			return model.BackendRegistry{Path: absolutePath, CentralServer: "tavall-ai", Connectors: []model.ManagedBackend{}}, nil
		}
		return model.BackendRegistry{}, fmt.Errorf("read backend registry: %w", err)
	}
	var payload fileModel
	if err := json.Unmarshal(content, &payload); err != nil {
		return model.BackendRegistry{}, fmt.Errorf("parse backend registry: %w", err)
	}
	registry := model.BackendRegistry{
		Path:          absolutePath,
		CentralServer: fallbackString(payload.CentralServer, "tavall-ai"),
		Connectors:    normalizeConnectors(payload.Connectors),
	}
	return registry, nil
}

func Render(registry model.BackendRegistry) ([]byte, error) {
	payload := fileModel{
		Version:       1,
		CentralServer: fallbackString(registry.CentralServer, "tavall-ai"),
		Connectors:    normalizeConnectors(registry.Connectors),
	}
	content, err := json.MarshalIndent(payload, "", "  ")
	if err != nil {
		return nil, fmt.Errorf("render backend registry: %w", err)
	}
	return append(content, '\n'), nil
}

func ParseContent(content []byte) (model.BackendRegistry, error) {
	var payload fileModel
	if err := json.Unmarshal(content, &payload); err != nil {
		return model.BackendRegistry{}, fmt.Errorf("parse backend registry: %w", err)
	}
	return model.BackendRegistry{
		CentralServer: fallbackString(payload.CentralServer, "tavall-ai"),
		Connectors:    normalizeConnectors(payload.Connectors),
	}, nil
}

func normalizeConnectors(connectors []model.ManagedBackend) []model.ManagedBackend {
	result := make([]model.ManagedBackend, 0, len(connectors))
	for _, connector := range connectors {
		connector.DisplayName = fallbackString(connector.DisplayName, connector.ID)
		if connector.Args == nil {
			connector.Args = []string{}
		}
		if connector.Env == nil {
			connector.Env = map[string]string{}
		}
		if connector.ToolCache == nil {
			connector.ToolCache = []model.ManagedTool{}
		}
		result = append(result, connector)
	}
	return result
}

func fallbackString(value string, fallback string) string {
	if strings.TrimSpace(value) == "" {
		return fallback
	}
	return value
}


