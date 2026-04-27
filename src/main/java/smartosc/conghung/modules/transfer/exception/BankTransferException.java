package smartosc.conghung.modules.transfer.exception;

public class BankTransferException extends Exception {

    public BankTransferException(String message) {
        super(message);
    }

    public BankTransferException(String message, Throwable cause) {
        super(message, cause);
    }
}
