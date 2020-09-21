package io.digital.patterns.workflow.encrypt;

import org.camunda.bpm.engine.rest.dto.runtime.VariableInstanceDto;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import java.util.List;

public class GetVariableInstanceDecryptInterceptor extends VariablesDecryptInterceptor {

    @Override
    public boolean shouldFilter(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo().getPath().equals("variable-instance");
    }

    @Override
    public void doFilter(ContainerRequestContext requestContext,
                         ContainerResponseContext responseContext) {

        Object entity = responseContext.getEntity();
        Boolean deserializeValues = deserializeValues(requestContext);
        if (entity instanceof List) {
            List<VariableInstanceDto> variables = (List) entity;
            variables.forEach(v -> decrypt(v, deserializeValues));
            return;
        }
        if (entity instanceof VariableInstanceDto) {
            decrypt((VariableInstanceDto) entity, deserializeValues);
        }
    }
}
