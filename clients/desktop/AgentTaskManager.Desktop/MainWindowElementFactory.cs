using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Controls.Primitives;
using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Media;

namespace AgentTaskManager.Desktop;

internal static class MainWindowElementFactory
{
    internal static Brush ShellBackgroundBrush => DesktopShellTheme.ShellBackground;
    internal static Brush SidebarBackgroundBrush => DesktopShellTheme.SidebarBackground;
    internal static Brush SidebarBorderBrush => DesktopShellTheme.SidebarBorder;
    internal static Brush PageBackgroundBrush => DesktopShellTheme.PageBackground;
    internal static Brush TextPrimaryBrush => DesktopShellTheme.TextPrimary;
    internal static Brush TextSecondaryBrush => DesktopShellTheme.TextSecondary;
    internal static Brush TextMutedBrush => DesktopShellTheme.TextMuted;
    internal static Brush AccentBrush => DesktopShellTheme.Accent;
    internal static Brush AccentSurfaceBrush => DesktopShellTheme.AccentSurface;
    internal static readonly Windows.UI.Text.FontWeight MediumWeight = DesktopShellTheme.MediumWeight;
    internal static readonly Windows.UI.Text.FontWeight SemiBoldWeight = DesktopShellTheme.SemiBoldWeight;
    internal static readonly Windows.UI.Text.FontWeight BoldWeight = DesktopShellTheme.BoldWeight;

    internal static Border BuildSidebarCard(string title, params UIElement[] children)
        => BuildCard(title, null, DesktopShellTheme.RaisedCardBackground, children);

    internal static Border BuildContentCard(string eyebrow, string title, string? description, params UIElement[] children)
    {
        var panel = new StackPanel { Spacing = 12 };
        if (!string.IsNullOrWhiteSpace(eyebrow))
        {
            panel.Children.Add(Eyebrow(eyebrow));
        }

        panel.Children.Add(Label(title, SemiBoldWeight, 20));
        if (!string.IsNullOrWhiteSpace(description))
        {
            panel.Children.Add(BodyText(description, DesktopShellTheme.TextSecondary));
        }

        foreach (UIElement child in children)
        {
            panel.Children.Add(child);
        }

        return new Border
        {
            Background = DesktopShellTheme.CardBackground,
            BorderBrush = DesktopShellTheme.CardBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(18),
            Padding = new Thickness(18),
            Child = panel
        };
    }

    internal static FrameworkElement BuildPage(string eyebrow, string title, string description, params UIElement[] children)
    {
        var stack = new StackPanel
        {
            Margin = new Thickness(0, 0, 0, 24),
            Spacing = 18
        };
        stack.Children.Add(new StackPanel
        {
            Spacing = 6,
            Children =
            {
                Eyebrow(eyebrow),
                Label(title, BoldWeight, 30),
                BodyText(description, DesktopShellTheme.TextSecondary)
            }
        });

        foreach (UIElement child in children)
        {
            stack.Children.Add(child);
        }

        return new ScrollViewer
        {
            Background = DesktopShellTheme.PageBackground,
            Content = stack,
            Padding = new Thickness(24, 24, 24, 0),
            VerticalScrollBarVisibility = ScrollBarVisibility.Auto
        };
    }

    internal static Button CreatePrimaryButton(string content)
        => CreateButton(
            content,
            DesktopShellTheme.Accent,
            DesktopShellTheme.Accent,
            DesktopShellTheme.TextOnAccent);

    internal static Button CreateSecondaryButton(string content)
        => CreateButton(
            content,
            DesktopShellTheme.CardBackground,
            DesktopShellTheme.InputBorder,
            DesktopShellTheme.TextPrimary);

    internal static Button CreateNavButton(string content)
    {
        var button = CreateButton(
            content,
            DesktopShellTheme.SidebarBackground,
            DesktopShellTheme.SidebarBorder,
            DesktopShellTheme.TextSecondary);
        button.HorizontalContentAlignment = HorizontalAlignment.Left;
        return button;
    }

    internal static void ApplyNavSelection(Button button, bool isSelected)
    {
        button.Background = isSelected
            ? DesktopShellTheme.NavSelectedBackground
            : DesktopShellTheme.SidebarBackground;
        button.BorderBrush = isSelected
            ? DesktopShellTheme.NavHoverBorder
            : DesktopShellTheme.SidebarBorder;
        button.Foreground = isSelected
            ? DesktopShellTheme.TextPrimary
            : DesktopShellTheme.TextSecondary;
    }

