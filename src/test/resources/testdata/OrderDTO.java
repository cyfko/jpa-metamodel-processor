package io.github.cyfko.example;

import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Projection;

import java.math.BigDecimal;

@Projection(from = Order.class)
public interface OrderDTO {
    Long getId();

    String getOrderNumber();

    @Projected(from = "totalAmount")
    BigDecimal getAmount();
}