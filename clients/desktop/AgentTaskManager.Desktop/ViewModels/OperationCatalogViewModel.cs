using AgentTaskManager.Desktop.Contracts;
using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.Utility;
using CommunityToolkit.Mvvm.ComponentModel;
using System.Collections.ObjectModel;

namespace AgentTaskManager.Desktop.ViewModels;

public sealed class OperationCatalogViewModel : ObservableObject
{
    private readonly IOperationCatalogService _operationCatalogService;
    private string _statusMessage = "Operation catalog not loaded.";
    private OperationGroupDto? _selectedGroup;
    private bool _isBusy;

    public OperationCatalogViewModel(IOperationCatalogService operationCatalogService)
    {
        _operationCatalogService = operationCatalogService;
    }

    public ObservableCollection<OperationGroupDto> Groups { get; } = new();

    public OperationGroupDto? SelectedGroup
    {
        get => _selectedGroup;
        set => SetProperty(ref _selectedGroup, value);
    }

    public string StatusMessage
    {
        get => _statusMessage;
        set => SetProperty(ref _statusMessage, value);
    }

    public bool IsBusy
    {
        get => _isBusy;
        set => SetProperty(ref _isBusy, value);
    }

    public async Task RefreshAsync(CancellationToken cancellationToken)
    {
        IsBusy = true;
        try
        {
            IReadOnlyList<OperationGroupDto> groups = await _operationCatalogService.ListOperationGroupsAsync(cancellationToken);
            Groups.ReplaceWith(groups.OrderBy(item => item.DisplayName));
            if (SelectedGroup == null || Groups.All(group => group.GroupKey != SelectedGroup.GroupKey))
            {
                SelectedGroup = Groups.FirstOrDefault();
            }

            StatusMessage = $"Loaded {Groups.Count} operation group(s).";
        }
        catch (Exception exception)
        {
            StatusMessage = exception.Message;
        }
        finally
        {
            IsBusy = false;
        }
    }
}

