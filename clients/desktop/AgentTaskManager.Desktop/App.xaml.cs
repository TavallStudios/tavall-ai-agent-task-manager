using AgentTaskManager.Desktop.Services;
using AgentTaskManager.Desktop.ViewModels;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Microsoft.UI.Xaml;

namespace AgentTaskManager.Desktop;

public partial class App : Application
{
    private readonly IHost _host;

    public App()
    {
        InitializeComponent();
        RequestedTheme = ApplicationTheme.Dark;
        UnhandledException += OnUnhandledException;
        AppDomain.CurrentDomain.UnhandledException += OnCurrentDomainUnhandledException;
        TaskScheduler.UnobservedTaskException += OnUnobservedTaskException;
        DesktopDiagnosticsLog.WriteInfo("Desktop application starting.");
        _host = Host.CreateDefaultBuilder()
            .ConfigureServices(services =>
            {
                services.AddSingleton<BackendAuthSessionStore>();
                services.AddSingleton<ISecureCredentialStorageService, SecureCredentialStorageService>();
                services.AddSingleton<IDevicePresenceService, DevicePresenceService>();
                services.AddSingleton<DesktopLocalBackendSupervisor>();
                services.AddSingleton<DesktopSshTunnelSupervisor>();
                services.AddSingleton<DesktopRemoteDefaultsDetector>();
                services.AddSingleton<IDesktopConnectionSettingsService, DesktopConnectionSettingsService>();
                services.AddSingleton<IBackendAuthService, BackendAuthService>();
                services.AddSingleton<IWorkspaceRegistryService, WorkspaceRegistryService>();
                services.AddSingleton<ISessionClientService, SessionClientService>();
                services.AddSingleton<ISessionStreamService, SessionStreamService>();
                services.AddSingleton<IRuntimeSessionClientService, RuntimeSessionClientService>();
                services.AddSingleton<ICodexExecutableResolverService, CodexExecutableResolverService>();
                services.AddSingleton<ICodexEnvironmentService, DesktopCodexEnvironmentService>();
                services.AddSingleton<ICodexWorkspaceConfigurationService, CodexWorkspaceConfigurationService>();
                services.AddSingleton<ICodexSupervisorService, CodexSupervisorService>();
                services.AddSingleton<ICodexRuntimeConnection, CodexRuntimeConnection>();
                services.AddSingleton<IMemoryContextService, MemoryContextService>();
                services.AddSingleton<IToolReceiptService, ToolReceiptService>();
                services.AddSingleton<IVerifierStatusService, VerifierStatusService>();
                services.AddSingleton<IOutputReleaseService, OutputReleaseService>();
                services.AddSingleton<IRepoLaunchService, RepoLaunchService>();
                services.AddSingleton<IDiffNavigationService, DiffNavigationService>();
                services.AddSingleton<IRemoteSessionResumeService, RemoteSessionResumeService>();
                services.AddSingleton<ConnectionSettingsViewModel>();
                services.AddSingleton<CodexSettingsViewModel>();
                services.AddSingleton<SignInViewModel>();
                services.AddSingleton<WorkspacePickerViewModel>();
                services.AddSingleton<SessionListViewModel>();
                services.AddSingleton<SessionDetailViewModel>();
                services.AddSingleton<StatusStripViewModel>();
                services.AddSingleton<MainShellViewModel>();
            })
            .Build();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        DesktopDiagnosticsLog.WriteInfo("Desktop application launched. Creating main window.");
        var window = new MainWindow(_host.Services.GetRequiredService<MainShellViewModel>());
        window.Activate();
    }

    private void OnUnhandledException(object sender, Microsoft.UI.Xaml.UnhandledExceptionEventArgs e)
    {
        DesktopDiagnosticsLog.WriteError("WinUI unhandled exception.", e.Exception);
    }

    private void OnCurrentDomainUnhandledException(object? sender, System.UnhandledExceptionEventArgs e)
    {
        DesktopDiagnosticsLog.WriteError(
            $"AppDomain unhandled exception. Terminating={e.IsTerminating}.",
            e.ExceptionObject as Exception);
    }

    private void OnUnobservedTaskException(object? sender, UnobservedTaskExceptionEventArgs e)
    {
        DesktopDiagnosticsLog.WriteError("Unobserved task exception.", e.Exception);
        e.SetObserved();
    }
}
