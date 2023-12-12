package com.jewel.reportmanager.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class TestExeDto {

    private String tc_run_id;

    private long start_time;

    private long end_time;

    @NotNull
    @NotBlank
    private String name;

    private Object category;

    private String log_file;

    @NotNull
    @Pattern(regexp = "^[A-Za-z]+$", message = "Invalid value")
    @Schema(example = "ACTIVE")
    private String status;

    @NotNull
    @NotBlank
    private String machine;
    private String result_file;

    @Pattern(regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_. -]*$", message = "Invalid value")
    @Schema(example = "Gemjar")
    private String product_type;

    private boolean ignore;

    @NotEmpty
    private List<Object> steps;

    private List<Map<String, Object>> meta_data;

    private Map<String, Object> user_defined_data;

    @NotNull
    @NotBlank
    private String s_run_id;

    @NotNull
    @Pattern(regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_. -]*$", message = "Invalid value")
    @Schema(example = "On Demand")
    private String run_type;

    @NotNull
    @Pattern(regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_. -]*$", message = "Invalid value")
    @Schema(example = "Linux")
    private String run_mode;

    @Pattern(regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_. -]*$", message = "Invalid value")
    @Schema(example = "Test.user_12")
    private String base_user;

    @Pattern(regexp = "^(?=.*[a-zA-Z])[a-zA-Z0-9_. -]*$",message = "Invalid value")
    @Schema(example = "Test.user_12")
    private String invoke_user;

    private Set<String> token_user;

    private String job_name;

    public Map<String, Object> getUser_defined_data() {
        return user_defined_data;
    }
    private ClassificationDetails classificationDetails;
    private Long varianceId;

    private List<Long> stepVarianceIds=new ArrayList<>();

    private Long testcase_id;

}
