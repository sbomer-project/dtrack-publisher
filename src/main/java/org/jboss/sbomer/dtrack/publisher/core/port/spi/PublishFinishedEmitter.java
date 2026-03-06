package org.jboss.sbomer.dtrack.publisher.core.port.spi;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishFinishedResult;

public interface PublishFinishedEmitter {
    /**
     * Emits the final success event to the messaging broker.
     *
     * @param result The domain representation of the finished task.
     */
    void emit(PublishFinishedResult result);
}