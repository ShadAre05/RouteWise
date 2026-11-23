"""Runnable integration walkthrough of the RouteWise API (the code form of the tutorial in ../guides/tutorial.md).

It registers a throw-away account, adds a vehicle, calculates and saves a trip, reads the history,
renames and deletes the trip, shows the error format, and finally deletes the account again.

    python walkthrough.py                         # against http://localhost:8000
    python walkthrough.py https://your-host       # against another server
    python walkthrough.py --save ./captured       # also store every response as JSON

Needs Python 3.10+ and `pip install httpx`. The trip step needs the routing engine (OSRM); without it
the API answers 502 and the script says so.
"""

import argparse
import json
import sys
import time
from pathlib import Path

import httpx

API = "/api/v1"
PASSWORD = "correct-horse-battery"


class Walkthrough:
    def __init__(self, base_url: str, save_dir: Path | None) -> None:
        self.http = httpx.Client(base_url=base_url, timeout=30)
        self.save_dir = save_dir
        self.step = 0
        if save_dir:
            save_dir.mkdir(parents=True, exist_ok=True)

    def call(self, name: str, method: str, path: str, *, token: str | None = None, body: dict | None = None):
        self.step += 1
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        response = self.http.request(method, path, json=body, headers=headers)
        print(f"\n[{self.step}] {name}\n    {method} {path}  ->  HTTP {response.status_code}")
        if body is not None:
            print("    request :", json.dumps(body, ensure_ascii=False))
        payload = response.json() if response.content else None
        if payload is not None:
            text = json.dumps(payload, ensure_ascii=False)
            print("    response:", text if len(text) < 400 else text[:400] + " ...")
        if self.save_dir:
            record = {"request": {"method": method, "path": path, "body": body}, "status": response.status_code}
            record["response"] = payload
            (self.save_dir / f"{self.step:02d}_{name.replace(' ', '_')}.json").write_text(
                json.dumps(record, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
            )
        return response, payload


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("base_url", nargs="?", default="http://localhost:8000")
    parser.add_argument("--save", type=Path, help="folder for the captured responses")
    args = parser.parse_args()
    w = Walkthrough(args.base_url, args.save)

    w.call("health", "GET", "/health")
    w.call("countries", "GET", f"{API}/countries")
    w.call("fuel prices", "GET", f"{API}/fuel-prices?fuel_type=petrol95")

    email = f"walkthrough-{int(time.time())}@example.com"
    _, tokens = w.call(
        "register", "POST", f"{API}/auth/register", body={"email": email, "password": PASSWORD, "privacy_accepted": True}
    )
    access = tokens["access_token"]

    w.call("vehicles without token", "GET", f"{API}/vehicles")
    w.call(
        "register invalid", "POST", f"{API}/auth/register",
        body={"email": "not-an-email", "password": "short", "privacy_accepted": False},
    )  # fmt: skip
    _, vehicle = w.call(
        "add vehicle", "POST", f"{API}/vehicles", token=access,
        body={"name": "Family car", "fuel_type": "petrol95", "consumption_l100": 7.5, "tank_l": 50, "is_default": True},
    )  # fmt: skip

    trip_request = {
        "origin_label": "Vilnius", "destination_label": "Warsaw",
        "origin": {"lon": 25.28, "lat": 54.69}, "destination": {"lon": 21.01, "lat": 52.23},
        "variant": "fastest", "daily_limit_h": 8, "lodging_class": "standard", "start_fuel": "half",
    }  # fmt: skip
    response, trip = w.call("calculate trip", "POST", f"{API}/trips", token=access, body=trip_request)
    if response.status_code == 502:
        print("\nThe routing engine is not reachable: start OSRM (see backend/README.md) and run again.")
    else:
        w.call("trip history", "GET", f"{API}/trips?limit=10", token=access)
        w.call("trip details", "GET", f"{API}/trips/{trip['id']}", token=access)
        w.call("rename trip", "PATCH", f"{API}/trips/{trip['id']}", token=access, body={"title": "Warsaw business trip"})
        w.call("delete trip", "DELETE", f"{API}/trips/{trip['id']}", token=access)
        w.call("deleted trip", "GET", f"{API}/trips/{trip['id']}", token=access)

    _, renewed = w.call("refresh tokens", "POST", f"{API}/auth/refresh", body={"refresh_token": tokens["refresh_token"]})
    w.call("refresh token reused", "POST", f"{API}/auth/refresh", body={"refresh_token": tokens["refresh_token"]})
    w.call("logout", "POST", f"{API}/auth/logout", body={"refresh_token": renewed["refresh_token"]})
    w.call("delete account", "DELETE", f"{API}/users/me", token=renewed["access_token"])
    print(f"\nDone: {w.step} calls; the throw-away account {email} was deleted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
