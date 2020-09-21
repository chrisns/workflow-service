package io.digital.patterns.workflow.encrypt

import io.digital.patterns.workflow.SpringApplicationContext
import io.digitalpatterns.camunda.encryption.*
import org.camunda.bpm.engine.rest.dto.runtime.VariableInstanceDto
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.camunda.bpm.engine.variable.Variables
import org.camunda.bpm.engine.variable.type.ValueType
import org.camunda.spin.Spin
import org.junit.Rule
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

import javax.crypto.SealedObject
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerResponseContext
import javax.ws.rs.core.UriInfo

import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME
import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT

class GetVariableInstanceDecryptInterceptorSpec extends Specification {

    @Rule
    ProcessEngineRule engineRule = new ProcessEngineRule()

    ApplicationContext context = new AnnotationConfigApplicationContext()

    GetVariableInstanceDecryptInterceptor interceptor
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

        interceptor = new GetVariableInstanceDecryptInterceptor()
    }

    def cleanup() {
        context.stop()
    }

    def 'can decrypt'() {
        given: 'mock context'
        ContainerRequestContext requestContext = Mock()
        UriInfo uriInfo = Mock()
        requestContext.getUriInfo() >> uriInfo
        requestContext.getMethod() >> 'GET'
        uriInfo.getPath() >> 'variable-instance'


        and: 'sealed variable'
        def json = Spin.S('''{
                               "name" : "test"
                               }''')
        def encrypt = encryptor.encrypt(json)
        def dto = new VariableInstanceDto()
        dto.setName("test")
        dto.setTaskId("test")
        dto.setValue(encrypt)
        dto.setValueInfo(
                Map.of(
                        VALUE_INFO_OBJECT_TYPE_NAME, SealedObject.class.getName(),
                        VALUE_INFO_SERIALIZATION_DATA_FORMAT,
                        Variables.SerializationDataFormats.JAVA.getName()
                )
        );
        dto.setType(ValueType.OBJECT.getName())
        def variables = List.of(dto)


        and:
        ContainerResponseContext responseContext = Mock()
        responseContext.getEntity() >> variables

        when: 'filter executed'
        interceptor.filter(requestContext, responseContext)

        then: 'data is decrypted'
        def instanceDto = variables.first()
        def value = instanceDto.getValue()
        !(value instanceof SealedObject)
        instanceDto.name == "test"
        instanceDto.taskId == "test"
    }

}
