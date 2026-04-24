using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class SessionListViewModel : ObservableObject
{
    private SessionSummaryDto? _selectedSession;
    private string _statusMessage = "No sessions loaded.";

    public SessionSummaryDto? SelectedSession
    {
        get => _selectedSession;
        set => SetProperty(ref _selectedSession, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public ObservableCollection<SessionSummaryDto> Sessions { get; } = new();

    public void ReplaceSessions(IEnumerable<SessionSummaryDto> sessions)
    {
        string? selectedSessionId = SelectedSession?.SessionId;
        List<SessionSummaryDto> ordered = sessions
            .OrderByDescending(item => item.LastEventAt)
            .ToList();
        Sessions.ReplaceWith(ordered);
        SelectedSession = selectedSessionId == null
            ? ordered.FirstOrDefault()
            : ordered.FirstOrDefault(item => item.SessionId == selectedSessionId) ?? ordered.FirstOrDefault();
        StatusMessage = Sessions.Count == 0
            ? "No sessions are available for this account."
            : $"Loaded {Sessions.Count} recent sessions.";
    }

    public void Upsert(SessionSummaryDto summary)
    {
        int index = -1;
        for (int i = 0; i < Sessions.Count; i++)
        {
            if (Sessions[i].SessionId == summary.SessionId)
            {
                index = i;
                break;
            }
        }

        if (index >= 0)
        {
            Sessions[index] = summary;
        }
        else
        {
            Sessions.Insert(0, summary);
        }

        if (SelectedSession == null || SelectedSession.SessionId == summary.SessionId)
        {
            SelectedSession = summary;
        }
    }

    public void Clear()
    {
        Sessions.Clear();
        SelectedSession = null;
        StatusMessage = "No sessions loaded.";
    }
}
