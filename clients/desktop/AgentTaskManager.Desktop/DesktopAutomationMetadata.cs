using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using System.Text;

namespace AgentTaskManager.Desktop;

internal static class DesktopAutomationMetadata
{
    internal static FrameworkElement LabeledField(string label, FrameworkElement field, string? automationId = null)
    {
        ApplyFieldMetadata(field, label, automationId);
        return new StackPanel
        {
            Spacing = 6,
            Children =
            {
                MainWindowElementFactory.Label(label, MainWindowElementFactory.MediumWeight, 11, MainWindowElementFactory.TextMutedBrush),
                field
            }
        };
    }

    internal static T WithAutomationId<T>(T element, string automationId, string? name = null) where T : DependencyObject
    {
        if (!string.IsNullOrWhiteSpace(automationId))
        {
            AutomationProperties.SetAutomationId(element, automationId);
        }

        if (!string.IsNullOrWhiteSpace(name))
        {
            AutomationProperties.SetName(element, name);
        }

        return element;
    }

    private static void ApplyFieldMetadata(DependencyObject element, string label, string? automationId)
    {
        string resolvedAutomationId = !string.IsNullOrWhiteSpace(automationId)
            ? automationId
            : BuildAutomationId("Field", label);
        string existingAutomationId = AutomationProperties.GetAutomationId(element);
        if (string.IsNullOrWhiteSpace(existingAutomationId))
        {
            AutomationProperties.SetAutomationId(element, resolvedAutomationId);
        }

        string existingName = AutomationProperties.GetName(element);
        if (string.IsNullOrWhiteSpace(existingName))
        {
            AutomationProperties.SetName(element, label);
        }
    }

    internal static string BuildAutomationId(string prefix, string value)
    {
        var builder = new StringBuilder(prefix.Length + value.Length + 1);
        builder.Append(prefix);
        builder.Append('_');
        bool lastWasSeparator = false;
        foreach (char character in value)
        {
            if (char.IsLetterOrDigit(character))
            {
                builder.Append(character);
                lastWasSeparator = false;
            }
            else if (!lastWasSeparator)
            {
                builder.Append('_');
                lastWasSeparator = true;
            }
        }

        return builder.ToString().TrimEnd('_');
    }
}
