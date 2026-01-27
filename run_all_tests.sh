#!/bin/bash

# CSC301 A1 - Automated Test Suite Runner
# This script starts all services and runs the test suite

set -e

BASEDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEST_SCRIPT="$BASEDIR/run_tests.py"
CONFIG_FILE="$BASEDIR/config.json"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Cleanup function
cleanup() {
    echo ""
    echo -e "${YELLOW}Cleaning up...${NC}"
    echo "Stopping services..."
    pkill -f "java.*UserService" 2>/dev/null || true
    pkill -f "java.*ProductService" 2>/dev/null || true
    pkill -f "java.*OrderService" 2>/dev/null || true
    pkill -f "ISCS.py" 2>/dev/null || true
    pkill -f "python.*ISCS" 2>/dev/null || true
    sleep 2
    echo "✓ Services stopped"
}

# Set trap to cleanup on exit
trap cleanup EXIT

main() {
    echo "========================================================================"
    echo "CSC301 A1 - AUTOMATED TEST SUITE"
    echo "========================================================================"
    echo ""
    
    # Kill any leftover processes from previous runs
    echo "Cleaning up any leftover processes..."
    pkill -9 -f "user-service" 2>/dev/null || true
    pkill -9 -f "product-service" 2>/dev/null || true
    pkill -9 -f "order-service" 2>/dev/null || true
    pkill -9 -f "ISCS.py" 2>/dev/null || true
    sleep 2
    echo ""
    
    # Check if test script exists
    if [ ! -f "$TEST_SCRIPT" ]; then
        echo -e "${RED}ERROR: Test script not found: $TEST_SCRIPT${NC}"
        return 1
    fi
    
    # Check if test cases exist
    if [ ! -d "$BASEDIR/CSC301_A1_testcases" ]; then
        echo -e "${RED}ERROR: Test cases directory not found${NC}"
        return 1
    fi
    
    echo -e "${GREEN}✓ Test files found${NC}"
    echo ""
    
    # Start services
    echo "========================================================================"
    echo "STARTING SERVICES"
    echo "========================================================================"
    echo ""
    
    echo "Starting ISCS..."
    cd "$BASEDIR"
    python3 ISCS/ISCS.py "$CONFIG_FILE" > /tmp/iscs.log 2>&1 &
    ISCS_PID=$!
    sleep 2
    
    echo "Starting UserService..."
    cd "$BASEDIR/UserService"
    java -jar "target/user-service-1.0.0.jar" "$CONFIG_FILE" > /tmp/user.log 2>&1 &
    USER_PID=$!
    sleep 2
    
    echo "Starting ProductService..."
    cd "$BASEDIR/ProductService"
    java -jar "target/product-service-1.0.0.jar" "$CONFIG_FILE" > /tmp/product.log 2>&1 &
    PRODUCT_PID=$!
    sleep 2
    
    echo "Starting OrderService..."
    cd "$BASEDIR/OrderService"
    java -jar "target/order-service-1.0.0.jar" "$CONFIG_FILE" > /tmp/order.log 2>&1 &
    ORDER_PID=$!
    sleep 3
    
    echo -e "${GREEN}✓ All services started${NC}"
    echo "  ISCS PID: $ISCS_PID"
    echo "  UserService PID: $USER_PID"
    echo "  ProductService PID: $PRODUCT_PID"
    echo "  OrderService PID: $ORDER_PID"
    echo ""
    
    # Check if all services are running
    echo "Verifying services..."
    sleep 1
    
    if ! kill -0 $ISCS_PID 2>/dev/null; then
        echo -e "${RED}✗ ISCS failed to start${NC}"
        echo "Log:"
        cat /tmp/iscs.log
        return 1
    fi
    
    if ! kill -0 $USER_PID 2>/dev/null; then
        echo -e "${RED}✗ UserService failed to start${NC}"
        echo "Log:"
        cat /tmp/user.log
        return 1
    fi
    
    if ! kill -0 $PRODUCT_PID 2>/dev/null; then
        echo -e "${RED}✗ ProductService failed to start${NC}"
        echo "Log:"
        cat /tmp/product.log
        return 1
    fi
    
    if ! kill -0 $ORDER_PID 2>/dev/null; then
        echo -e "${RED}✗ OrderService failed to start${NC}"
        echo "Log:"
        cat /tmp/order.log
        return 1
    fi
    
    echo -e "${GREEN}✓ All services verified${NC}"
    echo ""
    
    # Wait for services to fully initialize and become ready
    echo "Waiting for services to fully initialize..."
    sleep 5
    
    # Try to connect to a few endpoints to ensure services are ready
    echo "Verifying service connectivity..."
    RETRIES=0
    MAX_RETRIES=10
    while [ $RETRIES -lt $MAX_RETRIES ]; do
        if curl -s http://127.0.0.1:14001/user/0 > /dev/null 2>&1; then
            echo -e "${GREEN}✓ Services are ready${NC}"
            break
        fi
        RETRIES=$((RETRIES + 1))
        if [ $RETRIES -lt $MAX_RETRIES ]; then
            echo "Waiting... (attempt $RETRIES/$MAX_RETRIES)"
            sleep 1
        fi
    done
    
    if [ $RETRIES -ge $MAX_RETRIES ]; then
        echo -e "${RED}✗ Services failed to respond${NC}"
        return 1
    fi
    
    echo ""
    
    # Run tests
    echo "========================================================================"
    echo "RUNNING TEST SUITE"
    echo "========================================================================"
    echo ""
    
    cd "$BASEDIR"
    python3 "$TEST_SCRIPT"
    TEST_RESULT=$?
    
    echo ""
    echo "========================================================================"
    
    if [ $TEST_RESULT -eq 0 ]; then
        echo -e "${GREEN}✓ TEST SUITE PASSED${NC}"
    else
        echo -e "${RED}✗ TEST SUITE FAILED${NC}"
    fi
    
    echo "========================================================================"
    echo ""
    echo "Service Logs:"
    echo ""
    echo "--- ISCS Log ---"
    tail -10 /tmp/iscs.log 2>/dev/null || echo "No log available"
    echo ""
    echo "--- UserService Log ---"
    tail -10 /tmp/user.log 2>/dev/null || echo "No log available"
    echo ""
    echo "--- ProductService Log ---"
    tail -10 /tmp/product.log 2>/dev/null || echo "No log available"
    echo ""
    echo "--- OrderService Log ---"
    tail -10 /tmp/order.log 2>/dev/null || echo "No log available"
    echo ""
    
    return $TEST_RESULT
}

main
exit $?
