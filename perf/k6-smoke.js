import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s',
  thresholds: {
    http_req_duration: ['p(95)<400'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/search?query=vector%20search&limit=5`);
  check(res, {
    'status is 200': (r) => r.status === 200,
    'has array response': (r) => r.json && Array.isArray(r.json()),
  });
  sleep(1);
}
