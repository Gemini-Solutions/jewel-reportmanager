package com.jewel.reportmanager.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Getter
@Setter
public class SuiteDto {

    private Long s_id;

    @NotNull
    private Long p_id;

    @NotBlank
    private String project_name;

    @NotBlank
    private String report_name;

    private long created_on;

    private long updated_on;

    private String created_by;

    private String updated_by;

    private String status;
    @JsonIgnore
    private long jobs_scheduled;
    @JsonIgnore
    private String jar_link;
    @JsonIgnore
    private Email email;

    private long testcaseCount;

    private Map<String, Object> configuration;

    private String source = "JEWEL";

    private String accessToken;

}
