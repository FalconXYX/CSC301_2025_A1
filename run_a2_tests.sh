#!/bin/bash

# CSC301 A2 - Test Runner
# Docker runs PostgreSQL only. All app services are compiled and run locally.
#
# Usage:
#   ./run_a2_tests.sh                    # compile if needed, start services, run tests
#   ./run_a2_tests.sh --compile          # force recompile all services first
#   ./run_a2_tests.sh --skip-persistence # passed through to test_a2.py
#   ./run_a2_tests.sh --reset            # passed through to test_a2.py

BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="${BASEDIR}/test_a2.py"
CONFIG_FILE="${BASEDIR}/config.json"
MVN="${BASEDIR}/mvnw"

# ── Colours ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ── Parse flags ────────────────────────────────────────────────────────────────
FORCE_COMPILE=false
PYTEST_ARGS=()
for arg in "$@"; do
    case "$arg" in
        --compile) FORCE_COMPILE=true ;;
        *)         PYTEST_ARGS+=("$arg") ;;
    esac
done

# ── Cleanup ────────────────────────────────────────────────────────────────────
cleanup() {
    echo ""
    echo -e "${YELLOW}Stopping services...${NC}"
    [[ -n "$ISCS_PID"    ]] && kill "$ISCS_PID"    2>/dev/null || true
    [[ -n "$USER_PID"    ]] && kill "$USER_PID"    2>/dev/null || true
    [[ -n "$PRODUCT_PID" ]] && kill "$PRODUCT_PID" 2>/dev/null || true
    [[ -n "$ORDER_PID"   ]] && kill "$ORDER_PID"   2>/dev/null || true
    sleep 1
    echo -e "${YELLOW}Stopping Postgres (Docker)...${NC}"
    cd "${BASEDIR}"
    docker compose down 2>/dev/null || true
    echo -e "${GREEN}✓ All stopped${NC}"
}

trap cleanup EXIT

# ── Header ─────────────────────────────────────────────────────────────────────
echo "========================================================================"
echo "  CSC301 A2 – Test Runner"
echo "========================================================================"
echo ""

# ── Preflight ──────────────────────────────────────────────────────────────────
if [ ! -f "${TEST_SCRIPT}" ]; then
    echo -e "${RED}ERROR: test_a2.py not found${NC}"; exit 1
fi
if ! command -v docker &> /dev/null || ! docker info &> /dev/null; then
    echo -e "${RED}ERROR: Docker is not available / not running${NC}"; exit 1
fi
if ! command -v java &> /dev/null; then
    echo -e "${RED}ERROR: java not found in PATH${NC}"; exit 1
fi
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}ERROR: python3 not found in PATH${NC}"; exit 1
fi
echo -e "${GREEN}✓ Preflight checks passed${NC}"
echo ""

# ── Compile services if JARs are missing or --compile was passed ────────────────
need_compile() {
    [[ ! -f "${BASEDIR}/UserService/target/user-service-1.0.0.jar"       ]] ||
    [[ ! -f "${BASEDIR}/ProductService/target/product-service-1.0.0.jar" ]] ||
    [[ ! -f "${BASEDIR}/OrderService/target/order-service-1.0.0.jar"     ]]
}

if $FORCE_COMPILE || need_compile; then
    echo "========================================================================"
    echo "  COMPILING SERVICES (Maven)"
    echo "========================================================================"
    for svc in UserService ProductService OrderService; do
        echo -n "  Compiling ${svc}..."
        cd "${BASEDIR}/${svc}"
        "${MVN}" clean package -q -DskipTests
        echo -e " ${GREEN}done${NC}"
    done
    echo ""
fi

# ── Start Postgres via Docker ───────────────────────────────────────────────────
echo "========================================================================"
echo "  STARTING POSTGRES (Docker)"
echo "========================================================================"
cd "${BASEDIR}"
docker compose up -d postgres
echo ""

# Wait for Postgres to be healthy
echo -n "  Waiting for Postgres..."
for i in $(seq 1 30); do
    if docker compose exec -T postgres pg_isready -U csc301 > /dev/null 2>&1; then
        echo -e " ${GREEN}ready${NC}"
        break
    fi
    echo -n "."
    sleep 2
    if [ "$i" -eq 30 ]; then
        echo ""
        echo -e "${RED}ERROR: Postgres did not become ready in time${NC}"
        exit 1
    fi
