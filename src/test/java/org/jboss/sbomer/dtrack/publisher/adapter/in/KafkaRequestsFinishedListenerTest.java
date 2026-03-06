package org.jboss.sbomer.dtrack.publisher.adapter.in;

import static org.jboss.sbomer.dtrack.publisher.core.ApplicationConstants.COMPONENT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.jboss.sbomer.dtrack.publisher.core.domain.PublishingTask;
import org.jboss.sbomer.dtrack.publisher.core.port.api.SBOMPublishProcessor;
import org.jboss.sbomer.dtrack.publisher.core.port.spi.FailureNotifier;
import org.jboss.sbomer.events.common.ContextSpec;
import org.jboss.sbomer.events.common.PublisherSpec;
import org.jboss.sbomer.events.orchestration.RequestsFinished;
import org.jboss.sbomer.events.orchestration.RequestsFinishedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KafkaRequestsFinishedListenerTest {

    @Mock
    SBOMPublishProcessor processor;

    @Mock
    FailureNotifier failureNotifier;

    @InjectMocks
    KafkaRequestsFinishedListener listener;

    private RequestsFinished mockEvent;
    private RequestsFinishedData mockData;
    private PublishingTask dummyTask;

    @BeforeEach
    void setUp() {
        mockEvent = mock(RequestsFinished.class);
        mockData = mock(RequestsFinishedData.class);

        // Lenient because not every test path reaches the data payload
        Mockito.lenient().when(mockEvent.getData()).thenReturn(mockData);
        Mockito.lenient().when(mockData.getRequestId()).thenReturn("req-123");

        // Create a valid dummy record for the mapper to return
        PublishingTask.PublisherConfig pubConfig = new PublishingTask.PublisherConfig(
                COMPONENT_NAME, "1.0", Map.of()
        );
        dummyTask = new PublishingTask("req-123", pubConfig, List.of());
    }

    @Test
    void shouldProcessEventWhenIntendedForThisPublisher() {
        // Arrange
        PublisherSpec dtrackPublisher = mock(PublisherSpec.class);
        when(dtrackPublisher.getName()).thenReturn(COMPONENT_NAME);
        when(mockData.getPublishers()).thenReturn(List.of(dtrackPublisher));

        // Mock the static mapper to return our dummy domain record
        try (MockedStatic<PublishingTaskMapper> mapperMock = Mockito.mockStatic(PublishingTaskMapper.class)) {
            mapperMock.when(() -> PublishingTaskMapper.toPublishingTask(mockEvent)).thenReturn(dummyTask);

            // Act
            listener.onMessageReceived(mockEvent);

            // Assert
            verify(processor).publishSBOMs(dummyTask);
            verify(failureNotifier, never()).notify(any(), any(), any());
        }
    }

    @Test
    void shouldIgnoreEventWhenIntendedForDifferentPublisher() {
        // Arrange
        PublisherSpec otherPublisher = mock(PublisherSpec.class);
        when(otherPublisher.getName()).thenReturn("some-other-publisher");
        when(mockData.getPublishers()).thenReturn(List.of(otherPublisher));

        // Act
        listener.onMessageReceived(mockEvent);

        // Assert
        verify(processor, never()).publishSBOMs(any());
        verify(failureNotifier, never()).notify(any(), any(), any());
    }

    @Test
    void shouldNotifyFailureWhenExceptionIsThrownDuringProcessing() {
        // Arrange
        PublisherSpec dtrackPublisher = mock(PublisherSpec.class);
        when(dtrackPublisher.getName()).thenReturn(COMPONENT_NAME);
        when(mockData.getPublishers()).thenReturn(List.of(dtrackPublisher));

        ContextSpec mockContext = mock(ContextSpec.class);
        when(mockEvent.getContext()).thenReturn(mockContext);
        when(mockContext.getCorrelationId()).thenReturn("corr-456");

        try (MockedStatic<PublishingTaskMapper> mapperMock = Mockito.mockStatic(PublishingTaskMapper.class)) {
            mapperMock.when(() -> PublishingTaskMapper.toPublishingTask(mockEvent))
                    .thenThrow(new RuntimeException("Mapping failed!"));

            // Act
            listener.onMessageReceived(mockEvent);

            // Assert
            verify(processor, never()).publishSBOMs(any());
            verify(failureNotifier).notify(any(), eq("corr-456"), eq(mockEvent));
        }
    }

    @Test
    void shouldHandleCompletelyNullEventGracefully() {
        // Act
        listener.onMessageReceived(null);

        // Assert
        verify(processor, never()).publishSBOMs(any());
        verify(failureNotifier).notify(any(), isNull(), isNull());
    }
}
