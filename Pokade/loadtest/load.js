// #341 load 테스트 — 단계적으로 VU를 올리며 응답시간이 선형으로 늘어나는지,
// 특정 지점에서 무너지는지, 무너질 때 무엇이 먼저 바닥나는지를 본다.
//
// 실행: k6 run loadtest/load.js
// 관찰: Grafana "인증·프로필 도메인 모니터링" + "시스템 자원 포화 (#341)" 대시보드
//
// 결과 해석 시 주의:
// - 처음 1분(warmup 구간)은 JIT·커넥션 생성 비용이 섞이므로 판단에서 제외한다
//   (k6 요약에는 포함되어 나오므로, 구간 판단은 Grafana 시간축으로 한다)
// - 로그인이 가장 느린 것은 BCrypt 때문이며 결함이 아니라 사양이다
// - 로컬 측정이므로 절대 처리량을 운영 수치로 제시하지 않는다

import {
    loginSessionScenario,
    signupScenario,
    loginFailureScenario,
    readOnlyScenario,
    issueAccessToken,
} from './common.js';

export const options = {
    scenarios: {
        // A. 인증 주 경로 — 주 부하. 10 → 30 → 50으로 계단식 증가
        login_session: {
            executor: 'ramping-vus',
            exec: 'loginSession',
            startVUs: 0,
            stages: [
                { duration: '1m', target: 10 },  // warmup — 판단 제외 구간
                { duration: '2m', target: 30 },
                { duration: '2m', target: 50 },
                { duration: '2m', target: 50 },  // 최대 부하 유지
                { duration: '30s', target: 0 },
            ],
        },
        // D. 읽기 비교군 — BCrypt 없는 경로를 같은 시간축에 나란히 놓는다
        read_only: {
            executor: 'constant-vus',
            exec: 'readOnly',
            vus: 10,
            duration: '7m30s',
        },
        // B. 회원가입 — 쓰기 경로 표본. 실행마다 PENDING 유저가 쌓이므로 낮게 유지
        signup: {
            executor: 'constant-vus',
            exec: 'signup',
            vus: 3,
            duration: '7m30s',
        },
        // C. 로그인 실패 — 더미 BCrypt가 걸린 실패 경로 비용
        login_failure: {
            executor: 'constant-vus',
            exec: 'loginFailure',
            vus: 3,
            duration: '7m30s',
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        // p95 기준 — 수동 실측(로그인 117ms, 조회 13.6ms)에 부하 여유를 얹은 값.
        // 깨지는 것 자체가 관찰 대상이므로 abortOnFail은 걸지 않는다.
        'http_req_duration{name:login}': ['p(95)<1000'],
        'http_req_duration{name:me}': ['p(95)<300'],
        'http_req_duration{name:reissue}': ['p(95)<500'],
        'http_req_duration{name:logout}': ['p(95)<300'],
        'http_req_duration{name:signup}': ['p(95)<1000'],
        'http_req_duration{name:login_fail}': ['p(95)<1000'],
        'http_req_duration{name:me_readonly}': ['p(95)<300'],
    },
};

export function setup() {
    // access 수명이 30분이라 테스트(8분) 동안 유효하다. 매 반복 로그인하면
    // BCrypt가 섞여 읽기 비교군이 되지 못하므로 여기서 한 번만 발급한다.
    return { accessToken: issueAccessToken() };
}

export function loginSession() {
    loginSessionScenario();
}

export function readOnly(data) {
    readOnlyScenario(data.accessToken);
}

export function signup() {
    signupScenario();
}

export function loginFailure() {
    loginFailureScenario();
}
