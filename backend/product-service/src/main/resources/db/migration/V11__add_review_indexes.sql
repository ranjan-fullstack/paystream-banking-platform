CREATE INDEX idx_review_product_id
    ON review (product_id);

CREATE INDEX idx_review_user_id
    ON review (user_id);

CREATE INDEX idx_review_rating
    ON review (rating);
