import http.server
import os
import sys

sys.stdout.reconfigure(line_buffering=True)
sys.stderr.reconfigure(line_buffering=True)

PORT = 8502
DIRECTORY = "/Users/saurabh/.gemini/antigravity/scratch/spendwise-android"

class DownloadHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def log_message(self, format, *args):
        print(f"[{self.client_address[0]}] {format % args}", flush=True)

    def do_HEAD(self):
        if self.path.startswith("/version.json"):
            version_path = os.path.join(DIRECTORY, "version.json")
            if os.path.exists(version_path):
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(os.path.getsize(version_path)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
            else:
                self.send_error(404)
        elif self.path.startswith("/SpendWise.apk"):
            apk_path = os.path.join(DIRECTORY, "SpendWise.apk")
            if os.path.exists(apk_path):
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Length", str(os.path.getsize(apk_path)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
            else:
                self.send_error(404)
        elif self.path == "/" or self.path.startswith("/index.html"):
            index_path = os.path.join(DIRECTORY, "index.html")
            if os.path.exists(index_path):
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(os.path.getsize(index_path)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
            else:
                self.send_error(404)
        else:
            super().do_HEAD()

    def do_GET(self):
        print(f"GET Request: {self.path} from {self.client_address[0]}", flush=True)
        if self.path == "/version.json" or self.path.startswith("/version.json?"):
            version_path = os.path.join(DIRECTORY, "version.json")
            if os.path.exists(version_path):
                data = open(version_path, "rb").read()
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "version.json not found")

        elif self.path == "/SpendWise.apk" or self.path.startswith("/SpendWise.apk?"):
            apk_path = os.path.join(DIRECTORY, "SpendWise.apk")
            if os.path.exists(apk_path):
                data = open(apk_path, "rb").read()
                self.send_response(200)
                self.send_header("Content-Type", "application/vnd.android.package-archive")
                self.send_header("Content-Disposition", 'attachment; filename="SpendWise.apk"')
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(data)
            else:
                self.send_error(404, "APK not found")

        elif self.path == "/" or self.path == "/index.html" or self.path.startswith("/index.html?"):
            index_path = os.path.join(DIRECTORY, "index.html")
            if os.path.exists(index_path):
                data = open(index_path, "rb").read()
                self.send_response(200)
                self.send_header("Content-Type", "text/html; charset=utf-8")
                self.send_header("Content-Length", str(len(data)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(data)
            else:
                super().do_GET()
        else:
            super().do_GET()

if __name__ == "__main__":
    server_address = ("0.0.0.0", PORT)
    httpd = http.server.ThreadingHTTPServer(server_address, DownloadHandler)
    print(f"SpendWise Multi-Threaded OTA Server active on 0.0.0.0:{PORT}", flush=True)
    httpd.serve_forever()
