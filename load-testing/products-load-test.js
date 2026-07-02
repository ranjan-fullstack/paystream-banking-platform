/**
 * k6 Load Test — product-service performance gate
 *
 * Three selectable scenarios driven by the SCENARIO env var:
 *   smoke        — 1 VU, 1 min  (default: quick sanity)
 *   average_load — ramp to 50 VUs, hold 5 min, ramp down
 *   stress       — ramp to 200 VUs to find the breaking point
 *
 * SLA Thresholds (fail the Jenkins stage if breached):
 *   p(95) < 200 ms   global
 *   p(99) < 500 ms   global
 *   error rate < 0.1%
 *
 * Usage:
 *   # Smoke (default)
 *   k6 run -e BASE_URL=http://staging.internal \
 *          -e USERNAME=loadtest@flipkart.com \
 *          -e PASSWORD=LoadTest@123 \
 *          backend/k6/products-load-test.js
 *
 *   # Average load
 *   k6 run -e SCENARIO=average_load \
 *          -e BASE_URL=http://staging.internal \
 *          ...
 *
 *   # With JSON output for Jenkins archiving
 *   k6 run ... --out json=k6-results.json backend/k6/products-load-test.js
 */

import http    from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend  } from 'k6/metrics';

// ─────────────────────────────────────────────────────────────────────────────
// Custom per-flow metrics (visible in k6 output and Grafana Cloud)
// ─────────────────────────────────────────────────────────────────────────────
const errorRate              = new Rate('errors');
const paginatedListTrend     = new Trend('dur_paginated_listing',    true);
const productDetailTrend     = new Trend('dur_product_detail',       true);
const categoryPriceSortTrend = new Trend('dur_category_price_sort',  true);

// ─────────────────────────────────────────────────────────────────────────────
// Environment
// ─────────────────────────────────────────────────────────────────────────────
const BASE_URL  = __ENV.BASE_URL  || 'http://localhost:8080';
const USERNAME  = __ENV.USERNAME  || 'loadtest@flipkart.com';
const PASSWORD  = __ENV.PASSWORD  || 'LoadTest@123';
const SCENARIO  = __ENV.SCENARIO  || 'smoke';

// ─────────────────────────────────────────────────────────────────────────────
// Scenario definitions
// ─────────────────────────────────────────────────────────────────────────────
const ALL_SCENARIOS = {

    smoke: {
        executor: 'constant-vus',
        vus: 1,
        duration: '1m',
        tags: { scenario: 'smoke' },
    },

    average_load: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { target: 10,  duration: '1m'  },   // warm-up
            { target: 50,  duration: '2m'  },   // ramp to peak
            { target: 50,  duration: '5m'  },   // hold (steady-state)
            { target: 0,   duration: '1m'  },   // ramp down
        ],
        tags: { scenario: 'average_load' },
    },

    stress: {
        executor: 'ramping-vus',
        startVUs: 0,
        stages: [
            { target: 50,  duration: '2m'  },   // ramp to baseline
            { target: 100, duration: '2m'  },   // ramp to 2× baseline
            { target: 200, duration: '3m'  },   // peak stress
            { target: 200, duration: '2m'  },   // hold peak
            { target: 0,   duration: '2m'  },   // ramp down
        ],
        gracefulRampDown: '30s',
        tags: { scenario: 'stress' },
    },
};

// ─────────────────────────────────────────────────────────────────────────────
// Options
// ─────────────────────────────────────────────────────────────────────────────
export const options = {

    scenarios: { [SCENARIO]: ALL_SCENARIOS[SCENARIO] },

    // ── SLA Thresholds — Jenkins fails the build if any threshold is breached
    thresholds: {

        // Global SLA
        'http_req_duration': [
            { threshold: 'p(95)<200', abortOnFail: false },
            { threshold: 'p(99)<500', abortOnFail: false },
        ],

        // Error budget: < 0.1% of all requests may fail
        'errors':            [{ threshold: 'rate<0.001', abortOnFail: false }],
        'http_req_failed':   [{ threshold: 'rate<0.001', abortOnFail: false }],

        // Per-flow SLAs
        'dur_paginated_listing':   ['p(95)<200'],
        'dur_product_detail':      ['p(95)<150'],
        'dur_category_price_sort': ['p(95)<200'],

        // All checks must pass for > 99% of iterations
        'checks': ['rate>0.99'],
    },
};

