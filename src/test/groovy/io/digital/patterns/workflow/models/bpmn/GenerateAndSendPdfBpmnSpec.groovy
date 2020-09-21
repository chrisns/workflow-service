package io.digital.patterns.workflow.models.bpmn

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.VerifyEmailIdentityRequest
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import io.digital.patterns.workflow.pdf.PdfService
import org.apache.groovy.util.Maps
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
import static org.camunda.spin.DataFormats.JSON_DATAFORMAT_NAME
import static org.camunda.spin.Spin.S

@Deployment(resources = ['./models/bpmn/generate-and-send-pdf.bpmn'])
class GenerateAndSendPdfBpmnSpec extends Specification {

    def static wmPort = 8000


    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    @ClassRule
    @Shared
    WireMockRule wireMockRule = new WireMockRule(wmPort)


    @Shared
    static LocalStackContainer localstack =
            new LocalStackContainer().withServices(LocalStackContainer.Service.S3,
                    LocalStackContainer.Service.SES, LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS)


    public wireMockStub = new WireMockGroovy(wmPort)

    PdfService pdfService
    Environment environment
    AmazonS3 amazonS3
    AmazonSimpleEmailService amazonSimpleEmailService
    RestTemplate restTemplate

    def setupSpec() {
        System.setProperty("aws.s3.formData", "formdata")
        System.setProperty("aws.s3.pdfs", "pdfs")
        System.setProperty("ses.from.address", "from@from.com")
        System.setProperty('formApi.url', "http://localhost:${wmPort}")
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

        amazonSimpleEmailService =
                AmazonSimpleEmailServiceClientBuilder.standard()
                        .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SES))
                        .withCredentials(localstack.getDefaultCredentialsProvider()).build()


          amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(localstack.getDefaultCredentialsProvider())
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
                .enablePathStyleAccess()
                .build()

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


    def 'can generate pdf request'() {
        given: 'forms that a user has selected'

        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                            
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))



        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf]).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()

        when: 'job has been triggered'
        execute(job())


        then: 'process instance should have passed generate pdf task'
        assertThat(instance).hasPassed('generatePdf')
    }

    def 'can receive message from pdf server'() {
        given: 'forms that a user has selected'

        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                            
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))



        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf]).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        execute(job())
        assertThat(instance).hasPassed('generatePdf')

        when: 'response received from pdf server'
        runtimeService()
            .createMessageCorrelation(
                    'pdfGenerated_buildingPassRequest_2020-01-28T08:31:55'
            ).processInstanceId(instance.id)
            .setVariable('buildingPassRequest', S('''{
                                                                    "event" : "pdf-generation-success",
                                                                    "data": {
                                                                       "fileName" : "buildingPassRequest.pdf"
                                                                     } 
                                                                   }''')).correlateAllWithResult()

        then: 'should be waiting at sending pdf'
        assertThat(instance).isWaitingAt('sendpdfs')

    }

    def 'can create user task if pdf generation response is failed'() {
        given: 'forms that a user has selected'

        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                            
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))



        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf]).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        execute(job())
        assertThat(instance).hasPassed('generatePdf')

        when: 'response received from pdf server'
        runtimeService()
                .createMessageCorrelation(
                        'pdfGenerated_buildingPassRequest_2020-01-28T08:31:55'
                ).processInstanceId(instance.id)
                .setVariable('buildingPassRequest', S('''{
                                                                    "event" : "pdf-generation-failed",
                                                                    "data": {
                                                                       "fileName" : "buildingPassRequest.pdf"
                                                                     } 
                                                                   }''')).correlateAllWithResult()

        then: 'support task should be created'
        assertThat(instance).isWaitingAt('pdfFailureUserTask')
        assertThat(task()).hasDescription('PDF for buildingPassRequest has not been generated')

    }

    def 'can create user task timer expires'() {
        given: 'forms that a user has selected'

        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                            
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))



        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf]).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        execute(job())
        assertThat(instance).hasPassed('generatePdf')

        when: 'timer expires'
        execute(job())

        then: 'support task should be created'
        assertThat(instance).isWaitingAt('pdfFailureUserTask')
        assertThat(task()).hasDescription('PDF for buildingPassRequest has not been generated')

    }

    def 'can send pdf as attachments'() {
        given: 'forms that a user has selected'
        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                           
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.createBucket("pdfs")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))

        amazonS3.putObject(new PutObjectRequest("pdfs", "testPdf.pdf",
                new ClassPathResource("testPdf.pdf").getInputStream(), new ObjectMetadata()))



        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        execute(job())
        assertThat(instance).hasPassed('generatePdf')

        when: 'response received from pdf server'
        runtimeService()
                .createMessageCorrelation(
                        'pdfGenerated_buildingPassRequest_2020-01-28T08:31:55'
                ).processInstanceId(instance.id)
                .setVariable('buildingPassRequest', S('''{
                                                                    "event" : "pdf-generation-success",
                                                                    "data": {
                                                                       "fileName" : "testPdf.pdf"
                                                                     } 
                                                                   }''')).correlateAllWithResult()

        then: 'should be waiting at sending pdf'
        assertThat(instance).isWaitingAt('sendpdfs')

        when: 'send is executed'
        execute(job())

        then: 'pdf is sent'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(0))
        assertThat(instance).hasPassed('sendpdfs')
    }

    def 'User retries failed PDF sending task'() {
        given: 'forms that a user has selected'
        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                           
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        and: 'data exists in S3'
        amazonS3.createBucket("formdata")
        amazonS3.createBucket("pdfs")
        amazonS3.putObject(new PutObjectRequest("formdata", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .businessKey('businessKey')
                .setVariables(['generatePdf' : generatePdf, 'initiatedBy': 'test@test.com']).execute()


        then: 'process instance should be active'
        assertThat(instance).isActive()
        execute(job())
        assertThat(instance).hasPassed('generatePdf')

        when: 'response received from pdf server'
        runtimeService()
                .createMessageCorrelation(
                        'pdfGenerated_buildingPassRequest_2020-01-28T08:31:55'
                ).processInstanceId(instance.id)
                .setVariable('buildingPassRequest', S('''{
                                                                    "event" : "pdf-generation-success",
                                                                    "data": {
                                                                       "fileName" : "testPdfA.pdf"
                                                                     } 
                                                                   }''')).correlateAllWithResult()

        then: 'should be waiting at sending pdf'
        assertThat(instance).isWaitingAt('sendpdfs')

        when: 'send is executed'
        execute(job())

        then: 'user support task created'
        Assert.assertThat(taskQuery().processInstanceId(instance.id).list().size(), Matchers.is(1))
        assertThat(instance).isWaitingAt('sesSendFailure')

        when: 'User selects to retry sending PDF'
        complete(task(), Maps.of('sesSendFailure', S('''
                                                           {
                                                             "retry" : true
                                                           }
                                                           ''', JSON_DATAFORMAT_NAME)))

        amazonS3.putObject(new PutObjectRequest("pdfs", "testPdfA.pdf",
                new ClassPathResource("testPdf.pdf").getInputStream(), new ObjectMetadata()))

        execute(job())

        then: 'process is complete'
        assertThat(instance).isEnded()

    }

    def 'User cancels sending PDF via SES after failure'() {
        given: 'An email has failed to send'
        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                           
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        ProcessInstance processInstance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['generatePdf': generatePdf, 'initiatedBy': 'user', 'sesFailureCode': '22'])
                .startBeforeActivity('sesSendFailure')
                .execute()

        when: 'Task is running with PDF created'

        assertThat(processInstance).isActive()
        and: 'User has a task to investigate SES send failure'

        assertThat(task()).hasName("Investigate SES send failure 22")

        then: 'User selects to cancel sending via SES'
        complete(task(), Maps.of('sesSendFailure', S('''
                                                           {
                                                             "retry" : false
                                                           }
                                                           ''', JSON_DATAFORMAT_NAME)))

        and: 'process is complete'
        assertThat(processInstance).isEnded()

    }

    def 'User cancels retrying to create PDF after failure'() {
        given: 'An email has failed to send'

        def form = S('''{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        ProcessInstance processInstance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['businessKey': 'businessKey', 'initiatedBy': 'user', 'form': form])
                .startBeforeActivity('pdfFailureUserTask')
                .execute()

        when: 'A PDF has attempted to be created and failed'

        assertThat(processInstance).isActive()
        and: 'User has a task to investigate the PDF generation issue'

        assertThat(task()).hasName("Investigate generate PDF for failure ${S(form).prop('name').stringValue()}")

        then: 'User decides to not retry generating PDF'
        complete(task(), Maps.of('investigateFormPDF', S('''
                                                           {
                                                             "retry" : false
                                                           }
                                                           ''', JSON_DATAFORMAT_NAME)))

        and: 'Sub process is complete'
        assertThat(processInstance).hasPassed('EndEvent_1mmwa26')

    }

    def 'User retries to create PDF after failure'() {
        given: 'A PDF failed to be created'

        def generatePdf = S('''{
                            "businessKey" : "businessKey",
                            "forms": [{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }]
                            
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        def form = S('''{
                                "name": "buildingPassRequest",
                                "title" : "Building pass request",
                                "dataPath": "businessKey/buildingPassRequest/20200128T083155-xx1@x.com.json",
                                "submissionDate": "2020-01-28T08:31:55",
                                "submittedBy": "xx1@x.com",
                                "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a"
                            }''', DataFormats.JSON_DATAFORMAT_NAME)

        ProcessInstance processInstance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['generatePdf': generatePdf, 'businessKey': 'businessKey', 'initiatedBy': 'user', 'form': form])
                .startBeforeActivity('pdfFailureUserTask')
                .execute()

        when: 'A process is running but the PDF generation failed'

        assertThat(processInstance).isActive()
        and: 'User has a task to investigate the PDF generation issue'

        assertThat(task()).hasName("Investigate generate PDF for failure ${S(form).prop('name').stringValue()}")

        then: 'User decides to retry PDF generation'
        complete(task(), Maps.of('investigateFormPDF', S('''
                                                           {
                                                             "retry" : true
                                                           }
                                                           ''', JSON_DATAFORMAT_NAME)))

        and: 'PDF is retried'
        assertThat(processInstance).hasPassed('generatePdf')

    }

    def 'No attachments so dont send mail'() {
        given: 'A request to send an email'

        when: 'There are no attachments'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['initiatedBy': 'user',])
                .startBeforeActivity('hasAttachments')
                .execute()

        then: 'process instance completed'
        assertThat(instance).isEnded()
    }

    def 'No attachments so dont send mail - empty list defined'() {
        given: 'A request to send an email'
        def attachmentIds = []

        when: 'There are no attachments'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['attachmentIds': attachmentIds, 'initiatedBy': 'user',])
                .startBeforeActivity('hasAttachments')
                .execute()

        then: 'process instance completed'
        assertThat(instance).isEnded()
    }

    def 'Has attachments so send mail'() {
        given: 'A request to send an email'
        def attachmentIds = ['file1', 'file2']

        when: 'There are attachments defined'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('generate-and-send-pdf')
                .setVariables(['attachmentIds': attachmentIds, 'initiatedBy': 'user',])
                .startBeforeActivity('hasAttachments')
                .execute()

        then: 'should be waiting at sending pdf'
        assertThat(instance).isWaitingAt('sendpdfs')
    }
}