    internal static Border BuildHeaderMetric(string label, string path)
        => new()
        {
            Background = DesktopShellTheme.RaisedCardBackground,
            BorderBrush = DesktopShellTheme.CardBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(16),
            Padding = new Thickness(14),
            Child = new StackPanel
            {
                Spacing = 6,
                Children =
                {
                    Eyebrow(label),
                    BoundTextBlock(path, fontWeight: SemiBoldWeight)
                }
            }
        };

    internal static TextBlock BoundTextBlock(
        string path,
        Brush? foreground = null,
        Windows.UI.Text.FontWeight? fontWeight = null,
        double? fontSize = null,
        bool mono = false)
    {
        var textBlock = new TextBlock
        {
            Foreground = foreground ?? DesktopShellTheme.TextPrimary,
            TextWrapping = TextWrapping.Wrap,
            FontFamily = mono ? DesktopShellTheme.MonoFont : DesktopShellTheme.BodyFont
        };
        if (fontWeight.HasValue)
        {
            textBlock.FontWeight = fontWeight.Value;
        }

        if (fontSize.HasValue)
        {
            textBlock.FontSize = fontSize.Value;
        }

        textBlock.SetBinding(TextBlock.TextProperty, Binding(path));
        return textBlock;
    }

    internal static TextBlock Label(
        string text,
        Windows.UI.Text.FontWeight fontWeight,
        double? fontSize = null,
        Brush? foreground = null)
    {
        var textBlock = new TextBlock
        {
            Text = text,
            FontFamily = DesktopShellTheme.BodyFont,
            FontWeight = fontWeight,
            Foreground = foreground ?? DesktopShellTheme.TextPrimary,
            TextWrapping = TextWrapping.Wrap
        };
        if (fontSize.HasValue)
        {
            textBlock.FontSize = fontSize.Value;
        }

        return textBlock;
    }

    internal static FrameworkElement BoundTextBox(string label, string path)
        => LabeledField(label, CreateBoundTextBox(path));

    internal static FrameworkElement BoundMultilineTextBox(string label, string path, double minHeight)
        => LabeledField(label, CreateMultilineTextBox(path, minHeight, isReadOnly: false));

    internal static TextBox BoundMultilineBodyTextBox(string path, double minHeight)
        => CreateMultilineTextBox(path, minHeight, isReadOnly: false);

    internal static TextBox BoundReadOnlyBodyTextBox(string path, double minHeight)
        => CreateMultilineTextBox(path, minHeight, isReadOnly: true);

    internal static FrameworkElement ReadOnlyBoundValue(string label, string path)
        => LabeledField(label, ReadOnlyValue(path));

    internal static ComboBox BoundComboBox(string itemsPath, string selectedItemPath, string? displayMemberPath = null)
    {
        var comboBox = new ComboBox
        {
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Foreground = DesktopShellTheme.TextPrimary,
            FontFamily = DesktopShellTheme.BodyFont
        };
        if (!string.IsNullOrWhiteSpace(displayMemberPath))
        {
            comboBox.DisplayMemberPath = displayMemberPath;
        }

        BindItems(comboBox, itemsPath);
        comboBox.SetBinding(Selector.SelectedItemProperty, Binding(selectedItemPath, BindingMode.TwoWay));
        return comboBox;
    }

    internal static CheckBox BoundCheckBox(string content, string path)
    {
        var checkBox = new CheckBox
        {
            Content = content,
            Foreground = DesktopShellTheme.TextPrimary,
            FontFamily = DesktopShellTheme.BodyFont
        };
        checkBox.SetBinding(ToggleButton.IsCheckedProperty, Binding(path, BindingMode.TwoWay));
        return checkBox;
    }

    internal static ListView BoundListView(
        string itemsPath,
        double? minHeight = null,
        double? maxHeight = null,
        string? displayMemberPath = null)
    {
        var listView = new ListView
        {
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(14),
            Foreground = DesktopShellTheme.TextPrimary
        };
        if (!string.IsNullOrWhiteSpace(displayMemberPath))
        {
            listView.DisplayMemberPath = displayMemberPath;
        }

        if (minHeight.HasValue)
        {
            listView.MinHeight = minHeight.Value;
        }

        if (maxHeight.HasValue)
        {
            listView.MaxHeight = maxHeight.Value;
        }

        BindItems(listView, itemsPath);
        return listView;
    }