// ─────────────────────────────────────────────────────────────────────────────
// Setup — runs ONCE before all VUs start; returns shared data
// ─────────────────────────────────────────────────────────────────────────────
export function setup() {
    const res = http.post(
        `${BASE_URL}/auth/v1/login`,
        JSON.stringify({ email: USERNAME, password: PASSWORD }),
        { headers: { 'Content-Type': 'application/json' } }
    );

    const ok = check(res, {
        'setup: login status 200':    r => r.status === 200,
        'setup: accessToken present': r => {
            try { return !!r.json('accessToken'); }
            catch (_) { return false; }
        },
    });

    if (!ok) {
        console.error(`[setup] Login failed — status=${res.status} body=${res.body}`);
        console.warn('[setup] VUs will proceed with empty token and expect 401 errors');
        return { token: '' };
    }

    const token = res.json('accessToken');
    console.log(`[setup] Authenticated as ${USERNAME}. Token obtained.`);
    return { token };
}

// ─────────────────────────────────────────────────────────────────────────────
// Default function — executed by each VU on every iteration
// ─────────────────────────────────────────────────────────────────────────────
export default function (data) {

    const headers = {
        'Authorization':    `Bearer ${data.token}`,
        'X-Correlation-ID': `k6-vu${__VU}-iter${__ITER}`,
        'Content-Type':     'application/json',
    };

    // ─────────────────────────────────────────────────────────────────────────
    // Flow 1 — Paginated product listing (v2 primary endpoint)
    // Represents a user landing on the product catalogue page.
    // ─────────────────────────────────────────────────────────────────────────
    const listRes = http.get(
        `${BASE_URL}/api/v2/products?page=0&size=20&sortBy=createdAt&direction=desc`,
        { headers, tags: { name: 'paginated-listing' } }
    );
    paginatedListTrend.add(listRes.timings.duration);

    const listOk = check(listRes, {
        'listing: status 200':                 r => r.status === 200,
        'listing: has content array':          r => Array.isArray(r.json('content')),
        'listing: X-RateLimit-Reset present':  r =>
            r.headers['X-Ratelimit-Reset'] !== undefined ||
            r.headers['X-RateLimit-Reset'] !== undefined,
        'listing: X-Correlation-Id echoed':    r =>
            r.headers['X-Correlation-Id'] !== undefined ||
            r.headers['X-Correlation-ID'] !== undefined,
    });
    errorRate.add(!listOk);

    sleep(0.5);

    // ─────────────────────────────────────────────────────────────────────────
    // Flow 2 — Product detail page (v1 — tests deprecation headers too)
    // Random product ID 1–20 simulates realistic user navigation.
    // ─────────────────────────────────────────────────────────────────────────
    const productId = Math.floor(Math.random() * 20) + 1;
    const detailRes = http.get(
        `${BASE_URL}/api/v1/products/${productId}`,
        { headers, tags: { name: 'product-detail' } }
    );
    productDetailTrend.add(detailRes.timings.duration);

    const detailOk = check(detailRes, {
        'detail: status 200 or 404':          r => r.status === 200 || r.status === 404,
        'detail: Deprecation: true present':  r => r.headers['Deprecation'] === 'true',
        'detail: Sunset header present':      r => r.headers['Sunset'] !== undefined,
        'detail: Link successor present':     r =>
            (r.headers['Link'] || '').includes('successor-version'),
    });
    // 404 is not an error — product may not exist with that ID
    errorRate.add(!detailOk && detailRes.status !== 404);

    sleep(0.5);

    // ─────────────────────────────────────────────────────────────────────────
    // Flow 3 — Price-sorted category browse
    // Tests the composite (category, price) index added in V15 migration.
    // ─────────────────────────────────────────────────────────────────────────
    const sortRes = http.get(
        `${BASE_URL}/api/v2/products?page=0&size=10&sortBy=price&direction=asc`,
        { headers, tags: { name: 'category-price-sort' } }
    );
    categoryPriceSortTrend.add(sortRes.timings.duration);

    const sortOk = check(sortRes, {
        'price-sort: status 200': r => r.status === 200,
        'price-sort: results in ascending price order': r => {
            try {
                const items = r.json('content');
                if (!items || items.length < 2) return true;
                return items[0].price <= items[1].price;
            } catch (_) { return false; }
        },
    });
    errorRate.add(!sortOk);

    sleep(1);
}

// ─────────────────────────────────────────────────────────────────────────────
// Teardown — runs ONCE after all VUs finish
// ─────────────────────────────────────────────────────────────────────────────
export function teardown(data) {
    console.log(`[teardown] Scenario: ${SCENARIO}`);
    console.log(`[teardown] Auth token was ${data.token ? 'present' : 'MISSING — all requests got 401'}`);
}
