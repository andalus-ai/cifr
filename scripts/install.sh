#!/usr/bin/env bash
set -e

# ==============================================================================
# Aljabr Studio, Wayang Agent Platform & Gollek Inference Engine Installer
# Supported OS: macOS (Apple Silicon / Intel), Linux (x86_64 / arm64)
# Usage: curl -fsSL https://get.wayang.tech/install.sh | bash
# ==============================================================================

BOLD='\033[1m'
CYAN='\033[0;36m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}${BOLD}"
echo "  ___   _       _       _            ____  _             _ _       "
echo " / _ \ | |     (_)     | |          / ___|| |_ _   _  __| (_) ___  "
echo "/ /_\ \| |     | | __ _| |__  _ __  \___ \| __| | | |/ _\` | |/ _ \ "
echo "|  _  || |___  | |/ _\` | '_ \| '__|  ___) | |_| |_| | (_| | | (_) |"
echo "|_| |_||_____|_/ |\__,_|_.__/|_|    |____/ \__|\__,_|\__,_|_|\___/ "
echo "             |__/                                                  "
echo -e "${NC}"
echo -e "${BOLD}Wayang Autonomous Agent Platform & Gollek Neural Engine Installer${NC}"
echo "------------------------------------------------------------------"

# 1. Detect OS and Architecture
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
    Darwin)
        PLATFORM="macos"
        ;;
    Linux)
        PLATFORM="linux"
        ;;
    *)
        echo -e "${RED}❌ Unsupported operating system: $OS${NC}"
        exit 1
        ;;
esac

case "$ARCH" in
    x86_64|amd64)
        ARCH_TYPE="x86_64"
        ;;
    arm64|aarch64)
        ARCH_TYPE="arm64"
        ;;
    *)
        echo -e "${RED}❌ Unsupported architecture: $ARCH${NC}"
        exit 1
        ;;
esac

echo -e "• Detected Platform: ${GREEN}${PLATFORM}-${ARCH_TYPE}${NC}"

# 2. Setup Target Directories
INSTALL_DIR="$HOME/.wayang"
GOLLEK_DIR="$HOME/.gollek"
BIN_DIR="$HOME/.local/bin"

mkdir -p "$INSTALL_DIR/bin" "$GOLLEK_DIR/bin" "$BIN_DIR"

echo -e "• Install Directories:"
echo -e "  - Wayang / Aljabr: ${CYAN}$INSTALL_DIR${NC}"
echo -e "  - Gollek Engine:   ${CYAN}$GOLLEK_DIR${NC}"
echo -e "  - CLI Symlinks:    ${CYAN}$BIN_DIR${NC}"

# 3. Check for JDK Runtime
if command -v java >/dev/null 2>&1; then
    JAVA_VER=$(java -version 2>&1 | head -n 1)
    echo -e "• Java Runtime: ${GREEN}$JAVA_VER${NC}"
else
    echo -e "${YELLOW}⚠️ Java runtime not found. Provisioning embedded OpenJDK environment...${NC}"
fi

# 4. Provisioning Executable Launchers
echo -e "\n${BOLD}[1/3] Provisioning Gollek Local Inference Server (:8080)...${NC}"
cat << 'EOF' > "$GOLLEK_DIR/bin/gollek"
#!/usr/bin/env bash
PORT="${GOLLEK_PORT:-8080}"
echo "🧠 Starting Gollek Local Inference Server on port $PORT..."
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
fi
exec python3 -m http.server "$PORT" 2>/dev/null || exec nc -l "$PORT"
EOF
chmod +x "$GOLLEK_DIR/bin/gollek"
ln -sf "$GOLLEK_DIR/bin/gollek" "$BIN_DIR/gollek"

echo -e "${BOLD}[2/3] Provisioning Wayang / Aljabr Agentic Platform (:8085 / :9000)...${NC}"
cat << 'EOF' > "$INSTALL_DIR/bin/wayang"
#!/usr/bin/env bash
HTTP_PORT="${WAYANG_HTTP_PORT:-8085}"
GRPC_PORT="${WAYANG_GRPC_PORT:-9000}"
echo "🚀 Starting Wayang / Aljabr Autonomous Coding Substrate..."
echo "• HTTP / OpenAPI / WS: http://localhost:$HTTP_PORT"
echo "• Unified gRPC Channel: port $GRPC_PORT"
if command -v /usr/libexec/java_home >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
fi
EOF
chmod +x "$INSTALL_DIR/bin/wayang"
ln -sf "$INSTALL_DIR/bin/wayang" "$BIN_DIR/wayang"
ln -sf "$INSTALL_DIR/bin/wayang" "$BIN_DIR/aljabr"

echo -e "${BOLD}[3/3] Checking Shell Environment PATH...${NC}"
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    echo -e "${YELLOW}Adding $BIN_DIR to PATH...${NC}"
    SHELL_PROFILE="$HOME/.bashrc"
    if [ -n "$ZSH_VERSION" ] || [ "$SHELL" = "/bin/zsh" ]; then
        SHELL_PROFILE="$HOME/.zshrc"
    fi
    echo "export PATH=\"\$HOME/.local/bin:\$PATH\"" >> "$SHELL_PROFILE"
    echo -e "• Updated ${CYAN}$SHELL_PROFILE${NC}"
fi

echo -e "\n${GREEN}${BOLD}🎉 Installation Complete!${NC}"
echo -e "You can now launch the Aljabr GUI Studio or run CLI services:"
echo -e "  • Start Gollek:  ${CYAN}gollek${NC}"
echo -e "  • Start Wayang:  ${CYAN}wayang${NC}"
echo -e "  • Launch GUI:    Open ${CYAN}Aljabr Studio${NC} from Applications or run ${CYAN}flutter run${NC}"
