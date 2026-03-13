package org.jboss.sbomer.dtrack.publisher.adapter.out.dtrack;

import java.io.File;
import java.util.Map;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/bom")
@RegisterRestClient(configKey = "dtrack-api")
public interface DependencyTrackApiClient {

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    Map<String, String> uploadSbom(
            @HeaderParam("X-Api-Key") String apiKey,
            @RestForm("projectName") String projectName,
            @RestForm("projectVersion") String projectVersion,
            @RestForm("autoCreate") boolean autoCreate,
            @RestForm("bom") File file
    );
}
