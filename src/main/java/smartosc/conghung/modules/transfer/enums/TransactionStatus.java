package smartosc.conghung.modules.transfer.enums;

import lombok.Getter;

@Getter
public enum TransactionStatus {

    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED");

    private final String value;

    TransactionStatus(String value) {
        this.value = value;
    }
}
