package io.digital.patterns.workflow.models.bpmn

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityRequest
import com.amazonaws.services.sns.AmazonSNSClient
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import io.digital.patterns.workflow.notification.AmazonSMSService
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

@Deployment(resources = ['./models/bpmn/send-notifications.bpmn'])
class SendNotificationBpmnSpec extends Specification {

    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()


    @Shared
    static LocalStackContainer localstack =
            new LocalStackContainer().withServices(LocalStackContainer.Service.SES, LocalStackContainer.Service.SNS)



    AmazonS3 amazonS3
    PdfService pdfService
    Environment environment
    AmazonSimpleEmailService amazonSimpleEmailService
    AmazonSMSService amazonSMSService
    RestTemplate restTemplate
    AmazonSNSClient client

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


        client = (AmazonSNSClient) AmazonSNSClient.builder()
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SNS))
                .withCredentials(localstack.getDefaultCredentialsProvider()).build()


        amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .enablePathStyleAccess()
                .build()
        amazonSMSService = new AmazonSMSService( client)

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
        Mocks.register('amazonSMSService', amazonSMSService)


        amazonSimpleEmailService
                .verifyEmailIdentity(new VerifyEmailIdentityRequest().withEmailAddress("from@from.com"))


    }


    def 'can send email'() {
        given: 'a notification payload for email only'
        def notificationPayload = S('''
                                            {
                                             "businessKey" : "businessKey",
                                              "email" : {
                                                "recipients" : [
                                                  "test@test.com",
                                                  "appples@test.com"
                                                ]
                                              }
                                            }''', DataFormats.JSON_DATAFORMAT_NAME)


        when: 'a request to send email is submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('send-notification')
                .businessKey('businessKey')
                .setVariables(['notificationPayload' : notificationPayload, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        assertThat(instance).isWaitingAt('start')

        when: 'send is executed'
        execute(job())

        then: 'email is sent'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(0))
        assertThat(instance).hasPassed('sendSES')
    }

    def 'can send sms'() {
        given: 'a notification payload for SMS only'
        def notificationPayload = S('''
                                            {
                                             "businessKey" : "businessKey",
                                              "sms" : {
                                                "message" : "Hello",
                                                "phoneNumbers": [
                                                  "0124444343434",
                                                  "0343434433434"
                                                ]
                                              }
                                            }''', DataFormats.JSON_DATAFORMAT_NAME)


        when: 'a request to send sms is made'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('send-notification')
                .businessKey('businessKey')
                .setVariables(['notificationPayload' : notificationPayload, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        assertThat(instance).isWaitingAt('start')

        when: 'send is executed'
        execute(job())

        then: 'sms is sent'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(0))
        assertThat(instance).hasPassed('sendSMSs')
    }


    def 'can send both SMS and email'() {
        given: 'a notification payload for both sms and email'
        def notificationPayload = S('''
                                            {
                                             "businessKey" : "businessKey",
                                              "email" : {
                                                "recipients" : [
                                                  "test@test.com",
                                                  "appples@test.com"
                                                ]
                                              },
                                              "sms" : {
                                                "message" : "Hello",
                                                "phoneNumbers": [
                                                  "0124444343434",
                                                  "0343434433434"
                                                ]
                                              }
                                            }''', DataFormats.JSON_DATAFORMAT_NAME)


        when: 'a request to send both email and sms is submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('send-notification')
                .businessKey('businessKey')
                .setVariables(['notificationPayload' : notificationPayload, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        assertThat(instance).isWaitingAt('start')

        when: 'send is executed'
        execute(job())

        then: 'sms is sent'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(0))
        assertThat(instance).hasPassed('sendSMSs')

        and: 'email sent'
        assertThat(instance).hasPassed('sendSES')

        and: 'process instance completed'
        assertThat(instance).isEnded()
    }

    def 'support task created if SMS fails'() {
        given: 'a notification payload for SMS only'
        def notificationPayload = S('''
                                            {
                                             "businessKey" : "businessKey",
                                              "sms" : {
                                                "message" : "Hello",
                                                "phoneNumbers": [
                                                  "",
                                                  ""
                                                ]
                                              }
                                            }''', DataFormats.JSON_DATAFORMAT_NAME)


        when: 'a request to send sms is made'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('send-notification')
                .businessKey('businessKey')
                .setVariables(['notificationPayload' : notificationPayload, 'initiatedBy': 'test@test.com']).execute()

        then: 'process instance should be active'
        assertThat(instance).isActive()
        assertThat(instance).isWaitingAt('start')

        when: 'send is executed'
        execute(job())

        then: 'user support task should be created'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).count(), Matchers.is(2L))
    }

}
