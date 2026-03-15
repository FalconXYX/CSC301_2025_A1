#!/usr/bin/env python3

import json
import os
import sys
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
from threading import Thread
import time

config = {}
user_service_url = ""
product_service_url = ""

def load_config(config_path):
    global config, user_service_url, product_service_url

    # Allow env vars to override config.json (Docker support)
    user_ip = os.environ.get("USER_SERVICE_IP")
    user_port = os.environ.get("USER_SERVICE_PORT")
    product_ip = os.environ.get("PRODUCT_SERVICE_IP")
    product_port = os.environ.get("PRODUCT_SERVICE_PORT")

    with open(config_path, 'r') as f:
        config = json.load(f)

    user_config = config.get("UserService", {})
    product_config = config.get("ProductService", {})

    user_service_url = f"http://{user_ip or user_config.get('ip', '127.0.0.1')}:{user_port or user_config.get('port', 14001)}"
    product_service_url = f"http://{product_ip or product_config.get('ip', '127.0.0.1')}:{product_port or product_config.get('port', 15000)}"
    print(f"ISCS routing: user={user_service_url}, product={product_service_url}")

def make_request(url, method, data=None):
    """Make HTTP request to a service"""
    try:
        if data and method in ("POST", "PUT", "PATCH"):
            req = urllib.request.Request(
                url,
                data=data.encode('utf-8'),
                headers={'Content-Type': 'application/json'},
                method=method
            )
        else:
            req = urllib.request.Request(url, method=method)

        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8')
    except Exception as e:
        return 500, json.dumps({"error": str(e)})

class ISCSHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        """Handle POST requests"""
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length).decode('utf-8')

        if self.path == '/user' or self.path == '/user/':
            status, response = make_request(f"{user_service_url}/user", "POST", body)
        elif self.path == '/product' or self.path == '/product/':
            status, response = make_request(f"{product_service_url}/product", "POST", body)
        elif self.path == '/user/shutdown':
            status, response = make_request(f"{user_service_url}/user/shutdown", "POST", body)
        elif self.path == '/product/shutdown':
            status, response = make_request(f"{product_service_url}/product/shutdown", "POST", body)
        else:
            self.send_response(404)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Not found"}).encode('utf-8'))
            return

        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(response.encode('utf-8'))

    def do_GET(self):
        """Handle GET requests"""
        if self.path.startswith('/user/'):
            user_id = self.path.split('/')[2]
            status, response = make_request(f"{user_service_url}/user/{user_id}", "GET")
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(response.encode('utf-8'))
        elif self.path.startswith('/product/'):
            product_id = self.path.split('/')[2]
            status, response = make_request(f"{product_service_url}/product/{product_id}", "GET")
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(response.encode('utf-8'))
        elif self.path == '/health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"status": "ISCS is running"}).encode('utf-8'))
        else:
            self.send_response(404)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Not found"}).encode('utf-8'))

    def do_DELETE(self):
        """Handle DELETE requests"""
        if self.path == '/user/deleteall':
            status, response = make_request(f"{user_service_url}/user/deleteall", "DELETE")
        elif self.path == '/product/deleteall':
            status, response = make_request(f"{product_service_url}/product/deleteall", "DELETE")
        else:
            self.send_response(404)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Not found"}).encode('utf-8'))
            return

        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(response.encode('utf-8'))

    def log_message(self, format, *args):
        """Suppress default logging"""
        pass

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 ISCS.py <config_file>")
        sys.exit(1)

    config_file = sys.argv[1]
    load_config(config_file)

    iscs_config = config.get("InterServiceCommunication", {})
    # Allow env override for ISCS listen address (Docker binds on 0.0.0.0)
    ip = os.environ.get("ISCS_LISTEN_IP", iscs_config.get("ip", "127.0.0.1"))
    port = int(os.environ.get("ISCS_LISTEN_PORT", iscs_config.get("port", 14002)))

    server = HTTPServer((ip, port), ISCSHandler)
    server.allow_reuse_address = True
    print(f"ISCS starting on {ip}:{port}")
    server.serve_forever()

