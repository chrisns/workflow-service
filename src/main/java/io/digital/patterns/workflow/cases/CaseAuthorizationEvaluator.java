package io.digital.patterns.workflow.cases;

import lombok.extern.slf4j.Slf4j;
import org.camunda.spin.json.SpinJsonNode;
import org.springframework.stereotype.Component;

/**
 * Used to determine if case or submission data is viewable by user
 */
@Component
@Slf4j
public class CaseAuthorizationEvaluator {

    /**
     * Determine if user is allowed to view case
     *
     * @param caseDetail
     * @param platformUser
     * @return true/false
     */
    public boolean isAuthorized(CaseDetail caseDetail, PlatformUser platformUser) {
        return true;
    }

    /**
     * Determine if the submission data is viewable by the user
     *
     * @param data
     * @param platformUser
     * @return true/false
     */
    public boolean isAuthorized(SpinJsonNode data, PlatformUser platformUser) {
        return true;
    }
}
