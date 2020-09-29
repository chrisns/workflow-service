package io.digital.patterns.workflow.data;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import io.digital.patterns.workflow.aws.AwsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.history.HistoricProcessInstance;
import org.camunda.spin.Spin;
import org.camunda.spin.json.SpinJsonNode;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static java.lang.String.format;
import static java.lang.String.valueOf;

@Slf4j
public class FormDataService {
    private static final String FAILED_TO_CREATE_S3_RECORD = "FAILED_TO_CREATE_S3_RECORD";

    private final RuntimeService runtimeService;
    private final AmazonS3 amazonS3;
    private final AwsProperties awsProperties;
    private final RestHighLevelClient elasticsearchClient;

    public FormDataService(RuntimeService runtimeService, AmazonS3 amazonS3, AwsProperties awsProperties,
                           RestHighLevelClient elasticsearchClient) {
        this.runtimeService = runtimeService;
        this.amazonS3 = amazonS3;
        this.awsProperties = awsProperties;
        this.elasticsearchClient = elasticsearchClient;
    }

    public String save(String form,
                       HistoricProcessInstance processInstance,
                       String executionId, String product) {

        File scratchFile = null;
        String formName = "";
        try {
            String businessKey = processInstance.getBusinessKey();
            SpinJsonNode json = Spin.JSON(form);
            String submittedBy = json.jsonPath("$.form.submittedBy").stringValue();
            formName = json.jsonPath("$.form.name").stringValue();
            String formVersionId = json.jsonPath("$.form.formVersionId").stringValue();
            String title = json.jsonPath("$.form.title").stringValue();
            String submissionDate = json.jsonPath("$.form.submissionDate").stringValue();

            final String key = key(businessKey, formName, submittedBy, submissionDate);

            String bucketName = awsProperties.getBucketName() + (!product.equalsIgnoreCase("") ?
                    "-" + product : "");

            boolean dataExists = amazonS3.doesObjectExist(bucketName, key);
            if (!dataExists) {
                scratchFile
                        = File.createTempFile(UUID.randomUUID().toString(), ".json");
                FileUtils.copyInputStreamToFile(IOUtils.toInputStream(form, "UTF-8"), scratchFile);

                ObjectMetadata metadata = new ObjectMetadata();
                metadata.addUserMetadata("processinstanceid", processInstance.getId());
                metadata.addUserMetadata("processdefinitionid", processInstance.getProcessDefinitionId());
                metadata.addUserMetadata("formversionid", formVersionId);
                metadata.addUserMetadata("name", formName);
                metadata.addUserMetadata("title", title);
                metadata.addUserMetadata("submittedby", submittedBy);
                metadata.addUserMetadata("submissiondate", submissionDate);

                PutObjectRequest request = new PutObjectRequest(bucketName, key, scratchFile);
                request.setMetadata(metadata);
                final PutObjectResult putObjectResult = amazonS3.putObject(request);
                log.debug("Uploaded to S3 '{}'", putObjectResult.getETag());
                upload(form,
                        key,
                        processInstance);
                return key;
            } else {
                log.info("Key already exists...so not uploading");
                return null;
            }
        } catch (IOException | AmazonServiceException e) {
            log.error("Failed to upload to S3 ", e);
            runtimeService.createIncident(
                    FAILED_TO_CREATE_S3_RECORD,
                    executionId,
                    format("Failed to upload form data for %s", formName),
                    e.getMessage()

            );

        } finally {
             if (scratchFile != null && scratchFile.exists()) {
                scratchFile.delete();
            }
        }
        return null;
    }


    public void upload(String form,
                       String key,
                       HistoricProcessInstance processInstance) {

        log.info("Saving data to ES");
        String indexKey;
        if (processInstance.getBusinessKey() != null && processInstance.getBusinessKey().split("-").length == 3) {
            indexKey = processInstance.getBusinessKey().split("-")[1];
        } else {
            indexKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        IndexRequest indexRequest = new IndexRequest(indexKey).id(key);


        JSONObject indexSource = new JSONObject();
        indexSource.put("businessKey", processInstance.getBusinessKey());

        SpinJsonNode json = Spin.JSON(stringify(new JSONObject(form)).toString());
        String submittedBy = json.jsonPath("$.form.submittedBy").stringValue();
        String submissionDate = json.jsonPath("$.form.submissionDate").stringValue();
        String formName = json.jsonPath("$.form.name").stringValue();
        String timeStamp = DateTime.parse(submissionDate).toString("YYYYMMDD'T'HHmmss");

        indexSource.put("submissionDate", timeStamp);
        indexSource.put("submittedBy", submittedBy);
        indexSource.put("formName", formName);
        indexSource.put("data", json.toString());

        indexRequest.source(indexSource.toString(), XContentType.JSON);
        try {

            RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
            builder.addHeader("Content-Type", "application/json");
            final IndexResponse index = elasticsearchClient.index(indexRequest, builder.build());
            log.info("Document uploaded result response '{}'", index.getResult().getLowercase());
        } catch (IOException e) {
            log.error("Failed to create a document in ES due to '{}'", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private JSONObject stringify(JSONObject o) {
        for (String key : o.keySet()) {
            Object json = o.get(key);
            if (json instanceof JSONObject) {
                stringify((JSONObject) json);
            } else if (json instanceof JSONArray) {
                JSONArray array = (JSONArray) json;

                for (int i = 0; i < array.length(); i++) {
                    Object aObj = array.get(i);
                    if (aObj instanceof JSONObject) {
                        stringify((JSONObject) aObj);
                    } else {
                        array.put(i, String.valueOf(aObj));
                    }
                }
            } else {
                o.put(key, valueOf(json));
            }
        }
        return o;
    }

    public static String key(String businessKey, String formName, String email, String submissionDate) {
        StringBuilder keyBuilder = new StringBuilder();
        String timeStamp = DateTime.parse(submissionDate).toString("YYYYMMDD'T'HHmmss");

        return keyBuilder.append(businessKey)
                .append("/").append(formName).append("/").append(email).append("-").append(timeStamp).append(".json")
                .toString();

    }
}