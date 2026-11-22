"""A tiny OSRM look-alike used ONLY by the integration smoke test (the real engine needs gigabytes of map data).
It answers in the real OSRM JSON format; the application code under test is the real OsrmClient."""

import json
from http.server import BaseHTTPRequestHandler, HTTPServer

LT_PL = [(25.28, 54.69), (23.9, 54.9), (23.35, 54.56), (22.93, 54.11), (21.9, 53.2), (21.01, 52.23)]
PL_DE = [(21.01, 52.23), (18.6, 52.4), (16.9, 52.4), (14.55, 52.35), (13.4, 52.52)]


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):  # noqa: N802
        route = PL_DE if "13.4" in self.path else LT_PL
        body = {
            "code": "Ok",
            "routes": [
                {
                    "distance": 460000.0,
                    "duration": 18000.0,
                    "geometry": {"type": "LineString", "coordinates": [list(p) for p in route]},
                    "legs": [{"steps": [{"distance": 460000.0, "intersections": [{"classes": []}]}]}],
                }
            ],
        }
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *_):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 5050), Handler).serve_forever()
