$workspace = (Get-Location).Path
$outputFile = ""
$prompt = ""

for ($index = 0; $index -lt $args.Count; $index++) {
  switch ($args[$index]) {
    '-C' {
      if ($index + 1 -lt $args.Count) {
        $workspace = $args[$index + 1]
        $index++
      }
    }
    '-m' {
      if ($index + 1 -lt $args.Count) {
        $index++
      }
    }
    '-s' {
      if ($index + 1 -lt $args.Count) {
        $index++
      }
    }
    '--output-last-message' {
      if ($index + 1 -lt $args.Count) {
        $outputFile = $args[$index + 1]
        $index++
      }
    }
    'exec' {
    }
    'resume' {
    }
    '--json' {
    }
    '--skip-git-repo-check' {
    }
    default {
      $prompt = $args[$index]
    }
  }
}

New-Item -ItemType Directory -Force -Path $workspace | Out-Null
Set-Location $workspace

Write-Output '{"type":"thread.started","thread_id":"fake-thread"}'
Write-Output '{"type":"turn.started"}'
Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"runHarnessToolBundle","arguments":{"bundleName":"worker-context"}}}'
Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"runHarnessToolBundle","arguments":{"bundleName":"repo-context"}}}'
if ($prompt.Contains('[shell-command]')) {
  Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"shell_command","arguments":{"command":"Get-ChildItem"}}}'
}
Write-Output '{"type":"item.completed","item":{"type":"agent_message","text":"fake-codex processed prompt"}}'

if ($prompt.Contains('[no-op]') -or $prompt.Contains('[read-only]')) {
} elseif ($prompt.Contains('[java-signature-change]')) {
  New-Item -ItemType Directory -Force -Path 'src/main/java/example' | Out-Null
  Set-Content -Path 'src/main/java/example/FixtureApp.java' -Value @'
package example;

public class FixtureApp {

  public int greet() {
    return 42;
  }
}
'@
} elseif (Test-Path 'README.md') {
  Add-Content -Path 'README.md' -Value "`nAutonomous update from fake codex."
} else {
  New-Item -ItemType Directory -Force -Path 'src/main/java/example' | Out-Null
  Set-Content -Path 'src/main/java/example/AutonomousNote.java' -Value @'
package example;

public class AutonomousNote {
}
'@
}

git rev-parse --is-inside-work-tree *> $null
if ($LASTEXITCODE -eq 0) {
  $branch = 'atm-fakecodex-tj-v1'
  git config user.email integration@example.com *> $null
  git config user.name "Integration Test" *> $null
  if ($prompt.Contains('[skip-git-workflow]')) {
  } elseif ($prompt.Contains('[workflow-no-commit]')) {
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}'
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}'
    git show-ref --verify --quiet ("refs/heads/" + $branch) *> $null
    if ($LASTEXITCODE -eq 0) {
      git checkout $branch *> $null
    } else {
      git checkout -b $branch *> $null
    }
  } elseif ($prompt.Contains('[double-commit]')) {
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}'
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}'
    git show-ref --verify --quiet ("refs/heads/" + $branch) *> $null
    if ($LASTEXITCODE -eq 0) {
      git checkout $branch *> $null
    } else {
      git checkout -b $branch *> $null
    }
    git add -A
    git diff --cached --quiet *> $null
    if ($LASTEXITCODE -ne 0) {
      $body = @'
What Changed:
Recorded the fake codex fixture update.

Why:
Keeps the fake worker run aligned with git workflow enforcement.

Verification:
Fake codex fixture execution.

Branch:
atm-fakecodex-tj-v1

Concern:
atm / fakecodex

Final Change: yes
Change Type: Changed
'@
      git commit -m 'Changed: Fake codex worker fixture update' -m $body *> $null
      Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"createGitCommit","arguments":{"changeType":"Changed"}}}'
      Add-Content -Path 'README.md' -Value "`nFollow-up adjustment from fake codex."
      git add -A
      git commit -m 'Changed: Fake codex worker fixture follow-up' -m $body *> $null
    }
  } elseif ($prompt.Contains('[generic-git-mutation]')) {
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"git_commit","arguments":{"message":"generic"}}}'
    git add -A
    git diff --cached --quiet *> $null
    if ($LASTEXITCODE -ne 0) {
      git commit -m 'Changed: Generic git fixture update' *> $null
    }
  } else {
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"planGitCommit","arguments":{"changeType":"Changed"}}}'
    Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"prepareGitBranch","arguments":{"domain":"atm","system":"fakecodex","user":"tj","version":"v1"}}}'
    git show-ref --verify --quiet ("refs/heads/" + $branch) *> $null
    if ($LASTEXITCODE -eq 0) {
      git checkout $branch *> $null
    } else {
      git checkout -b $branch *> $null
    }
    git add -A
    git diff --cached --quiet *> $null
    if ($LASTEXITCODE -ne 0) {
      $body = @'
What Changed:
Recorded the fake codex fixture update.

Why:
Keeps the fake worker run aligned with git workflow enforcement.

Verification:
Fake codex fixture execution.

Branch:
atm-fakecodex-tj-v1

Concern:
atm / fakecodex

Final Change: yes
Change Type: Changed
'@
      git commit -m 'Changed: Fake codex worker fixture update' -m $body *> $null
      Write-Output '{"type":"item.completed","item":{"type":"tool_call","name":"createGitCommit","arguments":{"changeType":"Changed"}}}'
    }
  }
}

Write-Output '{"type":"turn.completed","usage":{"input_tokens":101,"cached_input_tokens":11,"output_tokens":41}}'

if ($outputFile -ne '') {
  Set-Content -Path $outputFile -Value ("Completed autonomous worker run for " + $prompt)
}
