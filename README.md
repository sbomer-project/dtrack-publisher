# OWASP Dependency Track Publisher

The **OWASP Dependency Track Publisher** is a dedicated microservice within the SBOMer NextGen architecture responsible for publishing the generated final SBOMs to an instance of OWASP Dependency Track

## Architecture

This service follows **Hexagonal Architecture (Ports and Adapters)**:

* **Core Domain:** TODO
* **Primary Port (Driving):** TODO
* **Secondary Port (Driven) (WIP):** TODO

## Features

TODO

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

This will spin up the manifest-storage-service on port 8085 along with the latest Quay images of the other components of the system.



Run the following to expose the service port to your local machine:
```bash
k port-forward svc/sbomer-release-manifest-storage-service-chart 8085:8080 -n sbomer-test
```