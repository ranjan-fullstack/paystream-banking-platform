package com.flipkartclone.productservice.validation;

import com.flipkartclone.productservice.dto.ProductRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PriceDiscountValidator implements ConstraintValidator<ValidPriceDiscount, ProductRequest> {

    @Override
    public boolean isValid(ProductRequest request, ConstraintValidatorContext context) {
        if (request.getPrice() == null || request.getDiscount() == null) {
            return true;
        }
        return request.getDiscount() < request.getPrice();
    }
}
