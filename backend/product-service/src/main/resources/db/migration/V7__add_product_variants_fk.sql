ALTER TABLE product_variants
    ADD CONSTRAINT fk_product_variants_product
        FOREIGN KEY (product_id)
            REFERENCES products (id)
            ON UPDATE NO ACTION
            ON DELETE NO ACTION;
