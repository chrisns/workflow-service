# Workflow Service

![Build Status](https://github.com/UKHomeOffice/workflow-service/workflows/Publish%20Docker/badge.svg)

Integrated Camunda engine with Cockpit for COP, eForms & Cerberus

## Starting the service locally

1. Start all the required services:
    ```shell script
    docker-compose up
    ```
2. Follow the [steps to run Workflow Service locally](https://design-guidelines.cop.homeoffice.gov.uk/guides/workflow/local-workflow-service/#how-to-git-clone-the-workflow-engine)
3. For WM options use:
    ```
   -Dencryption.passPhrase=test
   -Dencryption.salt=test
   -Dcamunda.variable.encryption=true
   -Daws.elasticsearch.region=eu-west-2
   -Daws.elasticsearch.endpoint=localhost
   -Daws.elasticsearch.port=9200
   -Daws.elasticsearch.scheme=http
   -Dauth.url=<ASK_DEVOPS>
   -Dauth.realm=<ASK_DEVOPS>
   -Dauth.clientId=<ASK_DEVOPS>
   -Dauth.clientSecret=<ASK_DEVOPS>
   -Ddatabase.url=jdbc:postgresql://localhost:5432/workflow-service
   -Ddatabase.username=postgres
   -Ddatabase.password=postgres
   -Ddatabase.driver-class-name=org.postgresql.Driver
   -Daws.s3.case-bucket-name=<ASK_DEVOPS>
   -Daws.elasticsearch.credentials.access-key=x
   -Daws.elasticsearch.credentials.secret-key=x
   -Dusername=<ASK_DEVOPS>
   -Dpassword=1ac74b32-<ASK_DEVOPS>
   -Dcamunda.bpmn.upload.roles=process_admin,bpmn_uploader
    ```
4. For ENV vars use:
    ```
   AWS_ACCESS_KEY=<ASK_DEVOPS>
   AWS_SECRET_KEY=<ASK_DEVOPS>
   AWS_REGION=eu-west-2
   SPRING_PROFILES_ACTIVE=local
    ```

## Environment

### Bootstrap configuration

The following environment variables are required to load properties from AWS secrets manager

* AWS_SECRETS_MANAGER_ENABLED
* AWS_REGION
* AWS_ACCESS_KEY
* AWS_SECRET_KEY
* SPRING_PROFILES_ACTIVE


### Application configuration

The following properties need to be configured in AWS secrets manager

```json5
{
  "database.driver-class-name": "org.postgresql.Driver",
  "database.password": "",
  "database.username": "admin",
  "auth.url": "https://keycloak.example.com",
  "auth.clientId": "servicename",
  "auth.clientSecret": "secret",
  "auth.realm": "master",
  "aws.s3.case-bucket-name": "bucketName",
  "aws.s3.pdfs": "bucketName2",
  "formApi.url": "https://formApi.example.com",
  "engine.webhook.url": "https://engine-service.example.com",
  "gov.notify.api.key": "xxxxxx",
  "database.url": "jdbc:postgresql://dbUrl.example.com:5432/engine?sslmode=require&currentSchema=public",
  "camunda.bpmn.upload.roles": "process_admin,bpmn_uploader",
  "camunda.variable.encryption": true,
  "encryption.passPhrase" : "passPhrase",
  "encryption.salt": "salt"
}
```

Example helm chart for install [helm - workflowservice](https://github.com/DigitalPatterns/helm/tree/master/workflowservice)
