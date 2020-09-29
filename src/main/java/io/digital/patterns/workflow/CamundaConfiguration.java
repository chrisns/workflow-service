package io.digital.patterns.workflow;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.digital.patterns.workflow.aws.AwsProperties;
import io.digital.patterns.workflow.data.FormDataService;
import io.digital.patterns.workflow.data.FormDataVariablePersistListener;
import io.digital.patterns.workflow.data.FormObjectSplitter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.impl.cfg.ProcessEnginePlugin;
import org.camunda.bpm.engine.impl.history.handler.CompositeDbHistoryEventHandler;
import org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration;
import org.camunda.bpm.spring.boot.starter.configuration.impl.AbstractCamundaConfiguration;
import org.camunda.connect.plugin.impl.ConnectProcessEnginePlugin;
import org.camunda.spin.impl.json.jackson.format.JacksonJsonDataFormat;
import org.camunda.spin.plugin.impl.SpinProcessEnginePlugin;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
@Configuration
public class CamundaConfiguration {

    @Bean
    public ConnectProcessEnginePlugin connectProcessEnginePlugin() {
        return new ConnectProcessEnginePlugin();
    }

    @Bean
    public JacksonJsonDataFormat formatter(ObjectMapper objectMapper) {
        return new JacksonJsonDataFormat("application/json", objectMapper);
    }

    @Bean
    public ProcessEnginePlugin spinProcessEnginePlugin() {
        return new SpinProcessEnginePlugin();
    }


    @Configuration
    public static class S3VariablePersistenceConfiguration extends AbstractCamundaConfiguration {

        private final AmazonS3 amazonS3;
        private final AwsProperties awsProperties;
        private final RestHighLevelClient elasticsearchClient;
        private final RetryTemplate retryTemplate;

        public S3VariablePersistenceConfiguration(AmazonS3 amazonS3, AwsProperties awsProperties,
                                                  RestHighLevelClient elasticsearchClient,
                                                  RetryTemplate retryTemplate) {
            this.amazonS3 = amazonS3;
            this.awsProperties = awsProperties;
            this.elasticsearchClient = elasticsearchClient;
            this.retryTemplate = retryTemplate;
        }


        @Override
        public void preInit(SpringProcessEngineConfiguration processEngineConfiguration) {
            processEngineConfiguration.setJavaSerializationFormatEnabled(true);
            processEngineConfiguration.setHistoryEventHandler(
                    new CompositeDbHistoryEventHandler(
                            new FormDataVariablePersistListener(
                                    new FormDataService(processEngineConfiguration.getRuntimeService(),
                                            amazonS3, awsProperties, elasticsearchClient),
                                    processEngineConfiguration.getRepositoryService(),
                                    processEngineConfiguration.getHistoryService(),
                                    new FormObjectSplitter(),
                                    retryTemplate
                            )));
            log.info("S3 variable persistence configured");
        }
    }
}