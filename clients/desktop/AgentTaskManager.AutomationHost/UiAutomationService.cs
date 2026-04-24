using System.Windows;
using System.Windows.Automation;

namespace AgentTaskManager.AutomationHost;

internal sealed class UiAutomationService
{
    internal UiTreeNode DumpTree(WindowSummary window, int maxDepth, int maxChildrenPerNode)
    {
        AutomationElement root = GetRoot(window);
        return BuildTree(root, "0", 0, Math.Max(1, maxDepth), Math.Max(1, maxChildrenPerNode));
    }

    internal IReadOnlyList<UiElementSummary> FindElements(WindowSummary window, ElementSelector selector, int maxDepth, int maxResults)
    {
        AutomationElement root = GetRoot(window);
        return FindMatches(root, selector, maxDepth, maxResults)
            .Select(match => ToSummary(match.Element, match.Path))
            .ToArray();
    }

    internal UiElementSummary Invoke(WindowSummary window, ElementSelector selector)
    {
        AutomationElement element = ResolveElement(window, selector);
        if (element.TryGetCurrentPattern(InvokePattern.Pattern, out object? invokePatternObject))
        {
            ((InvokePattern)invokePatternObject).Invoke();
            return ToSummary(element, BuildResolvedPath(window, selector));
        }

        if (element.TryGetCurrentPattern(SelectionItemPattern.Pattern, out object? selectionPatternObject))
        {
            ((SelectionItemPattern)selectionPatternObject).Select();
            return ToSummary(element, BuildResolvedPath(window, selector));
        }

        if (element.TryGetCurrentPattern(TogglePattern.Pattern, out object? togglePatternObject))
        {
            ((TogglePattern)togglePatternObject).Toggle();
            return ToSummary(element, BuildResolvedPath(window, selector));
        }

        throw new InvalidOperationException("The selected element does not support invoke, select, or toggle patterns.");
    }

    internal UiElementSummary SetValue(WindowSummary window, ElementSelector selector, string value)
    {
        AutomationElement element = ResolveElement(window, selector);
        if (!element.TryGetCurrentPattern(ValuePattern.Pattern, out object? patternObject))
        {
            throw new InvalidOperationException("The selected element does not support the Value pattern.");
        }

        ((ValuePattern)patternObject).SetValue(value);
        return ToSummary(element, BuildResolvedPath(window, selector));
    }

    internal UiElementSummary Select(WindowSummary window, ElementSelector selector)
    {
        AutomationElement element = ResolveElement(window, selector);
        if (element.TryGetCurrentPattern(SelectionItemPattern.Pattern, out object? selectionPatternObject))
        {
            ((SelectionItemPattern)selectionPatternObject).Select();
            return ToSummary(element, BuildResolvedPath(window, selector));
        }

        if (element.TryGetCurrentPattern(InvokePattern.Pattern, out object? invokePatternObject))
        {
            ((InvokePattern)invokePatternObject).Invoke();
            return ToSummary(element, BuildResolvedPath(window, selector));
        }

        throw new InvalidOperationException("The selected element does not support select or invoke patterns.");
    }

    private AutomationElement ResolveElement(WindowSummary window, ElementSelector selector)
    {
        IReadOnlyList<(AutomationElement Element, string Path)> matches = FindMatches(
            GetRoot(window),
            selector,
            maxDepth: 8,
            maxResults: selector.Index + 1);
        if (matches.Count <= selector.Index)
        {
            throw new InvalidOperationException("No matching UI element was found.");
        }

        return matches[selector.Index].Element;
    }

    private static AutomationElement GetRoot(WindowSummary window)
        => AutomationElement.FromHandle(new IntPtr(window.Handle));

    private UiTreeNode BuildTree(AutomationElement element, string path, int depth, int maxDepth, int maxChildrenPerNode)
    {
        if (depth >= maxDepth)
        {
            return ToTreeNode(element, path, Array.Empty<UiTreeNode>());
        }

        var children = new List<UiTreeNode>();
        AutomationElement child = TreeWalker.ControlViewWalker.GetFirstChild(element);
        int childIndex = 0;
        while (child is not null && children.Count < maxChildrenPerNode)
        {
            children.Add(BuildTree(child, $"{path}.{childIndex}", depth + 1, maxDepth, maxChildrenPerNode));
            child = TreeWalker.ControlViewWalker.GetNextSibling(child);
            childIndex++;
        }

        return ToTreeNode(element, path, children);
    }

