package io.digital.patterns.workflow.cases;


import lombok.Data;
import org.camunda.bpm.engine.history.HistoricActivityInstance;

import java.util.*;

@Data
public class CaseDetail {

    private String businessKey;
    private List<ProcessInstanceReference> processInstances = new ArrayList<>();
    private List<Action> actions = new ArrayList<>();
    private CaseMetrics metrics;


    @Data
    public static class Action {
        private Map<String, Object> process;
        private String completionMessage;
        private Map<String, Object> extensionData = new HashMap<>();

        public void addExtensionData(String name, Object value) {
            this.extensionData.put(name, value);
        }
    }

    @Data
    public static class ProcessInstanceReference {
        private String id;
        private String key;
        private String name;
        private String definitionId;
        private List<FormReference> formReferences = new ArrayList<>();
        private Date startDate;
        private Date endDate;
        private List<HistoricActivityInstance> openTasks = new ArrayList<>();
    }

    @Data
    public static class FormReference {
        private String name;
        private String title;
        private String formVersionId;
        private String dataPath;
        private String submissionDate;
        private String submittedBy;
    }

    @Data
    public static class CaseMetrics {
        private Long noOfRunningProcessInstances;
        private Long noOfCompletedProcessInstances;
        private Long overallTimeInSeconds;
        private Long noOfCompletedUserTasks;
        private Long noOfOpenUserTasks;
        private Long averageTimeToCompleteProcessInSeconds;
    }
}
