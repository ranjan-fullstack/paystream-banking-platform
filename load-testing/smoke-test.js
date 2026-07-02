/**
 * k6 Smoke Test — post-deployment sanity check
 *
 * Runs 1 VU for 5 iterations. Lenient thresholds: this test is only meant to
 * catch "completely broken" deployments (service down, 500 errors, auth dead).
 *
 * Usage:
 *   k6 run -e BASE_URL=http://staging.internal k6/smoke-test.js
 *
 * In Jenkins (after ArgoCD sync):
 *   k6 run -e BASE_URL=${STAGING_URL} \
 *          --out json=k6-smoke-results.json \
 *          backend/k6/smoke-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
    vus: 1,
    iterations: 5,

    thresholds: {
        'http_req_duration': ['p(95)<3000'],  // 3 s — lenient for cold start
        'http_req_failed':   ['rate<0.05'],   // < 5% failure tolerated
        'checks':            ['rate>0.90'],   // 90% checks must pass
    },
};

export default function () {
    // ── 1. Actuator liveness (no auth required)
    const livenessRes = http.get(
        `${BASE_URL}/actuator/health/liveness`,
        { tags: { name: 'liveness' } }
    );
    check(livenessRes, {
        'liveness: status 200': r => r.status === 200,
        'liveness: UP':         r => {
            try { return r.json('status') === 'UP'; }
            catch (_) { return false; }
        },
    });

    // ── 2. Actuator readiness (no auth required)
    const readinessRes = http.get(
        `${BASE_URL}/actuator/health/readiness`,
        { tags: { name: 'readiness' } }
    );
    check(readinessRes, {
        'readiness: status 200': r => r.status === 200,
    });

    // ── 3. Unauthenticated request to a protected endpoint
    //    Expect 401, never 500 — verifies auth filter is alive
    const unauthRes = http.get(
        `${BASE_URL}/api/v2/products`,
        { tags: { name: 'unauth-check' } }
    );
    check(unauthRes, {
        'unauth: returns 401, not 500': r => r.status === 401,
    });

    // ── 4. Rate-limit headers present on any response
    check(unauthRes, {
        'headers: X-RateLimit-Reset present': r =>
            r.headers['X-Ratelimit-Reset'] !== undefined ||
            r.headers['X-RateLimit-Reset'] !== undefined,
        'headers: X-Correlation-Id present': r =>
            r.headers['X-Correlation-Id'] !== undefined ||
            r.headers['X-Correlation-ID'] !== undefined,
    });

    sleep(1);
}
