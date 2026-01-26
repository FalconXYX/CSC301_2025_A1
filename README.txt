# CSC301 A1 - Microservices System

## Overview
This is a Spring Boot-based microservices system implementing a distributed e-commerce platform with User, Product, and Order management services. The system is designed to run on lab machines without requiring installation of any external libraries or software.

## Key Features
- **Zero External Dependencies**: Uses only standard libraries (Python standard library, bundled Maven)
- **Self-Contained**: All tools and libraries included or downloaded automatically
- **Microservices Architecture**: Four independent services communicating via HTTP/REST APIs

## Architecture
- **UserService**: Manages user accounts (CRUD operations) - Java/Spring Boot on port 14001
- **ProductService**: Manages products and inventory - Java/Spring Boot on port 15000
- **OrderService**: Processes orders and acts as the public API gateway - Java/Spring Boot on port 14000
- **ISCS**: Inter-Service Communication Service for routing - Python with http.server on port 14002
- **WorkloadParser**: Processes workload files and simulates client requests - Pure Python

## Prerequisites
- **Java 17 or higher** (typically pre-installed on lab machines)
- **Python 3.7+** (typically pre-installed on lab machines)

**IMPORTANT**: No additional libraries or packages need to be installed. The project uses:
- Maven Wrapper (mvnw) - downloads Maven automatically if needed
- Python standard library only (json, urllib, http.server, threading, etc.)
- Spring Boot (included in compiled JAR files)

## Compilation

To compile all services:
```bash
./runme.sh -c
```

This will:
1. Clean and compile UserService
2. Clean and compile ProductService
3. Clean and compile OrderService
4. Generate JAR files in each service's `target/` directory

## Running Services

### Start ISCS (Inter-Service Communication Service)
```bash
./runme.sh -i
```
Starts on port 14002 (configurable in config.json)

### Start User Service
```bash
./runme.sh -u
```
Starts on port 14001 (configurable in config.json)

### Start Product Service
```bash
./runme.sh -p
```
Starts on port 15000 (configurable in config.json)

### Start Order Service
```bash
./runme.sh -o
```
Starts on port 14000 (configurable in config.json)

### Run Workload Parser
```bash
./runme.sh -w <workload_file>
```
Example: `./runme.sh -w workload3u20c.txt`

## Service Startup Order
For proper operation, start services in this order:
1. ISCS
2. UserService
3. ProductService
4. OrderService

Then run the workload parser in another terminal.

## Configuration

Edit `config.json` to modify service endpoints:
```json
{
  "UserService": {
    "port": 14001,
    "ip": "127.0.0.1"
  },
  "ProductService": {
    "port": 15000,
    "ip": "127.0.0.1"
  },
  "OrderService": {
    "port": 14000,
    "ip": "127.0.0.1"
  },
  "InterServiceCommunication": {
    "port": 14002,
    "ip": "127.0.0.1"
  }
}
```

## API Endpoints

### User Service (/user)
- **POST /user** - Create/Update/Delete user
- **GET /user/{id}** - Get user details

### Product Service (/product)
- **POST /product** - Create/Update/Delete product
- **GET /product/{id}** - Get product details

### Order Service (Gateway)
- **POST /order** - Place order
- **POST /user** - Create/Update/Delete user (forwards to User Service)
- **GET /user/{id}** - Get user details (forwards to User Service)
- **POST /product** - Create/Update/Delete product (forwards to Product Service)
- **GET /product/{id}** - Get product details (forwards to Product Service)

## Request Format Examples

### Create User
```json
{
  "command": "create",
  "id": 1,
  "username": "john_doe",
  "email": "john@example.com",
  "password": "secure_password"
}
```

### Create Product
```json
{
  "command": "create",
  "id": 1,
  "productname": "Laptop",
  "price": 999.99,
  "quantity": 50
}
```

### Place Order
```json
{
  "command": "place order",
  "user_id": 1,
  "product_id": 1,
  "quantity": 2
}
```

## Status Codes
- **200** - Success
- **400** - Bad request (missing/invalid fields)
- **401** - Unauthorized (credentials mismatch for delete)
- **404** - Resource not found
- **409** - Conflict (duplicate ID)
- **500** - Server error

## Project Structure
```
.
├── config.json
├── runme.sh
├── UserService/
│   ├── pom.xml
│   └── src/main/java/com/csc301/...
├── ProductService/
│   ├── pom.xml
│   └── src/main/java/com/csc301/...
├── OrderService/
│   ├── pom.xml
│   └── src/main/java/com/csc301/...
├── ISCS/
│   └── ISCS.py
└── WorkloadParser/
    └── WorkloadParser.py
```

## Troubleshooting

### No External Dependencies - All Standard Libraries
✓ ISCS uses Python `http.server` and `urllib` (both standard library)
✓ WorkloadParser uses Python `urllib` and `json` (both standard library)
✓ Java services compiled to self-contained JAR files
✓ Maven Wrapper (mvnw) included - downloads Maven automatically if needed

**No `pip install` needed. No additional software required.**

If you see import errors, ensure you're using Python 3.7+ and Java 17+.

### Port Already in Use
If a port is already in use, modify `config.json` to use different ports.

### Compilation Errors
Ensure Java 17+ is installed:
```bash
java -version
```

The Maven wrapper (mvnw) will automatically download Maven if needed.

## Notes
- User passwords are stored as SHA-256 hashes
- Order IDs are UUIDs (randomly generated)
- Services use in-memory storage (data is lost on restart)
- All communication is HTTP/JSON-based
