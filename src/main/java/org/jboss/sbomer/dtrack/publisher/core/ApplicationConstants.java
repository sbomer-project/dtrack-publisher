package org.jboss.sbomer.dtrack.publisher.core;

public class ApplicationConstants {

    private ApplicationConstants() {}

    // TODO maybe make into property
    public static final String COMPONENT_NAME = "dtrack-publisher";
    public static final String COMPONENT_VERSION = "1.0.0";

    // Key to pass a url to where the SBOM was published
    public static final String PUBLISHED_URL_KEY = "projectUrl";
    // Key to identify if a projectName and projectVersion was explicitly passed for a generation
    public static final String HANDLER_PROJECT_NAME_KEY = "handler-projectName";
    public static final String HANDLER_PROJECT_VERSION_KEY = "handler-projectVersion";
    // Key to identify if a projectName and projectVersion was explicitly passed for a publisher
    public static final String PUBLISHER_PROJECT_NAME_KEY = "projectName";
    public static final String PUBLISHER_PROJECT_VERSION_KEY = "projectVersion";
}
