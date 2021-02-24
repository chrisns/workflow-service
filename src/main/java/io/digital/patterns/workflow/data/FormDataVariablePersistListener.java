package io.digital.patterns.workflow.data;

import io.digital.patterns.workflow.aws.AwsProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.bpm.engine.impl.history.event.HistoricVariableUpdateEventEntity;
import org.camunda.bpm.engine.impl.history.event.HistoryEvent;
import org.camunda.bpm.engine.impl.history.event.HistoryEventTypes;
import org.camunda.bpm.engine.impl.history.handler.HistoryEventHandler;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization;

@Slf4j
@AllArgsConstructor
public class FormDataVariablePersistListener implements HistoryEventHandler {

    protected static final List<String> VARIABLE_EVENT_TYPES = new ArrayList<>();
    private final String bucketNamePrefix;
    private final AwsProperties awsProperties;
    private final FormDataService formDataService;
    private final RepositoryService repositoryService;
    private final HistoryService historyService;
    private final FormObjectSplitter formObjectSplitter;
    private final RetryTemplate retryTemplate;

    static {
        VARIABLE_EVENT_TYPES.add(HistoryEventTypes.VARIABLE_INSTANCE_CREATE.getEventName());
        VARIABLE_EVENT_TYPES.add(HistoryEventTypes.VARIABLE_INSTANCE_UPDATE.getEventName());
    }

    @Override
    public void handleEvent(HistoryEvent historyEvent) {
        if (historyEvent instanceof HistoricVariableUpdateEventEntity &&
                VARIABLE_EVENT_TYPES.contains(historyEvent.getEventType())) {

            HistoricVariableUpdateEventEntity variable = (HistoricVariableUpdateEventEntity) historyEvent;
            String processDefinitionId = variable.getProcessDefinitionId();
            BpmnModelInstance model = Bpmn.
                    readModelFromStream(this.repositoryService.getProcessModel(processDefinitionId));
            registerSynchronization(new VariableS3TransactionSynchronisation(historyEvent, model));
        }
    }

    @AllArgsConstructor
    public class VariableS3TransactionSynchronisation extends TransactionSynchronizationAdapter {
        private HistoryEvent historyEvent;
        private BpmnModelInstance model;

        private final Logger log = LoggerFactory.getLogger(VariableS3TransactionSynchronisation.class);

        @Override
        public void afterCompletion(int status) {
            super.afterCompletion(status);
            if (status == STATUS_COMMITTED) {

                HistoricVariableUpdateEventEntity variable = (HistoricVariableUpdateEventEntity) historyEvent;
                String asJson = null;
                if (variable.getSerializerName().equalsIgnoreCase("json")) {
                    asJson = new String(variable.getByteValue(), StandardCharsets.UTF_8);
                }
                if (asJson != null) {
                    HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
                            .processInstanceId(variable.getProcessInstanceId()).singleResult();
                    List<String> forms = formObjectSplitter.split(asJson);
                    if (!forms.isEmpty()) {
                        String product = resolveProductName(bucketNamePrefix, variable, model);
                        forms.forEach(form ->
                                {
                                    log.info("Initiating save of form data");
                                    try {
                                        String key = retryTemplate.execute((RetryCallback<String, Throwable>)
                                                context -> formDataService.save(form, processInstance,
                                                        variable.getExecutionId(), product));
                                        log.info("Saved form data '{}'", key);
                                    } catch (Throwable th) {
                                        log.error("Failed to save data to S3/ES due to {}", th.getMessage());
                                    }
                                }
                        );
                    }
                }
            }
        }
    }

    private String resolveProductName(String bucketNamePrefix,
                                      HistoricVariableUpdateEventEntity variable,
                                      BpmnModelInstance model) {

        String productName = getAttribute(model, "product", s -> s, null );
        if (productName == null) {
            return awsProperties.getCaseBucketName();
        }
        return bucketNamePrefix + "-" + productName;
    }

    private <TO> TO getAttribute(BpmnModelInstance model, String key,
                                 Function<String, TO> converter,
                                 TO defaultValue) {
        return model.getModelElementsByType(CamundaProperty.class)
                .stream()
                .filter(p -> p.getCamundaName().equalsIgnoreCase(key))
                .findAny()
                .map(CamundaProperty::getCamundaValue)
                .map(converter)
                .orElse(defaultValue);
    }

    @Override
    public void handleEvents(List<HistoryEvent> historyEvents) {
        historyEvents.forEach(this::handleEvent);
    }
}