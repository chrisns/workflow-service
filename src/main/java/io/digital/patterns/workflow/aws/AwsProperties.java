package io.digital.patterns.workflow.aws;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "aws")
@Component
@Data
@Builder
public class AwsProperties {

    private String region;
    private String bucketName;
    private Credentials credentials;
    private String snsRegion;
    private ElasticSearch elasticSearch;
    private String caseBucketName;


    @Data
    public static class ElasticSearch {
        private String region;
        private String endpoint;
        private Credentials credentials;
    }

    @Data
    public static class Credentials {
        private String accessKey;
        private String secretKey;
    }
}