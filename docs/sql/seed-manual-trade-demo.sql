-- MySQL 8.4 / 개발용 수동 거래 테스트 데이터.
-- 먼저 SQL 도구에서 개인 개발 DB를 선택한다. Flyway V1/V2 적용 후 실행한다.
-- 공개 테스트 계정: demo.a@example.com / demo.b@example.com
-- 공통 비밀번호: Lancer1234!
-- 두 계정 모두 USER이며 구매·판매를 겸한다. 각 계정에 최초 적립금 100만원.
-- 기존 이메일이 있으면 전체 생성을 거절한다. 기존 비밀번호·잔액·거래는 덮어쓰지 않는다.
-- 주문·결제·환불·정산은 화면/API에서 직접 진행한다.
-- 새 주문은 CONTRACT_V3(최소 주문 금액 10,000원)로 만들어지므로 모든 상품을 10,000원 이상으로 둔다.
-- CREATE ROUTINE 권한이 필요하다. 실행 시 트랜잭션이 없는 새 SQL 연결을 사용한다.

SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS seed_lancerpick_manual_demo;
DELIMITER $$
CREATE PROCEDURE seed_lancerpick_manual_demo()
BEGIN
    DECLARE account_a BIGINT;
    DECLARE account_b BIGINT;
    DECLARE category_id_value BIGINT;
    DECLARE created_at_value DATETIME(6);
    DECLARE password_hash_value VARCHAR(255);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;
    IF DATABASE() IS NULL OR DATABASE() IN ('mysql', 'sys', 'information_schema', 'performance_schema') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Select your local development database first.';
    END IF;
    IF EXISTS (SELECT 1 FROM members WHERE email IN ('demo.a@example.com', 'demo.b@example.com')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo accounts already exist. Existing data was preserved.';
    END IF;

    SET created_at_value = UTC_TIMESTAMP(6);
    SET password_hash_value = '{bcrypt}$2a$10$u.I.gceRHRH/OQyGZufQU.HsRWQjwr8cGcqP9XmBQ8/T4JE29RAMW';

    INSERT INTO members(email, password_hash, display_name, role, created_at, updated_at)
    VALUES ('demo.a@example.com', password_hash_value, '테스트 회원 A', 'USER', created_at_value, created_at_value);
    SET account_a = LAST_INSERT_ID();
    INSERT INTO members(email, password_hash, display_name, role, created_at, updated_at)
    VALUES ('demo.b@example.com', password_hash_value, '테스트 회원 B', 'USER', created_at_value, created_at_value);
    SET account_b = LAST_INSERT_ID();

    INSERT INTO wallets(member_id, balance, updated_at)
    VALUES (account_a, 1000000, created_at_value), (account_b, 1000000, created_at_value);
    INSERT INTO wallet_entries(member_id, payment_id, topup_id, type, amount_delta, balance_after, event_key, created_at)
    VALUES
        (account_a, NULL, NULL, 'INITIAL_GRANT', 1000000, 1000000, CONCAT('INITIAL_GRANT:', account_a), created_at_value),
        (account_b, NULL, NULL, 'INITIAL_GRANT', 1000000, 1000000, CONCAT('INITIAL_GRANT:', account_b), created_at_value);

    INSERT INTO seller_profiles(member_id, introduction, specialty, portfolio_url, created_at, updated_at)
    VALUES
        (account_a, '수동 거래 테스트를 위한 디자인 판매자 프로필입니다.', '디자인', NULL, created_at_value, created_at_value),
        (account_b, '구매와 판매 역할을 바꿔 확인하는 테스트 프로필입니다.', '디자인', NULL, created_at_value, created_at_value);

    INSERT INTO categories(name, display_order) VALUES ('디자인', 0)
    ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id);
    SET category_id_value = LAST_INSERT_ID();

    INSERT INTO products(seller_id, category_id, title, description, scope_description, preparation_description,
                         price, duration_days, sale_status, is_hidden, version, created_at, updated_at)
    VALUES
        (account_a, category_id_value, '[테스트 A] 1만원 거래·구매 확정 확인',
         'B 계정으로 주문해 계약 서명·결제·납품·구매 확정을 확인하세요. 수수료 1,000원, 판매자 지급 9,000원입니다.',
         '간단한 배너 시안 1개. 수정 요청은 원래 범위에서 1회.', '원하는 문구와 색상을 요구사항으로 보내주세요.',
         10000, 1, 'ON_SALE', FALSE, 0, created_at_value, created_at_value),
        (account_a, category_id_value, '[테스트 A] 5만원 취소·환불 확인',
         'B 계정으로 주문·결제한 뒤 취소 요청과 승인, 적립금 반환을 확인하세요.',
         '로고 시안 1개. 원본 외 추가 작업 제외.', '브랜드명과 참고 스타일을 보내주세요.',
         50000, 3, 'ON_SALE', FALSE, 0, created_at_value, created_at_value),
        (account_b, category_id_value, '[테스트 B] 상세페이지 디자인',
         'A 계정으로 주문해 구매·판매 역할을 반대로 확인하세요.',
         '상세페이지 시안 1개. 상품 촬영과 문구 작성 제외.', '상품 소개 문구와 사진 링크를 보내주세요.',
         120000, 5, 'ON_SALE', FALSE, 0, created_at_value, created_at_value),
        (account_b, category_id_value, '[테스트 B] 썸네일 디자인',
         '수정 요청·재납품 흐름을 확인하는 상품입니다.',
         '썸네일 시안 1개. 원래 범위의 수정 1회.', '제목과 사용할 이미지 링크를 보내주세요.',
         15000, 2, 'ON_SALE', FALSE, 0, created_at_value, created_at_value);
    COMMIT;
END$$
DELIMITER ;

CALL seed_lancerpick_manual_demo();
DROP PROCEDURE seed_lancerpick_manual_demo;

SELECT m.id, m.email, m.display_name, m.role, w.balance
FROM members m JOIN wallets w ON w.member_id = m.id
WHERE m.email IN ('demo.a@example.com', 'demo.b@example.com');

SELECT p.id, m.email AS seller_email, p.title, p.price, p.duration_days
FROM products p JOIN members m ON m.id = p.seller_id
WHERE m.email IN ('demo.a@example.com', 'demo.b@example.com')
ORDER BY p.id;
