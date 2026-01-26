#!/bin/bash

# CSC301 A1 - Microservices System startup script
# Usage: ./runme.sh -c  (compile)
#        ./runme.sh -u  (start User service)
#        ./runme.sh -p  (start Product service)
#        ./runme.sh -o  (start Order service)
#        ./runme.sh -i  (start ISCS)
#        ./runme.sh -w <workloadfile> (run workload parser)

set -e

BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="${BASEDIR}/config.json"

# Use Maven wrapper (mvnw) - no external Maven installation needed
MVN_CMD="${BASEDIR}/mvnw"

compile() {
    echo "Compiling all services..."
    
    # Compile UserService
    echo "Compiling UserService..."
    cd "${BASEDIR}/UserService"
    ${MVN_CMD} clean package -q
    
    # Compile ProductService
    echo "Compiling ProductService..."
    cd "${BASEDIR}/ProductService"
    ${MVN_CMD} clean package -q
    
    # Compile OrderService
    echo "Compiling OrderService..."
    cd "${BASEDIR}/OrderService"
    ${MVN_CMD} clean package -q
    
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

# Parse command line arguments
if [ $# -eq 0 ]; then
    echo "Usage: $0 {-c|-u|-p|-o|-i|-w <file>}"
    echo "  -c              Compile all services"
    echo "  -u              Start User Service"
    echo "  -p              Start Product Service"
    echo "  -o              Start Order Service"
    echo "  -i              Start ISCS"
    echo "  -w <workloadfile> Run Workload Parser"
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
    -clean)
        clean
        ;;
    *)
        echo "Unknown option: $1"
        echo "Usage: $0 {-c|-u|-p|-o|-i|-w <file>}"
        exit 1
        ;;

esac
