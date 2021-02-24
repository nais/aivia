AiviA
=====

Simple app to mirror a topic from on-prem kafka to Aiven kafka.

How to use
----------

First you need to create a mapping of topics to mirror. The app will read this from a properties-file, mapping a source topic to a destination topic.

Example:

```properties
aapen-data-v2 = nais.data-v2
privat-hemmelig-saa-det-saa = nais.hemmelig-v2
```

With this configuration messages will be copied from `aapen-data-v2` on-prem to `nais.data-v2` on Aiven, and from `privat-hemmelig-saa-det-saa` on-prem to `nais.hemmelig-v2` on Aiven.

The application will read the file from the path `/var/run/configmaps/aivia-topic-mapping/topic_mapping.properties`. If you want something else, you can use the environment variable `AIVIA_TOPIC_MAPPING_PATH`.

The best way to achieve this is to use a [ConfigMap](https://kubernetes.io/docs/concepts/configuration/configmap/) that is mounted with the application using [`spec.filesFrom[].configmap`](https://doc.nais.io/nais-application/nais.yaml/reference/#specfilesfromconfigmap):

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  labels:
    team: nais
  name: aivia-topic-mapping
  namespace: nais
data:
  topic_mapping.properties: |
    aapen-data-v2 = nais.data-v2
    privat-hemmelig-saa-det-saa = nais.hemmelig-v2
```
