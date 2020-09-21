package io.digital.patterns.workflow.cases;

import org.springframework.context.event.EventListener;

public interface CaseAuditEventListener {

    @EventListener
    void handle(CaseAudit caseAudit);
}
