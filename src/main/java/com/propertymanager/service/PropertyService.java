package com.propertymanager.service;

import com.propertymanager.model.Property;
import com.propertymanager.repository.PropertyRepository;
import com.propertymanager.utils.NumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for properties.
 *
 * Responsibilities:
 *   - Validate input before touching the database
 *   - Auto-generate the property code
 *   - Write audit log entries for every change
 *   - Translate technical errors into user-facing messages
 */
public class PropertyService {

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

    private final PropertyRepository repository;
    private final AuditService audit;

    public PropertyService() {
        this.repository = new PropertyRepository();
        this.audit = new AuditService();
    }

    /* =================================================================== */

    /** All properties ordered by name. */
    public List<Property> listAll() {
        return repository.findAll();
    }

    /** Only ACTIVE properties — for dropdowns. */
    public List<Property> listActive() {
        return repository.findAllActive();
    }

    /** Case-insensitive search by name, code, or location. */
    public List<Property> search(String query) {
        return repository.search(query);
    }

    public Property findById(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Property not found."));
    }

    /* =================================================================== */

    /**
     * Validates and creates a new property.
     * Generates the property code automatically.
     *
     * @throws ValidationException with a user-friendly message on failure
     */
    public Property create(Property p) throws ValidationException {
        validateForCreate(p);

        // Auto-generate code if not provided
        if (p.getCode() == null || p.getCode().isBlank()) {
            p.setCode(NumberGenerator.generatePropertyCode(p.getName()));
        }
        // Ensure the code is unique
        if (repository.codeExists(p.getCode(), 0L)) {
            throw new ValidationException("Property code '" + p.getCode() + "' is already in use.");
        }

        if (p.getStatus() == null || p.getStatus().isBlank()) {
            p.setStatus("ACTIVE");
        }
        if (p.getRegisteredAt() == null) {
            p.setRegisteredAt(LocalDate.now());
        }

        long id = repository.insert(p);
        audit.log("PROPERTY_CREATED", "properties", String.valueOf(id),
                  p.getName() + " (" + p.getCode() + ")");
        log.info("Created property '{}' code={}", p.getName(), p.getCode());
        return p;
    }

    /* =================================================================== */

    /**
     * Validates and updates an existing property.
     */
    public Property update(Property p) throws ValidationException {
        validateForUpdate(p);

        if (repository.codeExists(p.getCode(), p.getId())) {
            throw new ValidationException("Property code '" + p.getCode() + "' is already in use.");
        }

        repository.update(p);
        audit.log("PROPERTY_UPDATED", "properties", String.valueOf(p.getId()),
                  p.getName() + " (" + p.getCode() + ")");
        log.info("Updated property id={}", p.getId());
        return p;
    }

    /* =================================================================== */

    /**
     * Marks a property INACTIVE. Safer than delete — its units, tenancies,
     * and payment history stay intact.
     */
    public void deactivate(long id) {
        Property p = findById(id);
        p.setStatus("INACTIVE");
        repository.update(p);
        audit.log("PROPERTY_DEACTIVATED", "properties", String.valueOf(id), p.getName());
        log.info("Deactivated property id={}", id);
    }

    public void activate(long id) {
        Property p = findById(id);
        p.setStatus("ACTIVE");
        repository.update(p);
        audit.log("PROPERTY_ACTIVATED", "properties", String.valueOf(id), p.getName());
        log.info("Activated property id={}", id);
    }

    /* =================================================================== */

    /**
     * Hard delete. Blocked by the DB if the property still has units.
     * Only used for cases like "I typed a wrong property name on day one".
     */
    public void delete(long id) {
        Property p = findById(id);
        repository.delete(id);
        audit.log("PROPERTY_DELETED", "properties", String.valueOf(id), p.getName());
        log.info("Deleted property id={}", id);
    }

    /* =================================================================== */

    private void validateForCreate(Property p) throws ValidationException {
        if (p == null) throw new ValidationException("Property data is required.");
        if (isBlank(p.getName())) throw new ValidationException("Property name is required.");
        if (p.getName().length() > 120)
            throw new ValidationException("Property name must be 120 characters or fewer.");
        if (p.getLocation() != null && p.getLocation().length() > 180)
            throw new ValidationException("Location must be 180 characters or fewer.");
        if (p.getStatus() != null && !"ACTIVE".equals(p.getStatus()) && !"INACTIVE".equals(p.getStatus()))
            throw new ValidationException("Status must be ACTIVE or INACTIVE.");
    }

    private void validateForUpdate(Property p) throws ValidationException {
        if (p == null) throw new ValidationException("Property data is required.");
        if (p.getId() <= 0) throw new ValidationException("Property id is missing.");
        validateForCreate(p);
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /* =================================================================== */
    /*  Validation exception                                                */
    /* =================================================================== */

    /** Signals a validation problem with a message suitable for display. */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
    }
}
