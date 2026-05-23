import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8800';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '2m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<5000'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/products/all`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'json message present': (r) => {
      try {
        const j = r.json();
        return j && typeof j.message === 'string';
      } catch {
        return false;
      }
    },
  });
  sleep(0.5);
}
