package io.digital.patterns.workflow.data

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import io.digital.patterns.workflow.aws.AwsProperties
import org.apache.http.HttpHost
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.history.HistoricProcessInstance
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Testcontainers
class FormDataServiceSpec extends Specification {


    @Shared
    LocalStackContainer localstack =
            new LocalStackContainer("0.11.4").withServices(LocalStackContainer.Service.S3)


    @Shared
    ElasticsearchContainer esContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.6.2")


    RuntimeService runtimeService = Mock()
    AmazonS3 amazonS3
    def elasticsearchClient

    FormDataService service


    def setup() {
        amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .enablePathStyleAccess()
                .build()

        elasticsearchClient = new RestHighLevelClient(RestClient.builder(
                HttpHost.create(esContainer.getHttpHostAddress())
        ))

        service = new FormDataService(runtimeService, amazonS3, elasticsearchClient )

    }

    def 'can generate request'() {
        given: 'a form'
        def form = '''
            {
                "submit": true,
                "test": "apples",
                "form": {
                   "submittedBy" : "email",
                   "name": "testForm",
                   "formVersionId": "versionId",
                   "submissionDate": "20200120T12:12:00",
                   "title": "test",
                   "process": {
                      
                   }
                }
            }
        '''
        and: 'process instance'
        HistoricProcessInstance processInstance = Mock()
        processInstance.getId() >> "processInstance"
        processInstance.getProcessDefinitionId() >> "processdefinitionid"
        processInstance.getBusinessKey() >> "businessKey"

        and: 'data set up in s3'
        amazonS3.createBucket("formdata")

        when: 'request is made'
        service.save(form, processInstance, "id", "formdata")

        then: 'request is not null'
        def result = amazonS3.getObject("formdata", FormDataService.key(
                "businessKey",
                "testForm",
                "email",
                "20200120T12:12:00"
        ))
        result

        and: 'data created in ES'
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder()
        GetRequest request = new GetRequest()
        request.index(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
        request.id("businessKey/testForm/email-202001200101T121200.json")
        def response = elasticsearchClient.get(request,
                builder.build())
        response.sourceAsMap.size() != 0

        0 * runtimeService.createIncident(_,_,_)
    }

}