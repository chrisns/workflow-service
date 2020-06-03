package io.digital.patterns.workflow.webhook

import com.fasterxml.jackson.databind.ObjectMapper
import io.digital.patterns.workflow.security.cockpit.KeycloakLogoutHandler
import org.camunda.bpm.engine.HistoryService
import org.camunda.bpm.engine.RepositoryService
import org.camunda.bpm.engine.RuntimeService
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.spin.Spin
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat
import org.camunda.spin.json.SpinJsonNode
import org.junit.ClassRule
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.context.WebApplicationContext
import spock.lang.Shared
import spock.lang.Specification
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity

@ContextConfiguration
@WebMvcTest(controllers = [WebhookController])
@ActiveProfiles("test")
class WebHookControllerSpec extends Specification {


    @TestConfiguration
    static class MockConfig {
        @Bean
        JacksonJsonDataFormat formatter() {
            return new JacksonJsonDataFormat("application/json", new ObjectMapper())
        }

    }

    @Autowired
    JacksonJsonDataFormat formatter

    @Autowired
    WebApplicationContext context

    MockMvc mvc

    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    @SpringBean
    private RuntimeService runtimeService = engineRule.runtimeService

    @SpringBean
    private RepositoryService repositoryService = engineRule.repositoryService

    @SpringBean
    private HistoryService historyService = engineRule.historyService

    @SpringBean
    private KeycloakLogoutHandler keycloakLogoutHandler = Mock()


    @SpringBean
    ResourceServerProperties resourceServerProperties = Mock()

    def setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build()
    }

    @WithMockUser(username = 'test')
    def 'can message on web-hook'() {

        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("messageWorkflow")
                .startEvent()
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'Hello Message'")
                .intermediateCatchEvent()
                .message("messageWaiting")
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'After Message'")
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("messageWorkflow.bpmn", modelInstance).deploy()
        def processInstance = runtimeService.startProcessInstanceByKey('messageWorkflow', businessKey)


        when: 'A message web-hook post has been peformed'
        def eventPayload = '''{"event": "pdf-generated",
                          "data": {
                             "location": "http://s3/location/myfile.pdf"
                          }
                        }'''

        def result = mvc.perform(MockMvcRequestBuilders
                .post("/webhook/process-instance/${processInstance.id}/message/messageWaiting?variableName=testVariableMessage")
                .content(eventPayload)
                .contentType(MediaType.APPLICATION_JSON).with(csrf()))

        then: 'Response should be successful'
        result.andExpect(MockMvcResultMatchers.status().is2xxSuccessful())

        and: 'process instance should be completed'
        def history = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult()
        history.state == "COMPLETED"
        history.endTime != null

        and: 'completed process instance should have the event payload as variable'
        def variableInstance = historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstance.id)
                .variableName("testVariableMessage").singleResult()

        SpinJsonNode payloadAsSpin = Spin.S(eventPayload, formatter)
        SpinJsonNode variableAsSpin = Spin.S(variableInstance.value, formatter)

        variableAsSpin.prop("event").stringValue() == payloadAsSpin.prop('event').stringValue()
        variableAsSpin
                .prop("data")
                .prop("location")
                .stringValue() == payloadAsSpin
                .prop("data")
                .prop("location").stringValue()

    }

    @WithMockUser(username = 'test')
    def 'throws bad request if payload is empty'() {
        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("simple")
                .startEvent()
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("simple.bpmn", modelInstance).deploy()
        def processInstance = runtimeService.startProcessInstanceByKey('simple', businessKey)

        when: 'Web hook post with no body'
        def result = mvc.perform(MockMvcRequestBuilders.post("/webhook/process-instance/${processInstance.id}/message/messageWaiting?variableName=testVariableMessage")
                .content('')
                .contentType(MediaType.APPLICATION_JSON))

        then: 'Response should be a bad request'
        result.andExpect(MockMvcResultMatchers.status().is4xxClientError())

    }

    @WithMockUser(username = 'test')
    def 'throws not found if business key does not relate to a running process instance'() {
        given: 'A process definition with a message is created'
        def businessKey = UUID.randomUUID().toString()
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("messageWorkflow")
                .startEvent()
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'Hello Message'")
                .intermediateCatchEvent()
                .message("messageWaiting")
                .scriptTask()
                .scriptFormat("Groovy")
                .scriptText("println 'After Message'")
                .endEvent()
                .done()


        and: 'the process definition has been uploaded to the camunda engine'
        repositoryService.createDeployment().addModelInstance("messageWorkflow.bpmn", modelInstance).deploy()
        runtimeService.startProcessInstanceByKey('messageWorkflow', businessKey)

        when: 'A message web-hook post has been peformed'
        def eventPayload = '''{"event": "pdf-generated",
                          "data": {
                             "location": "http://s3/location/myfile.pdf"
                          }
                        }'''
        def result = mvc.perform(MockMvcRequestBuilders.post("/webhook/process-instance/invalidBusinessKey/message/messageWaiting?variableName=testVariableMessage")
                .content(eventPayload)
                .contentType(MediaType.APPLICATION_JSON).with(csrf()))

        then: 'Response should be 404'
        result.andExpect(MockMvcResultMatchers.status().isNotFound())
    }


}
