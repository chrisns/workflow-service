package io.digital.patterns.workflow.pdf

import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService
import com.amazonaws.services.simpleemail.model.SendRawEmailResult
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit.WireMockRule
import com.github.tomjankes.wiremock.WireMockGroovy
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.engine.test.mock.Mocks
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.spin.DataFormats
import org.junit.ClassRule
import org.springframework.core.env.Environment
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource
import org.springframework.web.client.RestTemplate
import org.testcontainers.containers.localstack.LocalStackContainer
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.http.Response.response
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*
import static org.camunda.spin.Spin.S

class PdfServiceSpec extends Specification {

    def static wmPort = 8000


    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    @ClassRule
    @Shared
    WireMockRule wireMockRule = new WireMockRule(wmPort)

    public wireMockStub = new WireMockGroovy(wmPort)

    @Shared
    static LocalStackContainer localstack =
            new LocalStackContainer().withServices(LocalStackContainer.Service.S3)


    AmazonS3 amazonS3

    PdfService pdfService
    AmazonSimpleEmailService amazonSimpleEmailService
    Environment environment
    RestTemplate restTemplate

    def setupSpec() {
        System.setProperty("aws.s3.formData", "test")
        System.setProperty('formApi.url', "http://localhost:${wmPort}")
        localstack.start()
    }

    def cleanupSpec() {
        localstack.stop()
    }


    def setup() {
        restTemplate = new RestTemplate()
        environment = new StandardEnvironment()

        amazonSimpleEmailService = Mock()
        final BasicAWSCredentials credentials = new BasicAWSCredentials('accessKey', 'secretAccessKey')

        amazonS3 = AmazonS3ClientBuilder.standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials))
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

    }

    def 'can request pdf generation'() {
        given: 'initial data set up'
        amazonS3.createBucket("test-test")
        amazonS3.putObject(new PutObjectRequest("test-test", "TEST-20200120-000/aForm/xx@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))

        and: 'pdf server expectation is set up'
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

        and: 'a process with service task is created'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("pdf")
                .executable()
                .startEvent()
                .camundaFormKey('sampleForm')
                .serviceTask('generatePdf')
                .name('Generate PDF')
                .camundaExpression('${pdfService.requestPdfGeneration(form, businessKey, product, execution)}')
                .camundaInputParameter('form', '${exampleForm.prop(\'form\')}')
                .camundaInputParameter('product', 'test')
                .camundaInputParameter('businessKey', '${exampleForm.prop(\'businessKey\').stringValue()}')
                .endEvent()
                .done()

        repositoryService().createDeployment().addModelInstance("pdf.bpmn", modelInstance).deploy()

        when: 'a request to initiate pdf has been submitted'
        ProcessInstance instance = runtimeService()
        .createProcessInstanceByKey('pdf')
        .businessKey('TEST-20200120-000')
        .setVariables(['exampleForm' : S('''{
            "test" : "test",
            "businessKey": "TEST-20200120-000",
            "form" : {
               "name" : "aForm",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "formVersionId": "formVersionId"
            }
        }''', DataFormats.JSON_DATAFORMAT_NAME)]).execute()


        then: 'pdf request was successful'
        assertThat(instance).isEnded()
        assertThat(instance).hasPassed('generatePdf')

    }

    def 'can raise an incident if s3 fails'() {
        given: 'initial data set up'
        amazonS3.createBucket("test-test")
        amazonS3.putObject(new PutObjectRequest("test-test", "TEST-20200120-000/aForm/xx@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))

        and: 'a process with service task is created'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("pdfFailure")
                .executable()
                .startEvent()
                .camundaFormKey('sampleForm')
                .serviceTask('generatePdf')
                .camundaAsyncBefore()
                .name('Generate PDF')
                .camundaExpression('${pdfService.requestPdfGeneration(form, businessKey, product, execution)}')
                .camundaInputParameter('form', '${exampleForm.prop(\'form\')}')
                .camundaInputParameter('product', 'test')
                .camundaInputParameter('businessKey', '${exampleForm.prop(\'businessKey\').stringValue()}')
                .boundaryEvent()
                .error('FAILED_TO_REQUEST_PDF_GENERATION')
                .userTask()
                .name('Investigate issue')
                .camundaAsyncAfter()
                .endEvent().camundaAsyncBefore().camundaAsyncAfter()
                .done()

        repositoryService().createDeployment().addModelInstance("pdfFailure.bpmn", modelInstance).deploy()

        when: 'pdf fails'
        wireMockRule.stubFor(WireMock.post(WireMock.urlMatching("/pdf")).willReturn(
                aResponse().withStatus(500))
        )
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('pdfFailure')
                .businessKey('TEST-20200120-000')
                .setVariables(['exampleForm' : S('''{
            "test" : "test",
            "businessKey": "TEST-20200120-000",
            "form" : {
               "name" : "aForm",
               "submittedBy": "xx@x.com",
               "submissionDate": "2020-01-28T08:31:55.297Z",
               "formVersionId": "formVersionId"
            }
        }''', DataFormats.JSON_DATAFORMAT_NAME)]).execute()

        then: 'process is created'
        assertThat(instance).isActive()

        when: 'async task is executed'
        execute(job())

        then: 'user task created'
        assertThat(task()).hasName('Investigate issue')
    }

    def 'can send pdf for absolute urls'() {
        given: 'a endpoint that serves the pdf file'
        wireMockStub.stub {
            request {
                method 'GET'
                url '/myfiles/pdf/test.pdf'
            }
            response {
                status: 200
                headers {
                    "Content-Type" "application/octet-stream"
                }
            }
        }

        when: 'a request to send pdf attachments for urls is made'
        pdfService.sendPDFs('from@from.com', ['to@to.com'], 'body', 'subject', [
                'http://localhost:8000/myfiles/pdf/test.pdf'
        ])

        then: 'email service to be called with attachments'
        1 * amazonSimpleEmailService.sendRawEmail(_) >> {
            def result = Mock(SendRawEmailResult)
            result.getMessageId() >> 'messageId'
            return result
        }
    }
}
