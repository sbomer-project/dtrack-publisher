# OWASP Dependency Track Publisher

The **OWASP Dependency Track Publisher** is a dedicated microservice within the SBOMer NextGen architecture responsible for publishing the generated final SBOMs to an instance of OWASP Dependency Track.

## Architecture

This service follows **Hexagonal Architecture (Ports and Adapters)** to strictly separate business logic from external infrastructure concerns:

* **Core Domain (`org.jboss.sbomer.dtrack.publisher.core`):** * Contains the central `SBOMPublishingService` which orchestrates the end-to-end publishing workflow.
    * Encapsulates the business rules for resolving the target D-Track project identity (Name and Version) by gracefully degrading through Generation Options, Batch Publisher Options, and physical SBOM metadata extraction (`SBOMIdentityExtractor`).
    * Agnostic to how messages arrive or how HTTP calls are made.

* **Primary Port (Driving Adapters):** * **Kafka Consumer (`KafkaRequestsFinishedListener`):** Listens to the `requests.finished` Kafka topic. When an SBOM generation is marked as complete, this adapter translates the Avro event into a `PublishingTask` and drives the Core Domain to begin publishing.

* **Secondary Ports (Driven Adapters):** * **SBOM Storage Client (`HttpSBOMDownloaderAdapter`):** Reaches out to the SBOMer Manifest Storage Service via HTTP to download the physical SBOM JSON file (e.g., `bom-linux-amd64.json`) to local temporary storage prior to upload.
    * **Dependency-Track API Client (`DependencyTrackUploaderAdapter`):** A MicroProfile REST Client that streams the downloaded SBOM to the D-Track `/api/v1/bom` endpoint via a `multipart/form-data` POST request. It handles authentication and translates API responses/errors into domain exceptions.
    * **Kafka Producer (`KafkaPublishFinishedEmitterAdapter`):** Once publishing is complete, this adapter maps the domain results back into an Avro event and emits it to the `publish.finished` Kafka topic, passing along critical D-Track metadata.

## Features

* **Event-Driven & Asynchronous:** Fully decoupled from the main SBOM generation pipeline. It reacts to completion events and emits its own status events, allowing non-blocking operations.
* **Dynamic Identity Resolution:** Intelligently determines where an SBOM should be placed in Dependency-Track by evaluating explicit handler options, falling back to global publisher configs, or lazily parsing the `metadata.component` of the physical SBOM itself.
* **Resilient Streaming:** Downloads large SBOMs to local temporary files and streams them to Dependency-Track. Uses MicroProfile Fault Tolerance (`@Retry`) to gracefully handle transient network errors (e.g., broken pipes, gateway timeouts) during large uploads.
* **Deep Link Generation:** Automatically formulates the resulting D-Track Project Lookup URL (`projectUrl`) and the asynchronous task tracking URL (`tokenUrl`), attaching them to the final Kafka event so downstream systems or users can click directly through to the UI or track progress.
* **Auto-Creation:** Leverages Dependency-Track's `autoCreate=true` capability to seamlessly provision new projects and versions on the fly if they do not already exist.

## Getting Started (Local Development)

This component is designed to run alongside the wider SBOMer system using Helm.

### 1. Start the Infrastructure

Run the local dev from the root of the project repository to set up the minikube environment:

```shell script
bash ./hack/setup-local-dev.sh
```

Then run the command below to start the Helm chart with the component build:

```bash
bash ./hack/run-helm-with-local-build.sh
```

## Dependency-Track Setup for Local Testing

For the local instance of Dependency-Track for testing (that installs with the wider helm chart), we must perform initial setup before the Publisher can successfully upload SBOMs.

By default, Dependency-Track requires us to change the default admin password, configure a team with the correct permissions, and generate an API key.

### 1. Initial Login and Password Reset
1. Access the Dependency-Track frontend UI (e.g., `http://drack.localhost:8080` or your configured Ingress route).
2. Log in using the default credentials:
    * **Username:** `admin`
    * **Password:** `admin`
3. We will immediately be prompted to change the password. Enter a new, secure password and log back in.

### 2. Create a Team and Assign Privileges
The Publisher requires specific permissions to upload BOMs and create projects dynamically.
1. Navigate to **Administration** (the gear icon in the top right).
2. Go to **Access Management** -> **Teams**.
3. Create a new Team (e.g., `SBOM Publisher`).
4. Select the newly created team and go to the **Permissions** tab.
5. Grant the following required permissions:
    * `BOM_UPLOAD`
    * `PROJECT_CREATION_UPLOAD` (Required since the publisher uses `autoCreate=true`)

### 3. Generate an API Key
1. Still within your newly created team's configuration, navigate to the **API Keys** tab.
2. Click the button to generate a new API key.
3. Copy the generated key to your clipboard. *(Note: Treat this key as a sensitive secret).*

### 4. Inject the API Key into the Kubernetes Secret
The Publisher component loads the Dependency-Track API key from a Kubernetes Secret. We need to patch the existing dummy secret with your real key.

Run the following command against your cluster (replace `<YOUR_NEW_API_KEY>` with the key you just generated):

```bash
kubectl patch secret sbomer-release-dtrack-secret -n sbomer-test \
  -p '{"stringData": {"api-key": "<YOUR_NEW_API_KEY>"}}'
```

### 5. Restart the Publisher Component
Kubernetes Pods do not automatically hot-reload environment variables from patched secrets. You must restart the Publisher deployment so the new pod boots up and reads the updated API key.

```Bash
kubectl rollout restart deployment sbomer-release-dtrack-publisher-chart -n sbomer-test
```
Once the new pod is running, the Publisher is fully authenticated and ready to stream SBOMs to your local Dependency-Track instance