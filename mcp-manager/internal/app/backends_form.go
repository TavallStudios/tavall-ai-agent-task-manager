package app

import (
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/backends"
	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

func buildBackendRegistryFromForm(existing model.BackendRegistry, request formValueReader) (model.BackendRegistry, error) {
	rawJSON := strings.TrimSpace(request.FormValue("backendRegistryJson"))
	if rawJSON != "" {
		registry, err := backends.ParseContent([]byte(rawJSON))
		if err != nil {
			return model.BackendRegistry{}, err
		}
		registry.Path = existing.Path
		return registry, nil
	}

	connectors := make([]model.ManagedBackend, 0, len(existing.Connectors))
	for _, connector := range existing.Connectors {
		prefix := "backend." + connector.ID + "."
		updated := connector
		updated.Enabled = request.FormValue(prefix+"enabled") == "on"
		updated.TransportKind = strings.TrimSpace(request.FormValue(prefix + "transport"))
		updated.Command = strings.TrimSpace(request.FormValue(prefix + "command"))
		updated.URL = strings.TrimSpace(request.FormValue(prefix + "url"))
		updated.Args = splitLines(request.FormValue(prefix + "args"))
		updated.Env = parseEnvBlock(request.FormValue(prefix + "env"))
		connectors = append(connectors, updated)
	}

	existing.Connectors = connectors
	return existing, nil
}

type formValueReader interface {
	FormValue(string) string
}
