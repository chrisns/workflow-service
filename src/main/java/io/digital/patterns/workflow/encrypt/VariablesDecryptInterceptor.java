package io.digital.patterns.workflow.encrypt;

import io.digital.patterns.workflow.SpringApplicationContext;
import io.digitalpatterns.camunda.encryption.ProcessInstanceSpinVariableDecryptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.spi.impl.AbstractProcessEngineAware;
import org.camunda.spin.Spin;

import javax.crypto.SealedObject;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_OBJECT_TYPE_NAME;
import static org.camunda.bpm.engine.variable.type.SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT;

@Provider
@Slf4j
public abstract class VariablesDecryptInterceptor extends
        AbstractProcessEngineAware implements ContainerResponseFilter {


    protected final ProcessInstanceSpinVariableDecryptor processInstanceSpinVariableDecryptor;

    public VariablesDecryptInterceptor() {
        processInstanceSpinVariableDecryptor =
                SpringApplicationContext.getBean(ProcessInstanceSpinVariableDecryptor.class);
    }


    protected Boolean deserializeValues(ContainerRequestContext requestContext) {
        if (requestContext.getUriInfo()
                .getQueryParameters() == null || requestContext.getUriInfo()
                .getQueryParameters().isEmpty()) {
            return Boolean.FALSE;
        }
        return
                requestContext.getUriInfo()
                        .getQueryParameters().getFirst("deserializeValues") != null
                         && Boolean.parseBoolean(requestContext.getUriInfo()
                        .getQueryParameters().getFirst("deserializeValues"));
    }

    public abstract boolean shouldFilter(ContainerRequestContext requestContext);

    public abstract void doFilter(ContainerRequestContext requestContext,
                             ContainerResponseContext responseContext);


    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {

        if (shouldFilter(requestContext)) {
            doFilter(requestContext, responseContext);
        }
    }

    protected void decrypt(VariableValueDto value, Boolean deserializeValues) {
        decrypt(value, processInstanceSpinVariableDecryptor, deserializeValues);
    }

    static void decrypt(VariableValueDto value,
                        ProcessInstanceSpinVariableDecryptor processInstanceSpinVariableDecryptor,
                        Boolean deserializeValues) {
        Object objectTypeName = value.getValueInfo().get(VALUE_INFO_OBJECT_TYPE_NAME);
        if (objectTypeName != null && objectTypeName.toString().equals(SealedObject.class.getName())) {
            SealedObject object;
            if (value.getValue() instanceof String) {
                object = SerializationUtils.deserialize(
                        Base64.getDecoder().decode(value.getValue().toString())
                );
            } else {
                object = (SealedObject)value.getValue();
            }
            Object toReturn;
            Spin decrypted = processInstanceSpinVariableDecryptor.decrypt(object);
            if (deserializeValues) {
                toReturn = decrypted;
                value.setValueInfo(
                        new HashMap<>()
                );
            } else {
               toReturn = decrypted.toString();
                value.setValueInfo(
                        Map.of(
                                VALUE_INFO_SERIALIZATION_DATA_FORMAT, decrypted.getDataFormatName()
                        )
                );
            }
            value.setValue(toReturn);
            value.setType("Json");

        }
    }
}
