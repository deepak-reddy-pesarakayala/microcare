package com.microcare.billing.exception;

/**
 * Thrown when a requested resource is not found (HTTP 404).
 * Consistent with the pattern used in patient-service and appointment-service.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceName, String fieldName, Object fieldValue) {
        super(String.format("%s not found with %s: '%s'", resourceName, fieldName, fieldValue));
    }
}
