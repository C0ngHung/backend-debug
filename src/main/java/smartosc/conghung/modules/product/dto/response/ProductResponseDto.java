package smartosc.conghung.modules.product.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ProductResponseDto {

    private Long id;

    private String productName;

    private String description;

    private BigDecimal price;

    private String category;

    private String status;
}
