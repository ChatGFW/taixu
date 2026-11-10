#!/usr/bin/env python3
"""Reference LAN poller for TaiXu External Agent Runtime (/runtime/v1/*).

Cross-platform (stdlib only). Prints session info, optional pending peek, then
long-polls claim. By default only prints claimed events; pass --ack to settle
(--ack-status / --ack-error), --post for a progress message, or --release.

Env:
  TAIXU_RUNTIME_BASE   e.g. http://192.168.1.20:8901
  TAIXU_RUNTIME_TOKEN  plaintext tx_rt_… token (issued once in the app)

Example:
  python scripts/external_agent_runtime_poller.py --wait-ms 25000
  python scripts/external_agent_runtime_poller.py --ack --once
  python scripts/external_agent_runtime_poller.py --post "处理中…" --ack --once
  python scripts/external_agent_runtime_poller.py --auto-heartbeat
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any


def _req(
    method: str,
    url: str,
    token: str | None = None,
    body: dict[str, Any] | None = None,
    timeout: float = 35.0,
) -> tuple[int, Any]:
    data = None
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read()
            payload = json.loads(raw.decode("utf-8")) if raw else None
            return response.status, payload
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            payload = json.loads(raw.decode("utf-8")) if raw else None
        except json.JSONDecodeError:
            payload = raw.decode("utf-8", errors="replace") if raw else None
        return exc.code, payload


def _heartbeat(base: str, token: str, leases: list[dict[str, Any]]) -> tuple[int, Any]:
    return _req(
        "POST",
        f"{base}/runtime/v1/events/heartbeat",
        token,
        {
            "leases": [
                {
                    "deliveryId": lease["deliveryId"],
                    "claimNonce": lease["claimNonce"],
                }
                for lease in leases
            ],
        },
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="TaiXu external agent runtime poller")
    parser.add_argument(
        "--base",
        default=os.environ.get("TAIXU_RUNTIME_BASE", "http://127.0.0.1:8901"),
        help="Runtime base URL (default env TAIXU_RUNTIME_BASE or http://127.0.0.1:8901)",
    )
    parser.add_argument(
        "--token",
        default=os.environ.get("TAIXU_RUNTIME_TOKEN", ""),
        help="Bearer tx_rt_ token (default env TAIXU_RUNTIME_TOKEN)",
    )
    parser.add_argument("--wait-ms", type=int, default=25_000, help="claim long-poll waitMs")
    parser.add_argument("--pending-limit", type=int, default=16, help="pending peek limit")
    parser.add_argument("--once", action="store_true", help="exit after first claim attempt")
    parser.add_argument("--ack", action="store_true", help="batch-ack claimed leases")
    parser.add_argument(
        "--ack-status",
        default="completed",
        choices=("completed", "failed", "rejected", "unknown"),
        help="ack status when --ack is set (default completed)",
    )
    parser.add_argument("--ack-error", default="", help="optional error string for failed/rejected/unknown ack")
    parser.add_argument(
        "--post",
        default="",
        help="after claim, post this message to the event groupId before ack/release",
    )
    parser.add_argument("--release", action="store_true", help="release claimed leases back to pending")
    parser.add_argument(
        "--heartbeat-secs",
        type=int,
        default=0,
        help="hold claimed leases and renew every N seconds (0=off unless --auto-heartbeat)",
    )
    parser.add_argument(
        "--auto-heartbeat",
        action="store_true",
        help="renew at about claimLeaseTtlMs/3 using leaseExpiresAt from claim/heartbeat",
    )
    parser.add_argument("--skip-peek", action="store_true", help="skip GET /events/pending")
    parser.add_argument("--skip-health", action="store_true", help="skip GET /runtime/v1/health")
    args = parser.parse_args()

    if args.ack and args.release:
        print("choose at most one of --ack / --release", file=sys.stderr)
        return 2
    token = args.token.strip()
    if not token.startswith("tx_rt_"):
        print("missing or invalid token (expect tx_rt_…)", file=sys.stderr)
        return 2
    base = args.base.rstrip("/")

    if not args.skip_health:
        status, health = _req("GET", f"{base}/runtime/v1/health")
        if status != 200:
            print(f"/health failed: {status} {health}", file=sys.stderr)
            return 1
        print(json.dumps({"health": health}, ensure_ascii=False, indent=2))

    status, me = _req("GET", f"{base}/runtime/v1/me", token)
    if status != 200:
        print(f"/me failed: {status} {me}", file=sys.stderr)
        return 1
    print(json.dumps({"me": me}, ensure_ascii=False, indent=2))
    ttl_ms = int((me or {}).get("claimLeaseTtlMs") or 900_000)
    hold_secs = args.heartbeat_secs
    if hold_secs <= 0 and args.auto_heartbeat:
        hold_secs = max(30, ttl_ms // 1000)

    if not args.skip_peek:
        status, pending = _req(
            "GET",
            f"{base}/runtime/v1/events/pending?limit={args.pending_limit}",
            token,
        )
        if status != 200:
            print(f"/events/pending failed: {status} {pending}", file=sys.stderr)
            return 1
        print(json.dumps({"pending": pending}, ensure_ascii=False, indent=2))

    wait = max(0, min(args.wait_ms, 30_000))
    while True:
        status, event = _req(
            "POST",
            f"{base}/runtime/v1/events/claim?waitMs={wait}",
            token,
            body={},
            timeout=(wait / 1000.0) + 10.0,
        )
        if status == 204:
            print(json.dumps({"claim": None, "status": 204}, ensure_ascii=False))
            if args.once:
                return 0
            continue
        if status != 200 or not isinstance(event, dict):
            print(f"claim failed: {status} {event}", file=sys.stderr)
            return 1
        print(json.dumps({"claim": event}, ensure_ascii=False, indent=2))

        leases = event.get("leases") or []
        if not leases and event.get("deliveryId") and event.get("claimNonce"):
            leases = [{"deliveryId": event["deliveryId"], "claimNonce": event["claimNonce"]}]

        if hold_secs > 0 and not args.ack and not args.release:
            hold_until = time.time() + hold_secs
            lease_expires = float(event.get("leaseExpiresAt") or 0) / 1000.0
            renew_every = max(15.0, (ttl_ms / 1000.0) / 3.0)
            while time.time() < hold_until:
                now = time.time()
                sleep_for = renew_every
                if lease_expires > 0:
                    sleep_for = min(sleep_for, max(5.0, lease_expires - now - 30.0))
                sleep_for = min(sleep_for, max(1.0, hold_until - now))
                time.sleep(sleep_for)
                if time.time() >= hold_until:
                    break
                st, body = _heartbeat(base, token, leases)
                print(json.dumps({"heartbeat": st, "body": body}, ensure_ascii=False))
                if st != 200:
                    break
                if isinstance(body, dict) and body.get("leaseExpiresAt"):
                    lease_expires = float(body["leaseExpiresAt"]) / 1000.0

        post_text = args.post.strip()
        if post_text:
            group_id = str(event.get("groupId") or "")
            if not group_id:
                print("claim missing groupId; cannot --post", file=sys.stderr)
                return 1
            st, body = _req(
                "POST",
                f"{base}/runtime/v1/groups/{group_id}/messages",
                token,
                {"content": post_text},
            )
            print(json.dumps({"post": st, "body": body}, ensure_ascii=False))
            if st != 200:
                return 1

        if args.release:
            for lease in leases:
                st, body = _req(
                    "POST",
                    f"{base}/runtime/v1/events/release",
                    token,
                    {
                        "deliveryId": lease["deliveryId"],
                        "claimNonce": lease["claimNonce"],
                    },
                )
                print(json.dumps({"release": st, "body": body}, ensure_ascii=False))
                if st != 200:
                    return 1
        elif args.ack:
            payload: dict[str, Any] = {
                "status": args.ack_status,
                "leases": [
                    {
                        "deliveryId": lease["deliveryId"],
                        "claimNonce": lease["claimNonce"],
                    }
                    for lease in leases
                ],
            }
            if args.ack_error.strip():
                payload["error"] = args.ack_error.strip()
            st, body = _req(
                "POST",
                f"{base}/runtime/v1/events/ack",
                token,
                payload,
            )
            print(json.dumps({"ack": st, "body": body}, ensure_ascii=False))
            if st != 200:
                return 1

        if args.once:
            return 0


if __name__ == "__main__":
    raise SystemExit(main())
