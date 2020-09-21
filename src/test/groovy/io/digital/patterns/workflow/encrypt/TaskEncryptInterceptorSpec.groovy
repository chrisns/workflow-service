package io.digital.patterns.workflow.encrypt

import io.digital.patterns.workflow.SpringApplicationContext
import io.digitalpatterns.camunda.encryption.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SerializationUtils
import org.camunda.bpm.engine.rest.dto.VariableValueDto
import org.camunda.bpm.engine.rest.dto.task.CompleteTaskDto
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.bpm.model.bpmn.instance.Process
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty
import org.camunda.spin.Spin
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap
import org.junit.Rule
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import javax.crypto.SealedObject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.UriInfo

class TaskEncryptInterceptorSpec extends Specification {

    @Rule
    ProcessEngineRule engineRule = new ProcessEngineRule()

    ApplicationContext context = new AnnotationConfigApplicationContext()

    TaskEncryptInterceptor interceptor
    ProcessDefinitionEncryptionParser parser
    ProcessInstanceSpinVariableEncryptor encryptor
    ProcessInstanceSpinVariableDecryptor decryptor


    def setup() {

        parser = new ProcessDefinitionEncryptionParser(engineRule.repositoryService)
        encryptor = new DefaultProcessInstanceSpinVariableEncryptor(
                'test', 'test'
        )
        decryptor = new DefaultProcessInstanceSpinVariableDecryptor(
                'test', 'test'
        )

        ConfigurableListableBeanFactory beanFactory = ((ConfigurableApplicationContext) context).getBeanFactory()
        beanFactory.registerSingleton("parser", parser)
        beanFactory.registerSingleton("encryptor", encryptor)
        beanFactory.registerSingleton("decryptor", decryptor)

        context.refresh()
        context.start()

        SpringApplicationContext applicationContext = new SpringApplicationContext()
        applicationContext.setApplicationContext(context)

        interceptor = new TaskEncryptInterceptor()
    }

    def cleanup() {
        context.stop()
    }

    @Unroll
    def 'can encrypt on #operation with process name #processName'() {
        given: 'process definition is uploaded'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess(processName)
                .executable()
                .startEvent()
                .userTask()
                .name("Test")
                .endEvent()
                .done()
        CamundaProperties properties = modelInstance.newInstance(CamundaProperties.class)

        CamundaProperty property = modelInstance.newInstance(CamundaProperty.class)
        property.setCamundaName("encryptVariables")
        property.setCamundaValue("true")
        properties.getCamundaProperties().add(property)

        Process process = modelInstance.getModelElementsByType(Process.class).first()
        process.builder().addExtensionElement(properties)

        engineRule.repositoryService
                .createDeployment().addModelInstance("${processName}.bpmn", modelInstance).deploy()
        ProcessInstance instance = engineRule.runtimeService.createProcessInstanceByKey(processName).execute()
        def task = engineRule.taskService.createTaskQuery().processInstanceId(instance.id).list().first()

        and: 'mock context set up'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'POST'
        uriInfo.getPath() >> "task/${task.id}/${operation}"

        MultivaluedStringMap pathMap = new MultivaluedStringMap()
        pathMap.putSingle("id", task.id)
        uriInfo.getPathParameters() >> pathMap

        requestContext.getEntityStream() >> IOUtils.toInputStream(
                '''
                      {
                          "variables": {
                            "aVariable" : {
                                "value" : "aStringValue",
                                "type": "String"
                            },
                            "anotherVariable" : {
                              "value" : "{\\"name\\":\\"john\\",\\"age\\":22,\\"class\\":\\"mca\\"}",
                              "type": "json"
                            }
                          }
                        }
                      '''
        )


        when: 'filter executed'
        interceptor.filter(requestContext)

        then: 'data updated'
        1 * requestContext.setEntityStream(_) >> {
            def input = it[0] as InputStream
            String body = IOUtils.toString(input)
            CompleteTaskDto instanceDto = Spin.JSON(body).mapTo(CompleteTaskDto.class)
            def variable = instanceDto.getVariables().get("anotherVariable") as VariableValueDto
            assert SerializationUtils.deserialize(Base64.decoder.decode(variable.getValue() as byte[])) instanceof SealedObject
        }

        where:
        operation       | processName
        "complete"      | "example1"
        "resolve"       | "example2"
        "submit-form"   | "example3"
    }
}
