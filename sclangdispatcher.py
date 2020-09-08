#!/usr/bin/env python3

import argparse
import http.server
import logging
import os
import subprocess
import sys
import threading
import urllib.request

# Ideally urllib.request and http.server should be replaced by requests and
# something like flask or tornado but only using built-in python modules
# simplifies the distribution of this program.

def setup_args():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(title="action", dest="action")
    subparsers.required = True
    parser_server = subparsers.add_parser("server")
    parser_command = subparsers.add_parser("command")

    parser_server.add_argument("-p", "--port", default=5000, type=int)

    parser_command.add_argument("command", help="command to send to sclang")
    parser_command.add_argument("-p", "--port", default=5000, type=int,
        help="Port of the sclang pipe server")

    return parser.parse_args()

class RequestHandler(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        content_len = int(self.headers.get('Content-Length'))
        command = self.rfile.read(content_len).decode("utf8")
        self.server.sclang_thread.send_command(f"{command}".encode("utf8"))
        self.send_response(200)
        self.end_headers()

    def log_message(self, _format, *args):
        "This method logs nothing"
        pass

class SClangThread(threading.Thread):
    def __init__(self):
        threading.Thread.__init__(self)
        self.p = None
    def run(self):
        self.p = subprocess.Popen(["sclang", "-d", os.getcwd(), "-i",
            "scvim"], stdin=subprocess.PIPE, stderr=subprocess.PIPE)

    def send_command(self, command):
        self.p.stdin.write(command)
        self.p.stdin.flush()

    def stop(self):
        if self.p is not None:
            self.p.terminate()

def main():
    args = setup_args()
    if args.action == "server":
        t = SClangThread()
        t.start()

        server = http.server.HTTPServer(("", args.port), RequestHandler)
        server.sclang_thread = t
        server.serve_forever()
    elif args.action == "command":
        urllib.request.urlopen(f"http://localhost:{args.port}", data=args.command.encode("utf8"))
    else:
        print(f"Unknown action {args.action}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
