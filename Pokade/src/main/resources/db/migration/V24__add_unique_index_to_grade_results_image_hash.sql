-- 동시에 같은 사진으로 두 요청이 거의 동시에 들어오면(더블클릭 등) 캐시 조회가 아직 커밋되지 않은 첫
-- 요청을 못 보고 둘 다 Vision을 호출·과금할 수 있다(TOCTOU 경쟁). (user_id, image_hash) + SUCCESS
-- 조합에 유니크 인덱스를 걸어 DB 레벨에서 중복 저장 자체를 막는다 - 애플리케이션은 이 제약 위반을
-- "다른 요청이 이미 저장했다"는 신호로 받아 그 결과를 대신 반환한다(AiGradeService 참고).
-- V20의 일반 인덱스는 컬럼이 완전히 겹치는 유니크 인덱스로 대체되므로 제거한다.
DROP INDEX IF EXISTS idx_grade_results_user_id_image_hash;

CREATE UNIQUE INDEX IF NOT EXISTS uq_grade_results_user_image_hash_success
    ON grade_results (user_id, image_hash)
    WHERE status = 'SUCCESS' AND image_hash IS NOT NULL;
