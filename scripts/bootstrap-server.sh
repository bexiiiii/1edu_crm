#!/bin/bash

set -euo pipefail

SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
APP_DIR="${APP_DIR:-/opt/1edu_crm}"
DOCKER_NETWORK="${DOCKER_NETWORK:-1edu_1edu-network}"

log() {
    echo "[$(date '+%F %T')] $*"
}

install_base_packages() {
    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        ca-certificates curl git gnupg jq lsb-release make ufw openjdk-21-jdk-headless
}

install_docker() {
    if command -v docker >/dev/null 2>&1; then
        log "Docker is already installed"
        return
    fi

    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg

    . /etc/os-release
    echo \
        "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list

    apt-get update
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
        docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

    systemctl enable --now docker
}

configure_swap() {
    if swapon --show | grep -q .; then
        log "Swap already configured"
        return
    fi

    log "Creating ${SWAP_SIZE_GB}G swapfile"
    fallocate -l "${SWAP_SIZE_GB}G" /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
}

configure_sysctl() {
    cat >/etc/sysctl.d/99-1edu-crm.conf <<'EOF'
vm.max_map_count=262144
fs.inotify.max_user_watches=524288
EOF
    sysctl --system >/dev/null
}

configure_firewall() {
    ufw default deny incoming
    ufw default allow outgoing
    ufw allow OpenSSH
    ufw allow 80/tcp
    ufw allow 443/tcp
    ufw --force enable
}

prepare_runtime_dirs() {
    mkdir -p "$APP_DIR"
    docker network inspect "$DOCKER_NETWORK" >/dev/null 2>&1 || docker network create "$DOCKER_NETWORK" >/dev/null
}

main() {
    [[ "$(id -u)" -eq 0 ]] || { echo "Run as root"; exit 1; }

    install_base_packages
    install_docker
    configure_swap
    configure_sysctl
    configure_firewall
    prepare_runtime_dirs

    log "Bootstrap complete"
    log "Java: $(java -version 2>&1 | head -1)"
    log "Docker: $(docker --version)"
    log "Compose: $(docker compose version)"
    log "App directory: $APP_DIR"
}

main "$@"
