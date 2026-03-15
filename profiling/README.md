# Component 2 Profiling Toolkit

This directory contains resources to help you profile and create the graphs needed for your PDF final report.

## 1. Preparing the Component 2 Database

Since the Component 2 assignment mandates the database runs on a separate machine, we have adjusted the `docker-compose.yml`. You do NOT need to hardcode IPs anymore.

When you are on the lab machine and the database IP is known (say, `192.168.1.50`), you can launch the cluster to connect to that remote server using:

```bash
DB_HOST=192.168.1.50 DB_PORT=5432 docker compose up -d
```

_(If you don't provide the `DB_HOST`, it smoothly defaults to using the internal Docker postgres container for your local testing)._

## 2. Setting up load-testing

We have generated `k6_tests.js`. **K6** is an incredible tool for visualizing and measuring extreme traffic loads (perfect for hitting that 4,000+ RPS goal).

### Install k6 (macOS):

```bash
brew install k6
```

_(For linux lab machines: `sudo apt-get install k6`)_

### Run your first load test

While your Docker cluster is running:

```bash
k6 run k6_tests.js
```

### Collecting Results for the Report

When `k6` finishes, it outputs a gorgeous summary grid. Look for:

- `http_reqs`: Shows total requests handled and **Requests Per Second (RPS)**.
- `http_req_duration`: Shows you the average latency.

**How to generate Tables/Charts for your Assignment:**

1. Put connection pools to normal sizes, run K6, screenshot the terminal or log the RPS numbers.
2. Bump your connection pools and Nginx limits (the tunings we applied earlier).
3. Run K6 again - the numbers will jump up! Document that final state in Excel as your comparison chart mapping out "Thread Optimization resulted in a 4X TPS boost".
