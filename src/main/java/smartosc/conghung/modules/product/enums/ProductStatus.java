package smartosc.conghung.modules.product.enums;

import lombok.Getter;

@Getter
public enum ProductStatus {

    ACTIVE("ACTIVE"),
    INACTIVE("INACTIVE");

    private final String value;

    ProductStatus(String value) {
        this.value = value;
    }
}
