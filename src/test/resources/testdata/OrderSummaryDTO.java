package io.github.cyfko.example;

import io.github.cyfko.projection.Computed;
import io.github.cyfko.projection.Provider;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Projection;

import java.math.BigDecimal;

@Projection(from = Order.class, providers = {
        @Provider(value = TestComputationProvider.class, bean = "myBean")
})
public interface OrderSummaryDTO {

    @Projected(from = "orderNumber")
    String getOrderNumber();

    @Projected(from = "totalAmount")
    BigDecimal getTotalAmount();

    @Projected(from = "status")
    Order.OrderStatus getStatus();

    @Projected(from = "user.email")
    String getCustomerEmail();

    @Computed(dependsOn = { "user.firstName", "user.lastName" })
    String getCustomerName();

    @Computed(dependsOn = { "totalAmount" })
    String getFormattedAmount();
}