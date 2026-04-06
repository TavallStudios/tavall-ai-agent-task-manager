@echo off
setlocal EnableExtensions

set "workspace=%CD%"
set "output_file="
set "prompt="

:parse
if "%~1"=="" goto after_parse
if /I "%~1"=="-C" (
  set "workspace=%~2"
  shift
  shift
  goto parse
)
if /I "%~1"=="-m" (
  shift
  shift
  goto parse
)
if /I "%~1"=="-s" (
  shift
  shift
  goto parse
)
if /I "%~1"=="--output-last-message" (
  set "output_file=%~2"
  shift
  shift
  goto parse
)
if /I "%~1"=="exec" (
  shift
  goto parse
)
if /I "%~1"=="resume" (
  shift
  goto parse
)
if /I "%~1"=="--json" (
  shift
  goto parse
)
if /I "%~1"=="--skip-git-repo-check" (
  shift
  goto parse
)
set "prompt=%~1"
shift
goto parse

:after_parse
if not exist "%workspace%" mkdir "%workspace%"
cd /d "%workspace%"

echo {"type":"thread.started","thread_id":"fake-thread"}
echo {"type":"turn.started"}
echo {"type":"item.completed","item":{"type":"tool_call","name":"runHarnessToolBundle","arguments":{"bundleName":"worker-context"}}}
echo {"type":"item.completed","item":{"type":"tool_call","name":"runHarnessToolBundle","arguments":{"bundleName":"repo-context"}}}
echo %prompt% | findstr /C:"[shell-command]" >nul
if not errorlevel 1 echo {"type":"item.completed","item":{"type":"tool_call","name":"shell_command","arguments":{"command":"Get-ChildItem"}}}
echo {"type":"item.completed","item":{"type":"agent_message","text":"fake-codex processed prompt"}}

echo %prompt% | findstr /C:"[no-op]" /C:"[read-only]" >nul
if not errorlevel 1 goto after_edit

echo %prompt% | findstr /C:"[java-signature-change]" >nul
if not errorlevel 1 (
  if not exist src\main\java\example mkdir src\main\java\example
  > src\main\java\example\FixtureApp.java (
    echo package example;
    echo.
    echo public class FixtureApp {
    echo.
    echo   public int greet^(^) {
    echo     return 42;
    echo   }
    echo }
  )
  goto after_edit
)

if exist README.md (
  >> README.md echo.
  >> README.md echo Autonomous update from fake codex.
) else (
  if not exist src\main\java\example mkdir src\main\java\example
  > src\main\java\example\AutonomousNote.java (
    echo package example;
    echo.
    echo public class AutonomousNote {
    echo }
  )
)

:after_edit
git rev-parse --is-inside-work-tree >nul 2>&1
if not errorlevel 1 (
  set "branch=atm-fakecodex-tj-v1"
  git config user.email integration@example.com >nul 2>&1
  git config user.name "Integration Test" >nul 2>&1
  echo %prompt% | findstr /C:"[skip-git-workflow]" >nul
  if not errorlevel 1 goto after_git

  echo %prompt% | findstr /C:"[workflow-no-commit]" >nul
  if not errorlevel 1 (
    echo {"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}
    echo {"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}
    git show-ref --verify --quiet refs/heads/%branch%
    if errorlevel 1 (
      git checkout -b %branch% >nul 2>&1
    ) else (
      git checkout %branch% >nul 2>&1
    )
    goto after_git
  )

  echo %prompt% | findstr /C:"[double-commit]" >nul
  if not errorlevel 1 (
    echo {"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}
    echo {"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}
    git show-ref --verify --quiet refs/heads/%branch%
    if errorlevel 1 (
      git checkout -b %branch% >nul 2>&1
    ) else (
      git checkout %branch% >nul 2>&1
    )
    git add -A
    git diff --cached --quiet
    if errorlevel 1 (
      > "%TEMP%\\fake-codex-commit.txt" (
        echo What Changed:
        echo Recorded the fake codex fixture update.
        echo.
        echo Why:
        echo Keeps the fake worker run aligned with git workflow enforcement.
        echo.
        echo Verification:
        echo Fake codex fixture execution.
        echo.
        echo Branch:
        echo atm-fakecodex-tj-v1
        echo.
        echo Concern:
        echo atm / fakecodex
        echo.
        echo Final Change: yes
        echo Change Type: Changed
      )
      git commit -m "Changed: Fake codex worker fixture update" -F "%TEMP%\\fake-codex-commit.txt" >nul 2>&1
      echo {"type":"item.completed","item":{"type":"tool_call","name":"createGitCommit","arguments":{"changeType":"Changed"}}}
      >> README.md echo.
      >> README.md echo Follow-up adjustment from fake codex.
      git add -A
      git commit -m "Changed: Fake codex worker fixture follow-up" -F "%TEMP%\\fake-codex-commit.txt" >nul 2>&1
      del "%TEMP%\\fake-codex-commit.txt" >nul 2>&1
    )
    goto after_git
  )

  echo %prompt% | findstr /C:"[generic-git-mutation]" >nul
  if not errorlevel 1 (
    echo {"type":"item.completed","item":{"type":"tool_call","name":"git_commit","arguments":{"message":"generic"}}}
    git add -A
    git diff --cached --quiet
    if errorlevel 1 git commit -m "Changed: Generic git fixture update" >nul 2>&1
    goto after_git
  )

  echo {"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}
  echo {"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}
  git show-ref --verify --quiet refs/heads/%branch%
  if errorlevel 1 (
    git checkout -b %branch% >nul 2>&1
  ) else (
    git checkout %branch% >nul 2>&1
  )
  git add -A
  git diff --cached --quiet
  if errorlevel 1 (
    > "%TEMP%\fake-codex-commit.txt" (
      echo What Changed:
      echo Recorded the fake codex fixture update.
      echo.
      echo Why:
      echo Keeps the fake worker run aligned with git workflow enforcement.
      echo.
      echo Verification:
      echo Fake codex fixture execution.
      echo.
      echo Branch:
      echo atm-fakecodex-tj-v1
      echo.
      echo Concern:
      echo atm / fakecodex
      echo.
      echo Final Change: yes
      echo Change Type: Changed
    )
    git commit -m "Changed: Fake codex worker fixture update" -F "%TEMP%\fake-codex-commit.txt" >nul 2>&1
    del "%TEMP%\fake-codex-commit.txt" >nul 2>&1
    echo {"type":"item.completed","item":{"type":"tool_call","name":"createGitCommit","arguments":{"changeType":"Changed"}}}
  )
)

:after_git
echo {"type":"turn.completed","usage":{"input_tokens":101,"cached_input_tokens":11,"output_tokens":41}}

if not "%output_file%"=="" (
  > "%output_file%" echo Completed autonomous worker run for %prompt%
)
