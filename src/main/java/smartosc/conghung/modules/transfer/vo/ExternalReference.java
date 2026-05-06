package smartosc.conghung.modules.transfer.vo;

/**
 * Value Object representing a normalized external reference.
 * Guarantees that trailing spaces and leading spaces are stripped,
 * preventing idempotency key mismatches caused by raw string comparison.
 */
public final class ExternalReference {

    private static final int MAX_LENGTH = 16;

    private final String value;

    private ExternalReference(String value) {
        this.value = value;
    }

    /**
     * Creates a normalized ExternalReference from a raw input string.
     * Strips leading/trailing whitespace and validates length.
     *
     * @param rawValue the raw external reference from client/partner
     * @return a normalized ExternalReference
     * @throws IllegalArgumentException if null, blank, or exceeds max length
     */
    public static ExternalReference from(String rawValue) {

        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("External reference must not be blank");
        }

        String normalized = rawValue.strip();

        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "External reference must not exceed " + MAX_LENGTH + " characters"
            );
        }

        return new ExternalReference(normalized);
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }

        if (!(obj instanceof ExternalReference that)) {
            return false;
        }

        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
