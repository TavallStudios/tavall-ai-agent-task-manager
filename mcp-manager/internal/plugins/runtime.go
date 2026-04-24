package plugins

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

func hydrateAgentTaskManagerSettings(server model.ManagedServer) model.ManagedServer {
	server.Settings = cloneSettings(server.Settings)
	setSettingIfMissing(server.Settings, "connectionMode", inferAgentTaskManagerConnectionMode(server))
	setSettingIfMissing(server.Settings, "localLauncherPath", inferAgentTaskManagerLauncher(server))
	setSettingIfMissing(server.Settings, "remoteExecutionEnabled", envBool(server.Env, "AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED", true))
	setSettingIfMissing(server.Settings, "remoteBaseUrl", server.Env["AGENT_TASK_MANAGER_MCP_BASE_URL"])
	setSettingIfMissing(server.Settings, "mcpEndpoint", fallbackValue(server.Env["AGENT_TASK_MANAGER_MCP_ENDPOINT"], "/mcp"))
	setSettingIfMissing(server.Settings, "username", server.Env["AGENT_TASK_MANAGER_USERNAME"])
	setSettingIfMissing(server.Settings, "password", server.Env["AGENT_TASK_MANAGER_PASSWORD"])
	setSettingIfMissing(server.Settings, "downstreamCentralServer", server.Env["AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER"])
	setSettingIfMissing(server.Settings, "noAuthEnabled", envBool(server.Env, "AGENT_TASK_MANAGER_MCP_NO_AUTH_ENABLED", false))
	return server
}

func applyAgentTaskManagerSettings(server model.ManagedServer) model.ManagedServer {
	server.Env = cloneEnv(server.Env)
	connectionMode := strings.TrimSpace(fmt.Sprint(server.Settings["connectionMode"]))
	if connectionMode == "" {
		connectionMode = "local"
	}
	switch strings.ToLower(connectionMode) {
	case "local":
		launcherPath := strings.TrimSpace(fmt.Sprint(server.Settings["localLauncherPath"]))
		if launcherPath == "" || launcherPath == "<nil>" {
			launcherPath = defaultAgentTaskManagerLauncher()
		}
		server.Command, server.Args = launcherCommand(launcherPath)
		server.URL = ""
		server.TransportKind = "stdio"
	case "remote":
		baseURL := strings.TrimSpace(fmt.Sprint(server.Settings["remoteBaseUrl"]))
		endpoint := fallbackValue(fmt.Sprint(server.Settings["mcpEndpoint"]), "/mcp")
		server.URL = joinURL(baseURL, endpoint)
		server.Command = ""
		server.Args = nil
		server.TransportKind = "http"
	}
	setEnvBool(server.Env, "AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED", server.Settings["remoteExecutionEnabled"], true)
	setEnvString(server.Env, "AGENT_TASK_MANAGER_MCP_BASE_URL", server.Settings["remoteBaseUrl"])
	setEnvString(server.Env, "AGENT_TASK_MANAGER_MCP_ENDPOINT", server.Settings["mcpEndpoint"])
	setEnvString(server.Env, "AGENT_TASK_MANAGER_USERNAME", server.Settings["username"])
	setEnvString(server.Env, "AGENT_TASK_MANAGER_PASSWORD", server.Settings["password"])
	setEnvString(server.Env, "AGENT_TASK_MANAGER_CODEX_DOWNSTREAM_CENTRAL_SERVER", server.Settings["downstreamCentralServer"])
	setEnvBool(server.Env, "AGENT_TASK_MANAGER_MCP_NO_AUTH_ENABLED", server.Settings["noAuthEnabled"], false)
	return server
}

func hydrateChromeDevToolsSettings(server model.ManagedServer) model.ManagedServer {
	server.Settings = cloneSettings(server.Settings)
	baseArgs, packageName := unwrapChromeDevToolsArgs(server)
	setSettingIfMissing(server.Settings, "packageName", fallbackValue(packageName, "chrome-devtools-mcp@latest"))
	setSettingIfMissing(server.Settings, "autoConnect", containsArg(baseArgs, "--autoConnect"))
	setSettingIfMissing(server.Settings, "usageStatisticsEnabled", !containsArg(baseArgs, "--no-usage-statistics"))
	setSettingIfMissing(server.Settings, "performanceCruxEnabled", !containsArg(baseArgs, "--no-performance-crux"))
	setSettingIfMissing(server.Settings, "browserExecutablePath", extractFlagValue(baseArgs, "--executablePath"))
	return server
}

func applyChromeDevToolsSettings(server model.ManagedServer) model.ManagedServer {
	packageName := fallbackValue(fmt.Sprint(server.Settings["packageName"]), "chrome-devtools-mcp@latest")
	baseArgs := []string{packageName}
	if boolValue(server.Settings["autoConnect"], true) {
		baseArgs = append(baseArgs, "--autoConnect")
	}
	if !boolValue(server.Settings["usageStatisticsEnabled"], false) {
		baseArgs = append(baseArgs, "--no-usage-statistics")
	}
	if !boolValue(server.Settings["performanceCruxEnabled"], false) {
		baseArgs = append(baseArgs, "--no-performance-crux")
	}
	if executablePath := strings.TrimSpace(fmt.Sprint(server.Settings["browserExecutablePath"])); executablePath != "" {
		baseArgs = append(baseArgs, "--executablePath="+executablePath)
	}

	lowerCommand := strings.ToLower(server.Command)
	switch {
	case strings.HasSuffix(lowerCommand, "cmd.exe") || lowerCommand == "cmd.exe":
		server.Args = append([]string{"/c", "npx", "-y"}, baseArgs...)
	case strings.HasSuffix(lowerCommand, "bash") || strings.HasSuffix(lowerCommand, "bash.exe"):
		server.Args = baseArgs
		server.Command = "npx"
	default:
		if strings.TrimSpace(server.Command) == "" {
			server.Command = "npx"
		}
		if strings.EqualFold(server.Command, "npx") {
			server.Args = append([]string{"-y"}, baseArgs...)
		} else {
			server.Args = append([]string{packageName}, baseArgs[1:]...)
		}
	}
	server.TransportKind = "stdio"
	return server
}

