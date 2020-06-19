package io.digital.patterns.workflow.aws;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class AwsConfiguration {


    private final AwsProperties awsProperties;

    public AwsConfiguration(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }


    @Bean
    public AWSStaticCredentialsProvider credentials(){
        BasicAWSCredentials basicAWSCredentials =
                new BasicAWSCredentials(awsProperties.getCredentials().getAccessKey()
                , awsProperties.getCredentials().getSecretKey());
       return  new AWSStaticCredentialsProvider(basicAWSCredentials);

    }

    @Bean
    @Primary
    public AmazonS3 awsS3Client() {
        return AmazonS3ClientBuilder.standard().withRegion(Regions.fromName(awsProperties.getRegion()))
                .withCredentials(credentials()).build();
    }

    @Bean
    public AmazonSimpleEmailService amazonSimpleEmailService() {
       return AmazonSimpleEmailServiceClientBuilder.standard()
               .withRegion(Regions.fromName(awsProperties.getRegion()))
               .withCredentials(credentials()).build();
    }

    @Bean
    public AmazonSNSClient amazonSNSClient() {
       return (AmazonSNSClient) AmazonSNSClient.builder()
                .withRegion(Regions.fromName(awsProperties.getSnsRegion()))
                .withCredentials(credentials()).build();

    }

}
