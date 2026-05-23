import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8800';

export const options = {
  stages: [
    { duration: '1m', target: 25 },
    { duration: '1m', target: 50 },
    { duration: '1m', target: 75 },
    { duration: '1m', target: 100 },
    { duration: '2m', target: 100 },
  ],
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/products/all`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time under 10s': (r) => r.timings.duration < 10000,
  });
  sleep(0.2);
}
