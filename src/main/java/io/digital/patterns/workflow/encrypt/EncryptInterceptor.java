package io.digital.patterns.workflow.encrypt;

import io.digital.patterns.workflow.SpringApplicationContext;
import io.digitalpatterns.camunda.encryption.ProcessDefinitionEncryptionParser;
import io.digitalpatterns.camunda.encryption.ProcessInstanceSpinVariableDecryptor;
import io.digitalpatterns.camunda.encryption.ProcessInstanceSpinVariableEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.spi.impl.AbstractProcessEngineAware;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.ValueType;

import javax.crypto.SealedObject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Map;

import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME;
import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT;

@Provider
@Slf4j
public abstract class EncryptInterceptor extends
        AbstractProcessEngineAware implements ContainerRequestFilter {

    protected final ProcessDefinitionEncryptionParser processDefinitionEncryptionParser;
    protected final ProcessInstanceSpinVariableEncryptor processInstanceSpinVariableEncryptor;
    protected final ProcessInstanceSpinVariableDecryptor processInstanceSpinVariableDecryptor;

    public EncryptInterceptor() {
        processDefinitionEncryptionParser =
                SpringApplicationContext.getBean(ProcessDefinitionEncryptionParser.class);
        processInstanceSpinVariableEncryptor =
                SpringApplicationContext.getBean(ProcessInstanceSpinVariableEncryptor.class);
        processInstanceSpinVariableDecryptor =
                SpringApplicationContext.getBean(ProcessInstanceSpinVariableDecryptor.class);
    }

    public abstract boolean shouldFilter(ContainerRequestContext requestContext);

    public abstract ProcessDefinition processDefinition(ContainerRequestContext requestContext);

    public boolean shouldEncrypt(ContainerRequestContext requestContext) {
        return processDefinitionEncryptionParser.shouldEncrypt(
                processDefinition(requestContext), "encryptVariables"
        );
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (shouldFilter(requestContext) && shouldEncrypt(requestContext)) {
            try {
                doEncrypt(requestContext);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void doEncrypt(ContainerRequestContext requestContext) throws Exception;

    protected void encrypt(VariableValueDto v) {
        if (v.getType().equalsIgnoreCase("json")) {
            SealedObject sealedObject = processInstanceSpinVariableEncryptor.encrypt(
                    v.getValue()
            );
            v.setValue(SerializationUtils.serialize(sealedObject));
            v.setValueInfo(
                    Map.of(
                            VALUE_INFO_OBJECT_TYPE_NAME, SealedObject.class.getName(),
                            VALUE_INFO_SERIALIZATION_DATA_FORMAT,
                            Variables.SerializationDataFormats.JAVA.getName()
                    )
            );
            v.setType(ValueType.OBJECT.getName());
        }
    }
}
