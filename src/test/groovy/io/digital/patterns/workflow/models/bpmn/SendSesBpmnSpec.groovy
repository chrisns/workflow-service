package io.digital.patterns.workflow.models.bpmn

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityRequest
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import io.digital.patterns.workflow.pdf.PdfService
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.engine.test.Deployment
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.engine.test.mock.Mocks
import org.camunda.spin.DataFormats
import org.hamcrest.Matchers
import org.junit.Assert
import org.junit.ClassRule
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.localstack.LocalStackContainer
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.http.Response.response
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*
import static org.camunda.spin.Spin.S

@Deployment(resources = ['./models/bpmn/send-ses.bpmn'])
class SendSesBpmnSpec extends Specification {

    def static wmPort = 8000


    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    @ClassRule
    @Shared
    WireMockRule wireMockRule = new WireMockRule(wmPort)


    @Shared
    static LocalStackContainer localstack =
            new LocalStackContainer().withServices(LocalStackContainer.Service.SES)


    public wireMockStub = new WireMockGroovy(wmPort)

    AmazonS3 amazonS3
    PdfService pdfService
    Environment environment
    AmazonSimpleEmailService amazonSimpleEmailService
    RestTemplate restTemplate

    def setupSpec() {
        System.setProperty("ses.from.address", "from@from.com")
        localstack.start()

        localstack.execInContainer("aws ses verify-email-identity " +
                "--email-address from@from.com " +
                "--endpoint=${localstack.getEndpointConfiguration(LocalStackContainer.Service.SES).serviceEndpoint}")
    }

    def cleanupSpec() {
        localstack.stop()
    }


    def setup() {
        restTemplate = new RestTemplate()
        environment = new StandardEnvironment()

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .enablePathStyleAccess()
                .build()

        amazonSimpleEmailService =
                AmazonSimpleEmailServiceClientBuilder.standard()
                        .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SES))
                        .withCredentials(localstack.getDefaultCredentialsProvider()).build()

        pdfService = new PdfService(
                amazonS3,
                amazonSimpleEmailService,
                environment,
                restTemplate
        )
        Mocks.register('pdfService', pdfService)
        Mocks.register('environment', environment)

        wireMockStub.stub {
            request {
                method 'POST'
                url '/pdf'
            }
            response {
                status: 200
                headers {
                    "Content-Type" "application/json"
                }
            }
        }

        amazonSimpleEmailService
                .verifyEmailIdentity(new VerifyEmailIdentityRequest().withEmailAddress("from@from.com"))


    }


    def 'can send email'() {
        given: 'forms that a user has selected'
        def sendSES = S('''{
                            "businessKey" : "businessKey"
                            }''', DataFormats.JSON_DATAFORMAT_NAME)


        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('send-ses')
                .businessKey('businessKey')
                .setVariables(['sendSES' : sendSES, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        assertThat(instance).isWaitingAt('sendSES')

        when: 'send is executed'
        execute(job())

        then: 'email is sent'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(0))
        assertThat(instance).hasPassed('sendSES')
    }

}
