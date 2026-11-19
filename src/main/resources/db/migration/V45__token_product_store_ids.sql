-- 스토어 콘솔에 등록된 제품 ID 로 맞춘다(#262). V44 의 ID 는 콘솔에 없어 판매된 적이 없다.
-- 지우지 않고 끄는 이유: 이 ID 로 남은 결제 기록이 있어도 카탈로그에서 찾을 수 있게 한다.
UPDATE token_product SET active = FALSE, updated_at = NOW(6)
WHERE product_id IN ('token_300000', 'token_500000', 'token_1000000');

INSERT INTO token_product (product_id, token_amount, active, updated_at) VALUES
    ('token_300k', 300000, TRUE, NOW(6)),
    ('token_500k', 500000, TRUE, NOW(6)),
    ('token_1m', 1000000, TRUE, NOW(6));
