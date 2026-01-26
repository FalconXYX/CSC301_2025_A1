#!/usr/bin/env python3
"""
CSC301 A1 - Automated Test Suite
Runs all test cases from CSC301_A1_testcases and verifies responses
"""

import json
import sys
import urllib.request
import urllib.error
import time
import subprocess
import os
import signal
from pathlib import Path

# Configuration
ORDER_SERVICE_URL = "http://127.0.0.1:14000"
TEST_CASES_DIR = "CSC301_A1_testcases"
PAYLOAD_DIR = f"{TEST_CASES_DIR}/payloads"
RESPONSE_DIR = f"{TEST_CASES_DIR}/responses"

# Test results tracking
test_results = {
    "passed": 0,
    "failed": 0,
    "errors": 0,
    "skipped": 0
}

failed_tests = []
error_tests = []

def load_json_file(filepath):
    """Load JSON file"""
    try:
        with open(filepath, 'r') as f:
            return json.load(f)
    except FileNotFoundError:
        print(f"ERROR: Test file not found: {filepath}")
        return None
    except json.JSONDecodeError as e:
        print(f"ERROR: Invalid JSON in {filepath}: {e}")
        return None

def send_request(endpoint, data):
    """Send HTTP POST request to OrderService"""
    try:
        url = f"{ORDER_SERVICE_URL}{endpoint}"
        req = urllib.request.Request(
            url,
            data=json.dumps(data).encode('utf-8'),
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        with urllib.request.urlopen(req, timeout=5) as response:
            body = response.read().decode('utf-8')
            try:
                return response.status, json.loads(body)
            except:
                return response.status, body
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except:
            return e.code, body
    except Exception as e:
        return -1, {"error": str(e)}

def compare_passwords(expected_hash, response_password):
    """Compare password hashes (case-insensitive)"""
    if isinstance(response_password, str):
        return expected_hash.lower() == response_password.lower()
    return False

def verify_response(test_name, test_key, expected_response, actual_status, actual_response):
    """Verify response matches expected"""
    # Extract expected status from test name (e.g., "user_create_200_1000" -> 200)
    parts = test_name.split('_')
    expected_status = None
    for part in parts:
        if part.isdigit() and len(part) == 3:
            expected_status = int(part)
            break
    
    if expected_status is None:
        print(f"  ⚠ WARNING: Could not determine expected status from test name '{test_name}'")
        expected_status = 200
    
    # Check status code
    if actual_status != expected_status:
        return False, f"Status mismatch: expected {expected_status}, got {actual_status}"
    
    # Check response fields
    if isinstance(expected_response, dict) and isinstance(actual_response, dict):
        for key, expected_value in expected_response.items():
            if key not in actual_response:
                # Password field might not be in response (depending on implementation)
                if key == "password":
                    continue
                return False, f"Missing field: {key}"
            
            actual_value = actual_response[key]
            
            # Special handling for password (hash comparison)
            if key == "password":
                if not compare_passwords(expected_value, actual_value):
                    return False, f"Password hash mismatch"
            # Special handling for numeric fields
            elif key == "id" or key == "quantity" or key == "price":
                if int(expected_value) != int(actual_value):
                    return False, f"Field '{key}' mismatch: expected {expected_value}, got {actual_value}"
            else:
                if expected_value != actual_value:
                    return False, f"Field '{key}' mismatch: expected {expected_value}, got {actual_value}"
    
    return True, "OK"

def run_user_tests():
    """Run user service tests"""
    print("\n" + "="*80)
    print("TESTING USER SERVICE")
    print("="*80)
    
    payloads = load_json_file(f"{PAYLOAD_DIR}/user_testcases.json")
    responses = load_json_file(f"{RESPONSE_DIR}/user_responses.json")
    
    if not payloads or not responses:
        print("ERROR: Could not load user test files")
        test_results["errors"] += 1
        return
    
    test_count = 0
    for test_name, payload in payloads.items():
        test_count += 1
        expected_response = responses.get(test_name, {})
        
        print(f"\n[{test_count}] {test_name}")
        print(f"    Payload: {json.dumps(payload)[:80]}...")
        
        status, response = send_request("/user", payload)
        
        success, message = verify_response(test_name, test_name, expected_response, status, response)
        
        if success:
            print(f"    ✓ PASS - {message}")
            test_results["passed"] += 1
        else:
            print(f"    ✗ FAIL - {message}")
            print(f"      Expected: {expected_response}")
            print(f"      Got: {response}")
            test_results["failed"] += 1
            failed_tests.append((test_name, message))

def run_product_tests():
    """Run product service tests"""
    print("\n" + "="*80)
    print("TESTING PRODUCT SERVICE")
    print("="*80)
    
    payloads = load_json_file(f"{PAYLOAD_DIR}/product_testcases.json")
    responses = load_json_file(f"{RESPONSE_DIR}/product_responses.json")
    
    if not payloads or not responses:
        print("ERROR: Could not load product test files")
        test_results["errors"] += 1
        return
    
    test_count = 0
    for test_name, payload in payloads.items():
        test_count += 1
        expected_response = responses.get(test_name, {})
        
        print(f"\n[{test_count}] {test_name}")
        print(f"    Payload: {json.dumps(payload)[:80]}...")
        
        status, response = send_request("/product", payload)
        
        success, message = verify_response(test_name, test_name, expected_response, status, response)
        
        if success:
            print(f"    ✓ PASS - {message}")
            test_results["passed"] += 1
        else:
            print(f"    ✗ FAIL - {message}")
            print(f"      Expected: {expected_response}")
            print(f"      Got: {response}")
            test_results["failed"] += 1
            failed_tests.append((test_name, message))

def run_order_tests():
    """Run order service tests"""
    print("\n" + "="*80)
    print("TESTING ORDER SERVICE")
    print("="*80)
    
    payloads = load_json_file(f"{PAYLOAD_DIR}/order_testcases.json")
    responses = load_json_file(f"{RESPONSE_DIR}/order_responses.json")
    
    if not payloads or not responses:
        print("ERROR: Could not load order test files")
        test_results["errors"] += 1
        return
    
    test_count = 0
    for test_name, payload in payloads.items():
        test_count += 1
        expected_response = responses.get(test_name, {})
        
        print(f"\n[{test_count}] {test_name}")
        print(f"    Payload: {json.dumps(payload)[:80]}...")
        
        status, response = send_request("/order", payload)
        
        success, message = verify_response(test_name, test_name, expected_response, status, response)
        
        if success:
            print(f"    ✓ PASS - {message}")
            test_results["passed"] += 1
        else:
            print(f"    ✗ FAIL - {message}")
            print(f"      Expected: {expected_response}")
            print(f"      Got: {response}")
            test_results["failed"] += 1
            failed_tests.append((test_name, message))

def print_summary():
    """Print test summary"""
    print("\n" + "="*80)
    print("TEST SUMMARY")
    print("="*80)
    print(f"Passed:  {test_results['passed']}")
    print(f"Failed:  {test_results['failed']}")
    print(f"Errors:  {test_results['errors']}")
    print(f"Skipped: {test_results['skipped']}")
    print(f"Total:   {sum(test_results.values())}")
    
    if failed_tests:
        print(f"\n{len(failed_tests)} Test(s) Failed:")
        for test_name, message in failed_tests:
            print(f"  - {test_name}: {message}")
    
    if test_results['failed'] == 0 and test_results['errors'] == 0:
        print("\n✓ ALL TESTS PASSED!")
        return 0
    else:
        print(f"\n✗ {test_results['failed'] + test_results['errors']} test(s) failed")
        return 1

def main():
    """Main test runner"""
    print("CSC301 A1 - TEST SUITE")
    print("="*80)
    print(f"Order Service URL: {ORDER_SERVICE_URL}")
    print(f"Test Cases Dir: {TEST_CASES_DIR}")
    
    # Check if test directories exist
    if not os.path.exists(PAYLOAD_DIR):
        print(f"ERROR: {PAYLOAD_DIR} not found")
        return 1
    
    if not os.path.exists(RESPONSE_DIR):
        print(f"ERROR: {RESPONSE_DIR} not found")
        return 1
    
    # Check if OrderService is running
    print("\nChecking if OrderService is running...")
    try:
        req = urllib.request.Request(f"{ORDER_SERVICE_URL}/health" if False else f"{ORDER_SERVICE_URL}/user/0")
        with urllib.request.urlopen(req, timeout=2) as response:
            pass
    except:
        print(f"WARNING: Could not connect to OrderService at {ORDER_SERVICE_URL}")
        print("Make sure OrderService is running: ./runme.sh -o")
        return 1
    
    print("✓ OrderService is running")
    
    # Run tests in order: User -> Product -> Order
    run_user_tests()
    run_product_tests()
    run_order_tests()
    
    # Print summary and exit
    return print_summary()

if __name__ == "__main__":
    sys.exit(main())
