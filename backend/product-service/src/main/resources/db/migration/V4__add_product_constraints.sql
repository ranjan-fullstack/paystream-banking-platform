-- Unique constraint
ALTER TABLE products
    ADD CONSTRAINT uk_product_title_brand
        UNIQUE (title, brand);

-- Foreign key (category must already exist)
ALTER TABLE products
    ADD CONSTRAINT fk_products_category
        FOREIGN KEY (category_id)
            REFERENCES category (id)
            ON UPDATE NO ACTION
            ON DELETE NO ACTION;
