package io.digital.patterns.workflow.cases;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Component
@Aspect
@Slf4j
public class CaseAuditAspect {

    private final ApplicationEventPublisher applicationEventPublisher;

    public CaseAuditAspect(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Around("@annotation(AuditableCaseEvent)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {

        JwtAuthenticationToken user = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();

        CaseAudit caseAudit = new CaseAudit(
                joinPoint.getTarget(),
                joinPoint.getArgs(),
                user,
                joinPoint.getSignature().toShortString());
        StopWatch stopWatch = new StopWatch();
        try {
            stopWatch.start();
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            log.info("Total time taken for '{}' was '{}' seconds",
                    joinPoint.getSignature().toShortString(),
                    stopWatch.getTotalTimeSeconds());

            applicationEventPublisher.publishEvent(caseAudit);
        }
    }
}
