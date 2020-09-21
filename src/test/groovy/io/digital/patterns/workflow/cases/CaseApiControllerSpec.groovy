package io.digital.patterns.workflow.cases

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.PutObjectRequest
import io.digital.patterns.workflow.aws.AwsProperties
import io.digital.patterns.workflow.data.FormDataService
import io.digital.patterns.workflow.data.FormDataVariablePersistListener
import io.digital.patterns.workflow.data.FormObjectSplitter
import io.digital.patterns.workflow.security.cockpit.KeycloakLogoutHandler
import org.apache.http.HttpHost
import org.camunda.bpm.engine.AuthorizationService
import org.camunda.bpm.engine.HistoryService
import org.camunda.bpm.engine.IdentityService
import org.camunda.bpm.engine.ProcessEngine
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl
import org.camunda.bpm.engine.impl.history.handler.CompositeDbHistoryEventHandler
import org.camunda.bpm.engine.impl.test.TestHelper
import org.camunda.bpm.engine.runtime.ProcessInstance
import org.camunda.bpm.model.bpmn.Bpmn
import org.camunda.bpm.model.bpmn.BpmnModelInstance
import org.camunda.spin.DataFormats
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestHighLevelClient
import org.hamcrest.Matchers
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.WebApplicationContext
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.spock.Testcontainers
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.TimeUnit

import static org.camunda.spin.Spin.S
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(controllers = [CaseApiController])
@Testcontainers
class CaseApiControllerSpec extends Specification {


    @Shared
    ElasticsearchContainer esContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch-oss:7.6.2")


    @Shared
    LocalStackContainer localstack =
            new LocalStackContainer("0.11.4").withServices(LocalStackContainer.Service.S3)



    @Autowired
    WebApplicationContext context

    TransactionTemplate transactionTemplate

    ProcessEngine processEngineRule = TestHelper.getProcessEngine("/camunda.cfg.xml")


    @SpringBean
    RestHighLevelClient elasticsearchClient = new RestHighLevelClient(RestClient.builder(
            HttpHost.create(esContainer.getHttpHostAddress())
    ))



    @SpringBean
    CaseActionService caseService = Mock()

    @SpringBean
    AwsProperties awsProperties = Mock()

    @SpringBean
    HistoryService historyService = processEngineRule.historyService


    @SpringBean
    AuthorizationService authorizationService = processEngineRule.authorizationService


    @SpringBean
    KeycloakLogoutHandler keycloakLogoutHandler = Mock()

    @SpringBean
    IdentityService identityService = processEngineRule.identityService

    @SpringBean
    ClientRegistrationRepository clientRegistrationRepository = Mock()


    MockMvc mvc

    @SpringBean
    private JwtDecoder jwtDecoder = Mock()

    @Autowired
    private WebApplicationContext context


    @SpringBean
    AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
            .withCredentials(localstack.getDefaultCredentialsProvider())
            .withEndpointConfiguration(localstack.getEndpointConfiguration(LocalStackContainer.Service.S3))
            .enablePathStyleAccess()
            .build()

    @SpringBean
    CasesApplicationService applicationService =
            new CasesApplicationService(historyService,
                    amazonS3,
                    elasticsearchClient,
                    awsProperties, caseService,
                    authorizationService)

    def setup() {
               mvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build()

        def formDataService = new FormDataService(
                processEngineRule.runtimeService,
                amazonS3,
                awsProperties,
                elasticsearchClient)
        ((ProcessEngineConfigurationImpl)processEngineRule.getProcessEngineConfiguration())
                .setHistoryEventHandler(
                        new CompositeDbHistoryEventHandler(
                                new FormDataVariablePersistListener(
                                        formDataService,
                                        processEngineRule.repositoryService,
                                        processEngineRule.historyService,
                                        new FormObjectSplitter()
                                )
                        )
                )

       transactionTemplate = new TransactionTemplate(
               new DataSourceTransactionManager(processEngineRule.getProcessEngineConfiguration()
               .getDataSource())
       )

        awsProperties.getCaseBucketName() >> "casebucket"
    }

    def cleanup() {

    }

