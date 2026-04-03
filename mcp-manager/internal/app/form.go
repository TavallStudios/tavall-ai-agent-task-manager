package app

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
	"github.com/agenttaskmanager/mcp-manager/internal/plugins"
)

func buildProfileFromForm(base model.ManagedProfile, catalog []plugins.Definition, request *http.Request, selectedServerName string) (model.ManagedProfile, error) {
	definitions := make(map[string]plugins.Definition, len(catalog))
	for _, definition := range catalog {
		definitions[definition.ID] = definition
	}

	servers := make([]model.ManagedServer, 0, len(base.Servers))
	for _, server := range base.Servers {
		if selectedServerName != "" && !strings.EqualFold(server.Name, selectedServerName) {
			servers = append(servers, server)
			continue
		}
		prefix := "server." + server.Name + "."
		updated := server
		updated.Enabled = request.FormValue(prefix+"enabled") == "on"
		updated.Required = request.FormValue(prefix+"required") == "on"
		updated.Command = strings.TrimSpace(request.FormValue(prefix + "command"))
		updated.URL = strings.TrimSpace(request.FormValue(prefix + "url"))
		updated.TransportKind = strings.TrimSpace(request.FormValue(prefix + "transport"))
		updated.PluginID = strings.TrimSpace(request.FormValue(prefix + "plugin"))
		updated.Preset = strings.TrimSpace(request.FormValue(prefix + "preset"))
		updated.Args = splitLines(request.FormValue(prefix + "args"))
		updated.Env = parseEnvBlock(request.FormValue(prefix + "env"))
		extra, err := parseJSONObject(request.FormValue(prefix + "extraJson"))
		if err != nil {
			return base, fmt.Errorf("%s extra config: %w", server.Name, err)
		}
		updated.Extra = sanitizeExtra(extra)

		definition, ok := definitions[updated.PluginID]
		if !ok {
			definition = definitions["generic"]
			updated.PluginID = definition.ID
		}
		updated.Settings, err = parseSettings(request, prefix, definition, updated.Settings)
		if err != nil {
			return base, fmt.Errorf("%s settings: %w", server.Name, err)
		}
		servers = append(servers, updated)
	}

	base.Servers = servers
	return base, nil
}

func applyQuickAction(profile model.ManagedProfile, catalog []plugins.Definition, selectedServerName string, action string) model.ManagedProfile {
	if selectedServerName == "" || action == "" {
		return profile
	}

	definitions := make(map[string]plugins.Definition, len(catalog))
	for _, definition := range catalog {
		definitions[definition.ID] = definition
	}

	for index, server := range profile.Servers {
		if !strings.EqualFold(server.Name, selectedServerName) {
			continue
		}
		switch {
		case action == "quick-enable":
			server.Enabled = true
		case action == "quick-disable":
			server.Enabled = false
		case action == "quick-optional":
			server.Required = false
		case action == "quick-required":
			server.Required = true
		case action == "quick-transport-stdio":
			server.TransportKind = "stdio"
		case action == "quick-transport-http":
			server.TransportKind = "http"
		case action == "quick-clear-credentials":
			if server.Settings == nil {
				server.Settings = map[string]any{}
			}
			delete(server.Env, "AGENT_TASK_MANAGER_USERNAME")
			delete(server.Env, "AGENT_TASK_MANAGER_PASSWORD")
			server.Settings["username"] = ""
			server.Settings["password"] = ""
		case action == "quick-connection-local":
			if server.Settings == nil {
				server.Settings = map[string]any{}
			}
			server.Settings["connectionMode"] = "local"
		case action == "quick-connection-remote":
			if server.Settings == nil {
				server.Settings = map[string]any{}
			}
			server.Settings["connectionMode"] = "remote"
		case strings.HasPrefix(action, "quick-preset:"):
			presetID := strings.TrimPrefix(action, "quick-preset:")
			definition := definitions[server.PluginID]
			for _, preset := range definition.Presets {
				if preset.ID != presetID {
					continue
				}
				if server.Settings == nil {
					server.Settings = map[string]any{}
				}
				for key, value := range preset.Values {
					server.Settings[key] = value
				}
			}
		}
		profile.Servers[index] = server
	}
	return profile
}

func parseSettings(request *http.Request, prefix string, definition plugins.Definition, existing map[string]any) (map[string]any, error) {
	settings, err := parseJSONObject(request.FormValue(prefix + "settingsJson"))
	if err != nil {
		return nil, err
	}
	if len(settings) == 0 {
		settings = make(map[string]any, len(existing)+len(definition.Fields))
		for key, value := range existing {
			settings[key] = value
		}
	}
	for _, field := range definition.Fields {
		fieldName := prefix + "field." + field.Name
		switch field.Type {
		case plugins.FieldTypeBool:
			settings[field.Name] = request.FormValue(fieldName) == "on"
		case plugins.FieldTypeString, plugins.FieldTypeSelect:
			value := strings.TrimSpace(request.FormValue(fieldName))
			if value == "" {
				settings[field.Name] = field.Default
				continue
			}
			settings[field.Name] = value
		default:
			settings[field.Name] = request.FormValue(fieldName)
		}
	}
	return settings, nil
}

func splitLines(value string) []string {
	parts := strings.FieldsFunc(value, func(r rune) bool {
		return r == '\r' || r == '\n'
	})
	items := make([]string, 0, len(parts))
	for _, part := range parts {
		trimmed := strings.TrimSpace(part)
		if trimmed != "" {
			items = append(items, trimmed)
		}
	}
	return items
}

func parseEnvBlock(value string) map[string]string {
	result := map[string]string{}
	for _, line := range splitLines(value) {
		left, right, found := strings.Cut(line, "=")
		if !found {
			continue
		}
		result[strings.TrimSpace(left)] = strings.TrimSpace(right)
	}
	return result
}

func parseJSONObject(value string) (map[string]any, error) {
	if strings.TrimSpace(value) == "" {
		return map[string]any{}, nil
	}
	var parsed map[string]any
	if err := json.Unmarshal([]byte(value), &parsed); err != nil {
		return nil, err
	}
	if parsed == nil {
		return map[string]any{}, nil
	}
	return parsed, nil
}

func sanitizeExtra(value map[string]any) map[string]any {
	delete(value, "command")
	delete(value, "args")
	delete(value, "env")
	delete(value, "url")
	delete(value, "transport")
	delete(value, "required")
	delete(value, "disabled")
	return value
}
