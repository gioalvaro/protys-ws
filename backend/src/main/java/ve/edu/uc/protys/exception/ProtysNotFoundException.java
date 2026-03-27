package ve.edu.uc.protys.exception;

/**
 * Exception thrown when a requested entity is not found in the system.
 */
public class ProtysNotFoundException extends RuntimeException {

    private final String entityType;
    private final String entityId;

    /**
     * Constructs a new ProtysNotFoundException with entity type and ID.
     *
     * @param entityType the type of the entity that was not found (e.g., "OntologyModule")
     * @param entityId   the ID of the entity that was not found
     */
    public ProtysNotFoundException(String entityType, String entityId) {
        super(String.format("%s with id '%s' not found", entityType, entityId));
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /**
     * Constructs a new ProtysNotFoundException with a custom message.
     *
     * @param message the detail message
     */
    public ProtysNotFoundException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
    }

    /**
     * Constructs a new ProtysNotFoundException with entity type, ID, and a cause.
     *
     * @param entityType the type of the entity that was not found
     * @param entityId   the ID of the entity that was not found
     * @param cause      the cause of this exception
     */
    public ProtysNotFoundException(String entityType, String entityId, Throwable cause) {
        super(String.format("%s with id '%s' not found", entityType, entityId), cause);
        this.entityType = entityType;
        this.entityId = entityId;
    }

    /**
     * Gets the type of the entity that was not found.
     *
     * @return the entity type
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Gets the ID of the entity that was not found.
     *
     * @return the entity ID
     */
    public String getEntityId() {
        return entityId;
    }
}
