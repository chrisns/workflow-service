package io.digital.patterns.workflow.encrypt;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.PatchVariablesDto;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.spin.Spin;

import javax.ws.rs.container.ContainerRequestContext;


public class VariableEncryptInterceptor extends EncryptInterceptor {

    @Override
    public boolean shouldFilter(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo().getPath().matches("(process-instance|task)/(.*?)/(variables|localVariables)")
                && (requestContext.getMethod().equalsIgnoreCase("POST") ||
                requestContext.getMethod().equalsIgnoreCase("PUT"));
    }

    @Override
    public ProcessDefinition processDefinition(ContainerRequestContext requestContext) {
        String id = requestContext.getUriInfo().getPathParameters().getFirst("id");
        String processDefinitionId;
        if (requestContext.getUriInfo().getPath().startsWith("process-instance")) {
            ProcessInstance processInstance = super.processEngine.getRuntimeService()
                    .createProcessInstanceQuery().processInstanceId(id).singleResult();
            processDefinitionId = processInstance.getProcessDefinitionId();
        } else {
            Task task = super.processEngine.getTaskService().createTaskQuery()
                    .taskId(id).singleResult();
            processDefinitionId = task.getProcessDefinitionId();

        }

      return  super.processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
    }



    @Override
    protected void doEncrypt(ContainerRequestContext requestContext) throws Exception {
        String body = IOUtils.toString(requestContext.getEntityStream());
        if (requestContext.getMethod().equalsIgnoreCase("POST")) {
            PatchVariablesDto patchVariablesDto = Spin.JSON(body).mapTo(PatchVariablesDto.class);
            if (!patchVariablesDto.getModifications().isEmpty()) {
                patchVariablesDto.getModifications().forEach((k, v) -> encrypt(v));
                requestContext.setEntityStream(
                        IOUtils.toInputStream(Spin.JSON(patchVariablesDto).toString())
                );
            }
        } else {
            VariableValueDto variableValueDto = Spin.JSON(body).mapTo(VariableValueDto.class);
            encrypt(variableValueDto);
            requestContext.setEntityStream(
                    IOUtils.toInputStream(Spin.JSON(variableValueDto).toString())
            );
        }
    }

}
