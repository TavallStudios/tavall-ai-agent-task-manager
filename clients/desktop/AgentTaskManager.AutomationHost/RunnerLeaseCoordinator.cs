namespace AgentTaskManager.AutomationHost;

internal sealed class RunnerLeaseCoordinator
{
    private readonly object _gate = new();
    private readonly TimeSpan _leaseTtl;
    private string? _owner;
    private DateTimeOffset _expiresAt;

    internal RunnerLeaseCoordinator(TimeSpan leaseTtl)
    {
        _leaseTtl = leaseTtl <= TimeSpan.Zero ? TimeSpan.FromSeconds(30) : leaseTtl;
        _expiresAt = DateTimeOffset.MinValue;
    }

    internal LeaseSnapshot AcquireOrRenew(string owner, DateTimeOffset now)
    {
        lock (_gate)
        {
            if (IsLeaseExpired(now))
            {
                _owner = owner;
                _expiresAt = now.Add(_leaseTtl);
                return new LeaseSnapshot(_owner, _expiresAt, true);
            }

            if (!string.Equals(_owner, owner, StringComparison.Ordinal))
            {
                throw new RunnerLeaseConflictException(_owner ?? string.Empty, _expiresAt);
            }

            _expiresAt = now.Add(_leaseTtl);
            return new LeaseSnapshot(_owner, _expiresAt, false);
        }
    }

    internal LeaseSnapshot Heartbeat(string owner, DateTimeOffset now)
        => AcquireOrRenew(owner, now);

    internal LeaseSnapshot Snapshot(DateTimeOffset now)
    {
        lock (_gate)
        {
            if (IsLeaseExpired(now))
            {
                _owner = null;
                _expiresAt = DateTimeOffset.MinValue;
            }

            return new LeaseSnapshot(_owner, _expiresAt, false);
        }
    }

    private bool IsLeaseExpired(DateTimeOffset now)
        => string.IsNullOrWhiteSpace(_owner) || _expiresAt <= now;
}

internal sealed class RunnerLeaseConflictException : InvalidOperationException
{
    internal RunnerLeaseConflictException(string owner, DateTimeOffset leaseExpiresAt)
        : base($"Runner lease is currently owned by '{owner}' until {leaseExpiresAt:O}.")
    {
        Owner = owner;
        LeaseExpiresAt = leaseExpiresAt;
    }

    internal string Owner { get; }
    internal DateTimeOffset LeaseExpiresAt { get; }
}

internal sealed record LeaseSnapshot(string? Owner, DateTimeOffset ExpiresAt, bool Acquired);

