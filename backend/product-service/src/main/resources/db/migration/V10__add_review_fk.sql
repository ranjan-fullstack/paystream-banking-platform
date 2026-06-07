ALTER TABLE review
    ADD CONSTRAINT fk_review_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON UPDATE NO ACTION
            ON DELETE NO ACTION;