    private void Traverse(AutomationElement root, string rootPath, int depth, int maxDepth, Func<(AutomationElement Element, string Path), bool> visitor)
    {
        var queue = new Queue<(AutomationElement Element, string Path, int Depth)>();
        queue.Enqueue((root, rootPath, depth));
        while (queue.Count > 0)
        {
            (AutomationElement element, string path, int currentDepth) = queue.Dequeue();
            if (!visitor((element, path)))
            {
                return;
            }

            if (currentDepth >= maxDepth)
            {
                continue;
            }

            AutomationElement child = TreeWalker.ControlViewWalker.GetFirstChild(element);
            int childIndex = 0;
            while (child is not null)
            {
                queue.Enqueue((child, $"{path}.{childIndex}", currentDepth + 1));
                child = TreeWalker.ControlViewWalker.GetNextSibling(child);
                childIndex++;
            }
        }
    }

    private IReadOnlyList<(AutomationElement Element, string Path)> FindMatches(
        AutomationElement root,
        ElementSelector selector,
        int maxDepth,
        int maxResults)
    {
        var results = new List<(AutomationElement Element, string Path)>();
        int normalizedDepth = Math.Max(1, maxDepth);
        int normalizedResults = Math.Max(1, maxResults);

        Traverse(root, "0", 0, normalizedDepth, element =>
        {
            if (!Matches(element, selector))
            {
                return true;
            }

            results.Add(element);
            return results.Count < normalizedResults;
        });

        if (results.Count > 0)
        {
            return results;
        }

        AutomationElementCollection rawMatches = root.FindAll(
            TreeScope.Descendants,
            System.Windows.Automation.Condition.TrueCondition);
        for (int index = 0; index < rawMatches.Count && results.Count < normalizedResults; index++)
        {
            AutomationElement element = rawMatches[index];
            var candidate = (Element: element, Path: $"raw.{index}");
            if (Matches(candidate, selector))
            {
                results.Add(candidate);
            }
        }

        return results;
    }

    private static bool Matches((AutomationElement Element, string Path) candidate, ElementSelector selector)
    {
        AutomationElement.AutomationElementInformation current = candidate.Element.Current;
        if (!string.IsNullOrWhiteSpace(selector.AutomationId) &&
            !current.AutomationId.Equals(selector.AutomationId, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        if (!string.IsNullOrWhiteSpace(selector.Name) &&
            !current.Name.Equals(selector.Name, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        if (!string.IsNullOrWhiteSpace(selector.NameContains) &&
            !current.Name.Contains(selector.NameContains, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        if (!string.IsNullOrWhiteSpace(selector.ClassName) &&
            !current.ClassName.Equals(selector.ClassName, StringComparison.OrdinalIgnoreCase))
        {
            return false;
        }

        if (!string.IsNullOrWhiteSpace(selector.ControlType))
        {
            string programmaticName = current.ControlType?.ProgrammaticName ?? string.Empty;
            string localizedName = current.LocalizedControlType ?? string.Empty;
            if (!programmaticName.EndsWith(selector.ControlType, StringComparison.OrdinalIgnoreCase) &&
                !localizedName.Equals(selector.ControlType, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }
        }

        return true;
    }

    private static UiElementSummary ToSummary(AutomationElement element, string path)
    {
        AutomationElement.AutomationElementInformation current = element.Current;
        return new UiElementSummary(
            Path: path,
            AutomationId: current.AutomationId ?? string.Empty,
            Name: current.Name ?? string.Empty,
            ControlType: current.ControlType?.ProgrammaticName ?? string.Empty,
            ClassName: current.ClassName ?? string.Empty,
            FrameworkId: current.FrameworkId ?? string.Empty,
            IsEnabled: current.IsEnabled,
            IsOffscreen: current.IsOffscreen,
            Bounds: ToWindowRect(current.BoundingRectangle),
            SupportedPatterns: GetSupportedPatterns(element));
    }

    private static UiTreeNode ToTreeNode(AutomationElement element, string path, IReadOnlyList<UiTreeNode> children)
    {
        UiElementSummary summary = ToSummary(element, path);
        return new UiTreeNode(
            summary.Path,
            summary.AutomationId,
            summary.Name,
            summary.ControlType,
            summary.ClassName,
            summary.FrameworkId,
            summary.IsEnabled,
            summary.IsOffscreen,
            summary.Bounds,
            summary.SupportedPatterns,
            children);
    }

    private static WindowRect ToWindowRect(Rect rect)
        => new((int)rect.Left, (int)rect.Top, (int)rect.Width, (int)rect.Height);

    private static string[] GetSupportedPatterns(AutomationElement element)
        => element.GetSupportedPatterns()
            .Select(pattern => pattern.ProgrammaticName ?? pattern.Id.ToString())
            .OrderBy(value => value, StringComparer.OrdinalIgnoreCase)
            .ToArray();

    private static string BuildResolvedPath(WindowSummary window, ElementSelector selector)
    {
        string stableKey = !string.IsNullOrWhiteSpace(selector.AutomationId)
            ? selector.AutomationId
            : !string.IsNullOrWhiteSpace(selector.Name)
                ? selector.Name
                : selector.ControlType ?? "element";
        return $"{window.HandleHex}:{stableKey}";
    }
}
