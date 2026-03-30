package org.jboss.sbomer.dtrack.publisher.adapter.out.download;

import java.io.InputStream;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@RegisterRestClient(configKey = "manifest-storage")
public interface ManifestStorageApiClient {

    @GET
    @Path("/{path:.+}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    InputStream download(@PathParam("path") @Encoded String path);
}