import os
import re
import subprocess
import logging

import docker
from flask import Flask, jsonify

app = Flask(__name__)
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
logger = logging.getLogger(__name__)

BASE_DOMAIN = os.environ.get("BASE_DOMAIN", "1edu.kz")
CERT_EMAIL = os.environ.get("CERT_EMAIL", "admin@1edu.kz")
WEBROOT = os.environ.get("WEBROOT", "/var/www/certbot")
NGINX_TENANTS_DIR = os.environ.get("NGINX_TENANTS_DIR", "/etc/nginx/tenants")
LETSENCRYPT_DIR = os.environ.get("LETSENCRYPT_DIR", "/etc/letsencrypt")
NGINX_CONTAINER = os.environ.get("NGINX_CONTAINER", "1edu-nginx")

SUBDOMAIN_RE = re.compile(r"^[a-z0-9][a-z0-9-]{0,61}[a-z0-9]$")


def validate_subdomain(subdomain: str) -> bool:
    return bool(SUBDOMAIN_RE.match(subdomain))


def reload_nginx() -> None:
    client = docker.from_env()
    container = client.containers.get(NGINX_CONTAINER)
    result = container.exec_run("nginx -s reload")
    if result.exit_code != 0:
        output = result.output.decode("utf-8", errors="replace")
        raise RuntimeError(f"nginx reload failed (exit {result.exit_code}): {output}")
    logger.info("nginx reloaded successfully")


def nginx_conf_path(subdomain: str) -> str:
    return os.path.join(NGINX_TENANTS_DIR, f"{subdomain}.conf")


def build_nginx_config(subdomain: str) -> str:
    fqdn = f"{subdomain}.{BASE_DOMAIN}"
    return f"""\
server {{
    listen 80;
    server_name {fqdn};
    location /.well-known/acme-challenge/ {{
        root /var/www/certbot;
    }}
    location / {{
        return 301 https://$host$request_uri;
    }}
}}
server {{
    listen 443 ssl;
    http2 on;
    server_name {fqdn};
    ssl_certificate     /etc/letsencrypt/live/{fqdn}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/{fqdn}/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_session_cache shared:SSL:10m;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    location /api/ {{
        proxy_pass http://api-gateway:8090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_read_timeout 60s;
    }}
    location /auth/ {{
        proxy_pass http://keycloak:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffer_size 128k;
        proxy_buffers 4 256k;
    }}
    location / {{
        return 404;
    }}
}}
"""


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "UP"})


@app.route("/certs/<subdomain>", methods=["POST"])
def issue_cert(subdomain: str):
    if not validate_subdomain(subdomain):
        return jsonify({"error": "Invalid subdomain"}), 400

    fqdn = f"{subdomain}.{BASE_DOMAIN}"
    cert_path = os.path.join(LETSENCRYPT_DIR, "live", fqdn, "fullchain.pem")

    if os.path.exists(cert_path):
        logger.info("Certificate already exists for %s", fqdn)
        return jsonify({"status": "exists"}), 200

    # Run certbot
    cmd = [
        "certbot", "certonly",
        "--webroot",
        "-w", WEBROOT,
        "-d", fqdn,
        "--email", CERT_EMAIL,
        "--agree-tos",
        "--non-interactive",
        "--no-eff-email",
    ]
    logger.info("Running certbot for %s", fqdn)
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    except subprocess.TimeoutExpired:
        logger.error("certbot timed out for %s", fqdn)
        return jsonify({"error": "certbot timed out"}), 500
    except Exception as exc:
        logger.exception("certbot execution error for %s", fqdn)
        return jsonify({"error": str(exc)}), 500

    if result.returncode != 0:
        logger.error("certbot failed for %s: %s", fqdn, result.stderr)
        return jsonify({"error": "certbot failed", "detail": result.stderr}), 500

    logger.info("certbot succeeded for %s", fqdn)

    # Write nginx tenant config
    conf_path = nginx_conf_path(subdomain)
    try:
        os.makedirs(NGINX_TENANTS_DIR, exist_ok=True)
        with open(conf_path, "w") as f:
            f.write(build_nginx_config(subdomain))
        logger.info("Wrote nginx config: %s", conf_path)
    except OSError as exc:
        logger.exception("Failed to write nginx config for %s", subdomain)
        return jsonify({"error": f"Failed to write nginx config: {exc}"}), 500

    # Reload nginx
    try:
        reload_nginx()
    except Exception as exc:
        logger.exception("nginx reload failed after issuing cert for %s", subdomain)
        return jsonify({"error": f"Cert issued but nginx reload failed: {exc}"}), 500

    return jsonify({"status": "issued", "domain": fqdn}), 201


@app.route("/certs/<subdomain>", methods=["DELETE"])
def remove_cert(subdomain: str):
    if not validate_subdomain(subdomain):
        return jsonify({"error": "Invalid subdomain"}), 400

    conf_path = nginx_conf_path(subdomain)
    if os.path.exists(conf_path):
        try:
            os.remove(conf_path)
            logger.info("Removed nginx config: %s", conf_path)
        except OSError as exc:
            logger.exception("Failed to remove nginx config for %s", subdomain)
            return jsonify({"error": f"Failed to remove nginx config: {exc}"}), 500
    else:
        logger.info("No nginx config found for %s, skipping removal", subdomain)

    # Reload nginx
    try:
        reload_nginx()
    except Exception as exc:
        logger.exception("nginx reload failed after removing config for %s", subdomain)
        return jsonify({"error": f"Config removed but nginx reload failed: {exc}"}), 500

    return jsonify({"status": "removed"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=8200)
