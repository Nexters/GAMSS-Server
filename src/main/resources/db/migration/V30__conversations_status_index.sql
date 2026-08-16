-- 모니터링 게이지(진행 중 대화방 수)가 60초마다 status 로 count 한다. 인덱스가 없으면 그 주기마다
-- conversations 전체를 훑는다 — 지금은 행이 적어 티가 안 나지만, 관측을 붙인 대가로 DB 부하가
-- 늘어나는 구조는 데이터가 쌓인 뒤에 문제로 드러난다.
--
-- status 는 값이 3종(ACTIVE·ENDED·DELETED)뿐이라 조회 필터로는 선택도가 낮지만, count 는
-- 세컨더리 인덱스만 읽고 끝나므로(커버링) 클러스터형 인덱스 전체를 읽는 것보다 훨씬 싸다.
CREATE INDEX idx_conversation_status ON conversations (status);
