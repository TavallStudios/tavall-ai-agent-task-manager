using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace AgentTaskManager.Desktop;

internal static class MainWindowShellChromeFactory
{
    internal static FrameworkElement BuildSidebar(
        Button useLocalModeButton,
        Button useRemoteModeButton,
        Button detectRemoteDefaultsButton,
        Button connectTransportButton,
        Button disconnectTransportButton,
        params (string Header, FrameworkElement View)[] sections)
    {
        var container = new StackPanel
        {
            Margin = new Thickness(16, 16, 16, 16),
            Spacing = 16
        };
        container.Children.Add(BuildBrandCard());
        container.Children.Add(MainWindowElementFactory.BuildSidebarCard(
            "Environment",
            MainWindowElementFactory.BoundTextBlock("Connection.ProfileLabel", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("Connection.ModeSummary", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Connection.EffectiveBackendBaseUrl", foreground: MainWindowElementFactory.TextSecondaryBrush, mono: true),
            useRemoteModeButton,
            useLocalModeButton,
            detectRemoteDefaultsButton,
            connectTransportButton,
            disconnectTransportButton));
        container.Children.Add(BuildNavigationCard(sections));
        container.Children.Add(MainWindowElementFactory.BuildSidebarCard(
            "Operator",
            MainWindowElementFactory.BoundTextBlock("SignIn.DisplayName", fontWeight: MainWindowElementFactory.SemiBoldWeight),
            MainWindowElementFactory.BoundTextBlock("SignIn.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Codex.StatusMessage", foreground: MainWindowElementFactory.TextSecondaryBrush),
            MainWindowElementFactory.BoundTextBlock("Connection.TransportStatus", foreground: MainWindowElementFactory.TextMutedBrush)));

        return new Border
        {
            Background = MainWindowElementFactory.SidebarBackgroundBrush,
            BorderBrush = MainWindowElementFactory.SidebarBorderBrush,
            BorderThickness = new Thickness(0, 0, 1, 0),
            Child = new ScrollViewer
            {
                Content = container,
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto
            }
        };
    }

    internal static FrameworkElement BuildHeaderPanel()
    {
        var headerGrid = new Grid { ColumnSpacing = 18 };
        headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        headerGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        headerGrid.Children.Add(new StackPanel
        {
            Spacing = 6,
            Children =
            {
                MainWindowElementFactory.Label("Operator Surface", MainWindowElementFactory.BoldWeight, 28),
                MainWindowElementFactory.Label(
                    "Drive Work, Operations, Remote, and Settings from one desktop operator surface with managed compatibility adapters.",
                    MainWindowElementFactory.MediumWeight,
                    13,
                    MainWindowElementFactory.TextSecondaryBrush),
                MainWindowElementFactory.BoundTextBlock("Connection.ModeSummary", foreground: MainWindowElementFactory.TextSecondaryBrush)
            }
        });

        var endpointChip = new Border
        {
            Background = DesktopShellTheme.AccentSurface,
            BorderBrush = DesktopShellTheme.Accent,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(999),
            Padding = new Thickness(16, 10, 16, 10),
            Child = MainWindowElementFactory.BoundTextBlock("Connection.EffectiveBackendBaseUrl", mono: true)
        };
        Grid.SetColumn(endpointChip, 1);
        headerGrid.Children.Add(endpointChip);

        return new Border
        {
            Background = DesktopShellTheme.CardBackground,
            BorderBrush = DesktopShellTheme.CardBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(20),
            Margin = new Thickness(16, 16, 16, 16),
            Padding = new Thickness(20),
            Child = headerGrid
        };
    }

    internal static FrameworkElement BuildMetricStrip()
    {
        var grid = new Grid
        {
            Margin = new Thickness(16, 0, 16, 16),
            ColumnSpacing = 12
        };
        for (int index = 0; index < 5; index++)
        {
            grid.ColumnDefinitions.Add(new ColumnDefinition());
        }

        AddMetric(grid, MainWindowElementFactory.BuildHeaderMetric("Session", "StatusStrip.SessionStatus"), 0);
        AddMetric(grid, MainWindowElementFactory.BuildHeaderMetric("Runtime", "StatusStrip.RuntimeStatus"), 1);
        AddMetric(grid, MainWindowElementFactory.BuildHeaderMetric("Lease", "StatusStrip.LeaseStatus"), 2);
        AddMetric(grid, MainWindowElementFactory.BuildHeaderMetric("Stream", "StatusStrip.StreamStatus"), 3);
        AddMetric(grid, MainWindowElementFactory.BuildHeaderMetric("Device", "StatusStrip.DeviceStatus"), 4);
        return grid;
    }

    private static FrameworkElement BuildBrandCard()
    {
        var brandRow = new Grid { ColumnSpacing = 12 };
        brandRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        brandRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var glyph = new Border
        {
            Width = 42,
            Height = 42,
            Background = MainWindowElementFactory.AccentSurfaceBrush,
            BorderBrush = MainWindowElementFactory.AccentBrush,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Child = new TextBlock
            {
                Text = "C",
                Foreground = MainWindowElementFactory.AccentBrush,
                FontSize = 18,
                FontWeight = MainWindowElementFactory.BoldWeight,
                HorizontalAlignment = HorizontalAlignment.Center,
                VerticalAlignment = VerticalAlignment.Center
            }
        };
        brandRow.Children.Add(glyph);
        var brandText = new StackPanel
        {
            Spacing = 2,
            Children =
            {
                MainWindowElementFactory.Label("Codex Operator", MainWindowElementFactory.BoldWeight, 20),
                MainWindowElementFactory.Label("AgentTaskManager desktop surface", MainWindowElementFactory.MediumWeight, 12, MainWindowElementFactory.TextSecondaryBrush)
            }
        };
        Grid.SetColumn(brandText, 1);
        brandRow.Children.Add(brandText);

        return MainWindowElementFactory.BuildSidebarCard(
            "Workspace",
            brandRow,
            MainWindowElementFactory.Label(
                "Custom session launch, review, diagnostics, backend routing, and local Codex reuse inside a Codex-style Windows shell.",
                MainWindowElementFactory.MediumWeight,
                13,
                MainWindowElementFactory.TextSecondaryBrush));
    }

    private static FrameworkElement BuildNavigationCard(params (string Header, FrameworkElement View)[] sections)
    {
        var panel = new StackPanel { Spacing = 10 };
        var navButtons = new List<(Button Button, FrameworkElement View)>(sections.Length);

        void Select(FrameworkElement selectedView)
        {
            foreach ((Button button, FrameworkElement view) in navButtons)
            {
                bool isSelected = ReferenceEquals(view, selectedView);
                view.Visibility = isSelected ? Visibility.Visible : Visibility.Collapsed;
                MainWindowElementFactory.ApplyNavSelection(button, isSelected);
            }
        }

        foreach ((string header, FrameworkElement view) in sections)
        {
            Button button = DesktopAutomationMetadata.WithAutomationId(
                MainWindowElementFactory.CreateNavButton(header),
                DesktopAutomationMetadata.BuildAutomationId("Nav", header),
                header);
            button.Click += (_, _) => Select(view);
            navButtons.Add((button, view));
            panel.Children.Add(button);
        }

        Select(sections[0].View);
        return MainWindowElementFactory.BuildSidebarCard("Navigation", panel);
    }

    private static void AddMetric(Grid grid, FrameworkElement element, int column)
    {
        Grid.SetColumn(element, column);
        grid.Children.Add(element);
    }
}
