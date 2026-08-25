-- 도감 항목의 커스텀 표지 이미지(S3 key). NULL이면 카드 기본 이미지를 그대로 쓴다.
ALTER TABLE portfolio_items ADD COLUMN thumbnail_key VARCHAR(255);
