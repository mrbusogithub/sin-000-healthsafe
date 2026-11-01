package co.wethinkcode.healthsafe.application;

/**
 * Represents an application error that should be returned with a specific
 * HTTP status code.
 *
 * For example:
 * - 400 means the request contains invalid input.
 * - 404 means the requested ward does not exist.
 * - 502 means an upstream service returned an invalid response.
 * - 503 means a required dependency is unavailable.
 *
 * Keeping the status code with the exception allows the API layer to
 * translate application errors into HTTP responses.
 */
public class StaffingServiceException extends RuntimeException {

    private final int status;

    public StaffingServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public StaffingServiceException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Returns the HTTP status associated with this application error.
     */
    public int getStatus() {
        return status;
    }
}