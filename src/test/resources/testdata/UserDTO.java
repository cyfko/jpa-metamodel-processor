package io.github.cyfko.example;

import io.github.cyfko.projection.Computed;
import io.github.cyfko.projection.Provider;
import io.github.cyfko.projection.Projected;
import io.github.cyfko.projection.Projection;

import java.util.List;

@Projection(
        from = User.class,
        providers = @Provider(value = TestComputationProvider.class, bean = "myBean")
)
public interface UserDTO {
    // Getters and setters
    @Projected(from = "email")
    public String getUserEmail();

    @Projected(from = "address.city")
    public String getCity();

    @Projected(from = "department.name")
    public String getDepartmentName();

    @Computed(dependsOn = { "firstName", "lastName" })
    public String getFullName();

    @Computed(dependsOn = { "birthDate" })
    public Integer getAge();

    @Projected(from = "orders")
    public List<OrderDTO> getOrders();
}