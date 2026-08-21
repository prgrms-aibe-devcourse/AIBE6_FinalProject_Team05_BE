-- 값이 기록된 적이 없고 읽는 곳도 없는 users 컬럼을 정리한다.
--
--   phone_number / birth_date
--     입력 경로가 아예 없어 항상 NULL이었다. SignupRequest에 필드가 없고 수정 요청 DTO도
--     닉네임용뿐이다. 사용처도 없다(거래는 배송지 주소조차 두지 않고 연락은 채팅으로 한다).
--     개인정보처리방침 2조의 수집 항목에도 고지된 적이 없다. 응답 DTO에서도 함께 제거한다.
--
--   terms_agreed_at / marketing_opt_in
--     동의 이력이 user_agreements로 이관된 뒤 남은 잔재다. 엔티티가 매핑하지 않는다.
--     Flyway 도입 전 migrations/V4__drop_user_consent_columns.sql로 작성해 두었으나,
--     Flyway는 db/migration만 읽으므로 그 파일은 실행된 적이 없다. 여기서 함께 정리한다.
--
-- IF EXISTS를 붙여 재실행에도 안전하게 둔다. Flyway 도입 이전에 손으로 스키마를 맞춘
-- 로컬·운영의 적용 상태가 서로 다를 수 있다(V2가 같은 이유로 IF NOT EXISTS를 남겨 두었다).
ALTER TABLE users DROP COLUMN IF EXISTS phone_number;
ALTER TABLE users DROP COLUMN IF EXISTS birth_date;
ALTER TABLE users DROP COLUMN IF EXISTS terms_agreed_at;
ALTER TABLE users DROP COLUMN IF EXISTS marketing_opt_in;