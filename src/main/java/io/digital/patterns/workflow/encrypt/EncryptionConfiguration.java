package io.digital.patterns.workflow.encrypt;

import io.digitalpatterns.camunda.encryption.*;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.spring.boot.starter.rest.CamundaJerseyResourceConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "camunda.variable.encryption", havingValue = "true")
public class EncryptionConfiguration {

    @Value("${encryption.passPhrase}")
    private String passPhrase;

    @Value("${encryption.salt}")
    private String salt;

    private final CamundaJerseyResourceConfig jerseyResourceConfig;

    public EncryptionConfiguration(CamundaJerseyResourceConfig jerseyResourceConfig) {
        this.jerseyResourceConfig = jerseyResourceConfig;
    }

    @Bean
    public ProcessInstanceSpinVariableDecryptor processInstanceSpinVariableDecryptor() {
        return new DefaultProcessInstanceSpinVariableDecryptor(passPhrase, salt);
    }

    @Bean
    public ProcessInstanceSpinVariableEncryptor processInstanceSpinVariableEncryptor() {
        return new DefaultProcessInstanceSpinVariableEncryptor(passPhrase, salt);
    }


    @Bean
    public ProcessDefinitionEncryptionParser processDefinitionEncryptionParser(RepositoryService repositoryService) {
        return new ProcessDefinitionEncryptionParser(repositoryService);
    }

    @Bean
    public ProcessInstanceSpinVariableEncryptionPlugin plugin() {
        return new ProcessInstanceSpinVariableEncryptionPlugin(processInstanceSpinVariableEncryptor(),
                processInstanceSpinVariableDecryptor());
    }

    @PostConstruct
    public void init() {
        jerseyResourceConfig.register(StartProcessEncryptInterceptor.class);
        jerseyResourceConfig.register(TaskEncryptInterceptor.class);
        jerseyResourceConfig.register(VariableEncryptInterceptor.class);
        jerseyResourceConfig.register(GetVariablesDecryptInterceptor.class);
        jerseyResourceConfig.register(GetVariableInstanceDecryptInterceptor.class);
    }

}
