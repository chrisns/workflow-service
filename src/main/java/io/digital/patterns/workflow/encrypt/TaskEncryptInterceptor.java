package io.digital.patterns.workflow.encrypt;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.rest.dto.task.CompleteTaskDto;
import org.camunda.bpm.engine.task.Task;
import org.camunda.spin.Spin;

import javax.ws.rs.container.ContainerRequestContext;

public class TaskEncryptInterceptor extends EncryptInterceptor {
    @Override
    public boolean shouldFilter(ContainerRequestContext requestContext) {
        return requestContext.getUriInfo()
                .getPath().matches("task/(.*?)/(complete|submit-form|resolve)")
                && requestContext.getMethod().equalsIgnoreCase("POST");
    }

    @Override
    public ProcessDefinition processDefinition(ContainerRequestContext requestContext) {
        String id = requestContext.getUriInfo().getPathParameters().getFirst("id");
        Task task = super.processEngine.getTaskService().createTaskQuery()
                .taskId(id).singleResult();
        return super.processEngine.getRepositoryService()
                .createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId()).singleResult();

    }

    @Override
    protected void doEncrypt(ContainerRequestContext requestContext) throws Exception {
        String body = IOUtils.toString(requestContext.getEntityStream());
        CompleteTaskDto instanceDto = Spin.JSON(body).mapTo(CompleteTaskDto.class);
        instanceDto.getVariables().forEach((k, v) -> encrypt(v));
        requestContext.setEntityStream(
                IOUtils.toInputStream(Spin.JSON(instanceDto).toString())
        );

    }

}
