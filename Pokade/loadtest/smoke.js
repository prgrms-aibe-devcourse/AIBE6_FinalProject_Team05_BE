// #341 smoke 테스트 — VU 1로 시나리오 A~D가 기대한 응답 코드를 받는지 먼저 확인한다.
// 인증이 전부 실패하는 스크립트는 매우 빠른 응답시간을 만들어 성능으로 오독되므로,
// load.js를 돌리기 전에 반드시 이것부터 통과시킨다.
//
// 실행: k6 run loadtest/smoke.js
// 대상 변경: k6 run -e BASE_URL=http://localhost:8080 loadtest/smoke.js

import {
    loginSessionScenario,
    signupScenario,
    loginFailureScenario,
    readOnlyScenario,
    issueAccessToken,
} from './common.js';

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: {
        // smoke는 성능이 아니라 정합성 확인 — check 하나라도 깨지면 실패
        checks: ['rate==1'],
    },
};

export function setup() {
    return { accessToken: issueAccessToken() };
}

export default function (data) {
    loginSessionScenario();
    signupScenario();
    loginFailureScenario();
    readOnlyScenario(data.accessToken);
}
