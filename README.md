AiviA
=====

Simple app to mirror a topic from on-prem kafka to Aiven kafka. It does not support Schema Registry.

How does it work?
-----------------

First you need to create a mapping of topics to mirror. The app will read this from a properties-file, mapping a source topic to a destination topic. 

Example:

```properties
aapen-data-v2 = nais.data-v2
privat-hemmelig-saa-det-saa = nais.hemmelig-v2
```

With this configuration messages will be copied from `aapen-data-v2` on-prem to `nais.data-v2` on Aiven, and from `privat-hemmelig-saa-det-saa` on-prem to `nais.hemmelig-v2` on Aiven.

The application will read the file from the path `/var/run/configmaps/aivia-topic-mapping/topic_mapping.properties`. If you want something else, you can use the environment variable `AIVIA_TOPIC_MAPPING_PATH`.

If you want to use AiviA to mirror some other direction than on-prem to Aiven, you also need to configure source and target clusters using the environment variables `AIVIA_SOURCE` and `AIVIA_TARGET` respectively. Valid values are `on-prem` and `aiven`. The default is `on-prem` for source and `aiven` for target.

It is possible to mirror any combination of `AIVIA_SOURCE` and `AIVIA_TARGET` (even `aiven` to `aiven`).

Using AiviA
-----------

### 1. Create a new repository with a nais.yaml file:

```yaml
apiVersion: "nais.io/v1alpha1"
kind: "Application"
metadata:
  name: aivia
  namespace: myteam
  labels:
    team: myteam
spec:
  image: ghcr.io/nais/aivia:latest
  liveness:
    path: "/internal/isalive"
  readiness:
    path: "/internal/isready"
  replicas:
    min: 1
    max: 1
    cpuThresholdPercentage: 50
  prometheus:
    enabled: true
    path: "/internal/prometheus"
  limits:
    cpu: "200m"
    memory: "256Mi"
  requests:
    cpu: "200m"
    memory: "256Mi"
  env:
    - name: LOG_FORMAT
      value: logstash
  envFrom:
    - secret: aivia-kafka-on-prem
  filesFrom:
    - configmap: aivia-topic-mapping
  kafka:
    pool: nav-dev
```

If you want to use a specific version, get the latest aivia image from the [AiviA package page](https://github.com/orgs/nais/packages/container/package/aivia).

### 2. Create a configmap with your topic mappings

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    team: myteam
  annotations:
    reloader.stakater.com/match: "true"
  name: aivia-topic-mapping
  namespace: myteam
data:
  topic_mapping.properties: |
    aapen-data-v2 = myteam.data-v2
    privat-hemmelig-saa-det-saa = myteam.hemmelig-v2
```

### 3. Create a workflow to deploy the application and configmap

```yaml
name: "Deploy aivia"
on:
  push:
    branches:
    - "main"
jobs:
  deploy:
    name: "Deploy AiviA"
    runs-on: ubuntu-latest
    steps:
      - uses: "actions/checkout@v2"
      - uses: nais/deploy/actions/deploy@v1
        env:
          APIKEY: ${{ secrets.NAIS_DEPLOY_APIKEY }}
          CLUSTER: dev-gcp
          RESOURCE: configmap.yaml,nais.yaml
```

### 4. Create a secret with configuration for on-prem kafka, and apply it to the cluster

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: aivia-kafka-on-prem
  namespace: myteam
stringData:
  KAFKA_ON_PREM_BROKERS: "b27apvl00045.preprod.local:8443,b27apvl00046.preprod.local:8443,b27apvl00047.preprod.local:8443"
  KAFKA_ON_PREM_USERNAME: "myServiceUser"
  KAFKA_ON_PREM_PASSWORD: "myServiceUsersPassword"
```

Run `kubectl apply -f secret.yaml` to insert the secret into the cluster. Do *not* commit `secret.yaml` to the repository.

### 5. Commit and push

Commit `nais.yaml`, `configmap.yaml` and `.github/workflows/main.yaml` and push to github.

What does this do?
------------------

AiviA guarantees the following:

- At-least-once delivery of all messages present in the source topic.
- Preservation of message ordering if using the default partitioning strategy.
- Propagation of keys present in messages from the source topic.

AiviA _does not_ guarantee the following:

- Prompt delivery of messages.
- Preservation of offsets. Your application should ideally handle messages idempotently.

