package io.digital.patterns.workflow.health

import io.digital.patterns.workflow.security.cockpit.KeycloakLogoutHandler
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.ProcessEngineConfiguration
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity

@WebMvcTest(controllers = [ReadinessController])
class HealthApiControllerSpec extends Specification{

    @Autowired
    private WebApplicationContext context

    private MockMvc mvc

    @SpringBean
    ProcessEngineConfiguration processEngineConfiguration = Mock()

    @SpringBean
    KeycloakLogoutHandler keycloakLogoutHandler = Mock()

    @SpringBean
    private IdentityService identityService = Mock()

    @SpringBean
    private JwtDecoder jwtDecoder = Mock()

    @SpringBean
    private ClientRegistrationRepository clientRegistrationRepository = Mock()
    def setup() {
        mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build()
    }

    def 'can get readiness check' (){
        given:
        processEngineConfiguration.processEngineName >> 'engine'

        when:
        def result = mvc.perform(MockMvcRequestBuilders.get('/engine')
                .contentType(MediaType.APPLICATION_JSON))

        then:
        result.andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
    }
}
