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
    pkill -f "python.*ISCS" 2>/dev/null || true
    sleep 1
    echo "✓ Services stopped"
}

# Set trap to cleanup on exit
trap cleanup EXIT

main() {
    echo "========================================================================"
    echo "CSC301 A1 - AUTOMATED TEST SUITE"
    echo "========================================================================"
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
    java -cp "target/user-service-1.0.0.jar" com.csc301.UserServiceApp "$CONFIG_FILE" > /tmp/user.log 2>&1 &
    USER_PID=$!
    sleep 2
    
    echo "Starting ProductService..."
    cd "$BASEDIR/ProductService"
    java -cp "target/product-service-1.0.0.jar" com.csc301.ProductServiceApp "$CONFIG_FILE" > /tmp/product.log 2>&1 &
    PRODUCT_PID=$!
    sleep 2
    
    echo "Starting OrderService..."
    cd "$BASEDIR/OrderService"
    java -cp "target/order-service-1.0.0.jar" com.csc301.OrderServiceApp "$CONFIG_FILE" > /tmp/order.log 2>&1 &
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
    
    # Wait for services to fully initialize
    echo "Waiting for services to initialize..."
    sleep 2
    
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
