package org.jboss.sbomer.dtrack.publisher.core.port.api;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;

public interface SBOMPublishProcessor {

    void publishSBOMs(PublishingTask task);

}
