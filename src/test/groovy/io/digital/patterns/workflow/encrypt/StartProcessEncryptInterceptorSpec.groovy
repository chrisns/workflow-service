package io.digital.patterns.workflow.encrypt

import io.digital.patterns.workflow.SpringApplicationContext
import io.digitalpatterns.camunda.encryption.*
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.SerializationUtils
import org.camunda.bpm.engine.rest.dto.VariableValueDto
import org.camunda.bpm.engine.rest.dto.repository.DeploymentDto
import org.camunda.bpm.engine.rest.dto.runtime.StartProcessInstanceDto
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

class StartProcessEncryptInterceptorSpec extends Specification {

    @Rule
    ProcessEngineRule engineRule = new ProcessEngineRule()

    ApplicationContext context = new AnnotationConfigApplicationContext()

    StartProcessEncryptInterceptor interceptor
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

        interceptor = new StartProcessEncryptInterceptor()
    }

    def cleanup() {
        context.stop()
    }


    def 'can encrypt spin data'() {
        given: 'mock context'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'POST'
        uriInfo.getPath() >> 'process-definition/key/example/start'

        MultivaluedStringMap pathMap = new MultivaluedStringMap()
        pathMap.putSingle("key", "example")
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
                          },
                         "businessKey" : "myBusinessKey"
                        }
                      '''
        )



        and: 'process definition is uploaded'
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example")
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

        def deploy = engineRule.repositoryService
                .createDeployment().addModelInstance("example.bpmn", modelInstance).deploy()
        DeploymentDto.fromDeployment(deploy)

        when: 'filter executed'
        interceptor.filter(requestContext)

        then: 'data updated'
        1 * requestContext.setEntityStream(_) >> {
            def input = it[0] as InputStream
            String body = IOUtils.toString(input)
            StartProcessInstanceDto instanceDto = Spin.JSON(body).mapTo(StartProcessInstanceDto.class)
            def variable = instanceDto.getVariables().get("anotherVariable") as VariableValueDto
            assert SerializationUtils.deserialize(Base64.decoder.decode(variable.getValue() as byte[])) instanceof SealedObject
        }
    }

}
