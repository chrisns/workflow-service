package io.digital.patterns.workflow.encrypt;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.runtime.ProcessInstanceWithVariablesDto;
import org.camunda.bpm.engine.rest.dto.runtime.StartProcessInstanceDto;
import org.camunda.spin.Spin;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;

import static io.digital.patterns.workflow.encrypt.VariablesDecryptInterceptor.decrypt;

@Slf4j
public class StartProcessEncryptInterceptor extends EncryptInterceptor implements ContainerResponseFilter {


    @Override
    public boolean shouldFilter(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo().getPath().startsWith("process-definition/")
                && requestContext.getMethod().equalsIgnoreCase("POST");
    }

    @Override
    public ProcessDefinition processDefinition(ContainerRequestContext requestContext) {
        MultivaluedMap<String, String> pathParameters = requestContext.getUriInfo().getPathParameters();
        String id = pathParameters.getFirst("id");
        String key = pathParameters.getFirst("key");

        ProcessDefinition processDefinition;
        if (key == null) {
            processDefinition = super.processEngine.getRepositoryService()
                    .createProcessDefinitionQuery()
                    .latestVersion()
                    .active()
                    .processDefinitionId(id).singleResult();
        } else {
            processDefinition = super.processEngine.getRepositoryService()
                    .createProcessDefinitionQuery()
                    .latestVersion()
                    .active()
                    .processDefinitionKey(key).singleResult();
        }
        return processDefinition;
    }

    @Override
    public void doEncrypt(ContainerRequestContext requestContext) throws Exception {
        String body = IOUtils.toString(requestContext.getEntityStream());
        StartProcessInstanceDto instanceDto = Spin.JSON(body).mapTo(StartProcessInstanceDto.class);
        instanceDto.getVariables().forEach((k, v) -> {
            encrypt(v);
        });
        requestContext.setEntityStream(
                IOUtils.toInputStream(Spin.JSON(instanceDto).toString())
        );
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        if (shouldFilter(requestContext)) {
            Object response = responseContext.getEntity();
            if (response instanceof ProcessInstanceWithVariablesDto) {
                ((ProcessInstanceWithVariablesDto)response)
                        .getVariables()
                        .forEach((k,value) ->
                                decrypt(value, processInstanceSpinVariableDecryptor, false));
            }

        }
    }

}
