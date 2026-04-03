using Microsoft.UI.Dispatching;

namespace AgentTaskManager.Desktop.Utility;

public static class DispatcherQueueExtensions
{
    public static Task EnqueueAsync(this DispatcherQueue dispatcherQueue, Action action)
    {
        var completion = new TaskCompletionSource(TaskCreationOptions.RunContinuationsAsynchronously);
        bool enqueued = dispatcherQueue.TryEnqueue(() =>
        {
            try
            {
                action();
                completion.SetResult();
            }
            catch (Exception exception)
            {
                completion.SetException(exception);
            }
        });

        if (!enqueued)
        {
            completion.SetException(new InvalidOperationException("Could not enqueue work on the UI dispatcher."));
        }

        return completion.Task;
    }
}
