#!/usr/bin/env python3
import argparse
import base64
import json
import pathlib
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


DEFAULT_HEADERS = {
    "User-Agent": "agent-task-manager-automation-bridge/1.0",
    "Accept": "*/*",
}

DEFAULT_COMMANDS = [
    "hytale.launch-launcher",
    "hytale.launch-client",
    "hytale.join-server",
    "hytale.close-overlay",
    "hytale.open-creative-tools",
    "hytale.open-asset-editor",
    "hytale.asset-editor.navigate",
    "hytale.capture-timeline",
    "hytale.promote-memory",
    "hytale.list-playbooks",
    "hytale.execute-playbook",
    "hyrhythm.open-ui",
    "hyrhythm.select-chart",
    "hyrhythm.start-gameplay",
    "hyrhythm.press-lane",
    "hyrhythm.capture-state",
]


def basic_auth(username: str, password: str) -> str:
    token = base64.b64encode(f"{username}:{password}".encode("utf-8")).decode("ascii")
    return f"Basic {token}"


def post_json(base_url: str, path: str, payload: dict, auth_header: str) -> dict:
    url = urllib.parse.urljoin(base_url.rstrip("/") + "/", path.lstrip("/"))
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            **DEFAULT_HEADERS,
            "Authorization": auth_header,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def post_provider_json(provider_url: str, payload: dict) -> dict:
    request = urllib.request.Request(
        provider_url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            **DEFAULT_HEADERS,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=60) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def load_or_create_session_id(path: pathlib.Path) -> str:
    if path.exists():
      try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        session_id = payload.get("sessionId")
        if session_id:
            return session_id
      except json.JSONDecodeError:
        pass
    session_id = "session-" + str(uuid.uuid4())
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({"sessionId": session_id}, indent=2), encoding="utf-8")
    return session_id


def build_capabilities(args) -> dict:
    return {
        "bridgeTarget": args.bridge_target,
        "transport": "local-loopback-http",
        "cooperativeAutomation": True,
        "intrusiveInput": False,
        "automationCommands": args.commands,
        "providerUrl": args.provider_url,
    }


def register_session(args) -> None:
    post_json(args.base_url, "/api/bridge/sessions/register", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
        "hostName": args.host_name,
        "clientName": args.client_name,
        "repoPath": args.repo_path,
        "status": "online",
        "capabilities": build_capabilities(args),
    }, args.auth_header)


def heartbeat(args) -> None:
    post_json(args.base_url, "/api/bridge/sessions/heartbeat", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
        "hostName": args.host_name,
        "clientName": args.client_name,
        "repoPath": args.repo_path,
        "status": "online",
        "capabilities": build_capabilities(args),
    }, args.auth_header)


def claim_next(args) -> dict | None:
    response = post_json(args.base_url, "/api/bridge/automation/claims/next", {
        "sessionId": args.session_id,
        "agentId": args.agent_id,
    }, args.auth_header)
    return response.get("claim")


def complete_command(args, command_request_id: str, summary: str, result: dict) -> None:
    post_json(args.base_url, f"/api/bridge/automation/commands/{command_request_id}/complete", {
        "summary": summary,
        "result": result,
    }, args.auth_header)


def fail_command(args, command_request_id: str, summary: str, result: dict) -> None:
    post_json(args.base_url, f"/api/bridge/automation/commands/{command_request_id}/fail", {
        "summary": summary,
        "result": result,
    }, args.auth_header)


def execute_claim(args, claim: dict) -> None:
    payload = {
        "commandRequestId": claim["commandRequestId"],
        "session": {
            "sessionId": claim["sessionId"],
            "agentId": claim["targetAgentId"],
            "bridgeTarget": claim["bridgeTarget"],
            "repoPath": claim.get("repoPath") or "",
        },
        "command": {
            "commandId": claim["commandId"],
            "isolationClass": claim["isolationClass"],
            "arguments": claim.get("arguments") or {},
        },
    }
    try:
        provider_response = post_provider_json(args.provider_url, payload)
        status = str(provider_response.get("status") or "completed").strip().lower()
        summary = str(provider_response.get("summary") or f"Completed {claim['commandId']}")
        result = provider_response.get("result")
        if not isinstance(result, dict):
            result = {}
        if status == "completed":
            complete_command(args, claim["commandRequestId"], summary, result)
        else:
            fail_command(args, claim["commandRequestId"], summary, result)
    except Exception as error:  # noqa: BLE001
        fail_command(args, claim["commandRequestId"], f"Local automation provider failed: {error}", {})


def main() -> int:
    parser = argparse.ArgumentParser(description="AgentTaskManager local cooperative automation bridge")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--username", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--provider-url", required=True)
    parser.add_argument("--bridge-target", default="local-automation")
    parser.add_argument("--agent-id", default="local-automation-bridge")
    parser.add_argument("--client-name", default="Local Cooperative Automation Bridge")
    parser.add_argument("--repo-path", default="")
    parser.add_argument("--host-name", default="local-host")
    parser.add_argument("--poll-interval", type=float, default=2.0)
    parser.add_argument("--command", dest="commands", action="append", default=[])
    parser.add_argument(
        "--session-file",
        default=str(pathlib.Path.home() / ".agent-task-manager" / "local-automation-session.json"),
    )
    args = parser.parse_args()

    args.auth_header = basic_auth(args.username, args.password)
    args.session_id = load_or_create_session_id(pathlib.Path(args.session_file))
    if not args.commands:
        args.commands = list(DEFAULT_COMMANDS)

    register_session(args)
    while True:
        try:
            heartbeat(args)
            claim = claim_next(args)
            if claim:
                execute_claim(args, claim)
            else:
                time.sleep(args.poll_interval)
        except urllib.error.HTTPError as error:
            print(f"automation bridge http error: {error.code}")
            time.sleep(args.poll_interval)
        except Exception as error:  # noqa: BLE001
            print(f"automation bridge failure: {error}")
            time.sleep(args.poll_interval)


if __name__ == "__main__":
    raise SystemExit(main())
