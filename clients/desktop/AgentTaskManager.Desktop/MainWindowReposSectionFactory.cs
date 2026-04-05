using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AgentTaskManager.Desktop;

internal static class MainWindowReposSectionFactory
{
    internal static FrameworkElement BuildReposPage(TabView repoTabView, Button repoNextActionButton)
    {
        var grid = MainWindowElementFactory.CreateTwoColumnGrid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Overview",
            "Repository tabs",
            "Each tab represents one repository and tracks the active session plus deterministic next action.",
            MainWindowElementFactory.BoundTextBlock("Repos.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Repos.SelectedStatusLine", foreground: MainWindowElementFactory.TextSecondaryBrush),
            repoNextActionButton,
            MainWindowElementFactory.BoundTextBlock("Repos.SelectedNextActionDescription", foreground: MainWindowElementFactory.TextMutedBrush)), 0, 0);

        MainWindowElementFactory.AddToGrid(grid, MainWindowElementFactory.BuildContentCard(
            "Tabs",
            "Repo navigation",
            "Select a repository tab to focus its active session and stream updates for that repo.",
            repoTabView), 0, 1);

        return MainWindowElementFactory.BuildPage(
            "Repos",
            "Repository control",
            "Track repositories like IDE tabs while the selected tab stays live with backend session updates.",
            grid);
    }
}
