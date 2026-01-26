#!/usr/bin/env python3

import json
import sys
import urllib.request
import urllib.error
import time

def make_request(url, data):
    """Make HTTP POST request"""
    try:
        req = urllib.request.Request(
            url,
            data=data.encode('utf-8'),
            headers={'Content-Type': 'application/json'},
            method='POST'
        )
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception as e:
        print(f"Connection error: {e}")
        return -1

def parse_workload(workload_file, order_service_url):
    """Parse and execute workload commands"""
    with open(workload_file, 'r') as f:
        lines = f.readlines()
    
    total_commands = 0
    successful_commands = 0
    failed_commands = 0
    start_time = time.time()

    for line_num, line in enumerate(lines, 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        
        try:
            # Parse the JSON command
            command = json.loads(line)
            total_commands += 1
            
            # Determine the endpoint
            endpoint = '/order'
            if 'command' in command and command['command'].lower() == 'create':
                if 'username' in command or 'email' in command:
                    endpoint = '/user'
                elif 'productname' in command or 'price' in command:
                    endpoint = '/product'
            elif 'command' in command:
                if 'username' in command or 'email' in command or 'update' in command['command'].lower() or 'delete' in command['command'].lower():
                    # Check if it's a user command
                    if 'username' in command or 'email' in command:
                        endpoint = '/user'
                    elif 'productname' in command or 'price' in command:
                        endpoint = '/product'
            
            # Send request
            try:
                url = f"{order_service_url}{endpoint}"
                status = make_request(url, json.dumps(command))
                
                if status == 200:
                    successful_commands += 1
                else:
                    if status > 0:
                        print(f"Line {line_num}: Status {status}")
                    failed_commands += 1
            except Exception as e:
                print(f"Line {line_num}: Connection error - {e}")
                failed_commands += 1
        
        except json.JSONDecodeError:
            print(f"Line {line_num}: Invalid JSON - {line}")
            failed_commands += 1
        except Exception as e:
            print(f"Line {line_num}: Error - {e}")
            failed_commands += 1
    
    end_time = time.time()
    elapsed = end_time - start_time
    
    print(f"\nWorkload Summary:")
    print(f"Total Commands: {total_commands}")
    print(f"Successful: {successful_commands}")
    print(f"Failed: {failed_commands}")
    print(f"Time Elapsed: {elapsed:.2f}s")

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print("Usage: python3 WorkloadParser.py <workload_file> [order_service_url]")
        sys.exit(1)
    
    workload_file = sys.argv[1]
    order_service_url = sys.argv[2] if len(sys.argv) > 2 else "http://127.0.0.1:14000"
    
    print(f"Parsing workload from {workload_file}")
    print(f"Order Service URL: {order_service_url}\n")
    
    parse_workload(workload_file, order_service_url)
