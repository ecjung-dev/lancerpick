-- LancerPick physical schema design, 2026-10-29, MySQL 8.4.
-- Review artifact: NOT in the application's Flyway migration directory.
-- Target an empty, isolated test schema first. No database/user creation or seed data.
-- All times are supplied by the application in UTC, truncated to microseconds.
-- Foreign keys use the default restrictive action; there are no cascade deletes.
-- 16 tables: orders retains price/duration/progress; order_snapshots stores immutable terms.
-- Orders, snapshots and the initial history must be inserted in one application transaction.
-- If an earlier schema is already applied, use a new migration; do not rerun this file.

CREATE TABLE members (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 ID',
    email VARCHAR(254) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '정규화한 로그인 이메일',
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '인코더가 생성한 비밀번호 해시',
    display_name VARCHAR(30) NOT NULL COMMENT '표시 이름, 중복 허용',
    role VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'USER' COMMENT 'USER 또는 ADMIN',
    created_at DATETIME(6) NOT NULL COMMENT '가입 시각 UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_email (email),
    CONSTRAINT ck_members_email CHECK (CHAR_LENGTH(email) BETWEEN 3 AND 254 AND email = LOWER(TRIM(email))),
    CONSTRAINT ck_members_name CHECK (CHAR_LENGTH(TRIM(display_name)) BETWEEN 1 AND 30),
    CONSTRAINT ck_members_hash CHECK (CHAR_LENGTH(password_hash) BETWEEN 1 AND 255),
    CONSTRAINT ck_members_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_members_time CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seller_profiles (
    member_id BIGINT NOT NULL COMMENT '판매자 회원 ID, 공유 PK',
    introduction TEXT NOT NULL COMMENT '판매자 소개, 최대 2000자',
    specialty VARCHAR(100) NOT NULL COMMENT '전문 분야',
    portfolio_url VARCHAR(2048) NULL COMMENT '선택 포트폴리오 HTTPS URL',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
    PRIMARY KEY (member_id),
    CONSTRAINT fk_seller_profiles_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT ck_seller_profiles_intro CHECK ((CHAR_LENGTH(introduction) BETWEEN 1 AND 2000 AND CHAR_LENGTH(TRIM(introduction)) > 0)),
    CONSTRAINT ck_seller_profiles_specialty CHECK (CHAR_LENGTH(TRIM(specialty)) BETWEEN 1 AND 100),
    CONSTRAINT ck_seller_profiles_url CHECK (portfolio_url IS NULL OR CHAR_LENGTH(TRIM(portfolio_url)) BETWEEN 1 AND 2048),
    CONSTRAINT ck_seller_profiles_time CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '카테고리 ID',
    name VARCHAR(50) NOT NULL COMMENT '고정 분류명',
    display_order INT NOT NULL DEFAULT 0 COMMENT '화면 표시 순서',
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name),
    KEY ix_categories_display (display_order, id),
    CONSTRAINT ck_categories_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 50),
    CONSTRAINT ck_categories_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '상품 ID',
    seller_id BIGINT NOT NULL COMMENT '판매자 회원 ID',
    category_id BIGINT NOT NULL COMMENT '카테고리 ID',
    title VARCHAR(100) NOT NULL COMMENT '상품명',
    description TEXT NOT NULL COMMENT '상세 설명, 최대 10000자',
    scope_description TEXT NOT NULL COMMENT '제공 및 미제공 범위, 최대 3000자',
    preparation_description TEXT NOT NULL COMMENT '구매자 준비사항, 최대 3000자',
    price BIGINT NOT NULL COMMENT '현재 가격 원',
    duration_days INT NOT NULL COMMENT '소요일, 1일은 24시간',
    sale_status VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'ON_SALE' COMMENT '판매 상태',
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE COMMENT '관리자 숨김',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'JPA 상품 버전',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
    PRIMARY KEY (id),
    KEY ix_products_seller_created (seller_id, created_at DESC, id DESC),
    KEY ix_products_public_created (is_hidden, sale_status, created_at DESC, id DESC),
    KEY ix_products_category_public_created (category_id, is_hidden, sale_status, created_at DESC, id DESC),
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES members (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_title CHECK (CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 100),
    CONSTRAINT ck_products_description CHECK ((CHAR_LENGTH(description) BETWEEN 1 AND 10000 AND CHAR_LENGTH(TRIM(description)) > 0)),
    CONSTRAINT ck_products_scope CHECK ((CHAR_LENGTH(scope_description) BETWEEN 1 AND 3000 AND CHAR_LENGTH(TRIM(scope_description)) > 0)),
    CONSTRAINT ck_products_preparation CHECK ((CHAR_LENGTH(preparation_description) BETWEEN 1 AND 3000 AND CHAR_LENGTH(TRIM(preparation_description)) > 0)),
    CONSTRAINT ck_products_price CHECK (price BETWEEN 1 AND 1000000),
    CONSTRAINT ck_products_days CHECK (duration_days BETWEEN 1 AND 30),
    CONSTRAINT ck_products_sale_status CHECK (sale_status IN ('ON_SALE', 'STOPPED')),
    CONSTRAINT ck_products_hidden CHECK (is_hidden IN (0, 1)),
    CONSTRAINT ck_products_version CHECK (version >= 0),
    CONSTRAINT ck_products_time CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '주문 ID',
    buyer_id BIGINT NOT NULL COMMENT '구매자 회원 ID',
    seller_id BIGINT NOT NULL COMMENT '주문 당시 판매자 회원 ID',
    product_id BIGINT NOT NULL COMMENT '원상품 ID',
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '정규화 UUID 요청 키',
    status VARCHAR(32) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'REQUESTED' COMMENT '주문 상태',
    price_amount BIGINT NOT NULL COMMENT '주문 가격 원',
    duration_days_snapshot INT NOT NULL COMMENT '주문 소요일',
    accepted_at DATETIME(6) NULL COMMENT '수락 시각 UTC',
    buyer_confirmed_terms_at DATETIME(6) NULL COMMENT '결제 전 조건 확인 시각 UTC',
    paid_at DATETIME(6) NULL COMMENT '결제 시각 UTC, 환불 후 보존',
    due_at DATETIME(6) NULL COMMENT '최초 납기 UTC',
    completed_at DATETIME(6) NULL COMMENT '구매 확정 시각 UTC',
    cancelled_at DATETIME(6) NULL COMMENT '취소 시각 UTC',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각 UTC',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_buyer_request (buyer_id, request_key),
    KEY ix_orders_buyer_created (buyer_id, created_at DESC, id DESC),
    KEY ix_orders_seller_created (seller_id, created_at DESC, id DESC),
    KEY ix_orders_product_created (product_id, created_at DESC, id DESC),
    CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES members (id),
    CONSTRAINT fk_orders_seller FOREIGN KEY (seller_id) REFERENCES members (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_orders_parties CHECK (buyer_id <> seller_id),
    CONSTRAINT ck_orders_status CHECK (status IN ('REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_orders_price CHECK (price_amount BETWEEN 1 AND 1000000),
    CONSTRAINT ck_orders_days CHECK (duration_days_snapshot BETWEEN 1 AND 30),
    CONSTRAINT ck_orders_paid_fields CHECK (
        (paid_at IS NULL AND due_at IS NULL AND buyer_confirmed_terms_at IS NULL)
        OR (paid_at IS NOT NULL AND due_at IS NOT NULL AND buyer_confirmed_terms_at IS NOT NULL AND accepted_at IS NOT NULL)),
    CONSTRAINT ck_orders_completed CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL) OR (status <> 'COMPLETED' AND completed_at IS NULL)),
    CONSTRAINT ck_orders_cancelled CHECK ((status = 'CANCELLED' AND cancelled_at IS NOT NULL) OR (status <> 'CANCELLED' AND cancelled_at IS NULL)),
    CONSTRAINT ck_orders_paid_status CHECK (status NOT IN ('IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED') OR paid_at IS NOT NULL),
    CONSTRAINT ck_orders_unpaid_status CHECK (status NOT IN ('REQUESTED', 'AWAITING_PAYMENT', 'REJECTED') OR paid_at IS NULL),
    CONSTRAINT ck_orders_acceptance CHECK (
        (status IN ('REQUESTED', 'REJECTED') AND accepted_at IS NULL)
        OR (status IN ('AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED') AND accepted_at IS NOT NULL)
        OR status = 'CANCELLED'),
    CONSTRAINT ck_orders_event_times CHECK (
        (accepted_at IS NULL OR accepted_at >= created_at)
        AND (buyer_confirmed_terms_at IS NULL OR buyer_confirmed_terms_at >= accepted_at)
        AND (paid_at IS NULL OR paid_at >= buyer_confirmed_terms_at)
        AND (due_at IS NULL OR due_at > paid_at)
        AND (completed_at IS NULL OR completed_at >= paid_at)
        AND (cancelled_at IS NULL OR (
            cancelled_at >= created_at
            AND (accepted_at IS NULL OR cancelled_at >= accepted_at)
            AND (paid_at IS NULL OR cancelled_at >= paid_at)))),
    CONSTRAINT ck_orders_updated_events CHECK (
        (accepted_at IS NULL OR updated_at >= accepted_at)
        AND (buyer_confirmed_terms_at IS NULL OR updated_at >= buyer_confirmed_terms_at)
        AND (paid_at IS NULL OR updated_at >= paid_at)
        AND (completed_at IS NULL OR updated_at >= completed_at)
        AND (cancelled_at IS NULL OR updated_at >= cancelled_at)),
    CONSTRAINT ck_orders_time CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Shared PK enforces at most one snapshot per order.
-- Existence for every order and snapshot immutability are application/test responsibilities.
-- Existing candidate data types, lengths and checks are preserved; they remain review proposals.
CREATE TABLE order_snapshots (
    order_id BIGINT NOT NULL COMMENT '주문 ID, 공유 PK',
    product_version_snapshot BIGINT NOT NULL COMMENT '확인한 상품 버전',
    product_title_snapshot VARCHAR(100) NOT NULL COMMENT '주문 당시 상품명',
    description_snapshot TEXT NOT NULL COMMENT '주문 당시 상세 설명',
    scope_snapshot TEXT NOT NULL COMMENT '주문 당시 제공 범위',
    preparation_snapshot TEXT NOT NULL COMMENT '주문 당시 준비사항',
    terms_snapshot TEXT NOT NULL COMMENT '주문 당시 거래 규칙 원문, 최대 10000자',
    requirements TEXT NOT NULL COMMENT '구매자 요구사항, 최대 5000자',
    PRIMARY KEY (order_id),
    CONSTRAINT fk_order_snapshots_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_order_snapshots_version CHECK (product_version_snapshot >= 0),
    CONSTRAINT ck_order_snapshots_title CHECK (CHAR_LENGTH(TRIM(product_title_snapshot)) BETWEEN 1 AND 100),
    CONSTRAINT ck_order_snapshots_description CHECK ((CHAR_LENGTH(description_snapshot) BETWEEN 1 AND 10000 AND CHAR_LENGTH(TRIM(description_snapshot)) > 0)),
    CONSTRAINT ck_order_snapshots_scope CHECK ((CHAR_LENGTH(scope_snapshot) BETWEEN 1 AND 3000 AND CHAR_LENGTH(TRIM(scope_snapshot)) > 0)),
    CONSTRAINT ck_order_snapshots_preparation CHECK ((CHAR_LENGTH(preparation_snapshot) BETWEEN 1 AND 3000 AND CHAR_LENGTH(TRIM(preparation_snapshot)) > 0)),
    CONSTRAINT ck_order_snapshots_terms CHECK ((CHAR_LENGTH(terms_snapshot) BETWEEN 1 AND 10000 AND CHAR_LENGTH(TRIM(terms_snapshot)) > 0)),
    CONSTRAINT ck_order_snapshots_requirements CHECK ((CHAR_LENGTH(requirements) BETWEEN 1 AND 5000 AND CHAR_LENGTH(TRIM(requirements)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_histories (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '주문 이력 ID',
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    actor_id BIGINT NOT NULL COMMENT '행동한 회원 ID',
    action VARCHAR(40) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '업무 행동 코드',
    from_status VARCHAR(32) COLLATE utf8mb4_0900_as_cs NULL COMMENT '이전 주문 상태, 최초 생성은 NULL',
    to_status VARCHAR(32) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '이후 주문 상태',
    reason VARCHAR(1000) NULL COMMENT '선택 사유',
    created_at DATETIME(6) NOT NULL COMMENT '기록 시각 UTC',
    PRIMARY KEY (id),
    KEY ix_order_histories_order_created (order_id, created_at DESC, id DESC),
    KEY ix_order_histories_actor (actor_id),
    CONSTRAINT fk_order_histories_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_histories_actor FOREIGN KEY (actor_id) REFERENCES members (id),
    CONSTRAINT ck_order_histories_action CHECK (action IN ('CREATED', 'ACCEPTED', 'REJECTED', 'PAID', 'DELIVERED', 'REVISION_REQUESTED', 'CANCELLATION_REQUESTED', 'CANCELLATION_APPROVED', 'CANCELLATION_REJECTED', 'CANCELLATION_WITHDRAWN', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT ck_order_histories_from CHECK (from_status IS NULL OR from_status IN ('REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_order_histories_to CHECK (to_status IN ('REQUESTED', 'AWAITING_PAYMENT', 'IN_PROGRESS', 'AWAITING_CONFIRMATION', 'REVISION_IN_PROGRESS', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_order_histories_initial CHECK ((action = 'CREATED' AND from_status IS NULL AND to_status = 'REQUESTED') OR (action <> 'CREATED' AND from_status IS NOT NULL)),
    CONSTRAINT ck_order_histories_reason CHECK (reason IS NULL OR CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE deliveries (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '납품 ID',
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    sequence_no INT NOT NULL COMMENT '최초 1, 재납품 2',
    description TEXT NOT NULL COMMENT '납품 설명, 최대 5000자',
    result_url VARCHAR(2048) NOT NULL COMMENT 'HTTPS 결과물 URL',
    submitted_at DATETIME(6) NOT NULL COMMENT '제출 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_deliveries_order_sequence (order_id, sequence_no),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_deliveries_sequence CHECK (sequence_no IN (1, 2)),
    CONSTRAINT ck_deliveries_description CHECK ((CHAR_LENGTH(description) BETWEEN 1 AND 5000 AND CHAR_LENGTH(TRIM(description)) > 0)),
    CONSTRAINT ck_deliveries_url CHECK (CHAR_LENGTH(TRIM(result_url)) BETWEEN 1 AND 2048)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE revision_requests (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '수정 요청 ID',
    order_id BIGINT NOT NULL COMMENT '주문당 한 번',
    delivery_id BIGINT NOT NULL COMMENT '대상 최초 납품 ID',
    reason VARCHAR(1000) NOT NULL COMMENT '수정 사유',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 시각 UTC',
    due_at DATETIME(6) NOT NULL COMMENT '재납품 기한 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_revision_requests_order (order_id),
    UNIQUE KEY uk_revision_requests_delivery (delivery_id),
    CONSTRAINT fk_revision_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_revision_requests_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    CONSTRAINT ck_revision_requests_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_revision_requests_due CHECK (due_at > requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cancellation_requests (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '취소 요청 ID',
    order_id BIGINT NOT NULL COMMENT '주문 ID',
    requested_by BIGINT NOT NULL COMMENT '요청 구매자 회원 ID',
    request_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '정규화 UUID 요청 키',
    reason VARCHAR(1000) NOT NULL COMMENT '취소 요청 사유',
    status VARCHAR(16) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'PENDING' COMMENT '취소 요청 상태',
    requested_at DATETIME(6) NOT NULL COMMENT '요청 시각 UTC',
    resolved_at DATETIME(6) NULL COMMENT '해결 시각 UTC',
    resolved_by BIGINT NULL COMMENT '해결한 회원 ID',
    response_reason VARCHAR(1000) NULL COMMENT '선택 응답 사유',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cancellation_requests_key (order_id, requested_by, request_key),
    KEY ix_cancellation_requests_order_status (order_id, status, requested_at DESC, id DESC),
    KEY ix_cancellation_requests_requester (requested_by),
    KEY ix_cancellation_requests_resolver (resolved_by),
    CONSTRAINT fk_cancellation_requests_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_cancellation_requests_requester FOREIGN KEY (requested_by) REFERENCES members (id),
    CONSTRAINT fk_cancellation_requests_resolver FOREIGN KEY (resolved_by) REFERENCES members (id),
    CONSTRAINT ck_cancellation_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_cancellation_requests_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_cancellation_requests_response CHECK (response_reason IS NULL OR CHAR_LENGTH(TRIM(response_reason)) BETWEEN 1 AND 1000),
    CONSTRAINT ck_cancellation_requests_resolution CHECK (
        (status = 'PENDING' AND resolved_at IS NULL AND resolved_by IS NULL)
        OR (status <> 'PENDING' AND resolved_at IS NOT NULL AND resolved_by IS NOT NULL)),
    CONSTRAINT ck_cancellation_requests_time CHECK (resolved_at IS NULL OR resolved_at >= requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallets (
    member_id BIGINT NOT NULL COMMENT '회원 ID, 지갑 공유 PK',
    balance BIGINT NOT NULL DEFAULT 0 COMMENT '구매용 테스트 적립금 잔액 원',
    updated_at DATETIME(6) NOT NULL COMMENT '최종 변동 시각 UTC',
    PRIMARY KEY (member_id),
    CONSTRAINT fk_wallets_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT ck_wallets_balance CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '결제 ID',
    order_id BIGINT NOT NULL COMMENT '주문당 한 건',
    amount BIGINT NOT NULL COMMENT '실제 결제액 원',
    fee_rate_bps INT NOT NULL DEFAULT 1000 COMMENT '결제 당시 수수료율, MVP 1000bps',
    status VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL DEFAULT 'PAID' COMMENT '결제 상태',
    paid_at DATETIME(6) NOT NULL COMMENT '결제 시각 UTC',
    refunded_at DATETIME(6) NULL COMMENT '전액 환불 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_order (order_id),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_payments_amount CHECK (amount BETWEEN 1 AND 1000000),
    CONSTRAINT ck_payments_rate CHECK (fee_rate_bps BETWEEN 0 AND 10000),
    CONSTRAINT ck_payments_status CHECK (status IN ('PAID', 'REFUNDED')),
    CONSTRAINT ck_payments_refund CHECK ((status = 'PAID' AND refunded_at IS NULL) OR (status = 'REFUNDED' AND refunded_at IS NOT NULL)),
    CONSTRAINT ck_payments_time CHECK (refunded_at IS NULL OR refunded_at >= paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE wallet_entries (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '적립금 내역 ID',
    member_id BIGINT NOT NULL COMMENT '지갑 소유자 회원 ID',
    payment_id BIGINT NULL COMMENT '결제 ID, 초기 지급은 NULL',
    type VARCHAR(16) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '초기 지급, 결제, 환불',
    amount_delta BIGINT NOT NULL COMMENT '변동액 원, 결제는 음수',
    balance_after BIGINT NOT NULL COMMENT '변동 직후 잔액 원',
    event_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '서버가 고정 규칙으로 생성한 중복 방지 키',
    created_at DATETIME(6) NOT NULL COMMENT '변동 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_entries_event (event_key),
    UNIQUE KEY uk_wallet_entries_payment_type (payment_id, type),
    KEY ix_wallet_entries_member_created (member_id, created_at DESC, id DESC),
    CONSTRAINT fk_wallet_entries_wallet FOREIGN KEY (member_id) REFERENCES wallets (member_id),
    CONSTRAINT fk_wallet_entries_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT ck_wallet_entries_type CHECK (type IN ('INITIAL_GRANT', 'PAYMENT', 'REFUND')),
    CONSTRAINT ck_wallet_entries_amount CHECK (
        (type = 'INITIAL_GRANT' AND payment_id IS NULL AND amount_delta = 1000000)
        OR (type = 'PAYMENT' AND payment_id IS NOT NULL AND amount_delta BETWEEN -1000000 AND -1)
        OR (type = 'REFUND' AND payment_id IS NOT NULL AND amount_delta BETWEEN 1 AND 1000000)),
    CONSTRAINT ck_wallet_entries_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_wallet_entries_event CHECK (CHAR_LENGTH(event_key) BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE settlements (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '정산 ID',
    order_id BIGINT NOT NULL COMMENT '주문당 한 건',
    gross_amount BIGINT NOT NULL COMMENT '결제액 원',
    fee_rate_bps INT NOT NULL COMMENT '원결제에 고정한 수수료율',
    fee_amount BIGINT NOT NULL COMMENT '원 미만 버림 수수료 원',
    seller_amount BIGINT NOT NULL COMMENT '판매 수익 원',
    created_at DATETIME(6) NOT NULL COMMENT '구매 확정 정산 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_settlements_order (order_id),
    KEY ix_settlements_created (created_at DESC, id DESC),
    CONSTRAINT fk_settlements_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_settlements_gross CHECK (gross_amount BETWEEN 1 AND 1000000),
    CONSTRAINT ck_settlements_rate CHECK (fee_rate_bps BETWEEN 0 AND 10000),
    CONSTRAINT ck_settlements_fee CHECK (fee_amount = FLOOR(gross_amount * fee_rate_bps / 10000)),
    CONSTRAINT ck_settlements_sum CHECK (fee_amount >= 0 AND seller_amount >= 0 AND seller_amount + fee_amount = gross_amount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE reviews (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '후기 ID',
    order_id BIGINT NOT NULL COMMENT '주문당 한 건',
    rating INT NOT NULL COMMENT '별점 1~5',
    content TEXT NULL COMMENT '선택 후기 글, 최대 2000자',
    created_at DATETIME(6) NOT NULL COMMENT '작성 시각 UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reviews_order (order_id),
    KEY ix_reviews_created (created_at DESC, id DESC),
    CONSTRAINT fk_reviews_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT ck_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT ck_reviews_content CHECK (content IS NULL OR (CHAR_LENGTH(content) BETWEEN 1 AND 2000 AND CHAR_LENGTH(TRIM(content)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_moderation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '상품 운영 이력 ID',
    product_id BIGINT NOT NULL COMMENT '상품 ID',
    admin_id BIGINT NOT NULL COMMENT '실행 관리자 회원 ID',
    action VARCHAR(10) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '숨김 또는 복구',
    reason VARCHAR(1000) NOT NULL COMMENT '운영 사유',
    created_at DATETIME(6) NOT NULL COMMENT '처리 시각 UTC',
    PRIMARY KEY (id),
    KEY ix_product_moderation_product_created (product_id, created_at DESC, id DESC),
    KEY ix_product_moderation_admin (admin_id),
    CONSTRAINT fk_product_moderation_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_moderation_admin FOREIGN KEY (admin_id) REFERENCES members (id),
    CONSTRAINT ck_product_moderation_action CHECK (action IN ('HIDE', 'RESTORE')),
    CONSTRAINT ck_product_moderation_reason CHECK (CHAR_LENGTH(TRIM(reason)) BETWEEN 1 AND 1000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
