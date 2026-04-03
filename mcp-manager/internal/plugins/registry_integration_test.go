package plugins

import (
	"strings"
	"testing"

	"github.com/agenttaskmanager/mcp-manager/internal/model"
)

func TestAgentTaskManagerPluginAppliesDefaultsAndBlocksRequiredInjection(t *testing.T) {
	registry := NewRegistry()
	applied := registry.Apply(model.ManagedServer{
		Name: "agent-task-manager",
		Env:  map[string]string{},
	})

	if applied.PluginID != "agent-task-manager" {
		t.Fatalf("expected ATM plugin id, got %s", applied.PluginID)
	}
	if applied.Settings["connectionMode"] != "local" {
		t.Fatalf("expected local connectionMode default, got %v", applied.Settings["connectionMode"])
	}
	if applied.Settings["remoteExecutionEnabled"] != true {
		t.Fatalf("expected remoteExecutionEnabled default to be true, got %v", applied.Settings["remoteExecutionEnabled"])
	}
	if applied.Env["AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED"] != "true" {
		t.Fatalf(
			"expected remote execution env to stay enabled for local ATM proxying, got %s",
			applied.Env["AGENT_TASK_MANAGER_CODEX_REMOTE_TOOL_EXECUTION_ENABLED"],
		)
	}
	if applied.Env["AGENT_TASK_MANAGER_MCP_ENDPOINT"] != "/mcp" {
		t.Fatalf("expected MCP endpoint default, got %s", applied.Env["AGENT_TASK_MANAGER_MCP_ENDPOINT"])
	}

	findings := registry.Validate(model.ManagedServer{
		Name: "agent-task-manager",
		Env: map[string]string{
			"AGENT_TASK_MANAGER_CODEX_REQUIRED_MCP_SERVERS": "filesystem,ripgrep",
		},
	})
	if len(findings) == 0 {
		t.Fatal("expected validation findings for forbidden required MCP injection")
	}
}

func TestChromeDevToolsPluginExtractsAndRewritesLaunchArgs(t *testing.T) {
	registry := NewRegistry()
	applied := registry.Apply(model.ManagedServer{
		Name:    "chrome-devtools-local",
		Command: "cmd.exe",
		Args: []string{
			"/c",
			"npx",
			"-y",
			"chrome-devtools-mcp@latest",
			"--autoConnect",
			"--no-usage-statistics",
			"--no-performance-crux",
			"--executablePath=C:\\Program Files\\Google\\Chrome\\chrome.exe",
		},
	})

	if applied.PluginID != "chrome-devtools" {
		t.Fatalf("expected chrome-devtools plugin id, got %s", applied.PluginID)
	}
	if applied.Settings["autoConnect"] != true {
		t.Fatalf("expected autoConnect true, got %v", applied.Settings["autoConnect"])
	}
	if applied.Settings["usageStatisticsEnabled"] != false {
		t.Fatalf("expected usageStatisticsEnabled false, got %v", applied.Settings["usageStatisticsEnabled"])
	}
	if applied.Settings["performanceCruxEnabled"] != false {
		t.Fatalf("expected performanceCruxEnabled false, got %v", applied.Settings["performanceCruxEnabled"])
	}
	if applied.Settings["browserExecutablePath"] != "C:\\Program Files\\Google\\Chrome\\chrome.exe" {
		t.Fatalf("unexpected executable path: %v", applied.Settings["browserExecutablePath"])
	}

	applied.Settings["autoConnect"] = false
	applied.Settings["usageStatisticsEnabled"] = true
	applied.Settings["performanceCruxEnabled"] = true
	applied.Settings["browserExecutablePath"] = "D:\\Browsers\\Chrome\\chrome.exe"
	rewritten := registry.Apply(applied)
	joined := strings.Join(rewritten.Args, " ")
	if strings.Contains(joined, "--autoConnect") {
		t.Fatalf("expected autoConnect flag removed, got %s", joined)
	}
	if strings.Contains(joined, "--no-usage-statistics") {
		t.Fatalf("expected usage statistics flag removed, got %s", joined)
	}
	if strings.Contains(joined, "--no-performance-crux") {
		t.Fatalf("expected performance crux flag removed, got %s", joined)
	}
	if !strings.Contains(joined, "--executablePath=D:\\Browsers\\Chrome\\chrome.exe") {
		t.Fatalf("expected updated executable path, got %s", joined)
	}
}
