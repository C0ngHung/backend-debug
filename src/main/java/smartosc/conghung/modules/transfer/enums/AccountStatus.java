package smartosc.conghung.modules.transfer.enums;

import lombok.Getter;

@Getter
public enum AccountStatus {

    ACTIVE("ACTIVE"),
    BLOCKED("BLOCKED"),
    CLOSED("CLOSED");

    private final String value;

    AccountStatus(String value) {
        this.value = value;
    }
}