done
echo ""

# ── Start application services locally ─────────────────────────────────────────
echo "========================================================================"
echo "  STARTING APPLICATION SERVICES"
echo "========================================================================"

# Kill any stale processes from a previous run
pkill -f "ISCS/ISCS.py"              2>/dev/null || true
pkill -f "user-service-1.0.0.jar"    2>/dev/null || true
pkill -f "product-service-1.0.0.jar" 2>/dev/null || true
pkill -f "order-service-1.0.0.jar"   2>/dev/null || true
sleep 1

echo "  Starting ISCS..."
cd "${BASEDIR}"
python3 ISCS/ISCS.py "${CONFIG_FILE}" > /tmp/iscs.log 2>&1 &
ISCS_PID=$!

echo "  Starting UserService..."
cd "${BASEDIR}/UserService"
java -jar target/user-service-1.0.0.jar "${CONFIG_FILE}" > /tmp/user.log 2>&1 &
USER_PID=$!

echo "  Starting ProductService..."
cd "${BASEDIR}/ProductService"
java -jar target/product-service-1.0.0.jar "${CONFIG_FILE}" > /tmp/product.log 2>&1 &
PRODUCT_PID=$!

echo "  Starting OrderService..."
cd "${BASEDIR}/OrderService"
java -jar target/order-service-1.0.0.jar "${CONFIG_FILE}" > /tmp/order.log 2>&1 &
ORDER_PID=$!

echo -e "  ${GREEN}✓ Processes launched${NC}  (ISCS=$ISCS_PID  User=$USER_PID  Product=$PRODUCT_PID  Order=$ORDER_PID)"
echo ""

# ── Wait for services to be ready ──────────────────────────────────────────────
echo "========================================================================"
echo "  WAITING FOR SERVICES TO BECOME READY"
echo "========================================================================"

MAX_WAIT=120
INTERVAL=3

wait_for() {
    local name=$1 url=$2
    local elapsed=0
    echo -n "  ${name} ..."
    until curl -s --max-time 2 "${url}" > /dev/null 2>&1; do
        sleep ${INTERVAL}
        elapsed=$((elapsed + INTERVAL))
        echo -n "."
        if [ ${elapsed} -ge ${MAX_WAIT} ]; then
            echo ""
            echo -e "${RED}  ✗ ${name} did not become ready within ${MAX_WAIT}s${NC}"
            return 1
        fi
    done
    echo -e " ${GREEN}ready${NC}"
}

wait_for "UserService   (:14001)" "http://127.0.0.1:14001/user/0"    || { cat /tmp/user.log;    exit 1; }
wait_for "ProductService(:15000)" "http://127.0.0.1:15000/product/0" || { cat /tmp/product.log; exit 1; }
wait_for "OrderService  (:14000)" "http://127.0.0.1:14000"           || { cat /tmp/order.log;   exit 1; }

echo ""
echo -e "${GREEN}✓ All services are ready${NC}"
echo ""

# ── Run tests ──────────────────────────────────────────────────────────────────
echo "========================================================================"
echo "  RUNNING test_a2.py  ${PYTEST_ARGS[*]}"
echo "========================================================================"
echo ""

cd "${BASEDIR}"
python3 "${TEST_SCRIPT}" "${PYTEST_ARGS[@]}"
TEST_EXIT=$?

echo ""
echo "========================================================================"
if [ ${TEST_EXIT} -eq 0 ]; then
    echo -e "  ${GREEN}✓ ALL TESTS PASSED${NC}"
else
    echo -e "  ${RED}✗ SOME TESTS FAILED (exit code ${TEST_EXIT})${NC}"
    echo ""
    echo "  Tail of service logs:"
    for log in iscs user product order; do
        echo "  --- /tmp/${log}.log ---"
        tail -5 "/tmp/${log}.log" 2>/dev/null || true
    done
fi
echo "========================================================================"
echo ""

exit ${TEST_EXIT}
