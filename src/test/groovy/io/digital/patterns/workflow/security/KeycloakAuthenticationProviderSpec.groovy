package io.digital.patterns.workflow.security

import io.digital.patterns.workflow.security.cockpit.KeycloakAuthenticationProvider
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.identity.GroupQuery
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest

class KeycloakAuthenticationProviderSpec extends Specification {

    KeycloakAuthenticationProvider underTest

    ProcessEngine processEngine = Mock()
    IdentityService identityService = Mock()

    def setup() {
        underTest = new KeycloakAuthenticationProvider()
    }

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def 'can extract authenticated user'() {
        given: 'a request'
        HttpServletRequest request = Mock()
        OAuth2AuthenticationToken token= Mock()
        GroupQuery query =Mock()
        processEngine.getIdentityService() >> identityService
        identityService.createGroupQuery() >> query
        query.groupMember(_) >> query
        query.list() >> []

        token.getDetails() >> new Object()
        token.getName() >> 'Name'
        SecurityContextHolder.getContext().setAuthentication(token)

        when: 'attempt to extract user is made'
        def result = underTest.extractAuthenticatedUser(request, processEngine)

        then: 'user should be authenticated'
        result
        result.authenticated
    }

    def 'returns unauthenticated'() {
        given: 'a request'
        HttpServletRequest request = Mock()
        OAuth2AuthenticationToken token= Mock()
        SecurityContextHolder.getContext().setAuthentication(token)

        when: 'attempt to extract user is made'
        def result = underTest.extractAuthenticatedUser(request, processEngine)

        then: 'user should be unauthenticated'
        result
        !result.authenticated
    }

    def 'returns unauthenticated if security context is null'() {
        given: 'a request'
        HttpServletRequest request = Mock()

        SecurityContextHolder.getContext().setAuthentication(null)

        when: 'attempt to extract user is made'
        def result = underTest.extractAuthenticatedUser(request, processEngine)

        then: 'user should be unauthenticated'
        result
        !result.authenticated
    }
}
