package io.digital.patterns.workflow.cases

import io.digital.patterns.workflow.security.rest.KeycloakAuthenticationConverter
import org.camunda.bpm.engine.test.Deployment
import org.camunda.bpm.engine.test.ProcessEngineRule
import org.junit.ClassRule
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import spock.lang.Shared
import spock.lang.Specification

@Deployment(resources = ['./test-actions.dmn', './generate-pdf.bpmn', './userTask.bpmn'])
class CaseActionServiceSpec extends Specification {


    @ClassRule
    @Shared
    ProcessEngineRule engineRule = new ProcessEngineRule()

    def caseActionService = new CaseActionService(
            engineRule.repositoryService,
            engineRule.formService,
            engineRule.decisionService
    )

    def 'can get actions'() {
        given: 'a platform user'
        Jwt jwt = Jwt.withTokenValue('token')
                .header("alg", "none")
                .claim(JwtClaimNames.SUB, "user")
                .claim("email", "email")
                .claim("realm_access", [
                        'roles': ['special-role']
                ])
                .claim("scope", "read").build()
        def platformUser = new PlatformUser((JwtAuthenticationToken) new KeycloakAuthenticationConverter().convert(jwt))
        def caseDetails = new CaseDetail()
        def instance = new CaseDetail.ProcessInstanceReference()
        instance.key = 'test-process'
        caseDetails.processInstances = [instance]

        when: 'action invoked'
        def result = caseActionService.getAvailableActions(caseDetails, platformUser)

        then: 'there should be 2 actions'
        result.size() == 2
    }


    def 'returns default action if decision returns no result'() {
        given: 'a user and case'
        Jwt jwt = Jwt.withTokenValue('token')
                .header("alg", "none")
                .claim(JwtClaimNames.SUB, "user")
                .claim("email", "email")
                .claim("realm_access", [
                        'roles': ['normal-role']
                ])
                .claim("scope", "read").build()
        def platformUser = new PlatformUser((JwtAuthenticationToken) new KeycloakAuthenticationConverter().convert(jwt))

        def caseDetails = new CaseDetail()
        def instance = new CaseDetail.ProcessInstanceReference()
        instance.key = 'test-process'
        caseDetails.processInstances = [instance]

        when: 'action invoked'
        def result = caseActionService.getAvailableActions(caseDetails, platformUser)

        then: 'there should be 1 actions'
        result.size() == 1
        result.first().process['process-definition'].key == 'generate-case-pdf'
    }

    def 'returns default action if service fails to evaluate rules'() {
        given: 'a platform user'
        Jwt jwt = Jwt.withTokenValue('token')
                .header("alg", "none")
                .claim(JwtClaimNames.SUB, "user")
                .claim("email", "email")
                .claim("realm_access", [
                        'roles': ['special-role']
                ])
                .claim("scope", "read").build()
        def platformUser = new PlatformUser((JwtAuthenticationToken) new KeycloakAuthenticationConverter().convert(jwt))


        and: 'case process instances are null'
        def caseDetails = new CaseDetail()
        caseDetails.processInstances = null

        when: 'action invoked'
        def result = caseActionService.getAvailableActions(caseDetails, platformUser)

        then: 'there should be 1 actions'
        result.size() == 1
        result.first().process['process-definition'].key == 'generate-case-pdf'
    }

}
