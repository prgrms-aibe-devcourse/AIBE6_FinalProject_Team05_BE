// #341 한계점(stress) 테스트 — 로그인 도착률을 강제로 계단식 인상해 시스템이
// 실제로 무너지는 지점과 무너지는 방식을 찾는다.
//
// load.js(닫힌 모델)와의 차이: VU 루프는 서버가 느려지면 요청 속도도 같이 줄어
// 시스템이 스스로 보호된다. 여기서는 ramping-arrival-rate(열린 모델)로 응답과
// 무관하게 초당 도착률을 밀어붙인다 — 현실의 트래픽 폭주와 같은 모양.
//
// "무너짐" 판정: 오류율 1% 초과 또는 로그인 p95 5초 초과 → abortOnFail로 즉시 중단.
// 중단 시점의 도착률이 곧 한계점이다. k6 로그의 마지막 stage 진행률로 읽는다.
//
// 대상: 2코어·2GB 제한 컨테이너 (t3.small 하한 근사). 호스트 무제한을 무너뜨리려면
// 도착률을 훨씬 올려야 하고 k6 자신의 CPU 사용이 측정을 흐린다.
//
// 실행: k6 run loadtest/stress.js
// 예상 붕괴 순서: 큐잉 지연 급증 → Hikari connectionTimeout(30s) 초과로 500 발생
// → 톰캣 워커(기본 200) 고갈. 실제로 어느 것이 먼저인지가 이 테스트의 관찰 대상.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, SEED_PASSWORD, seedEmail } from './common.js';

export const options = {
    scenarios: {
        login_breakpoint: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            // 도착률이 처리 능력을 넘으면 대기 요청이 VU를 계속 점유하므로 여유 있게 배정.
            // maxVUs에 닿으면 k6가 요청을 떨구기(dropped_iterations) 시작한다 — 그것도 포화 신호다.
            preAllocatedVUs: 100,
            maxVUs: 500,
            stages: [
                { duration: '1m', target: 20 },   // warmup
                { duration: '1m', target: 40 },   // 2코어 실행에서 관측된 처리량 부근
                { duration: '1m', target: 60 },
                { duration: '1m', target: 80 },
                { duration: '1m', target: 110 },
                { duration: '1m', target: 150 },  // 여기까지 버티면 한계점은 150/s 이상
            ],
        },
    },
    thresholds: {
        // 붕괴 판정 — 깨지는 순간 테스트를 멈춰 한계점을 고정한다
        http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: true, delayAbortEval: '1m' }],
        'http_req_duration{name:stress_login}': [
            { threshold: 'p(95)<5000', abortOnFail: true, delayAbortEval: '1m' },
        ],
    },
};

export default function () {
    // 도착률 기반이라 __ITER가 아닌 무작위로 계정을 고른다. 같은 계정의 동시 로그인은
    // 세션별 refresh 키(#210) 덕에 독립 세션이라 안전하다.
    const email = seedEmail(Math.floor(Math.random() * 50) + 1);
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email, password: SEED_PASSWORD }),
        { headers: { 'Content-Type': 'application/json' }, tags: { name: 'stress_login' } });
    check(res, { '로그인 200': (r) => r.status === 200 });
}
