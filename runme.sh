#!/bin/bash

# CSC301 A2 - Microservices System startup script
# Usage: ./runme.sh -c              (compile all services)
#        ./runme.sh -u              (start User service locally)
#        ./runme.sh -p              (start Product service locally)
#        ./runme.sh -o              (start Order service locally)
#        ./runme.sh -i              (start ISCS locally)
#        ./runme.sh -w <file>       (run workload parser)
#        ./runme.sh -d              (start all services via Docker Compose)
#        ./runme.sh -ddown          (stop all Docker Compose services)
#        ./runme.sh -remote         (bootstrap and start on remote hosts)
#        ./runme.sh -clean          (remove compiled artifacts)

set -e

BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${BASEDIR}/config.json"
REMOTE_SETUP_SCRIPT="${BASEDIR}/setup_remote.sh"

# Use Maven wrapper (mvnw) - no external Maven installation needed
MVN_CMD="${BASEDIR}/mvnw"

compile() {
    echo "Compiling all services..."
    
    # Compile UserService
    echo "Compiling UserService..."
    cd "${BASEDIR}/UserService"
    ${MVN_CMD} clean package -q -DskipTests
    
    # Compile ProductService
    echo "Compiling ProductService..."
    cd "${BASEDIR}/ProductService"
    ${MVN_CMD} clean package -q -DskipTests
    
    # Compile OrderService
    echo "Compiling OrderService..."
    cd "${BASEDIR}/OrderService"
    ${MVN_CMD} clean package -q -DskipTests
    
    echo "Compilation complete!"
}

start_user_service() {
    echo "Starting User Service..."
    cd "${BASEDIR}/UserService"
    java -jar "target/user-service-1.0.0.jar" "${CONFIG_FILE}"
}

start_product_service() {
    echo "Starting Product Service..."
    cd "${BASEDIR}/ProductService"
    java -jar "target/product-service-1.0.0.jar" "${CONFIG_FILE}"
}

start_order_service() {
    echo "Starting Order Service..."
    cd "${BASEDIR}/OrderService"
    java -jar "target/order-service-1.0.0.jar" "${CONFIG_FILE}"
}

start_iscs() {
    echo "Starting ISCS..."
    cd "${BASEDIR}/ISCS"
    python3 ISCS.py "${CONFIG_FILE}"
}

run_workload() {
    local workload_file=$1
    if [ -z "$workload_file" ]; then
        echo "Error: Workload file not specified"
        echo "Usage: ./runme.sh -w <workloadfile>"
        exit 1
    fi
    
    if [ ! -f "$workload_file" ]; then
        echo "Error: Workload file not found: $workload_file"
        exit 1
    fi
    
    echo "Running workload from: $workload_file"
    cd "${BASEDIR}/WorkloadParser"
    python3 WorkloadParser.py "$workload_file" "http://127.0.0.1:14000"
}

docker_up() {
    echo "Starting all services via Docker Compose..."
    cd "${BASEDIR}"

    # Compose ignores deploy.replicas unless using Swarm, so use --scale for local multi-instance runs.
    local replicas="${REPLICAS:-5}"
    
    # If DB_HOST is set, it means we are using an external database lab machine.
    # Therefore, we use the specific docker-compose-app.yml which completely omits the local postgres DB container!
    if [ -n "$DB_HOST" ]; then
        echo "External DB_HOST detected ($DB_HOST). Using docker-compose-app.yml to avoid spinning up local postgres!"
        docker compose -f docker-compose-app.yml up --build -d \
            --scale user-service="$replicas" \
            --scale product-service="$replicas" \
            --scale order-service="$replicas" \
            --scale iscs="$replicas"
    else
        echo "No DB_HOST detected. Using standard docker-compose.yml (includes local postgres)."
        docker compose up --build -d \
            --scale user-service="$replicas" \
            --scale product-service="$replicas" \
            --scale order-service="$replicas" \
            --scale iscs="$replicas"
    fi
    
    echo ""
    echo "========================================="
    echo "🚀 SCALED CLUSTER STARTED SUCCESSFULLY! 🚀"
    echo "========================================="
    echo "Your unified Load Balancer is now running at:"
    echo "  http://localhost:80"
    echo ""
    echo "Under the hood, this is routing to:"
    echo "  - UserService x3 replicas"
    echo "  - ProductService x3 replicas"
    echo "  - OrderService x3 replicas"
    echo "  - ISCS x3 replicas"
    echo "  - PostgreSQL High-Conn DB"
    echo "========================================="
}

docker_down() {
    echo "Stopping all Docker Compose services..."
    cd "${BASEDIR}"
    
    if [ -n "$DB_HOST" ]; then
        docker compose -f docker-compose-app.yml down
    else
        docker compose down
    fi
    
    echo "All services stopped."
}

clean() {
    echo "Cleaning compiled files..."
    rm -rf "${BASEDIR}/UserService/target"
    rm -rf "${BASEDIR}/ProductService/target"
    rm -rf "${BASEDIR}/OrderService/target"
    rm -rf "${BASEDIR}/UserService/dependency-reduced-pom.xml"
    rm -rf "${BASEDIR}/ProductService/dependency-reduced-pom.xml"
    rm -rf "${BASEDIR}/OrderService/dependency-reduced-pom.xml"
    echo "Clean complete!"
}

remote_setup() {
    if [ ! -f "$REMOTE_SETUP_SCRIPT" ]; then
        echo "Error: $REMOTE_SETUP_SCRIPT not found"
        exit 1
    fi
    if [ ! -x "$REMOTE_SETUP_SCRIPT" ]; then
        chmod +x "$REMOTE_SETUP_SCRIPT"
    fi
    "$REMOTE_SETUP_SCRIPT"
}

# Parse command line arguments
if [ $# -eq 0 ]; then
    echo "Usage: $0 {-c|-u|-p|-o|-i|-w <file>|-d|-ddown|-remote|-clean}"
    echo "  -c                Compile all services"
    echo "  -u                Start User Service (local)"
    echo "  -p                Start Product Service (local)"
    echo "  -o                Start Order Service (local)"
    echo "  -i                Start ISCS (local)"
    echo "  -w <workloadfile> Run Workload Parser"
    echo "  -d                Start all services via Docker Compose"
    echo "  -ddown            Stop all Docker Compose services"
    echo "  -remote           Bootstrap and start on remote hosts"
    echo "  -clean            Remove compiled artifacts"
    exit 1
fi

case "$1" in
    -c)
        compile
        ;;
    -u)
        start_user_service
        ;;
    -p)
        start_product_service
        ;;
    -o)
        start_order_service
        ;;
    -i)
        start_iscs
        ;;
    -w)
        run_workload "$2"
        ;;
    -d)
        docker_up
        ;;
    -ddown)
        docker_down
        ;;
    -remote)
        remote_setup
        ;;
    -clean)
        clean
        ;;
    *)
        echo "Unknown option: $1"
        echo "Usage: $0 {-c|-u|-p|-o|-i|-w <file>|-d|-ddown|-remote|-clean}"
        exit 1
        ;;
esac

