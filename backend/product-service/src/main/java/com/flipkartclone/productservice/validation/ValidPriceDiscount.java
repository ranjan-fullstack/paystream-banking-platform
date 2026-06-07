package com.flipkartclone.productservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PriceDiscountValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPriceDiscount {
    String message() default "Discount must be less than price";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
