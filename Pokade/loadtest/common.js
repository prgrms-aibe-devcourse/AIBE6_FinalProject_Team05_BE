// #341 부하테스트 공용 모듈 — smoke.js와 load.js가 공유한다.
//
// 제외 대상(이슈 본문의 도메인 제약):
// - 이메일 발송·코드 검증 API — SMTP 실발송 + 쿨다운 60초라 부하 시나리오가 성립하지 않는다
// - OAuth2 콜백 — 인가 코드가 1회용·외부 IdP 왕복이라 반복 호출 불가 (계측은 #350이 커버)

import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// DevUserInit이 심는 dev 시드 계정 수. 시드보다 VU가 많으면 모듈로로 계정을 공유하는데,
// 세션별 refresh 키(#210) 덕에 같은 계정의 동시 로그인은 서로 독립 세션이라 안전하다.
export const SEED_USER_COUNT = 50;
export const SEED_PASSWORD = 'test1234';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function seedEmail(vu) {
    return `loadtest${((vu - 1) % SEED_USER_COUNT) + 1}@pokade.com`;
}

// 시나리오 A: 로그인 → 내 정보 → 재발급 → 로그아웃 (인증 주 경로 한 바퀴)
export function loginSessionScenario() {
    const email = seedEmail(__VU);

    const loginRes = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email, password: SEED_PASSWORD }),
        { headers: JSON_HEADERS, tags: { name: 'login' } });
    const loginOk = check(loginRes, {
        '로그인 200': (r) => r.status === 200,
        'access 토큰 수신': (r) => !!r.json('data.accessToken'),
    });
    if (!loginOk) return; // 인증 실패 상태로 이어가면 이후 401만 측정하게 된다

    const accessToken = loginRes.json('data.accessToken');
    const authHeaders = { Authorization: `Bearer ${accessToken}` };

    const meRes = http.get(`${BASE_URL}/api/users/me`,
        { headers: authHeaders, tags: { name: 'me' } });
    check(meRes, { '내 정보 200': (r) => r.status === 200 });

    // refresh는 응답 쿠키로 내려오고 k6가 VU별 쿠키 잼에 보관한다.
    // 토큰 문자열을 직접 재사용하면 회전 후 TOKEN_STOLEN이 나므로 잼에 맡긴다.
    const reissueRes = http.post(`${BASE_URL}/api/auth/reissue`, null,
        { tags: { name: 'reissue' } });
    check(reissueRes, {
        '재발급 200': (r) => r.status === 200,
        '새 access 수신': (r) => !!r.json('data.accessToken'),
    });

    const logoutRes = http.post(`${BASE_URL}/api/auth/logout`, null,
        { tags: { name: 'logout' } });
    check(logoutRes, { '로그아웃 200': (r) => r.status === 200 });
}

// 시나리오 B: 회원가입 단독 (가입 직후엔 PENDING이라 로그인으로 이어갈 수 없어 분리)
// 남는 PENDING 유저는 loadtest/cleanup.sql로 정리한다.
export function signupScenario() {
    // __VU·__ITER만 쓰면 재실행 시 이전 실행이 남긴 PENDING 유저와 이메일이 충돌한다.
    // 무작위 접미사로 실행 간에도 고유하게 만든다 (닉네임 20자 제한 안쪽).
    const suffix = `${__VU}x${Math.random().toString(36).slice(2, 10)}`;
    const res = http.post(`${BASE_URL}/api/auth/signup`,
        JSON.stringify({
            email: `signup_${suffix}@loadtest.local`,
            password: 'loadtest1234',
            nickname: `부하가입${suffix}`,
            termsOfService: true,
            privacyPolicy: true,
            thirdPartySharing: true,
            marketing: false,
        }),
        { headers: JSON_HEADERS, tags: { name: 'signup' } });
    check(res, { '가입 200': (r) => r.status === 200 });
}

// 시나리오 C: 로그인 실패 경로 (계정 없음 → 더미 BCrypt)
// 반복마다 다른 미가입 이메일을 쓴다. per-email 카운터에 각 1회씩만 쌓여
// 잠금 임계(10회)에 닿지 않으면서, 타이밍 방어가 걸린 실패 경로의 비용을 잰다.
export function loginFailureScenario() {
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({
            email: `bruteforce_${__VU}x${__ITER}@loadtest.local`,
            password: 'wrongpass1234',
        }),
        {
            headers: JSON_HEADERS,
            tags: { name: 'login_fail' },
            // 이 시나리오는 401이 기대 응답 — http_req_failed에 실패로 집계되지 않게 한다
            responseCallback: http.expectedStatuses(401),
        });
    check(res, { '실패 401': (r) => r.status === 401 });
}

// 시나리오 D: 읽기 비교군 — BCrypt 없는 인증 경로. setup에서 받은 access를 재사용한다.
export function readOnlyScenario(accessToken) {
    const res = http.get(`${BASE_URL}/api/users/me`,
        { headers: { Authorization: `Bearer ${accessToken}` }, tags: { name: 'me_readonly' } });
    check(res, { '조회 200': (r) => r.status === 200 });
}

// 시나리오 D용 access 토큰 1개를 setup 단계에서 발급한다.
// 매 반복 로그인하면 BCrypt가 섞여 "읽기만"의 비교군이 되지 못한다.
export function issueAccessToken() {
    const res = http.post(`${BASE_URL}/api/auth/login`,
        JSON.stringify({ email: seedEmail(1), password: SEED_PASSWORD }),
        { headers: JSON_HEADERS });
    if (res.status !== 200) {
        throw new Error(`setup 로그인 실패 (${res.status}) — dev 시드 계정과 앱 상태를 확인할 것`);
    }
    return res.json('data.accessToken');
}
