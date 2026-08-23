-- 문의 처리 완료 알림(INQUIRY_HANDLED)을 클릭했을 때 "내 문의 목록"이 아니라 해당 문의로 바로
-- 앵커할 수 있도록 참조 문의 ID를 보관한다(#338).
-- card_id와 동일한 성격의 "알림 클릭 시 이동 대상" 선택 컬럼으로, 문의와 무관한 알림은 NULL이다.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS inquiry_id BIGINT REFERENCES inquiries(id);