    internal static ListView BoundSelectableListView(
        string itemsPath,
        string selectedItemPath,
        double maxHeight,
        string? displayMemberPath = null)
    {
        var listView = BoundListView(itemsPath, maxHeight: maxHeight, displayMemberPath: displayMemberPath);
        listView.SetBinding(Selector.SelectedItemProperty, Binding(selectedItemPath, BindingMode.TwoWay));
        return listView;
    }

    internal static StackPanel HorizontalButtons(params Button[] buttons)
    {
        var panel = new StackPanel
        {
            Orientation = Orientation.Horizontal,
            Spacing = 10
        };
        foreach (Button button in buttons)
        {
            panel.Children.Add(button);
        }

        return panel;
    }

    internal static FrameworkElement LabeledField(string label, FrameworkElement field)
        => DesktopAutomationMetadata.LabeledField(label, field);

    internal static Grid CreateTwoColumnGrid()
    {
        var grid = new Grid
        {
            ColumnSpacing = 18,
            RowSpacing = 18
        };
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        return grid;
    }

    internal static void AddToGrid(Grid grid, FrameworkElement element, int row, int column)
    {
        Grid.SetRow(element, row);
        Grid.SetColumn(element, column);
        grid.Children.Add(element);
    }

    private static Border BuildCard(string title, string? description, Brush background, params UIElement[] children)
    {
        var panel = new StackPanel { Spacing = 12 };
        panel.Children.Add(Label(title, SemiBoldWeight, 16));
        if (!string.IsNullOrWhiteSpace(description))
        {
            panel.Children.Add(BodyText(description, DesktopShellTheme.TextSecondary));
        }

        foreach (UIElement child in children)
        {
            panel.Children.Add(child);
        }

        return new Border
        {
            Background = background,
            BorderBrush = DesktopShellTheme.CardBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(16),
            Padding = new Thickness(16),
            Child = panel
        };
    }

    private static TextBlock Eyebrow(string text)
        => Label(text.ToUpperInvariant(), MediumWeight, 11, DesktopShellTheme.TextMuted);

    private static TextBlock BodyText(string text, Brush foreground)
        => Label(text, MediumWeight, 13, foreground);

    private static FrameworkElement ReadOnlyValue(string path)
        => new Border
        {
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(12, 10, 12, 10),
            Child = BoundTextBlock(path, DesktopShellTheme.TextPrimary, mono: true)
        };

    private static Button CreateButton(string content, Brush background, Brush borderBrush, Brush foreground)
        => new()
        {
            Content = content,
            MinWidth = 132,
            HorizontalAlignment = HorizontalAlignment.Stretch,
            Padding = new Thickness(16, 10, 16, 10),
            CornerRadius = new CornerRadius(12),
            Background = background,
            BorderBrush = borderBrush,
            BorderThickness = new Thickness(1),
            Foreground = foreground,
            FontFamily = DesktopShellTheme.BodyFont,
            FontWeight = SemiBoldWeight
        };

    private static TextBox CreateBoundTextBox(string path)
    {
        var textBox = new TextBox
        {
            Background = DesktopShellTheme.InputBackground,
            BorderBrush = DesktopShellTheme.InputBorder,
            BorderThickness = new Thickness(1),
            CornerRadius = new CornerRadius(12),
            Foreground = DesktopShellTheme.TextPrimary,
            FontFamily = DesktopShellTheme.BodyFont
        };
        textBox.SetBinding(TextBox.TextProperty, Binding(path, BindingMode.TwoWay, UpdateSourceTrigger.PropertyChanged));
        return textBox;
    }

    private static TextBox CreateMultilineTextBox(string path, double minHeight, bool isReadOnly)
    {
        TextBox textBox = CreateBoundTextBox(path);
        textBox.AcceptsReturn = true;
        textBox.MinHeight = minHeight;
        textBox.TextWrapping = TextWrapping.Wrap;
        textBox.IsReadOnly = isReadOnly;
        if (isReadOnly)
        {
            textBox.Background = DesktopShellTheme.MonoBackground;
            textBox.FontFamily = DesktopShellTheme.MonoFont;
        }

        return textBox;
    }

    private static void BindItems(ItemsControl control, string path)
        => control.SetBinding(ItemsControl.ItemsSourceProperty, Binding(path));

    private static Binding Binding(string path, BindingMode mode = BindingMode.OneWay, UpdateSourceTrigger trigger = UpdateSourceTrigger.Default)
        => new()
        {
            Path = new PropertyPath(path),
            Mode = mode,
            UpdateSourceTrigger = trigger
        };
}