func unwrapChromeDevToolsArgs(server model.ManagedServer) ([]string, string) {
	args := append([]string(nil), server.Args...)
	if len(args) >= 4 && strings.EqualFold(server.Command, "cmd.exe") && strings.EqualFold(args[0], "/c") && strings.EqualFold(args[1], "npx") {
		args = args[3:]
		return args, server.Args[3]
	}
	if len(args) >= 2 && strings.EqualFold(server.Command, "npx") && strings.EqualFold(args[0], "-y") {
		args = args[1:]
		return args, args[0]
	}
	if len(args) > 0 {
		return args, args[0]
	}
	return nil, ""
}

func containsArg(args []string, candidate string) bool {
	for _, arg := range args {
		if strings.EqualFold(arg, candidate) {
			return true
		}
	}
	return false
}

func extractFlagValue(args []string, flagName string) string {
	prefix := flagName + "="
	for _, arg := range args {
		if strings.HasPrefix(arg, prefix) {
			return strings.TrimPrefix(arg, prefix)
		}
	}
	return ""
}

func cloneSettings(source map[string]any) map[string]any {
	if source == nil {
		return map[string]any{}
	}
	result := make(map[string]any, len(source))
	for key, value := range source {
		result[key] = value
	}
	return result
}

func setSettingIfMissing(settings map[string]any, key string, value any) {
	if existing, ok := settings[key]; ok {
		switch typed := existing.(type) {
		case string:
			if strings.TrimSpace(typed) != "" {
				return
			}
		default:
			if existing != nil {
				return
			}
		}
	}
	settings[key] = value
}

func setEnvString(env map[string]string, key string, value any) {
	text := strings.TrimSpace(fmt.Sprint(value))
	if text == "" || text == "<nil>" {
		delete(env, key)
		return
	}
	env[key] = text
}

func setEnvBool(env map[string]string, key string, value any, defaultValue bool) {
	resolved := boolValue(value, defaultValue)
	if resolved == defaultValue {
		if defaultValue {
			env[key] = "true"
		} else {
			delete(env, key)
		}
		return
	}
	if resolved {
		env[key] = "true"
		return
	}
	env[key] = "false"
}

func envBool(env map[string]string, key string, defaultValue bool) bool {
	value, ok := env[key]
	if !ok {
		return defaultValue
	}
	return strings.EqualFold(value, "true")
}

func boolValue(value any, defaultValue bool) bool {
	switch typed := value.(type) {
	case bool:
		return typed
	case string:
		switch strings.TrimSpace(strings.ToLower(typed)) {
		case "true", "1", "yes":
			return true
		case "false", "0", "no":
			return false
		default:
			return defaultValue
		}
	default:
		return defaultValue
	}
}

func fallbackValue(value string, fallback string) string {
	trimmed := strings.TrimSpace(value)
	if trimmed == "" || trimmed == "<nil>" {
		return fallback
	}
	return trimmed
}

func inferAgentTaskManagerConnectionMode(server model.ManagedServer) string {
	if strings.TrimSpace(server.URL) != "" || strings.EqualFold(server.TransportKind, "http") {
		return "remote"
	}
	return "local"
}

func inferAgentTaskManagerLauncher(server model.ManagedServer) string {
	if strings.EqualFold(server.Command, "cmd.exe") && len(server.Args) >= 2 && strings.EqualFold(server.Args[0], "/c") {
		return server.Args[1]
	}
	if strings.EqualFold(server.Command, "bash") && len(server.Args) >= 1 {
		return server.Args[0]
	}
	if strings.TrimSpace(server.Command) != "" && len(server.Args) == 0 {
		return server.Command
	}
	return defaultAgentTaskManagerLauncher()
}

func launcherCommand(launcherPath string) (string, []string) {
	switch strings.ToLower(filepath.Ext(launcherPath)) {
	case ".cmd", ".bat":
		return "cmd.exe", []string{"/c", launcherPath}
	case ".sh":
		return "bash", []string{launcherPath}
	default:
		if _, err := os.Stat(launcherPath); err == nil {
			return launcherPath, nil
		}
		return "cmd.exe", []string{"/c", launcherPath}
	}
}

func joinURL(baseURL string, endpoint string) string {
	baseURL = strings.TrimRight(strings.TrimSpace(baseURL), "/")
	endpoint = strings.TrimSpace(endpoint)
	if baseURL == "" {
		return ""
	}
	if endpoint == "" {
		return baseURL
	}
	if strings.HasPrefix(endpoint, "/") {
		return baseURL + endpoint
	}
	return baseURL + "/" + endpoint
}
