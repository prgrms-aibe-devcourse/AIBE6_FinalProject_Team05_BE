-- 워치리스트 등록 카드에 매물이 없다가 새로 생겼을 때(재입고) 알림을 이미 보냈는지 추적한다(#300).
-- 목표가 알림용 is_notified와 별개 플래그다 - 한 워치리스트가 두 종류 알림을 독립적으로 받을 수 있다.
-- 이번 범위에서는 리셋 로직이 없어 한 번 true가 되면 계속 true로 유지된다(후속 작업에서 다룰 예정).
ALTER TABLE watchlist ADD COLUMN IF NOT EXISTS listing_notified boolean NOT NULL DEFAULT false;
