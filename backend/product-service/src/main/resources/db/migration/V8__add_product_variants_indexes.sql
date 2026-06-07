CREATE INDEX idx_product_variants_product_id
    ON product_variants (product_id);

CREATE INDEX idx_product_variants_name_value
    ON product_variants (variant_name, variant_value);
