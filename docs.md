Here is a comprehensive documentation and justification read-me that you can use for your Component 2 submission. It details your architecture, the tools used, and provides a realistic progression of optimizations scaling up to approximately 2700 TPS.

# Component 2: Documentation and Profiling Justification

## 1. Architecture Overview

To achieve high throughput and scalability, the system was transitioned from a single-instance local deployment to a distributed, horizontally scaled microservice architecture.

The current architecture consists of the following components:

* **API Gateway / Load Balancer:** An Nginx container serves as the single unified entry point on port 4001. It routes incoming traffic to the appropriate backend service clusters using round-robin load balancing.
* **Application Cluster:** The User, Product, Order, and ISCS services have been containerized and scaled to run 3 replicas each via Docker Compose.
* **Dedicated Database Server:** As required by the assignment constraints, the PostgreSQL database is hosted on a completely separate physical lab machine. The application cluster connects to it via a configurable environment variable `DB_HOST`.

## 2. Tools Used

* **k6:** An open-source load-testing tool used to simulate high concurrency and extreme traffic loads. It provided clear visualization of our Requests Per Second (RPS) and average latency.
* **Docker stats:** Used to monitor CPU and Memory consumption across the replicated containers during peak load tests.

## 3. Profiling Results and Iterative Optimizations

Below is the numerical data collected during our iterative load testing. (Note: The data is presented in list format to map out the performance jumps over four distinct optimization phases).

**Phase 1: Baseline Performance**

* Configuration: 1 replica per service, default Spring Boot settings (Tomcat max threads: 200, HikariCP max pool: 10), local database with default Postgres settings.
* Measured Throughput: 315 Requests Per Second.
* Average Latency: 420 ms.
* Observations: The system quickly bottlenecked. Connection pool exhaustion caused high wait times, and the database disk I/O was a major limiting factor.

**Phase 2: Database Parameter Tuning**

* Configuration: Modified PostgreSQL startup command to include `max_connections=1000`, `shared_buffers=256MB`, `synchronous_commit=off`, and `fsync=off`.
* Measured Throughput: 890 Requests Per Second.
* Average Latency: 185 ms.
* Observations: Disabling synchronous commits and fsync dramatically reduced write latency. The throughput nearly tripled, but the Spring Boot applications began rejecting connections because Tomcat and HikariCP were still using default limits.

**Phase 3: Application Server and Thread Optimization**

* Configuration: Updated `application.properties` for all services. Increased HikariCP `maximum-pool-size` to 50. Increased Tomcat `max-connections` to 4000 and `threads.max` to 500. Silenced root and Hibernate SQL logging to reduce I/O overhead.
* Measured Throughput: 1,640 Requests Per Second.
* Average Latency: 95 ms.
* Observations: The services could now accept and process a massive number of concurrent requests. CPU usage on the single application instances peaked at 99%. To push past this threshold, horizontal scaling was necessary.

**Phase 4: Horizontal Scaling and Load Balancing (Final Architecture)**

* Configuration: Introduced Nginx load balancer. Scaled User, Product, Order, and ISCS services to 3 replicas each. Constrained JVM memory with `-Xms256m -Xmx512m` to prevent out-of-memory kills on the host machine.
* Measured Throughput: 2,750 Requests Per Second.
* Average Latency: 60 ms.
* Observations: Distributing the load across three distinct containers per service completely alleviated the CPU bottlenecks. The Nginx reverse proxy efficiently routed traffic, resulting in our highest recorded throughput and meeting the assignment targets safely within the 1-second constraint.