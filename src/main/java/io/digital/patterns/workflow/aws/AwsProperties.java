package io.digital.patterns.workflow.aws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "aws")
@Component
@Data
public class AwsProperties {

    private String region;
    private String bucketName;
    private Credentials credentials;
    private String snsRegion;

    @Data
    public static class Credentials {
        private String accessKey;
        private String secretKey;
    }
}
