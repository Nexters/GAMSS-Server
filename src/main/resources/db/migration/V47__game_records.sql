-- 미니게임 플레이 기록. 게임 진행은 전부 클라이언트가 하고 서버는 결과 점수만 저장·조회한다.
-- 게임이 늘어도 테이블을 새로 만들지 않도록 game_type 으로 구분한다.
CREATE TABLE game_records (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    game_type  VARCHAR(30) NOT NULL,
    score      INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

-- 내 기록(최신순·점수순)
CREATE INDEX idx_game_records_member_type_created ON game_records (member_id, game_type, created_at);
-- 랭킹의 not exists 가 더 나은 기록을 먼저 만나 바로 멈추도록 점수 높은 순, id 순으로 둔다.
CREATE INDEX idx_game_records_member_type_score ON game_records (member_id, game_type, score DESC, id);
-- 전체 랭킹(최신순·점수순)
CREATE INDEX idx_game_records_type_created ON game_records (game_type, created_at);
CREATE INDEX idx_game_records_type_score ON game_records (game_type, score);