    @WithMockUser(username = 'test')
    def 'can query for cases'() {
        given: 'A process definition with a message is created'
        def businessKey = "businessKey"
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example")
                .startEvent()
                .userTask()
                .name("Do something")
                .endEvent()
                .done()

        and: 'case bucket request'
        awsProperties.getBucketName() >> "casebucket"
        amazonS3.createBucket("casebucket")

        and: 'the process definition has been uploaded to the camunda engine'
        processEngineRule.repositoryService.createDeployment()
                .addModelInstance("example.bpmn", modelInstance).deploy()

        and: 'process instance is started'
        final ProcessInstance instance = transactionTemplate.execute(new TransactionCallback<ProcessInstance>() {
            @Override
            ProcessInstance doInTransaction(TransactionStatus status) {
                return processEngineRule.runtimeService.createProcessInstanceByKey("example")
                        .businessKey(businessKey)
                        .setVariable("data", S('''{
                                  "textField": "API-A-20200120-17",
                                  "textField2": "APPLES",
                                  "submit": true,
                                  "lastName": "XXXXX-AAAA",
                                  "businessKey": "businessKey"
                                }
                                ''', DataFormats.JSON_DATAFORMAT_NAME))
                        .execute()
            }
        })


        and: 'task is completed'
        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            Void doInTransaction(TransactionStatus status) {
                def task = processEngineRule.getTaskService().createTaskQuery()
                        .processInstanceId(instance.id).singleResult()
                processEngineRule.taskService.complete(task.id, Map.of(
                        'taskData', S('''{
                                  "textField": "API-A-20200120-17",
                                  "textField2": "APPLES",
                                  "submit": true,
                                  "lastName": "XXXXX-AAAA",
                                  "businessKey": "businessKey",
                                  "form": {
                                    "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a",
                                    "formId": "d8e0ea6d-e12d-4291-8938-b86db773527f",
                                    "title": "l5vp40zwx1",
                                    "name": "vloigdgx2mr",
                                    "submissionDate": "2020-01-28T08:31:55.297Z",
                                    "submittedBy": "user@user.com"
                                  }
                                }
                                ''', DataFormats.JSON_DATAFORMAT_NAME)
                ))
                return null
            }
        })

        TimeUnit.SECONDS.sleep(5)

        when: 'request is made'
        def result = mvc.perform(MockMvcRequestBuilders.get('/cases?query=APPLES')
                .with(jwt().authorities([new SimpleGrantedAuthority('test')]))
                .accept(MediaType.APPLICATION_JSON))

        then: 'result is successful'
        result.andReturn().getResponse().getStatus() == 200

        and: 'result should not be empty'
        result.andExpect(jsonPath('$.page.totalElements', Matchers.is(1)))
    }

    def 'can get submission data'() {
        given: 'data is stored'
        amazonS3.createBucket("casebucket")
        amazonS3.putObject(new PutObjectRequest("casebucket", "businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json",
                new ClassPathResource("data.json").getInputStream(), new ObjectMetadata()))


        when: 'a call to get submission data is made'
        def result = mvc.perform(MockMvcRequestBuilders.get('/cases/businessKey/submission?key=businessKey/buildingPassRequest/xx1@x.com-20200128T083155.json')
                .with(jwt().authorities([new SimpleGrantedAuthority('test')]))
                .accept(MediaType.APPLICATION_JSON))

        then: 'result is successful'
        result.andReturn().getResponse().getStatus() == 200

    }

    def 'can get case details'() {
        given: 'A process definition with a message is created'
        def businessKey = "newBusinessKey"
        BpmnModelInstance modelInstance = Bpmn.createExecutableProcess("example")
                .startEvent()
                .userTask()
                .name("Do something")
                .endEvent()
                .done()

        and: 'case bucket request'
        awsProperties.getBucketName() >> "casebucket"
        amazonS3.createBucket("casebucket")

        and: 'the process definition has been uploaded to the camunda engine'
        processEngineRule.repositoryService.createDeployment()
                .addModelInstance("example.bpmn", modelInstance).deploy()

        and: 'process instance is started'
        final ProcessInstance instance = transactionTemplate.execute(new TransactionCallback<ProcessInstance>() {
            @Override
            ProcessInstance doInTransaction(TransactionStatus status) {
                return processEngineRule.runtimeService.createProcessInstanceByKey("example")
                        .businessKey(businessKey)
                        .setVariable("data", S('''{
                                  "textField": "API-A-20200120-17",
                                  "textField2": "APPLES",
                                  "submit": true,
                                  "lastName": "XXXXX-AAAA",
                                  "businessKey": "newBusinessKey"
                                }
                                ''', DataFormats.JSON_DATAFORMAT_NAME))
                        .execute()
            }
        })


        and: 'task is completed'
        transactionTemplate.execute(new TransactionCallback<Void>() {
            @Override
            Void doInTransaction(TransactionStatus status) {
                def task = processEngineRule.getTaskService().createTaskQuery()
                        .processInstanceId(instance.id).singleResult()
                processEngineRule.taskService.complete(task.id, Map.of(
                        'taskData', S('''{
                                  "textField": "API-A-20200120-17",
                                  "textField2": "APPLES",
                                  "submit": true,
                                  "lastName": "XXXXX-AAAA",
                                  "businessKey": "newBusinessKey",
                                  "form": {
                                    "formVersionId": "84a32079-8e8b-4042-91db-c75d1cc3933a",
                                    "formId": "d8e0ea6d-e12d-4291-8938-b86db773527f",
                                    "title": "l5vp40zwx1",
                                    "name": "vloigdgx2mr",
                                    "submissionDate": "2020-01-30T08:31:55.297Z",
                                    "submittedBy": "apples@apples.cpm"
                                  }
                                }
                                ''', DataFormats.JSON_DATAFORMAT_NAME)
                ))
                return null
            }
        })

        caseService.getAvailableActions(_ as CaseDetail,_ as PlatformUser) >> []

        when: 'a call to get case details'
        def result = mvc.perform(MockMvcRequestBuilders.get('/cases/newBusinessKey')
                .with(jwt().authorities([new SimpleGrantedAuthority('test')]))
                .accept(MediaType.APPLICATION_JSON))

        then: 'result is successful'
        result.andReturn().getResponse().getStatus() == 200
    }

}
