# CSC301 A1 - Microservices System

Spring Boot microservices for User, Product, and Order management with a lightweight Python ISCS router and workload parser.

## Compile
```
./runme.sh -c
```

## Run
Start services in this order (each in its own terminal):
1. ./runme.sh -i  (ISCS, port 14002)
2. ./runme.sh -u  (UserService, port 14001)
3. ./runme.sh -p  (ProductService, port 15000)
4. ./runme.sh -o  (OrderService, port 14000)

Run workload parser:
```
./runme.sh -w <workload_file>
```
Example:
```
./runme.sh -w "A1 Details/workload3u20c.txt"
```

## Configuration
Edit config.json to change IPs/ports for all services.

## API Summary
OrderService (public gateway): /order, /user, /product
UserService: /user
ProductService: /product

## Requirements
- Java 17+
- Python 3.7+
No additional libraries required.
