package io.digital.patterns.workflow.encrypt;

import org.camunda.bpm.engine.rest.dto.VariableValueDto;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import java.util.Map;

public class GetVariablesDecryptInterceptor extends VariablesDecryptInterceptor {

    @Override
    public boolean shouldFilter(ContainerRequestContext requestContext) {
        return (requestContext.getUriInfo().getPath().contains("variables")
                || requestContext.getUriInfo().getPath().contains("localVariables"))
                && requestContext.getMethod().equalsIgnoreCase("GET");
    }

    @Override
    public void doFilter(ContainerRequestContext requestContext,
                         ContainerResponseContext responseContext) {
        Object response = responseContext.getEntity();
        Boolean deserializeValues = deserializeValues(requestContext);
        if (response instanceof Map) {
            Map<String, VariableValueDto> variablesDtoMap =
                    (Map<String, VariableValueDto>) response;
            variablesDtoMap.forEach((key, value) -> decrypt(value, deserializeValues));
            return;
        }
        if (response instanceof VariableValueDto) {
            decrypt((VariableValueDto) response,deserializeValues);
        }
    }


}
