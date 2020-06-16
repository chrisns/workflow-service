package io.digital.patterns.workflow.notification

import com.amazonaws.services.sns.AmazonSNSClient
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.engine.test.mock.Mocks
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.spin.DataFormats
import org.junit.ClassRule
import org.testcontainers.containers.localstack.LocalStackContainer
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.execute
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.job
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.repositoryService
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.runtimeService
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.task
import static org.camunda.spin.Spin.S

class AmazonSMSServiceSpec extends Specification {

    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    @Shared
    static LocalStackContainer localstack =
            new LocalStackContainer().withServices(LocalStackContainer.Service.SNS)


    def setupSpec() {
        localstack.start()
    }

    def cleanupSpec() {
        localstack.stop()
    }

    AmazonSMSService amazonSMSService
    def setup() {
        def client = (AmazonSNSClient) AmazonSNSClient.builder()
                .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.SNS))
                .withCredentials(localstack.getDefaultCredentialsProvider()).build()

        amazonSMSService = new AmazonSMSService(client)

        Mocks.register('amazonSMSService', amazonSMSService)
    }

    def 'can send sms'() {
        given: 'a process that wants to send a SMS'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("sms")
                .executable()
                .startEvent()
                .camundaFormKey('sampleForm')
                .serviceTask('sendSMS')
                .name('sendSMS')
                .camundaExpression('${amazonSMSService.sendSMS(phoneNumber, message)}')
                .camundaInputParameter('phoneNumber', '08444343242343')
                .camundaInputParameter('message', 'Hello')
                .endEvent()
                .done()

        and: 'it is deployed'
        repositoryService().createDeployment().addModelInstance("sms.bpmn", modelInstance).deploy()

        when: 'process in started'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('sms')
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

        then: 'call to SMS should be executed'
        assertThat(instance).isEnded()
        assertThat(instance).hasPassed("sendSMS")

    }

    def 'user task created if sms fails'() {
        def conditions = new PollingConditions(timeout: 10, initialDelay: 1.5, factor: 1.25)
        given: 'a process that wants to send a SMS'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("sms2")
                .executable()
                .startEvent()
                .camundaFormKey('sampleForm')
                .serviceTask('sendSMS')
                .name('sendSMS')
                .camundaExpression('${amazonSMSService.sendSMS(phoneNumber, message)}')
                .camundaInputParameter('phoneNumber', null)
                .camundaInputParameter('message', null)
                .camundaAsyncAfter()
                .camundaAsyncBefore()
                .boundaryEvent()
                    .error("SMS_FAILURE")
                    .userTask().name("Handle exception")
                .endEvent()
                .done()

        and: 'it is deployed'
        repositoryService().createDeployment().addModelInstance("sms2.bpmn", modelInstance).deploy()


        when: 'process in started'
        ProcessInstance instance = runtimeService()
                .createProcessInstanceByKey('sms2')
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


        then: 'process is active'
        assertThat(instance).isActive()

        when: 'async task is executed'
        execute(job())

        then: 'user task created'
        assertThat(task()).hasName("Handle exception")
    }
}
