package io.digital.patterns.workflow.cases

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import io.digital.patterns.workflow.aws.AwsProperties
import org.camunda.bpm.engine.AuthorizationService
import org.camunda.bpm.engine.HistoryService
import org.elasticsearch.client.RestHighLevelClient
import org.springframework.core.io.ClassPathResource
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

@Testcontainers
class CasesApplicationServiceSpec extends Specification {


    @Shared
    LocalStackContainer localstack =
            new LocalStackContainer("0.11.4").withServices(LocalStackContainer.Service.S3)

    AmazonS3 amazonS3
    CasesApplicationService casesApplicationService

    def historyService = Mock(HistoryService)
    def elasticsearchClient = Mock(RestHighLevelClient)
    def caseActionService = Mock(CaseActionService)
    def authorizationService = Mock(AuthorizationService)

    def setup() {
        final BasicAWSCredentials credentials = new BasicAWSCredentials('accessKey', 'secretAccessKey')

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .enablePathStyleAccess()
                .build()

        def awsProperties = new AwsProperties()
        awsProperties.setCaseBucketName("casedata")

        casesApplicationService = new CasesApplicationService(
                historyService,
                amazonS3,
                elasticsearchClient,
                awsProperties,
                caseActionService,
                authorizationService
        )

    }

    def 'can return JSON array of submission data'() {
        given: 's3 bucket set up'
        amazonS3.createBucket("casedata")

        and: 'form data exists in that bucket'
        amazonS3.putObject(new PutObjectRequest("casedata", "TEST-20200120-000/aForm/xx@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))

        amazonS3.putObject(new PutObjectRequest("casedata", "TEST-20200120-000/somethingElse/xx@x.com-20200128T083323.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))

        when: 'a call to get all submission data is requested'
        def result = casesApplicationService.getSubmissionData("TEST-20200120-000")

        then: 'result should not be null'
        result

        and: 'it should be an array of data'
        result.elements().size() == 2
    }

}
