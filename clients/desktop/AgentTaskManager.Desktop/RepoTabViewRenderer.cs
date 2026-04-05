using AgentTaskManager.Desktop.Contracts;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AgentTaskManager.Desktop;

internal sealed class RepoTabViewRenderer
{
    private readonly TabView _tabView;

    internal RepoTabViewRenderer(TabView tabView)
    {
        _tabView = tabView;
    }

    internal bool IsSyncing { get; private set; }

    internal void Rebuild(IReadOnlyList<RepoTabSummary> summaries, string? selectedRepoKey)
    {
        IsSyncing = true;
        try
        {
            _tabView.TabItems.Clear();
            foreach (RepoTabSummary summary in summaries)
            {
                _tabView.TabItems.Add(new TabViewItem
                {
                    Header = BuildHeader(summary),
                    Content = BuildContent(summary),
                    Tag = summary.RepoKey
                });
            }

            SyncSelected(selectedRepoKey);
        }
        finally
        {
            IsSyncing = false;
        }
    }

    internal void SyncSelected(string? selectedRepoKey)
    {
        bool priorSyncState = IsSyncing;
        IsSyncing = true;
        try
        {
            _tabView.SelectedItem = string.IsNullOrWhiteSpace(selectedRepoKey)
                ? _tabView.TabItems.FirstOrDefault()
                : _tabView.TabItems
                    .OfType<TabViewItem>()
                    .FirstOrDefault(item =>
                        string.Equals(item.Tag as string, selectedRepoKey, StringComparison.OrdinalIgnoreCase));
        }
        finally
        {
            IsSyncing = priorSyncState;
        }
    }

    internal bool TryGetSelectedRepoKey(out string repoKey)
    {
        if (_tabView.SelectedItem is TabViewItem tabItem && tabItem.Tag is string key && !string.IsNullOrWhiteSpace(key))
        {
            repoKey = key;
            return true;
        }

        repoKey = string.Empty;
        return false;
    }

    private static UIElement BuildHeader(RepoTabSummary summary)
        => new StackPanel
        {
            Spacing = 2,
            Children =
            {
                new TextBlock
                {
                    Text = summary.HeaderTitle,
                    FontWeight = MainWindowElementFactory.SemiBoldWeight
                },
                new TextBlock
                {
                    Text = summary.HeaderSubtitle,
                    Opacity = 0.8,
                    FontSize = 11
                }
            }
        };

    private static UIElement BuildContent(RepoTabSummary summary)
        => new StackPanel
        {
            Spacing = 6,
            Padding = new Thickness(8, 4, 8, 4),
            Children =
            {
                new TextBlock
                {
                    Text = summary.StatusLine,
                    TextWrapping = TextWrapping.Wrap
                },
                new TextBlock
                {
                    Text = $"Next: {summary.NextActionLabel}",
                    Opacity = 0.8
                }
            }
        };
}
