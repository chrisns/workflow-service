package io.digital.patterns.workflow.cases;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.spin.json.SpinJsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;



@RequestMapping(path = "/cases",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Slf4j
@AllArgsConstructor(onConstructor = @__(@Autowired))
@RestController
public class CaseApiController {

    private CasesApplicationService casesApplicationService;
    private PagedResourcesAssembler<Case> pagedResourcesAssembler;

    @GetMapping
    public PagedModel<Case> getCases(Pageable pageable,
                                     @RequestParam("query") String query) {

        Page<Case> cases = casesApplicationService.query(query, pageable,  new PlatformUser(
                (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()
        ));
        return pagedResourcesAssembler.toModel(cases, entity -> entity);
    }

    @GetMapping(path = "/{businessKey}")
    public ResponseEntity<CaseDetail> getCaseDetails(@PathVariable String businessKey,
                                                     @RequestParam(required = false, defaultValue = "")
                                                     String excludes) {
        List<String> excludeProcessKeys = excludes.equalsIgnoreCase("") ? new ArrayList<>()
                : Arrays.asList(excludes.split(","));
        CaseDetail caseDetail = casesApplicationService.getByKey(businessKey, excludeProcessKeys,
                new PlatformUser(
                        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()
                ));
        return ResponseEntity.ok(caseDetail);
    }

    @GetMapping(path = "/{businessKey}/submission")
    public ResponseEntity<Object> getSubmissionData(@PathVariable String businessKey,
                                                    @RequestParam String key) {
        SpinJsonNode submissionData = casesApplicationService.getSubmissionData(businessKey,
                key, new PlatformUser(
                        (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()
                ));

        return ResponseEntity.ok(submissionData.toString());
    }


}
