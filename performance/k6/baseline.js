import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8800';

export const options = {
  vus: 1,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<2000'],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/products/all`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'body has message': (r) => r.body && r.body.includes('message'),
  });
  sleep(1);
}
