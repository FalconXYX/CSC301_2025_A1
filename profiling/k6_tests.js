import http from "k6/http";
import { check, sleep } from "k6";
import exec from "k6/execution";

// This script simulates your demo test scaling up.
export const options = {
  // Defines a tiered step test for gradual scale.
  // Adjust target values higher to test maximum throughput!
  stages: [
    { duration: "5s", target: 50 }, // Ramp up to 50 concurrent users
    { duration: "30s", target: 200 }, // Hold at 200
    { duration: "60s", target: 500 }, // Hold at 500
    { duration: "60s", target: 1000 }, // Hold at 1000
    { duration: "10s", target: 0 }, // Ramp down to 0
  ],
  thresholds: {
    http_req_failed: ["rate<0.50"], // We expect failures because we deliberately inject BAD requests (approx 2/6 requests fail by design)!
    http_req_duration: ["p(95)<500"], // 95% of requests must be below 500ms
  },
};

const BASE_URL = __ENV.TARGET_URL || "http://localhost:80";

export default function () {
  const iter = exec.scenario.iterationInTest;
  const vu = exec.vu.idInTest;
  const uniqueId = parseInt(`${vu}${iter}`);

  // ==========================================
  // 1. Well-formed correct POST requests
  // ==========================================
  const createPayload = JSON.stringify({
    command: "create",
    id: uniqueId,
    username: `user_${uniqueId}`,
    email: `user_${uniqueId}@email.com`,
    password: "password123",
  });

  let res = http.post(`${BASE_URL}/user`, createPayload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, {
    "POST user create status is 200/201": (r) =>
      r.status === 200 || r.status === 201,
  });

  const createProductPayload = JSON.stringify({
    command: "create",
    id: uniqueId,
    name: `product_${uniqueId}`,
    description: "A test product",
    price: 19.99,
    quantity: 100,
  });

  res = http.post(`${BASE_URL}/product`, createProductPayload, {
    headers: { "Content-Type": "application/json" },
  });

  check(res, {
    "POST product create status is 200/201": (r) =>
      r.status === 200 || r.status === 201,
  });

  // ==========================================
  // 2. "Incorrect" POST requests
  // ==========================================
  // Missing fields
  const badPayload = JSON.stringify({
    command: "create",
    username: "baduser", // missing id, email, password
  });
  res = http.post(`${BASE_URL}/user`, badPayload, {
    headers: { "Content-Type": "application/json" },
  });
  check(res, {
    "POST bad user (missing field) handled correctly (400)": (r) =>
      r.status >= 400 && r.status < 500,
  });

  // Values of correct fields are incorrect (e.g., "placeeee oooorder")
  const badOrderPayload = JSON.stringify({
    command: "placeeee oooorder", // Invalid command value literally specified in the assignment outline!
    user_id: uniqueId,
    product_id: uniqueId,
    quantity: "one", // String instead of int
  });
  res = http.post(`${BASE_URL}/order`, badOrderPayload, {
    headers: { "Content-Type": "application/json" },
  });
  check(res, {
    "POST bad command/data handled correctly (400)": (r) =>
      r.status >= 400 && r.status < 500,
  });

  // ==========================================
  // 3. GET requests with correct data routes
  // ==========================================
  res = http.get(`${BASE_URL}/user/${uniqueId}`);
  check(res, {
    "GET existing user status is 200": (r) => r.status === 200,
  });

  // ==========================================
  // 4. GET requests with routes that do not exist
  // ==========================================
  res = http.get(`${BASE_URL}/user/999999999`); // High probability of not existing initially
  check(res, {
    "GET non-existent user handled properly": (r) =>
      r.status === 404 || r.status === 400,
  });

  sleep(0.1); // Small think time
}
