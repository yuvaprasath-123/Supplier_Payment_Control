package com.ofss.exception;

public class ResourceNotFoundException
        extends RuntimeException {

    public ResourceNotFoundException(
            String resourceName,
            String resourceId
    ) {

        super(
                resourceName
                        + " not found with id: "
                        + resourceId
        );
    }
}
