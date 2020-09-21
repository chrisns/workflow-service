package io.digital.patterns.workflow.cases;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.camunda.bpm.engine.rest.dto.history.HistoricProcessInstanceDto;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@Relation(collectionRelation = "cases")
public class Case extends RepresentationModel<Case> {

    private String businessKey;
    private List<HistoricProcessInstanceDto> processInstances = new ArrayList<>();

}
