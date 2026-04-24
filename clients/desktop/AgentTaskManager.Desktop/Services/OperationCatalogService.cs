using AgentTaskManager.Desktop.Contracts;
using System.Net.Http.Json;
using System.Text.Json;

namespace AgentTaskManager.Desktop.Services;

public sealed class OperationCatalogService : IOperationCatalogService
{
    private readonly IBackendAuthService _backendAuthService;
    private readonly IDesktopConnectionSettingsService _connectionSettingsService;

    public OperationCatalogService(
        IBackendAuthService backendAuthService,
        IDesktopConnectionSettingsService connectionSettingsService)
    {
        _backendAuthService = backendAuthService;
        _connectionSettingsService = connectionSettingsService;
    }

    public async Task<IReadOnlyList<OperationGroupDto>> ListOperationGroupsAsync(CancellationToken cancellationToken)
    {
        try
        {
            await _connectionSettingsService.EnsureBackendTransportAsync(cancellationToken);
            using var client = new HttpClient
            {
                BaseAddress = _backendAuthService.GetBackendBaseUri()
            };
            using var request = new HttpRequestMessage(HttpMethod.Get, "api/codex-client/operations");
            _backendAuthService.ApplyAuthentication(request);
            using HttpResponseMessage response = await client.SendAsync(request, cancellationToken);
            if (!response.IsSuccessStatusCode)
            {
                return Fallback();
            }

            JsonElement payload = await response.Content.ReadFromJsonAsync<JsonElement>(DesktopJson.Default, cancellationToken);
            IReadOnlyList<OperationGroupDto> parsed = ParseGroups(payload);
            return parsed.Count == 0 ? Fallback() : parsed;
        }
        catch
        {
            return Fallback();
        }
    }

    private static IReadOnlyList<OperationGroupDto> ParseGroups(JsonElement payload)
    {
        if (payload.ValueKind != JsonValueKind.Object)
        {
            return Array.Empty<OperationGroupDto>();
        }

        if (!payload.TryGetProperty("groups", out JsonElement groups) || groups.ValueKind != JsonValueKind.Array)
        {
            return Array.Empty<OperationGroupDto>();
        }

        var result = new List<OperationGroupDto>();
        foreach (JsonElement group in groups.EnumerateArray())
        {
            string groupKey = ReadString(group, "groupKey", "key", "name");
            string displayName = ReadString(group, "displayName", "title", "name");
            string summary = ReadString(group, "summary", "description");
            var operations = new List<OperationDescriptorDto>();
            if (group.TryGetProperty("operations", out JsonElement operationsElement)
                && operationsElement.ValueKind == JsonValueKind.Array)
            {
                foreach (JsonElement operation in operationsElement.EnumerateArray())
                {
                    operations.Add(new OperationDescriptorDto(
                        ReadString(operation, "operationKey", "key", "name"),
                        ReadString(operation, "displayName", "title", "name"),
                        ReadString(operation, "summary", "description"),
                        ReadBoolean(operation, "enabled", true),
                        ReadString(operation, "source", fallback: "backend")));
                }
            }

            result.Add(new OperationGroupDto(
                string.IsNullOrWhiteSpace(groupKey) ? Guid.NewGuid().ToString("N") : groupKey,
                string.IsNullOrWhiteSpace(displayName) ? "Operations" : displayName,
                summary,
                operations));
        }

        return result;
    }

    private static IReadOnlyList<OperationGroupDto> Fallback()
        =>
        [
            new OperationGroupDto(
                "delegation-and-gate",
                "Delegation and Gate",
                "Codex-native delegation runs and fail-closed approval controls.",
                [
                    new OperationDescriptorDto("startDelegationRun", "Start Delegation Run", "Start canonical delegation-run orchestration.", true, "fallback"),
                    new OperationDescriptorDto("completeDelegationRun", "Complete Delegation Run", "Finalize run state and persist summary.", true, "fallback"),
                    new OperationDescriptorDto("runHarnessApprovalGate", "Run Approval Gate", "Enforce cleanup, validation, patch scope, and integration tests.", true, "fallback"),
                    new OperationDescriptorDto("runJavaLintValidation", "Run Java Lint", "Run Checkstyle, PMD, and Error Prone lint checks.", true, "fallback")
                ]),
            new OperationGroupDto(
                "memory",
                "Memory",
                "Thread, semantic, and prior-fix memory operations.",
                [
                    new OperationDescriptorDto("searchRelatedContexts", "Search Related Contexts", "Load related semantic context chunks.", true, "fallback"),
                    new OperationDescriptorDto("searchPriorFixes", "Search Prior Fixes", "Retrieve related fix history.", true, "fallback"),
                    new OperationDescriptorDto("loadRelatedSemanticContext", "Load Semantic Context", "Hydrate task context from semantic memory.", true, "fallback")
                ]),
            new OperationGroupDto(
                "computer-use",
                "Computer Use",
                "Runner registration, remote sessions, capture, and input orchestration.",
                [
                    new OperationDescriptorDto("registerComputerUseRunner", "Register Runner", "Register external automation runners.", true, "fallback"),
                    new OperationDescriptorDto("startComputerUseSession", "Start Session", "Start a computer-use session on a selected runner.", true, "fallback"),
                    new OperationDescriptorDto("captureComputerUseWindow", "Capture Window", "Capture and optionally persist window/frame artifacts.", true, "fallback")
                ])
        ];

    private static string ReadString(JsonElement element, string first, string second = "", string third = "", string fallback = "")
    {
        if (TryReadString(element, first, out string value))
        {
            return value;
        }

        if (!string.IsNullOrWhiteSpace(second) && TryReadString(element, second, out value))
        {
            return value;
        }

        if (!string.IsNullOrWhiteSpace(third) && TryReadString(element, third, out value))
        {
            return value;
        }

        return fallback;
    }

    private static bool TryReadString(JsonElement element, string name, out string value)
    {
        value = string.Empty;
        if (!element.TryGetProperty(name, out JsonElement property))
        {
            return false;
        }

        if (property.ValueKind != JsonValueKind.String)
        {
            return false;
        }

        value = property.GetString() ?? string.Empty;
        return true;
    }

    private static bool ReadBoolean(JsonElement element, string propertyName, bool fallback)
    {
        if (!element.TryGetProperty(propertyName, out JsonElement property))
        {
            return fallback;
        }

        return property.ValueKind == JsonValueKind.True
            ? true
            : property.ValueKind == JsonValueKind.False
                ? false
                : fallback;
    }
}
