package io.digital.patterns.workflow.security

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.junit.Rule
import spock.lang.Specification

class KeycloakClientSpec extends Specification {
    def wmPort = 8182

    @Rule
    WireMockRule wireMockRule = new WireMockRule(wmPort)

    KeycloakClient service = new KeycloakClient("http://localhost:8182", "myRealm", "client_id", "very_secret")

    def shouldReturnAccessToken() {
        given:
        WireMock.stubFor(WireMock.post("/auth/realms/myRealm/protocol/openid-connect/token")
                .withHeader("Content-Type", WireMock.equalTo("application/x-www-form-urlencoded;charset=UTF-8"))
                .withHeader("Authorization", WireMock.equalTo("Basic Y2xpZW50X2lkOnZlcnlfc2VjcmV0"))
                .withHeader("Accept", WireMock.equalTo("application/json"))
                .withRequestBody(WireMock.equalTo("grant_type=client_credentials"))
                .willReturn(WireMock.aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                                        {
                                            "access_token": "MY_SECURE_TOKEN"
                                        }
                                        """)))

        when:
        def token = service.bearerToken()

        then:
        token == "MY_SECURE_TOKEN"

    }
}
