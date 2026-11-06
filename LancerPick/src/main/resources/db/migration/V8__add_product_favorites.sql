CREATE TABLE product_favorites (
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (member_id, product_id),
    KEY ix_product_favorites_recent (member_id, created_at DESC, product_id DESC),
    KEY ix_product_favorites_product (product_id),
    CONSTRAINT fk_product_favorites_member FOREIGN KEY (member_id) REFERENCES members (id),
    CONSTRAINT fk_product_favorites_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB;
