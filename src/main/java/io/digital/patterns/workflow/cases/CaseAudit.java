package io.digital.patterns.workflow.cases;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import org.springframework.security.core.Authentication;

import java.util.Date;


@Getter
public class CaseAudit extends ApplicationEvent {

    private Object[] args;
    private Authentication user;
    private Date date;
    private String type;

    public CaseAudit(Object source,
                     Object[] args,
                     Authentication user,
                     String type) {
        super(source);
        this.type = type;
        this.args = args;
        this.user = user;
        this.date = new Date();
    }
}
