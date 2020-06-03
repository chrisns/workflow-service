package io.digital.patterns.workflow.webhook;

import io.digital.patterns.workflow.exception.ResourceNotFound;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;
import static org.camunda.spin.Spin.S;

@RestController
@Slf4j
@RequestMapping(path = "/webhook/process-instance")
public class WebhookController {

    private final RuntimeService runtimeService;
    private final JacksonJsonDataFormat formatter;

    public WebhookController(RuntimeService runtimeService, JacksonJsonDataFormat formatter) {
        this.runtimeService = runtimeService;
        this.formatter = formatter;
    }

    @PostMapping(value = "/{processInstanceId}/message/{messageKey}", consumes = "application/json")
    public void handleProcessInstanceMessage(@PathVariable
                                                     String processInstanceId,
                                             @PathVariable String messageKey,
                                             @RequestParam() String variableName,
                                             @RequestBody @NotEmpty() String payload) {

        log.info("Received web-hook message notification for '{}' and message key '{}'", processInstanceId, messageKey);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();

        if (processInstance == null) {
            log.error("Process instance '{}' not found", processInstanceId);
            throw new ResourceNotFound(format("Process instance with processInstanceId id '%s' does not exist", processInstanceId));
        }

        correlate(processInstance, messageKey, Collections.singletonMap(variableName, S(payload, formatter)));

    }

    private void correlate(ProcessInstance processInstance, String messageKey, Map<String, Object> variables) {
        MessageCorrelationResult result = runtimeService
                .createMessageCorrelation(messageKey)
                .processInstanceId(processInstance.getProcessInstanceId())
                .setVariables(variables).correlateWithResult();
            log.info("Performed web-hook message correlation for {} with key {} and result {}", processInstance.getId(), messageKey,
                result.getResultType());

    }

}
