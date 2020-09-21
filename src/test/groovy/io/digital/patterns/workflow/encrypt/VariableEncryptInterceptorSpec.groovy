package io.digital.patterns.workflow.encrypt

import io.digital.patterns.workflow.SpringApplicationContext
import io.digitalpatterns.camunda.encryption.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SerializationUtils
import org.camunda.bpm.engine.rest.dto.PatchVariablesDto
import org.camunda.bpm.engine.rest.dto.VariableValueDto
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

import javax.crypto.SealedObject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.UriInfo

class VariableEncryptInterceptorSpec extends Specification {

    @Rule
    ProcessEngineRule engineRule = new ProcessEngineRule()

    ApplicationContext context = new AnnotationConfigApplicationContext()

    VariableEncryptInterceptor interceptor
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

        interceptor = new VariableEncryptInterceptor()
    }

    def cleanup() {
        context.stop()
    }

    def 'can encrypt spin data on variable'() {

        given: 'process definition is uploaded'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example")
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
                .createDeployment().addModelInstance("example.bpmn", modelInstance).deploy()
        ProcessInstance instance = engineRule.runtimeService.createProcessInstanceByKey("example").execute()

        and: 'mock context'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'PUT'
        uriInfo.getPath() >> "process-instance/${instance.id}/variables"

        MultivaluedStringMap pathMap = new MultivaluedStringMap()
        pathMap.putSingle("id", instance.getId())
        uriInfo.getPathParameters() >> pathMap

        requestContext.getEntityStream() >> IOUtils.toInputStream(
                '''
                      {
                           "value" : "{\\"name\\":\\"john\\",\\"age\\":22,\\"class\\":\\"mca\\"}",
                           "type": "json"
                        }
                      '''
        )

        when: 'filter executed'
        interceptor.filter(requestContext)

        then: 'data updated'
        1 * requestContext.setEntityStream(_) >> {
            def input = it[0] as InputStream
            String body = IOUtils.toString(input)
            VariableValueDto instanceDto = Spin.JSON(body).mapTo(VariableValueDto.class)
            assert SerializationUtils.deserialize(Base64.decoder.decode(instanceDto.getValue() as byte[])) instanceof SealedObject
        }
    }

    def 'can encrypt spin data on patch variables'() {

        given: 'process definition is uploaded'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example2")
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
                .createDeployment().addModelInstance("example2.bpmn", modelInstance).deploy()
        ProcessInstance instance = engineRule.runtimeService.createProcessInstanceByKey("example2").execute()

        and: 'mock context'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'POST'
        uriInfo.getPath() >> "process-instance/${instance.id}/variables"

        MultivaluedStringMap pathMap = new MultivaluedStringMap()
        pathMap.putSingle("id", instance.getId())
        uriInfo.getPathParameters() >> pathMap

        requestContext.getEntityStream() >> IOUtils.toInputStream(
                '''
                      {
                           "modifications": {"aVariable": {
                                "value" : "{\\"name\\":\\"john\\",\\"age\\":22,\\"class\\":\\"mca\\"}" ,
                                "type" : "json"
                            }}
                        }
                      '''
        )



        when: 'filter executed'
        interceptor.filter(requestContext)

        then: 'data updated'
        1 * requestContext.setEntityStream(_) >> {
            def input = it[0] as InputStream
            String body = IOUtils.toString(input)
            PatchVariablesDto instanceDto = Spin.JSON(body).mapTo(PatchVariablesDto.class)
            def variableValueDto = instanceDto.getModifications().get("aVariable")
            assert SerializationUtils.deserialize(Base64.decoder.decode(variableValueDto.getValue() as byte[])) instanceof SealedObject
        }
    }

    def 'can encrypt spin data on patch variables on task'() {

        given: 'process definition is uploaded'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example3")
                .executable()
                .startEvent()
                .userTask()
                .name("Test")
                .endEvent()
                .done()
        CamundaProperties properties = modelInstance.newInstance(CamundaProperties.class);

        CamundaProperty property = modelInstance.newInstance(CamundaProperty.class);
        property.setCamundaName("encryptVariables")
        property.setCamundaValue("true")
        properties.getCamundaProperties().add(property)

        Process process = modelInstance.getModelElementsByType(Process.class).first()
        process.builder().addExtensionElement(properties)

        engineRule.repositoryService
                .createDeployment().addModelInstance("example3.bpmn", modelInstance).deploy()
        ProcessInstance instance = engineRule.runtimeService.createProcessInstanceByKey("example3").execute()
        def task = engineRule.taskService.createTaskQuery().processInstanceId(instance.id).list().first()

        and: 'mock context'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'POST'
        uriInfo.getPath() >> "task/${instance.id}/variables"

        MultivaluedStringMap pathMap = new MultivaluedStringMap()
        pathMap.putSingle("id", task.getId())
        uriInfo.getPathParameters() >> pathMap

        requestContext.getEntityStream() >> IOUtils.toInputStream(
                '''
                      {
                           "modifications": {"aVariable": {
                                "value" : "{\\"name\\":\\"john\\",\\"age\\":22,\\"class\\":\\"mca\\"}" ,
                                "type" : "json"
                            }}
                        }
                      '''
        )



        when: 'filter executed'
        interceptor.filter(requestContext)

        then: 'data updated'
        1 * requestContext.setEntityStream(_) >> {
            def input = it[0] as InputStream
            String body = IOUtils.toString(input)
            PatchVariablesDto instanceDto = Spin.JSON(body).mapTo(PatchVariablesDto.class)
            def variableValueDto = instanceDto.getModifications().get("aVariable")
            assert SerializationUtils.deserialize(Base64.decoder.decode(variableValueDto.getValue() as byte[])) instanceof SealedObject
        }
    }


}
