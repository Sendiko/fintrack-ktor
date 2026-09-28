# FinTrack API — Linux Server Deployment Guide

> Tested on **Ubuntu 22.04 LTS** / **Debian 12**. Commands should work on any systemd-based distro.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build the Fat JAR](#2-build-the-fat-jar)
3. [Set Up the Database (MySQL)](#3-set-up-the-database-mysql)
4. [Install Ollama (AI Receipt Extraction)](#4-install-ollama-ai-receipt-extraction)
5. [Deploy the JAR](#5-deploy-the-jar)
6. [Configure Environment Variables](#6-configure-environment-variables)
7. [Run as a systemd Service](#7-run-as-a-systemd-service)
8. [Reverse Proxy with Nginx](#8-reverse-proxy-with-nginx)
9. [TLS / HTTPS with Certbot](#9-tls--https-with-certbot)
10. [Smoke Tests](#10-smoke-tests)
11. [Useful Commands](#11-useful-commands)

---

## 1. Prerequisites

### Java 21+

```bash
sudo apt update && sudo apt install -y curl wget unzip

# Install Eclipse Temurin JDK 21 (recommended)
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | sudo tee /etc/apt/keyrings/adoptium.asc
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] https://packages.adoptium.net/artifactory/deb $(lsb_release -cs) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jdk

java -version   # should print: openjdk 21...
```

### MySQL 8.x

```bash
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo mysql_secure_installation   # follow prompts
```

### Nginx (for reverse proxy)

```bash
sudo apt install -y nginx
sudo systemctl enable nginx
```

---

## 2. Build the Fat JAR

On your **local machine** (where you have the source code):

```bash
cd /path/to/fintrack-ktor
./gradlew buildFatJar
```

The output file will be at:

```
build/libs/fintrack-ktor-all.jar
```

Copy it to the server:

```bash
scp build/libs/fintrack-ktor-all.jar user@your-server-ip:/tmp/fintrack.jar
```

> **Note:** You only need Java on the server — not Gradle or the full source tree.

---

## 3. Set Up the Database (MySQL)

SSH into the server and create the database and a dedicated user:

```bash
sudo mysql
```

```sql
CREATE DATABASE fintrack_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'fintrack_user'@'localhost' IDENTIFIED BY 'your_strong_password_here';
GRANT ALL PRIVILEGES ON fintrack_db.* TO 'fintrack_user'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

> The app uses Exposed's `SchemaUtils.create()` on first startup, so **tables are created automatically** — no manual migrations needed.

---

## 4. Install Ollama (AI Receipt Extraction)

Ollama runs the local LLM that powers the `POST /extract` endpoint.

```bash
# Install Ollama
curl -fsSL https://ollama.com/install.sh | sh

# Pull the required model (~2 GB download)
ollama pull llama3.2:3b

# Verify
ollama list
```

Ollama's installer sets up a systemd service automatically:

```bash
sudo systemctl enable --now ollama
sudo systemctl status ollama
```

> **Skip this step** if you don't need receipt extraction. All other endpoints continue to work. The `/extract` endpoint will return a `500` if Ollama is unreachable.

---

## 5. Deploy the JAR

Create the app directory and a dedicated system user:

```bash
sudo mkdir -p /opt/fintrack
sudo useradd -r -s /sbin/nologin fintrack

# Move the JAR from /tmp
sudo mv /tmp/fintrack.jar /opt/fintrack/fintrack.jar
sudo chown -R fintrack:fintrack /opt/fintrack
```

---

## 6. Configure Environment Variables

Create an environment file that systemd will read:

```bash
sudo mkdir -p /etc/fintrack
sudo nano /etc/fintrack/env
```

Paste and fill in your values:

```env
APP_PORT=3000

DB_HOST=localhost
DB_PORT=3306
DB_NAME=fintrack_db
DB_USER=fintrack_user
DB_PASS=your_strong_password_here

JWT_SECRET=replace_with_a_long_random_secret_at_least_32_chars
JWT_DOMAIN=https://api.yourdomain.com/
JWT_AUDIENCE=fintrack-users
JWT_REALM=fintrack

OLLAMA_HOST=http://localhost:11434
OLLAMA_MODEL=llama3.2:3b
```

Generate a secure `JWT_SECRET`:

```bash
openssl rand -hex 32
```

Lock down the file (it contains secrets):

```bash
sudo chown root:fintrack /etc/fintrack/env
sudo chmod 640 /etc/fintrack/env
```

---

## 7. Run as a systemd Service

Create the service unit file:

```bash
sudo nano /etc/systemd/system/fintrack.service
```

```ini
[Unit]
Description=FinTrack API (Ktor)
After=network.target mysql.service ollama.service
Requires=mysql.service

[Service]
Type=simple
User=fintrack
Group=fintrack
WorkingDirectory=/opt/fintrack
EnvironmentFile=/etc/fintrack/env
ExecStart=/usr/bin/java -jar /opt/fintrack/fintrack.jar
Restart=on-failure
RestartSec=5s
StandardOutput=journal
StandardError=journal
SyslogIdentifier=fintrack

# Security hardening
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full

[Install]
WantedBy=multi-user.target
```

Enable and start it:

```bash
sudo systemctl daemon-reload
sudo systemctl enable fintrack
sudo systemctl start fintrack

# Watch startup logs
sudo journalctl -u fintrack -f
```

You should see:

```
Application started in 0.6 seconds.
Responding at http://0.0.0.0:3000
```

---

## 8. Reverse Proxy with Nginx

Create a site config:

```bash
sudo nano /etc/nginx/sites-available/fintrack
```

```nginx
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass         http://127.0.0.1:3000;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;

        # Longer timeouts for /extract — LLM inference can take 30–60s
        proxy_connect_timeout  10s;
        proxy_send_timeout     120s;
        proxy_read_timeout     120s;

        client_max_body_size   2M;
    }
}
```

Enable and reload:

```bash
sudo ln -s /etc/nginx/sites-available/fintrack /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

---

## 9. TLS / HTTPS with Certbot

```bash
sudo apt install -y certbot python3-certbot-nginx

# Issue certificate (replace with your domain)
sudo certbot --nginx -d api.yourdomain.com

# Verify auto-renewal
sudo certbot renew --dry-run
```

After Certbot finishes, update `JWT_DOMAIN` in `/etc/fintrack/env` to your HTTPS URL, then restart:

```bash
sudo systemctl restart fintrack
```

---

## 10. Smoke Tests

Run these from any machine to confirm the deployment:

```bash
BASE="https://api.yourdomain.com"

# 1. Health check
curl "$BASE/"
# Expected: FinTrack API is running

# 2. Register a user
curl -s -X POST "$BASE/register" \
  -H "Content-Type: application/json" \
  -d '{"name":"Sendiko","email":"user@example.com","password":"SecurePassword123"}' | jq .

# 3. Login and capture token
TOKEN=$(curl -s -X POST "$BASE/login" \
  -H "Content-Type: application/json" \
  -d '{"name":"Sendiko","password":"SecurePassword123"}' | jq -r '.user.token')

# 4. Create a wallet (requires auth)
curl -s -X POST "$BASE/wallets" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"BCA Main","purpose":"Savings","type":"card","balance":1000000}' | jq .

# 5. Spending analysis
curl -s "$BASE/analysis/spending" \
  -H "Authorization: Bearer $TOKEN" | jq .data.totalSpending

# 6. Test single-device enforcement
#    Login again — old token should be rejected
NEW_TOKEN=$(curl -s -X POST "$BASE/login" \
  -H "Content-Type: application/json" \
  -d '{"name":"Sendiko","password":"SecurePassword123"}' | jq -r '.user.token')
curl -s "$BASE/wallets" -H "Authorization: Bearer $TOKEN"
# Expected: {"status":401,"message":"Your account has logged in from another device."}
```

---

## 11. Useful Commands

| Task | Command |
|---|---|
| View live logs | `sudo journalctl -u fintrack -f` |
| Restart service | `sudo systemctl restart fintrack` |
| Check service status | `sudo systemctl status fintrack` |
| Test Nginx config | `sudo nginx -t` |
| Reload Nginx | `sudo systemctl reload nginx` |
| Check Ollama status | `sudo systemctl status ollama` |
| List Ollama models | `ollama list` |
| MySQL shell | `mysql -u fintrack_user -p fintrack_db` |
| Check listening ports | `ss -tlnp` |

---

## Updating to a New Version

```bash
# 1. Build locally
./gradlew buildFatJar

# 2. Upload to server
scp build/libs/fintrack-ktor-all.jar user@your-server-ip:/opt/fintrack/fintrack.jar

# 3. Fix ownership and restart
ssh user@your-server-ip "sudo chown fintrack:fintrack /opt/fintrack/fintrack.jar && sudo systemctl restart fintrack"

# 4. Watch startup
ssh user@your-server-ip "sudo journalctl -u fintrack -f"
```

---

> **H2 fallback:** If `DB_HOST` / `DB_NAME` env vars are missing, the app falls back to an **in-memory H2 database**. Data is lost on every restart — always configure MySQL in production.
