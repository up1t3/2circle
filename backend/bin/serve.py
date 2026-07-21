#!/usr/bin/env python3
"""
Threaded HTTP server with Range (resume) support for serving region packages.

Python's built-in http.server is single-threaded and doesn't support Range
requests. For large region packages (500 MB – 3 GB) this causes downloads to
fail at ~30-50% when OkHttp times out or the WiFi flickers, because the server
can't handle the reconnection / partial-content flow.

This server:
  - Handles Range requests (HTTP 206 Partial Content) so OkHttp can resume
  - Is multi-threaded (one connection per client, non-blocking for others)
  - Logs each request so we can diagnose issues

Run: python serve.py [port] [directory]
     python serve.py 8765 /path/to/regions
"""

import http.server
import os
import socketserver
import sys
import threading
from datetime import datetime, timezone


class RangeRequestHandler(http.server.SimpleHTTPRequestHandler):
    """Adds HTTP Range support so large files can resume after interruption."""

    protocol_version = "HTTP/1.1"

    def end_headers(self):
        # Announce Range support for all responses.
        self.send_header("Accept-Ranges", "bytes")
        super().end_headers()

    def do_GET(self):
        """Handle GET with optional Range header for partial content."""
        if "Range" not in self.headers:
            return super().do_GET()

        # Parse the Range header (e.g. "bytes=500000-")
        range_header = self.headers["Range"]
        path = self.translate_path(self.path)

        if not os.path.isfile(path):
            return super().do_GET()

        file_size = os.path.getsize(path)

        # Parse "bytes=start-end" or "bytes=start-"
        try:
            range_spec = range_header.replace("bytes=", "")
            if "-" in range_spec:
                start_str, end_str = range_spec.split("-", 1)
                start = int(start_str) if start_str else 0
                end = int(end_str) if end_str else file_size - 1
            else:
                start = 0
                end = file_size - 1
        except (ValueError, IndexError):
            self.send_error(400, "Invalid Range")
            return

        # Clamp to file bounds.
        start = max(0, min(start, file_size - 1))
        end = max(start, min(end, file_size - 1))
        content_length = end - start + 1

        self.send_response(206)
        self.send_header("Content-Type", "application/octet-stream")
        self.send_header("Content-Length", str(content_length))
        self.send_header("Content-Range", f"bytes {start}-{end}/{file_size}")
        self.send_header("Accept-Ranges", "bytes")
        self.end_headers()

        # Stream the requested byte range.
        with open(path, "rb") as f:
            f.seek(start)
            remaining = content_length
            while remaining > 0:
                chunk = f.read(min(65536, remaining))
                if not chunk:
                    break
                self.wfile.write(chunk)
                remaining -= len(chunk)

    def log_message(self, format, *args):
        """Compact log: timestamp + client IP + request."""
        ts = datetime.now(timezone.utc).strftime("%H:%M:%S")
        client = self.client_address[0]
        sys.stderr.write(f"[{ts}] {client} {format % args}\n")
        sys.stderr.flush()


class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    """Handle each request in a separate thread."""
    daemon_threads = True
    allow_reuse_address = True


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8765
    directory = sys.argv[2] if len(sys.argv) > 2 else "."

    os.chdir(directory)
    server = ThreadedHTTPServer(("0.0.0.0", port), RangeRequestHandler)

    print(f"Serving '{directory}' on 0.0.0.0:{port}")
    print(f"Range requests: ENABLED")
    print(f"Multi-threaded: YES")
    print(f"Press Ctrl+C to stop")
    sys.stdout.flush()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
