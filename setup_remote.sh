#!/bin/bash

# Bootstrap and start the CSC301 services on remote hosts over SSH.
# Reads user/password and host list from .env in this repo.

set -euo pipefail

BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${BASEDIR}/.env"
REMOTE_DIR_DEFAULT="$HOME/CSC301_2025_A1"
MODE="docker"
REMOTE_DIR="${REMOTE_DIR_DEFAULT}"

usage() {
    echo "Usage: $0 [--mode docker|compile-only] [--remote-dir PATH]"
    echo "  --mode docker        Copy repo, compile, and start docker compose (default)"
    echo "  --mode compile-only  Copy repo and compile only"
    echo "  --remote-dir PATH    Remote directory to deploy to (default: ${REMOTE_DIR_DEFAULT})"
    exit 1
}

while [ $# -gt 0 ]; do
    case "$1" in
        --mode)
            MODE="$2"
            shift 2
            ;;
        --remote-dir)
            REMOTE_DIR="$2"
            shift 2
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "Unknown option: $1"
            usage
            ;;
    esac
done

if [ ! -f "$ENV_FILE" ]; then
    echo "Error: .env not found at $ENV_FILE"
    exit 1
fi

if ! command -v ssh >/dev/null 2>&1; then
    echo "Error: ssh is required but not found."
    exit 1
fi

if ! command -v scp >/dev/null 2>&1; then
    echo "Error: scp is required but not found."
    exit 1
fi

read_env_value() {
    local key="$1"
    local line
    line=$(grep -E "^[[:space:]]*${key}[[:space:]]*=" "$ENV_FILE" | head -n 1 || true)
    if [ -z "$line" ]; then
        echo ""
        return
    fi
    echo "$line" | sed -E "s/^[^=]+=//" | sed -E "s/^[[:space:]]*//;s/[[:space:]]*$//" | sed -E "s/^\"|\"$//g; s/^'|'$//g"
}

SSH_USER="$(read_env_value user)"
SSH_PASS="$(read_env_value password)"
SSH_HOSTS_LINE="$(read_env_value SSH_HOSTS)"
DB_HOST_ENV="$(read_env_value DB_HOST)"
DB_PORT_ENV="$(read_env_value DB_PORT)"

HOSTS=()
if [ -n "$SSH_HOSTS_LINE" ]; then
    while IFS= read -r h; do
        [ -n "$h" ] && HOSTS+=("$h")
    done < <(echo "$SSH_HOSTS_LINE" | tr ', ' '\n' | sed -E '/^[[:space:]]*$/d')
else
    # Fallback: parse hostnames from a line like: ssh=["host1", "host2"]
    while IFS= read -r h; do
        [ -n "$h" ] && HOSTS+=("$h")
    done < <(grep -E "^[[:space:]]*ssh[[:space:]]*=" "$ENV_FILE" | grep -oE "[A-Za-z0-9._-]+\.[A-Za-z]{2,}")
fi

if [ -z "$SSH_USER" ]; then
    echo "Error: user not found in .env (expected key: user)."
    exit 1
fi

if [ -n "$SSH_PASS" ]; then
    echo "Note: password in .env is ignored for key-based SSH."
fi

if [ ${#HOSTS[@]} -eq 0 ]; then
    echo "Error: no SSH hosts found in .env (expected SSH_HOSTS or ssh list)."
    exit 1
fi

DB_HOST_TARGET=""
if [ -n "$DB_HOST_ENV" ]; then
    DB_HOST_TARGET="$DB_HOST_ENV"
else
    DB_HOST_TARGET="${HOSTS[0]}"
fi

DB_HOST_PRESENT=false
for host in "${HOSTS[@]}"; do
    if [ "$host" = "$DB_HOST_TARGET" ]; then
        DB_HOST_PRESENT=true
        break
    fi
done

if [ "$DB_HOST_PRESENT" != "true" ]; then
    echo "Error: DB_HOST '$DB_HOST_TARGET' is not in the SSH host list."
    exit 1
fi

ARCHIVE_PATH="/tmp/CSC301_2025_A1_bundle.tgz"

create_bundle() {
    echo "Creating bundle..."
    tar -czf "$ARCHIVE_PATH" \
        --exclude='.git' \
        --exclude='**/target' \
        --exclude='**/.mvn' \
        --exclude='**/__pycache__' \
        -C "$BASEDIR" .
}

remote_exec() {
    local host="$1"
    local cmd="$2"
    ssh -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        "$SSH_USER@$host" "$cmd"
}

remote_copy() {
    local host="$1"
    scp -o BatchMode=yes -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
        "$ARCHIVE_PATH" "$SSH_USER@$host:$REMOTE_DIR/"
}

create_bundle

for host in "${HOSTS[@]}"; do
    echo "==============================="
    echo "Setting up $host"
    remote_exec "$host" "mkdir -p '$REMOTE_DIR'"
    remote_copy "$host"
    remote_exec "$host" "cd '$REMOTE_DIR' && tar -xzf CSC301_2025_A1_bundle.tgz && rm -f CSC301_2025_A1_bundle.tgz"
    remote_exec "$host" "cd '$REMOTE_DIR' && chmod +x runme.sh"
    remote_exec "$host" "cd '$REMOTE_DIR' && ./runme.sh -c"

    if [ "$MODE" = "docker" ]; then
        if [ "$host" = "$DB_HOST_TARGET" ]; then
            remote_exec "$host" "cd '$REMOTE_DIR' && ./runme.sh -d"
        else
            if [ -n "$DB_PORT_ENV" ]; then
                remote_exec "$host" "cd '$REMOTE_DIR' && DB_HOST='${DB_HOST_TARGET}' DB_PORT='${DB_PORT_ENV}' ./runme.sh -d"
            else
                remote_exec "$host" "cd '$REMOTE_DIR' && DB_HOST='${DB_HOST_TARGET}' ./runme.sh -d"
            fi
        fi
    fi

done

echo "Remote setup complete."
