package com.jewel.reportmanager.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jewel.reportmanager.dto.*;
import com.jewel.reportmanager.enums.OperationType;
import com.jewel.reportmanager.enums.StatusColor;
import com.jewel.reportmanager.enums.UserRole;
import com.jewel.reportmanager.exception.CustomDataException;
import com.jewel.reportmanager.service.ColumnMappingService;
import com.mongodb.BasicDBObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static com.jewel.reportmanager.enums.OperationType.*;
import static com.jewel.reportmanager.enums.ProjectAccessType.ADMIN;
import static com.jewel.reportmanager.enums.TestCaseType.MANUAL;
import static com.jewel.reportmanager.enums.UserRole.SUPER_ADMIN;
import static com.jewel.reportmanager.utils.ReportResponseConstants.*;

@Slf4j
@Service
public class ReportUtils {

    private static String userManagerUrl;
    private static String projectManagerUrl;
    private static MongoOperations mongoOperations;
    private static RestTemplate restTemplate;
    private static ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private ColumnMappingService columnMappingService;

    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        ReportUtils.restTemplate = restTemplate;
    }

    @Value("${user.manager.url}")
    public void setUserManagerUrl(String userManagerUrl) {
        ReportUtils.userManagerUrl = userManagerUrl;
    }

    @Value("${project.manager.url}")
    public void setProjectManagerUrl(String projectManagerUrl) {
        ReportUtils.projectManagerUrl = projectManagerUrl;
    }

    @Autowired
    public void setMongoOperations(MongoOperations mongoOperations) {
        ReportUtils.mongoOperations = mongoOperations;
    }

    public void populateIconProperties(TestExeDto testExe, Map<String, Object> temp, boolean clickable, boolean varianceIsActive, boolean falsePositiveIsActive, boolean varianceIsThere, boolean falsePositiveIsThere) {
        if (testExe.getStatus().equalsIgnoreCase("FAIL") || testExe.getStatus().equalsIgnoreCase("ERR")) {
            temp.put("EDIT_ICON", ReportUtils.createCustomObject(ACTIVE_STATUS, "text", ACTIVE_STATUS, "left"));
        } else {
            temp.put("EDIT_ICON", ReportUtils.createCustomObject("INACTIVE", "text", "INACTIVE", "left"));
        }
        if (varianceIsActive) {
            temp.put("ISCLICKABLE", ReportUtils.createCustomObject(clickable, "text", clickable, "left"));
            temp.put("ICON", ReportUtils.createCustomObject("VARIANCE_ACTIVE", "text", "VARIANCE_ACTIVE", "left"));
        } else if (falsePositiveIsActive) {
            temp.put("ISCLICKABLE", ReportUtils.createCustomObject(clickable, "text", clickable, "left"));
            temp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_ACTIVE", "text", "FALSE_POSITIVE_ACTIVE", "left"));
            if (testExe.getClassificationDetails().getReason() != null && !testExe.getClassificationDetails().getReason().isEmpty())
                temp.put("REASON", ReportUtils.createCustomObject(testExe.getClassificationDetails().getReason(), "text", testExe.getClassificationDetails().getReason(), "left"));
        } else if (varianceIsThere) {
            temp.put("ICON", ReportUtils.createCustomObject("VARIANCE_INACTIVE", "text", "VARIANCE_INACTIVE", "left"));
        } else if (falsePositiveIsThere) {
            temp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_INACTIVE", "text", "FALSE_POSITIVE_INACTIVE", "left"));
            if (testExe.getClassificationDetails().getReason() != null && !testExe.getClassificationDetails().getReason().isEmpty())
                temp.put("REASON", ReportUtils.createCustomObject(testExe.getClassificationDetails().getReason(), "text", testExe.getClassificationDetails().getReason(), "left"));
        }
    }

    public void depopulateMap(Map<String, Object> map) {
        map.remove("user_defined_data");
        map.remove("steps");
        map.remove("meta_data");
        map.remove("ignore");
        map.remove("log_file");
        map.remove("result_file");
        map.remove("s_run_id");
        map.remove("tc_run_id");
    }

    public void populateTempAccordingToKey(String key, Map<String, Object> temp, LinkedHashMap<String, Object> map, Map<String, Object> statusSubType) {
        if (key.equalsIgnoreCase("start_time") || key.equalsIgnoreCase("end_time")) {
            Map<String, Object> timeReport = new HashMap<>();
            timeReport.put("subType", "datetime");
            temp.put(ReportUtils.changeKeyValue(key),
                    ReportUtils.createCustomObject(map.get(key), "date", map.get(key), "center",
                            timeReport));
        } else if (key.equalsIgnoreCase("status")) {
            temp.put(ReportUtils.changeKeyValue(key),
                    ReportUtils.createCustomObject(map.get(key), "crud", map.get(key), "center", statusSubType));
        } else {
            temp.put(ReportUtils.changeKeyValue(key),
                    ReportUtils.createCustomObject(map.get(key), "text", map.get(key), "left"));

        }
    }

    public Map<String, Object> getMapAccordingToSuiteVarianceAndFalsePositive(boolean suiteVarianceIsActive, boolean suiteFalsePositiveIsActive, boolean suiteVarianceIsThere, boolean suiteFalsePositiveIsThere) {
        Map<String, Object> iconMap = new HashMap<>();
        if (suiteVarianceIsActive) {
            iconMap = ReportUtils.createCustomObject("VARIANCE_ACTIVE", "text", "VARIANCE_ACTIVE", "left");
        } else if (suiteFalsePositiveIsActive) {
            iconMap = ReportUtils.createCustomObject("FALSE_POSITIVE_ACTIVE", "text", "FALSE_POSITIVE_ACTIVE", "left");
        } else if (suiteVarianceIsThere) {
            iconMap = ReportUtils.createCustomObject("VARIANCE_INACTIVE", "text", "VARIANCE_INACTIVE", "left");
        } else if (suiteFalsePositiveIsThere) {
            iconMap = ReportUtils.createCustomObject("FALSE_POSITIVE_INACTIVE", "text", "FALSE_POSITIVE_INACTIVE", "left");
        }
        return iconMap;
    }

    public Response getResultWithTcRunId(String tc_run_id) {
        ObjectMapper oMapper = new ObjectMapper();
        Map<String, Object> stepData = new HashMap<>();
        Set<String> stepsListHeaders = new HashSet<>();
        List<Map<String, Object>> stepsVariableValue = new ArrayList<>();

        TestExeDto tempTest = RestApiUtils.getTestExe(tc_run_id);
        if (tempTest == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(TESTCASE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.OK);
        }

        String username = ReportUtils.getUserDtoFromServetRequest().getUsername();

        UserDto user = ReportUtils.getUserDtoFromServetRequest();

        SuiteExeDto getSuite = RestApiUtils.getSuiteExe(tempTest.getS_run_id());
        if (getSuite == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }

        ProjectDto project = RestApiUtils.getProjectByPidAndStatus(getSuite.getP_id(), ACTIVE_STATUS);
        if (project == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        }

        ProjectRoleDto currentProject = RestApiUtils.getProjectRoleByPidAndUsername(getSuite.getP_id(), username);
        if (currentProject == null && !((user.getRole().equalsIgnoreCase(UserRole.ADMIN.toString()) && project.getRealcompanyname().equalsIgnoreCase(user.getRealCompany())) || user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString()))) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ACCESS_TO_PROJECT, null, OperationType.Info, HttpStatus.NOT_ACCEPTABLE, REQUEST_ACCESS);
        }

        List<VarianceClassificationDto> varianceClassificationList = RestApiUtils.getVarianceClassificationList(getSuite.getVarianceIds(), ACTIVE_STATUS);
        Map<Long, VarianceClassificationDto> varianceList = new HashMap<>();
        for (VarianceClassificationDto varianceClassification : varianceClassificationList) {
            varianceList.put(varianceClassification.getVarianceId(), varianceClassification);
        }
        boolean varianceIsActiveAtTestLevel = false;
        boolean falsePositiveIsActiveAtTestLevel = false;
        boolean varianceIsThereAtTestLevel = false;
        boolean falsePositiveIsThereAtTestLevel = false;
        String statusTestLevel = null;
        if (tempTest.getVarianceId() != null) {
            varianceIsThereAtTestLevel = true;
        }
        if(varianceList.get(tempTest.getVarianceId()) != null) {
            varianceIsActiveAtTestLevel = true;
            statusTestLevel = "PASS";
        }
        if (tempTest.getClassificationDetails() != null) {
            falsePositiveIsThereAtTestLevel = true;
            if (tempTest.getClassificationDetails().isFalsePositiveStatus()) {
                falsePositiveIsActiveAtTestLevel = true;
                statusTestLevel = "PASS";
            }
        }
        Map<String, Object> statusSubType = new HashMap<>();
        statusSubType.put("subType", "falseVariance");
        List<Map<String, Object>> gallery = new ArrayList<>();
        StepsDto steps = RestApiUtils.getSteps(tc_run_id);
        if (steps != null) {
            List<String> statuesList = new ArrayList<>();
            for (Object step : steps.getSteps()) {
                boolean clickable = false;
                boolean varianceIsActive = varianceIsActiveAtTestLevel;
                boolean varianceIsThere = varianceIsThereAtTestLevel;
                boolean falsePositiveIsActive = falsePositiveIsActiveAtTestLevel;
                boolean falsePositiveIsThere = falsePositiveIsThereAtTestLevel;
                Map<String, Object> stepMap = oMapper.convertValue(step, Map.class);
                stepsListHeaders.addAll(stepMap.keySet());
                stepsListHeaders.remove("tc_run_id");
                stepsListHeaders.remove("s_run_id");
                Map<String, Object> temp = new HashMap<>();
                for (String key : stepsListHeaders) {
                    String status = statusTestLevel;
                    String subStepStatus = statusTestLevel;
                    ClassificationDetails classificationDetails = null;
                    if (stepsListHeaders.contains("VARIANCEID")) {
                        Integer intVarianceValue  = (Integer)stepMap.get("VARIANCEID");
                        Long varianceId = intVarianceValue != null ? intVarianceValue.longValue(): null;
                        varianceIsThere = true;
                        if(varianceList.get(varianceId) != null) {
                            clickable = true;
                            varianceIsActive = true;
                            status = "PASS";
                            subStepStatus = "PASS";
                        }
                    }
                    if (stepsListHeaders.contains("CLASSIFICATIONDETAILS")) {
                        falsePositiveIsThere = true;
                        classificationDetails = oMapper.convertValue(stepMap.get("CLASSIFICATIONDETAILS"), ClassificationDetails.class);
                        if (classificationDetails != null && classificationDetails.isFalsePositiveStatus()) {
                            clickable = true;
                            falsePositiveIsActive = true;
                            subStepStatus = "Pass";
                        }
                    }

                    if (key.equalsIgnoreCase("sub_step")) {
                        if(stepMap.get("sub_step") != null) {
                            List<Map<String, Object>> subStepsVariableValue = new ArrayList<>();
                            List<Map<String, Object>> maps = (List<Map<String, Object>>) stepMap.get(key);
                            Set<String> subStepsHeaders = new HashSet<>();
                            for (Map<String, Object> map1 : maps) {
                                Map<String, Object> subStepsTemp = new HashMap<>();
                                subStepsHeaders.addAll(map1.keySet());
                                for (String key2 : subStepsHeaders) {
                                    if (key2.equalsIgnoreCase("start_time") || key2.equalsIgnoreCase("end_time")) {
                                        Map<String, Object> timeReport = new HashMap<>();
                                        timeReport.put("subType", "datetime");
                                        subStepsTemp.put(ReportUtils.changeKeyValue(key2),
                                                ReportUtils.createCustomObject(map1.get(key2), "date", map1.get(key2),
                                                        "center", timeReport));
                                    } else if (key2.equalsIgnoreCase("status")) {
                                        if (subStepStatus != null) {
                                            subStepsTemp.put(ReportUtils.changeKeyValue(key2),
                                                    ReportUtils.createCustomObject(subStepStatus, "crud", subStepStatus,
                                                            "center", statusSubType));
                                        } else {
                                            subStepsTemp.put(ReportUtils.changeKeyValue(key2),
                                                    ReportUtils.createCustomObject(map1.get(key2), "crud", map1.get(key2),
                                                            "center", statusSubType));
                                        }
                                        subStepsTemp.put("EDIT_ICON", ReportUtils.createCustomObject("INACTIVE", "text", "INACTIVE", "left"));
                                        if (map1.get(key2) != null && (map1.get(key2).toString().equalsIgnoreCase("ERR") || map1.get(key2).toString().equalsIgnoreCase("FAIL")) || (classificationDetails != null && classificationDetails.isFalsePositiveStatus())) {
                                            if (varianceIsActive) {
                                                subStepsTemp.put("ISCLICKABLE", ReportUtils.createCustomObject(false, "text", false, "left"));
                                                subStepsTemp.put("ICON", ReportUtils.createCustomObject("VARIANCE_ACTIVE", "text", "VARIANCE_ACTIVE", "left"));
                                            } else if (falsePositiveIsActive && classificationDetails != null) {
                                                subStepsTemp.put("ISCLICKABLE", ReportUtils.createCustomObject(false, "text", false, "left"));
                                                subStepsTemp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_ACTIVE", "text", "FALSE_POSITIVE_ACTIVE", "left"));
                                                if (classificationDetails.getReason() != null && !classificationDetails.getReason().isEmpty())
                                                    subStepsTemp.put("REASON", ReportUtils.createCustomObject(classificationDetails.getReason(), "text", classificationDetails.getReason(), "left"));
                                            } else if (varianceIsThere) {
                                                subStepsTemp.put("ICON", ReportUtils.createCustomObject("VARIANCE_INACTIVE", "text", "VARIANCE_INACTIVE", "left"));
                                            } else if (falsePositiveIsThere && classificationDetails != null) {
                                                subStepsTemp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_INACTIVE", "text", "FALSE_POSITIVE_INACTIVE", "left"));
                                                if (classificationDetails.getReason() != null && !classificationDetails.getReason().isEmpty())
                                                    subStepsTemp.put("REASON", ReportUtils.createCustomObject(classificationDetails.getReason(), "text", classificationDetails.getReason(), "left"));
                                            }
                                        }

                                    } else if (key2.equalsIgnoreCase("screenshot")) {
                                        subStepsTemp.put(ReportUtils.changeKeyValue(key2),
                                                ReportUtils.createCustomObject(map1.get(key2), "image",
                                                        map1.get(key2),
                                                        "center"));
                                        Map<String, Object> screenshot = new HashMap<>();
                                        if (map1.get(key2) != null) {
                                            if (map1.get("step name") != null) {
                                                screenshot.put(map1.get("step name").toString(), (map1.get(key2)));
                                            } else {
                                                screenshot.put(map1.get("title").toString(), (map1.get(key2)));
                                            }
                                            gallery.add(screenshot);
                                        }

                                    } else {
                                        subStepsTemp.put(ReportUtils.changeKeyValue(key2),
                                                ReportUtils.createCustomObject(map1.get(key2), "text", map1.get(key2),
                                                        "left"));
                                    }

                                }
                                subStepsVariableValue.add(subStepsTemp);
                            }
                            Map<String, Object> subStepsData = new HashMap<>();
                            subStepsData.put("data", subStepsVariableValue);
                            subStepsData.put("headers", ReportUtils.headersDataStepRefactor(subStepsHeaders));
                            temp.put("SUB_STEPS", subStepsData);
                        }
                        continue;
                    }

                    if (key.equalsIgnoreCase("start_time") || key.equalsIgnoreCase("end_time")) {
                        Map<String, Object> timeReport = new HashMap<>();
                        timeReport.put("subType", "datetime");
                        temp.put(ReportUtils.changeKeyValue(key),
                                ReportUtils.createCustomObject(stepMap.get(key), "date", stepMap.get(key),
                                        "center", timeReport));
                    } else if (key.equalsIgnoreCase("status")) {
                        if (status != null) {
                            statuesList.add(status);
                            temp.put(ReportUtils.changeKeyValue(key),
                                    ReportUtils.createCustomObject(status, "status", status,
                                            "center"));

                            if (status.equalsIgnoreCase("FAIL") || status.equalsIgnoreCase("ERR") && !varianceIsActive && !falsePositiveIsActive) {
                                temp.put("EDIT_ICON", ReportUtils.createCustomObject(ACTIVE_STATUS, "text", ACTIVE_STATUS, "left"));
                            } else {
                                temp.put("EDIT_ICON", ReportUtils.createCustomObject("INACTIVE", "text", "INACTIVE", "left"));
                            }
                        } else {
                            String stepStatus = (String) stepMap.get(key);
                            statuesList.add(stepStatus);
                            temp.put(ReportUtils.changeKeyValue(key),
                                    ReportUtils.createCustomObject(stepStatus, "status", stepStatus,
                                            "center"));

                            if (stepStatus.equalsIgnoreCase("FAIL") || stepStatus.equalsIgnoreCase("ERR") && !varianceIsActive && !falsePositiveIsActive) {
                                temp.put("EDIT_ICON", ReportUtils.createCustomObject(ACTIVE_STATUS, "text", ACTIVE_STATUS, "left"));
                            } else {
                                temp.put("EDIT_ICON", ReportUtils.createCustomObject("INACTIVE", "text", "INACTIVE", "left"));
                            }
                        }

                        if ((stepMap.get(key).toString() != null && (stepMap.get(key).toString().equalsIgnoreCase("ERR") || stepMap.get(key).toString().equalsIgnoreCase("FAIL"))) || (classificationDetails != null && classificationDetails.isFalsePositiveStatus())) {
                            if (varianceIsActive) {
                                temp.put("ISCLICKABLE", ReportUtils.createCustomObject(clickable, "text", clickable, "left"));
                                temp.put("ICON", ReportUtils.createCustomObject("VARIANCE_ACTIVE", "text", "VARIANCE_ACTIVE", "left"));
                            } else if (falsePositiveIsActive && classificationDetails != null && classificationDetails.isFalsePositiveStatus()) {
                                temp.put("ISCLICKABLE", ReportUtils.createCustomObject(clickable, "text", clickable, "left"));
                                temp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_ACTIVE", "text", "FALSE_POSITIVE_ACTIVE", "left"));
                                if (classificationDetails.getReason() != null && !classificationDetails.getReason().isEmpty())
                                    temp.put("REASON", ReportUtils.createCustomObject(classificationDetails.getReason(), "text", classificationDetails.getReason(), "left"));
                            } else if (varianceIsThere) {
                                temp.put("ICON", ReportUtils.createCustomObject("VARIANCE_INACTIVE", "text", "VARIANCE_INACTIVE", "left"));
                            } else if (falsePositiveIsThere && classificationDetails != null) {
                                temp.put("ICON", ReportUtils.createCustomObject("FALSE_POSITIVE_INACTIVE", "text", "FALSE_POSITIVE_INACTIVE", "left"));
                                if (classificationDetails.getReason() != null && !classificationDetails.getReason().isEmpty())
                                    temp.put("REASON", ReportUtils.createCustomObject(classificationDetails.getReason(), "text", classificationDetails.getReason(), "left"));
                            }
                        }
                    } else if (key.equalsIgnoreCase("screenshot")) {
                        temp.put(ReportUtils.changeKeyValue(key),
                                ReportUtils.createCustomObject(stepMap.get(key), "image",
                                        stepMap.get(key),
                                        "center"));
                        Map<String, Object> screenshot = new HashMap<>();
                        if (stepMap.get(key) != null) {
                            if (stepMap.get("step name") != null) {
                                screenshot.put(stepMap.get("step name").toString(), (stepMap.get(key)));
                            } else {
                                screenshot.put(stepMap.get("title").toString(), (stepMap.get(key)));
                            }
                            gallery.add(screenshot);
                        }
                    } else {
                        temp.put(ReportUtils.changeKeyValue(key),
                                ReportUtils.createCustomObject(stepMap.get(key), "text", stepMap.get(key),
                                        "left"));
                    }
                }
                temp.put("PRODUCT TYPE", ReportUtils.createCustomObject(tempTest.getProduct_type(), "text", tempTest.getProduct_type(),
                        "left"));
                temp.remove("CLASSIFICATIONDETAILS");
                stepsVariableValue.add(temp);
            }
            Map<String, Object> testcase_info = new HashMap<>();
            for (String status : statuesList) {

                testcase_info.put(status.toUpperCase(),
                        Long.parseLong(testcase_info.getOrDefault(status.toUpperCase(), 0L).toString()) + 1);

            }
            if (!testcase_info.isEmpty()) {
                testcase_info.put("TOTAL", (long) statuesList.size());
            }
            if (tempTest.getMeta_data() != null && tempTest.getMeta_data().size() >= 3) {
                tempTest.getMeta_data().set(2, testcase_info);
            }
        }

        stepsListHeaders.remove("sub_step");
        stepsListHeaders.remove("tc_run_id");
        stepsListHeaders.remove("s_run_id");
        stepsListHeaders.remove("CLASSIFICATIONDETAILS");
        stepsListHeaders.remove("VARIANCEID");
        stepData.put("headers", ReportUtils.headersDataStepRefactor(stepsListHeaders));
        stepData.put("metaData", tempTest.getMeta_data());
        stepData.put("gallery", gallery);
        stepData.put("data", stepsVariableValue);
        stepData.put("tc_run_id", tc_run_id);

        return new Response(stepData, DATA_FETCHED_SUCCESSFULLY, Success);
    }

    public Response populateResultWithoutTestExes(
            SuiteExeDto getSuite,
            Map<String, Object> result,
            Integer pageNo,
            String s_run_id,
            Integer sort,
            String sortedColumn,
            Map<Long, VarianceClassificationDto> varianceList,
            List<Long> varianceIds,
            ProjectDto project,
            String user
    ) {
//        Map<String, Object> last5RunsBarGraph = ReportUtils.Last5RunsStackedBarChartBySuiteExe(getSuite);
//        if (last5RunsBarGraph != null) {
//            result.put("Last_5_Runs_Bar_Chart", last5RunsBarGraph);
//        }
        Map<String, Object> testcaseDetails = new HashMap<>();
        List<Map<String, Object>> testcaseDetailsdata = new ArrayList<>();
        Map<String, List<Map<String, Object>>> statusFilterMap = new HashMap<>();
        Set<String> testcaseDetailsHeaders = new LinkedHashSet<>();

        if (getSuite.getTestcase_details() == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(TESTCASE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.OK);
        }
        if(getSuite.getTestcase_details().isEmpty()) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(TESTCASE_DETAILS_NOT_FOUND_FOR_INTERVAL, null, Failure, HttpStatus.OK);
        }
        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }

        List<TestExeDto> tempTest = RestApiUtils.fetchTestExes(s_run_id, sort, sortedColumn);
        if (tempTest.isEmpty()) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(TESTCASE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.OK);
        }

        Set<String> frameworks = new HashSet<>();
        Set<String> category = new HashSet<>();
        Map<String, Long> categoryMap = new HashMap<>();
        List<String> statues = new ArrayList<>();
        boolean suiteVarianceIsActive = false;
        boolean suiteVarianceIsThere = false;
        boolean suiteFalsePositiveIsActive = false;
        boolean suiteFalsePositiveIsThere = false;
        HashSet<String> statusesSet = new HashSet<>();
        Map<String, Object> statusSubType = new HashMap<>();
        statusSubType.put("subType", "falseVariance");
        for (TestExeDto testExe : tempTest) {
            boolean clickable = false;
            boolean varianceIsActive = false;
            boolean varianceIsThere = false;
            boolean falsePositiveIsActive = false;
            boolean falsePositiveIsThere = false;
            if (testExe.getVarianceId() != null || (testExe.getStepVarianceIds() != null && !testExe.getStepVarianceIds().isEmpty())) {
                varianceIsThere = true;
                suiteVarianceIsThere = true;
                if(varianceList.get(testExe.getVarianceId()) != null) {
                    varianceIsActive = true;
                    suiteVarianceIsActive = true;
                    clickable = true;
                    testExe.setStatus("PASS");
                }
                if (ReportUtils.checkoneListContainsElementOfAnotherList(varianceIds, testExe.getStepVarianceIds())) {
                    varianceIsActive = true;
                    suiteVarianceIsActive = true;
                    testExe.setStatus(ReportUtils.checkStatusOfTestCaseByStepsIfVarianceIsThere(testExe.getTc_run_id(), varianceList));
                }
            }
            if (testExe.getClassificationDetails() != null) {
                suiteFalsePositiveIsThere = true;
                falsePositiveIsThere = true;
                if (testExe.getClassificationDetails().isFalsePositiveStatus()) {
                    suiteFalsePositiveIsActive = true;
                    falsePositiveIsActive = true;
                    clickable = true;
                }
                if (testExe.getClassificationDetails().isChildFalsePostiveStatus()) {
                    suiteFalsePositiveIsActive = true;
                    falsePositiveIsActive = true;
                }
            }
            ObjectMapper oMapper = new ObjectMapper();
            LinkedHashMap<String, Object> map = oMapper.convertValue(testExe, LinkedHashMap.class);
            if (testExe.getUser_defined_data() != null) {
                map.putAll(testExe.getUser_defined_data());
            }

            depopulateMap(map);

            map.remove("classificationDetails");
            map.remove("stepVarianceIds");
            map.remove("job_name");
            testcaseDetailsHeaders.addAll(map.keySet());
            Map<String, Object> temp = new HashMap<>();
            for (String key : map.keySet()) {
                populateTempAccordingToKey(key, temp, map, statusSubType);
            }

            populateIconProperties(testExe, temp, clickable, varianceIsActive, falsePositiveIsActive, varianceIsThere, falsePositiveIsThere);

            if (testExe.getCategory() != null) {
                if (testExe.getCategory().getClass().isArray()) {
                    String[] categories = (String[]) testExe.getCategory();
                    for (String data : categories) {
                        category.add(data);
                        categoryMap.put(data.toUpperCase() + "_" + testExe.getStatus(),
                                categoryMap.getOrDefault(data.toUpperCase() + "_" + testExe.getStatus(), 0L) + 1);
                    }
                } else {
                    String category1 = (String) testExe.getCategory();
                    category.add(category1);
                    categoryMap.put(category1.toUpperCase() + "_" + testExe.getStatus(),
                            categoryMap.getOrDefault(category1.toUpperCase() + "_" + testExe.getStatus(), 0L) + 1);
                }
            }
            frameworks.add(testExe.getProduct_type());
            statusesSet.add(testExe.getStatus());
            statues.add(testExe.getStatus());
            temp.put("TC_RUN_ID", ReportUtils.createCustomObject(testExe.getTc_run_id(), "text", testExe.getTc_run_id(), "left"));
            temp.put("VARIANCEID", ReportUtils.createCustomObject(testExe.getVarianceId(), "text", testExe.getVarianceId(), "left"));
            temp.remove("STEPVARIANCEIDS");
            temp.remove("CLASSIFICATIONDETAILS");

            List<Map<String, Object>> statusMap = statusFilterMap.getOrDefault(testExe.getStatus().toUpperCase(), new ArrayList<>());
            statusMap.add(temp);
            statusFilterMap.put(testExe.getStatus().toUpperCase(), statusMap);

        }

        for (StatusColor statusColor : ReportUtils.getStatusColorInSorted()) {
            if (statusFilterMap.get(statusColor.toString().toUpperCase()) != null) {
                testcaseDetailsdata.addAll(statusFilterMap.get(statusColor.toString().toUpperCase()));
            }
        }
        testcaseDetails.put("data", testcaseDetailsdata);
        Map<String, Object> iconMap = getMapAccordingToSuiteVarianceAndFalsePositive(suiteVarianceIsActive, suiteFalsePositiveIsActive, suiteVarianceIsThere, suiteFalsePositiveIsThere);
        String finalStatus = "";
        int priority = Integer.MAX_VALUE;
        for (String status : statusesSet) {
            if (StatusColor.valueOf(status.toUpperCase()).priority < priority) {
                priority = StatusColor.valueOf(status.toUpperCase()).priority;
            }
        }
        for (StatusColor val : StatusColor.values()) {
            if (val.priority == priority) {
                finalStatus = val.name();
            }
        }

        testcaseDetailsHeaders.remove("varianceId");
        testcaseDetails.put("headers", testcaseDetailsHeaders);
        getSuite.setStatus(finalStatus);
        Map<String, Object> testCaseInfo = ReportUtils.testCaseInfoDoughnutChart(statues);
        if (testCaseInfo != null) {
            result.put("Testcase Info", testCaseInfo);
        }
        Map<String, Object> CategoryBarChart = ReportUtils.categoryStackedBarChartByS_run_id(categoryMap, category);
        if (CategoryBarChart != null) {
            result.put("Category_Bar_Chart", CategoryBarChart);
        }
        result.put("Execution Info", ReportUtils.createExecutionInfoHeaders(getSuite));
        result.put("Infra Headers", ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "infraDetails"));
        result.put("User Details", ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "userDetails"));
        result.put("Build Details", ReportUtils.createBuildHeaders(getSuite));
        result.put("Execution details", ReportUtils.createExecutionDetailsHeaders(tempTest));
        result.put("Time Details", ReportUtils.createTimeReportHeaders(tempTest,getSuite));
        result.put("status", getSuite.getStatus());

        ProjectRoleDto projectRole = RestApiUtils.getProjectRoleByPidAndUsername(project.getPid(), user);
        if(projectRole!=null) {
            result.put("Project role", projectRole.getRole());
        } else {
            result.put("Project role","ADMIN");
        }

        List<String> columns = columnMappingService.findColumnMapping(project.getPid(), getSuite.getReport_name(), new ArrayList<>(frameworks));
        if (columns != null && columns.size() > 0) {
            List<String> headers = new ArrayList<>();
            for (String header : testcaseDetailsHeaders) {
                headers.add(header.replace(" ", "_").toLowerCase());
            }
            List<String> finalHeaders = new ArrayList<>();
            for (String column : columns) {
                String value = column.toLowerCase().replace(" ", "_");
                if (headers.contains(value)) {
                    finalHeaders.add(value);
                    headers.remove(value);
                }
            }
            testcaseDetails.put("headers", finalHeaders);
            testcaseDetails.put("filterHeaders", headers);
            testcaseDetails.replace("headers", ReportUtils.headersDataRefactor((List<String>) testcaseDetails.get("headers")));
            testcaseDetails.replace("filterHeaders", ReportUtils.headersDataRefactor((List<String>) testcaseDetails.get("filterHeaders")));
            result.put("TestCase_Details", testcaseDetails);
            result.put("totalElements", getSuite.getTestcase_details().size());

            return new Response(result, DATA_FETCHED_SUCCESSFULLY, Success);
        }

        List<String> data = new ArrayList<>((Set<String>) testcaseDetails.get("headers"));
        testcaseDetails.replace("headers", ReportUtils.headersDataRefactor(data));
        result.put("TestCase_Details", testcaseDetails);
        result.put("totalElements", getSuite.getTestcase_details().size());

        return new Response(result, DATA_FETCHED_SUCCESSFULLY, Success);
    }

    public static Object createTimeReportHeaders(List<TestExeDto> testcaseList, SuiteExeDto getSuite) {
        Map<String, Object> timeSubType = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        timeSubType.put("Clock Start Time", getSuite.getS_start_time());
        if (getSuite.getS_end_time() != 0) {

            timeSubType.put("Clock End Time", getSuite.getS_end_time());
            timeSubType.put("Total Duration", getDuration(getSuite.getS_start_time(), getSuite.getS_end_time()));
        } else {
            timeSubType.put("Clock End Time", null);
            timeSubType.put("Total Duration", null);
        }

        data.put("Clock Time", timeSubType);
        timeSubType = new HashMap<>();

        long minStartTime = testcaseList.get(0).getStart_time();
        long maxEndTime = testcaseList.get(0).getEnd_time();

        for (TestExeDto test : testcaseList) {
            if (test.getStart_time() < minStartTime) {
                minStartTime = test.getStart_time();
            }
            if (test.getEnd_time() > maxEndTime) {
                maxEndTime = test.getEnd_time();
            }
        }

        timeSubType.put("Automation Start Time", minStartTime);
        if (maxEndTime != 0) {

            timeSubType.put("Automation End Time", maxEndTime);
            timeSubType.put("Total Duration", getDuration(minStartTime, maxEndTime));
        } else {
            timeSubType.put("Automation End Time", null);
            timeSubType.put("Total Duration", null);
        }
        data.put("Automation Time", timeSubType);

        return data;
    }

    public static Object createExecutionDetailsHeaders(List<TestExeDto> testcaseList) {
        StringBuilder run_type = new StringBuilder();
        StringBuilder run_mode = new StringBuilder();
        Set<String> run_typeSet = new HashSet<>();
        Set<String> run_modeSet = new HashSet<>();
        Map<String, Object> data = new HashMap<>();
        for (TestExeDto t : testcaseList) {
            if (!run_modeSet.contains(t.getRun_mode())) {
                run_mode.append(t.getRun_mode()).append(", ");
                run_modeSet.add(t.getRun_mode());
            }
            if (!run_typeSet.contains(t.getRun_type().toUpperCase(Locale.ROOT))) {
                if (t.getRun_type() != null) {
                    run_type.append(t.getRun_type().toUpperCase(Locale.ROOT)).append(", ");
                    run_typeSet.add(t.getRun_type().toUpperCase(Locale.ROOT));
                }
            }


        }
        if (!(run_mode.toString().equals("null, ") || run_mode.length() == 0)) {
            run_mode = new StringBuilder(run_mode.substring(0, run_mode.length() - 2));
            data.put("Run Mode", run_mode.toString());
        } else {
            data.put("Run Mode", null);
        }

        if (!(run_type.toString().equals("null, ") || run_type.length() == 0)) {
            run_type = new StringBuilder(run_type.substring(0, run_type.length() - 2));
            data.put("Run Type", run_type.toString());
        } else {
            data.put("Run Type", null);
        }

        if (testcaseList!=null && !testcaseList.isEmpty() && testcaseList.get(0).getJob_name() != null) {
            data.put("Job Name", testcaseList.get(0).getJob_name());
        } else {
            data.put("Job Name", null);
        }

        return data;

    }

    public static Object createBuildHeaders(SuiteExeDto getSuite) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("Build ID", getSuite.getBuild_id());
        data.put("Sprint Name", getSuite.getSprint_name());

        return data;
    }

    public static Object createInfraAndUserHeaders(List<TestExeDto> testcaseList, SuiteExeDto getSuite, String dataCategory) {
        StringBuilder machine = new StringBuilder();
        String os = getSuite.getOs();
        StringBuilder base_user = new StringBuilder();
        StringBuilder token_user = new StringBuilder();
        String framework_version = getSuite.getFramework_version();
        String framework_name = getSuite.getFramework_name();

        Set<String> machineSet = new HashSet<>();
        Set<String> baseUserSet = new HashSet<>();
        Set<String> tokenUserSet = new HashSet<>();
        Map<String, Object> data = new HashMap<>();

        if (dataCategory.equalsIgnoreCase("infraDetails")) {
            for (TestExeDto t : testcaseList) {
                if (t.getMachine() != null) {
                    if (!machineSet.contains(t.getMachine())) {
                        machine.append(t.getMachine()).append(", ");
                        machineSet.add(t.getMachine());
                    }
                }

            }

            if (!(machine.toString().equals("null, ") || machine.length() == 0)) {
                machine = new StringBuilder(machine.substring(0, machine.length() - 2));
                data.put("Machine", machine.toString());
            } else if (getSuite.getMachine()!=null) {
                data.put("Machine",getSuite.getMachine());
            }
            else {
                data.put("Machine", null);
            }

            if (!(os == null || os.length() == 0)) {
                data.put("OS", os);
            } else {
                data.put("OS", null);
            }

            if (!(framework_name == null || framework_name.length() == 0) && !(framework_version == null || framework_version.length() == 0)) {
                data.put("Framework", framework_name+" "+framework_version);
            }


        }

        if (dataCategory.equalsIgnoreCase("userDetails")) {

            for (TestExeDto t : testcaseList) {
                if (t.getToken_user() != null) {
                    if (!tokenUserSet.contains(t.getToken_user().toArray(new String[t.getToken_user().size()])[0])) {
                        token_user.append(t.getToken_user().toArray(new String[t.getToken_user().size()])[0]).append(",");
                        tokenUserSet.add(t.getToken_user().toArray(new String[t.getToken_user().size()])[0]);
                    }
                }
                if (t.getBase_user() != null) {
                    if (!baseUserSet.contains(t.getBase_user())) {
                        base_user.append(t.getBase_user()).append(",");
                        baseUserSet.add(t.getBase_user());
                    }
                }

            }

            if (!(base_user.toString().equals("null,") || base_user.length() == 0)) {
                base_user = new StringBuilder(base_user.substring(0, base_user.length() - 1));
                data.put("Machine Base User", base_user.toString());
            } else if (getSuite.getUser()!=null) {
                data.put("Machine base user",getSuite.getUser());
            } else {
                data.put("Machine Base User", null);
            }

            if (!(token_user.toString().equals("null,") || token_user.length() == 0)) {
                token_user = new StringBuilder(token_user.substring(0, token_user.length() - 1));
                data.put("Jewel Token User", token_user.toString());
            } else {
                data.put("Jewel Token User", null);
            }

        }

        return data;

    }

    public static Object createExecutionInfoHeaders(SuiteExeDto getSuite) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("Status", getSuite.getStatus());
        data.put("Project Name", StringUtils.capitalize(getSuite.getProject_name()));
        data.put("Env", StringUtils.capitalize(getSuite.getEnv()));
        data.put("Report Name", StringUtils.capitalize(getSuite.getReport_name()));

        return data;
    }

    public void populateResultWithTestExes(
            List<TestExeDto> tempTest,
            Map<Long, VarianceClassificationDto> varianceList,
            List<Long> varianceIds,
            Set<String> testcaseDetailsHeaders,
            Map<String, Object> testcaseDetails,
            Map<String, Object> statusSubType,
            Map<String, List<Map<String, Object>>> statusFilterMap,
            List<Map<String, Object>> testcaseDetailsData,
            SuiteExeDto getSuite,
            String expectedStatus,
            Map<String, Object> result,
            ProjectDto project
    ) {
        Set<String> frameworks = new HashSet<>();
        List<String> statuses = new ArrayList<>();
        long testcaseCountWithoutExe = 0L;
        boolean suiteVarianceIsActive = false;
        boolean suiteVarianceIsThere = false;
        boolean suiteFalsePositiveIsActive = false;
        boolean suiteFalsePositiveIsThere = false;
        for (TestExeDto testExe : tempTest) {
            boolean clickable = false;
            boolean varianceIsActive = false;
            boolean varianceIsThere = false;
            boolean falsePositiveIsActive = false;
            boolean falsePositiveIsThere = false;
            if (testExe.getVarianceId() != null || (testExe.getStepVarianceIds() != null && !testExe.getStepVarianceIds().isEmpty())) {
                varianceIsThere = true;
                suiteVarianceIsThere = true;
                if(varianceList.get(testExe.getVarianceId()) != null) {
                    varianceIsActive = true;
                    suiteVarianceIsActive = true;
                    clickable = true;
                    testExe.setStatus("PASS");
                }
                if (ReportUtils.checkoneListContainsElementOfAnotherList(varianceIds, testExe.getStepVarianceIds())) {
                    varianceIsActive = true;
                    suiteVarianceIsActive = true;
                    testExe.setStatus(ReportUtils.checkStatusOfTestCaseByStepsIfVarianceIsThere(testExe.getTc_run_id(), varianceList));
                }
            }
            if (testExe.getClassificationDetails() != null) {
                suiteFalsePositiveIsThere = true;
                falsePositiveIsThere = true;
                if (testExe.getClassificationDetails().isFalsePositiveStatus()) {
                    suiteFalsePositiveIsActive = true;
                    falsePositiveIsActive = true;
                    clickable = true;
                }
                if (testExe.getClassificationDetails().isChildFalsePostiveStatus()) {
                    suiteFalsePositiveIsActive = true;
                    falsePositiveIsActive = true;
                }
            }
            ObjectMapper oMapper = new ObjectMapper();
            LinkedHashMap<String, Object> map = oMapper.convertValue(testExe, LinkedHashMap.class);
            if (testExe.getUser_defined_data() != null) {
                map.putAll(testExe.getUser_defined_data());
            }
            map.remove("user_defined_data");
            map.remove("steps");
            map.remove("meta_data");
            map.remove("ignore");
            map.remove("log_file");
            map.remove("result_file");
            map.remove("s_run_id");
            map.remove("tc_run_id");
            map.remove("job_name");
            map.remove("classificationDetails");
            map.remove("varianceId");
            map.remove("stepVarianceIds");
            map.remove("testcase_id");
            map.remove("job_name");
            testcaseDetailsHeaders.remove("classificationDetails");
            testcaseDetailsHeaders.remove("stepVarianceIds");
            testcaseDetailsHeaders.addAll(map.keySet());


            testcaseDetails.put("headers", testcaseDetailsHeaders);
            Map<String, Object> temp = new HashMap<>();

            for (String key : map.keySet()) {
                populateTempAccordingToKey(key, temp, map, statusSubType);
            }

            populateIconProperties(testExe, temp, clickable, varianceIsActive, falsePositiveIsActive, varianceIsThere, falsePositiveIsThere);

            statuses.add(testExe.getStatus());
            frameworks.add(testExe.getProduct_type());
            if (!testExe.getStatus().equalsIgnoreCase("EXE")) {
                testcaseCountWithoutExe += 1;
            }

            temp.put("VARIANCEID", ReportUtils.createCustomObject(testExe.getVarianceId(), "text", testExe.getVarianceId(), "left"));
            temp.put("TC_RUN_ID", ReportUtils.createCustomObject(testExe.getTc_run_id(), "text", testExe.getTc_run_id(), "left"));
            temp.remove("STEPVARIANCEIDS");
            temp.remove("CLASSIFICATIONDETAILS");
            List<Map<String, Object>> statusMap = statusFilterMap.getOrDefault(testExe.getStatus().toUpperCase(), new ArrayList<>());
            statusMap.add(temp);
            statusFilterMap.put(testExe.getStatus().toUpperCase(), statusMap);
        }
        if (statusFilterMap.get("EXE") != null) {
            testcaseDetailsData.addAll(statusFilterMap.get("EXE"));
            statusFilterMap.put("EXE", null);
        }
        for (StatusColor statusColor : ReportUtils.getStatusColorInSorted()) {
            if (statusFilterMap.get(statusColor.toString().toUpperCase()) != null) {
                testcaseDetailsData.addAll(statusFilterMap.get(statusColor.toString().toUpperCase()));
            }
        }
        testcaseDetailsHeaders.remove("varianceId");
        testcaseDetails.put("data", testcaseDetailsData);
        testcaseDetails.put("headers", testcaseDetailsHeaders);

        Map<String, Object> response = RestApiUtils.getCountByStatusList(statuses,getSuite.getS_run_id());
        ObjectMapper om = new ObjectMapper();
        Map<String, Long> testcaseInfo = om.convertValue(response.get("testcaseInfo"), new TypeReference<>() {});
        expectedStatus = om.convertValue(response.get("expectedStatus"), new TypeReference<>() {});

        if (expectedStatus.equalsIgnoreCase("EXE")) {
            expectedStatus = "PASS";
        }
        Map<String, Object> exeData = new HashMap<>();
        Map<String, Object> testcaseProgress = new HashMap<>();
        Map<String, Object> expectedStatusMap = new HashMap<>();
        expectedStatusMap.put("expected", expectedStatus);
        expectedStatusMap.put("current", getSuite.getStatus());
        testcaseProgress.put("expected",
                getSuite.getExpected_testcases() != null ? getSuite.getExpected_testcases() : 0);
        testcaseProgress.put("executed", testcaseCountWithoutExe);

        SuiteRun suiteRunData = RestApiUtils.getSuiteRun(getSuite.getS_run_id());
        List<List<DependencyTree>> ans = new ArrayList<>();
        assert suiteRunData != null;
        for (SuiteRunValues suiteRunValues : suiteRunData.getValues()) {
            if (suiteRunValues.getExpected_testcases() != null) {
                ans.addAll(suiteRunValues.getExpected_testcases());
            }
        }
        exeData.put("testcase_progress", testcaseProgress);
        exeData.put("expected_status", expectedStatusMap);

        exeData.put("expected_completion",
                Math.round(RestApiUtils.getTimeRemainingNew(getSuite, ans)));
        result.put("Infra Headers", ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "infraDetails"));
        result.put("User Details", ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "userDetails"));
        result.put("Execution details", ReportUtils.createExecutionDetailsHeaders(tempTest));

        testcaseInfo.put("EXE",getSuite.getExpected_testcases() - testcaseCountWithoutExe);

//        if (statuses.isEmpty()) {
//            testcaseInfo = null;
//        }

        Map<String,Object> tcInfoData = new HashMap<>();
        List<Map.Entry<String, Long>> legend = new ArrayList<>(testcaseInfo.entrySet());
        tcInfoData.put("value", ReportUtils.createDoughnutChart(testcaseInfo));
        tcInfoData.put("legend", legend);
        exeData.put("testcase_info", tcInfoData);

        List<String> columns = columnMappingService.findColumnMapping(project.getPid(), getSuite.getReport_name(), new ArrayList<>(frameworks));
        if (columns != null && !columns.isEmpty()) {
            List<String> headers = new ArrayList<>();
            for (String header : testcaseDetailsHeaders) {
                headers.add(header.replace(" ", "_").toLowerCase());
            }
            List<String> finalHeaders = new ArrayList<>();
            for (String column : columns) {
                String value = column.toLowerCase().replace(" ", "_");
                if (headers.contains(value)) {
                    finalHeaders.add(value);
                    headers.remove(value);
                }
            }
            testcaseDetails.put("headers", finalHeaders);
            testcaseDetails.put("filterHeaders", headers);
            testcaseDetails.replace("headers", ReportUtils.headersDataRefactor((List<String>) testcaseDetails.get("headers")));
            testcaseDetails.replace("filterHeaders", ReportUtils.headersDataRefactor((List<String>) testcaseDetails.get("filterHeaders")));
            result.put("exe_data", exeData);
            result.put("TestCase_Details", testcaseDetails);
        } else {
            List<String> data = new ArrayList<>();
            data.addAll((Set<String>) testcaseDetails.get("headers"));
            testcaseDetails.replace("headers", ReportUtils.headersDataRefactor(data));
            result.put("exe_data", exeData);
            result.put("TestCase_Details", testcaseDetails);
        }
    }

    /**
     * @return Username from the httpServletRequest
     */
    public static String getUsernameFromServletRequest() {
        String username = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        log.info("Username from servlet request: {}", username);
        return username;
    }

    /**
     * Return user from servlet request.
     *
     * @return user
     */
    public static UserDto getUserDtoFromServetRequest() {
        String username = getUsernameFromServletRequest();
        UserDto userDto = getUsernameAndIsDeleted(username, false);
        if (userDto == null) {
            log.error("Error occurred while trying to fetch user for username: {}", username);
            throw new CustomDataException(USER_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        }
        log.info("User details from servlet request: {}", userDto);
        return userDto;
    }

//    /**
//     * Returns user details by username and IsDeleted
//     *
//     * @param username
//     * @param deleted
//     * @return user
//     */
//    public static UserDto getUsernameAndIsDeleted(String username, Boolean deleted) {
//        HttpHeaders headers = new HttpHeaders();
//        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
//        HttpEntity httpEntity = new HttpEntity(null, headers);
//        Map<String, Object> uriVariables = new HashMap<>();
//        uriVariables.put("username", username);
//        uriVariables.put("deleted", deleted);
//        return (UserDto) RestClient.getApi(userManagerUrl + "/v1/username/deleted?username={username}&deleted={deleted}", httpEntity, UserDto.class, uriVariables).getBody();
//    }

    /**
     * Returns user details by username and IsDeleted
     *
     * @param username
     * @param deleted
     * @return user
     */
    public static UserDto getUsernameAndIsDeleted(String username, Boolean deleted) {
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("username", username);
        uriVariables.put("deleted", deleted);

        String url = userManagerUrl + "/userManagement/v1/username/deleted?username={username}&deleted={deleted}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if(response != null && response.getOperation().equals(Success)) {
                return mapper.convertValue(response.getData(), new TypeReference<>() {});
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("User details not found for username: {}, ", username);
            return null;
        } catch (RestClientException ex) {
            log.error(ex.getMessage());
            throw new CustomDataException("Something went wrong. Please try again later !!",null,Failure, HttpStatus.OK);

        }
        return null;
    }

    /**
     * To create custom map object.
     *
     * @param value
     * @param type
     * @param sortValue
     * @param align
     * @return map
     */
    public static Map<String, Object> createCustomObject(Object value, String type, Object sortValue, String align) {
        Map<String, Object> result = new HashMap<>();
        result.put("value", value);
        result.put("type", type);
        result.put("sortValue", sortValue);
        result.put("align", align);
        return result;
    }

    /**
     * To create custom map object.
     *
     * @param value
     * @param type
     * @param sortValue
     * @param align
     * @param map
     * @return map
     */
    public static Map<String, Object> createCustomObject(Object value, String type, Object sortValue, String align, Map<String, Object> map) {
        Map<String, Object> result = new HashMap<>();
        result.put("value", value);
        result.put("type", type);
        result.put("sortValue", sortValue);
        result.put("align", align);
        result.putAll(map);
        return result;
    }

    public static Map<String, String> getSuiteColumnName() {
        Map<String, String> columns = new HashMap<>();
        columns.put("suite name", "report_name");
        columns.put("project name", "project_name");
        columns.put("environment", "env");
        columns.put("status", "status");
        columns.put("framework", "framework_name");
        columns.put("start time", "s_start_time");
        columns.put("end time", "s_end_time");
        return columns;
    }

    public static boolean validateRoleWithViewerAccess(UserDto user, ProjectDto project) {
        if (project == null) {
            return false;
        }
        ProjectRoleDto projectRole = getActiveProjectRole(project.getPid(), user.getUsername());
        return projectRole != null || ((user.getRole().equalsIgnoreCase(ADMIN.toString()) && project.getRealcompanyname().equalsIgnoreCase(user.getRealCompany())) || user.getRole().equalsIgnoreCase(UserRole.SUPER_ADMIN.toString()));
    }

    public static ProjectRoleDto getActiveProjectRole(Long pid, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        String url = projectManagerUrl + "/v2/project/role/entity?pid={pid}&userName={username}&status=ACTIVE";
        uriVariables.put("username", username);
        uriVariables.put("pid", pid);
        ProjectRoleDto projectRole;
        try {
            ResponseEntity response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Object.class,
                    uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new com.google.gson.reflect.TypeToken<ProjectRoleDto>() {
            }.getType();
            projectRole = gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Error occurred due to ProjectRole details not found for username: {}", username);
            return null;
        }
        log.info("Project Role Details ==> {}", projectRole);
        return projectRole;
    }

//    public static Map<String, Object> getAllTestExesForTcRunId(RuleApi payload, Integer pageNo, Integer sort,
//                                                               String sortedColumn) throws ParseException {
//        Map<String, Object> data = new HashMap<>();
//        Map<String, String> getTestcaseColumnName = getTestcaseColumnName();
//        long startTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getStartTime()).getTime();
//        long endTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getEndTime()).getTime()
//                + (1000 * 60 * 60 * 24);
//        List<String> projects = payload.getProject();
//        projects.replaceAll(String::toLowerCase);
//        List<String> envs = payload.getEnv();
//        envs.replaceAll(String::toLowerCase);
//        List<Criteria> criteria2 = new ArrayList<>();
//        criteria2.add(Criteria.where("result.p_id").in(payload.getProjectid()));
//        criteria2.add(Criteria.where("result.env").in(envs));
//        criteria2.add(Criteria.where("start_time").gte(startTime));
//        criteria2.add(Criteria.where("end_time").lte(endTime));
//
//        LookupOperation lookupOperation = LookupOperation.newLookup()
//                .from("suiteExe")
//                .localField("s_run_id")
//                .foreignField("s_run_id")
//                .as("result");
//        MatchOperation matchOperation = Aggregation
//                .match(new Criteria().andOperator(criteria2.toArray(new Criteria[criteria2.size()])));
//        LimitOperation limitOperation = null;
//        SkipOperation skipOperation = null;
//        SortOperation sortOperation = null;
//        Aggregation aggregation = null;
//        if (pageNo != null && pageNo > 0) {
//            skipOperation = Aggregation.skip((((pageNo - 1) * 8L)));
//            limitOperation = Aggregation.limit(pageNo * 8);
//        }
//
//        if (sort != null && sort != 0 && sortedColumn != null) {
//            sortOperation = Aggregation.sort(sort == 1 ? Sort.Direction.ASC : Sort.Direction.DESC,
//                    getTestcaseColumnName.get(sortedColumn.toLowerCase()));
//        }
//
//        if (pageNo != null && (sort == null || sortedColumn == null)) {
//            aggregation = Aggregation.newAggregation(lookupOperation, matchOperation, limitOperation, skipOperation);
//        } else if (pageNo == null && sort != null && sortedColumn != null) {
//            aggregation = Aggregation.newAggregation(lookupOperation, matchOperation, sortOperation);
//        } else if (pageNo != null) {
//            aggregation = Aggregation.newAggregation(lookupOperation, matchOperation, sortOperation, skipOperation,
//                    limitOperation);
//        } else {
//            aggregation = Aggregation.newAggregation(lookupOperation, matchOperation);
//        }
//
//        CountOperation countOperation = new CountOperation("tc_run_id");
//        Aggregation countAggregation = Aggregation.newAggregation(lookupOperation, matchOperation, countOperation);
//        BasicDBObject countResult = mongoOperations.aggregate(countAggregation, "testExe", BasicDBObject.class)
//                .getUniqueMappedResult();
//        long count = 0;
//        if (countResult != null) {
//            count = ((Number) countResult.get("tc_run_id")).longValue();
//        }
//        List<BasicDBObject> results = mongoOperations.aggregate(aggregation, "testExe", BasicDBObject.class)
//                .getMappedResults();
//        data.put("count", count);
//        data.put("results", results);
//        return data;
//    }

    public Map<String, String> getTestcaseColumnName() {
        Map<String, String> columns = new HashMap<>();
        columns.put("name", "name");
        columns.put("category", "category");
        columns.put("status", "status");
        columns.put("user", "user");
        columns.put("product Type", "product_type");
        columns.put("start time", "start_time");
        columns.put("end time", "end_time");
        columns.put("machine", "machine");
        return columns;
    }

//    public static double brokenIndexForTestExe(List<TestExeCommonDto> testExes) {
//
//        if (testExes.size() == 1) {
//            if (testExes.get(0).getStatus().equalsIgnoreCase("FAIL")) {
//                return 1;
//            } else {
//                return 0;
//            }
//        }
//        double denom = Math.floor(testExes.size() / (double) 2);
//
//        double numenator = 0;
//        int count = 0;
//        TestExeCommonDto curr = null;
//        TestExeCommonDto prev = null;
//
//        for (TestExeCommonDto testExe : testExes) {
//
//            if (curr == null && prev == null) {
//                if (testExe.getStatus().equalsIgnoreCase("FAIL")) {
//                    count++;
//                }
//                curr = testExe;
//            } else {
//                if (testExe.getStatus().equalsIgnoreCase("FAIL")) {
//                    count++;
//                }
//                prev = curr;
//                curr = testExe;
//                if ((prev.getStatus().equalsIgnoreCase("PASS") || prev.getStatus().equalsIgnoreCase("INFO")
//                        || prev.getStatus().equalsIgnoreCase("EXE") || prev.getStatus().equalsIgnoreCase("WARN"))
//                        && (curr.getStatus().equalsIgnoreCase("FAIL") || curr.getStatus().equalsIgnoreCase("ERR"))) {
//                    numenator++;
//                }
//
//            }
//        }
//
//        if (count == testExes.size()) {
//            return 1;
//        }
//        double res = Math.round((float) (numenator / denom) * 100.0) / 100.0;
//        return res;
//    }

//        public static double brokenIndexForSuiteExe(List<SuiteExeDto> suites) {
//
//        if (suites.size() == 1) {
//            if (suites.get(0).getStatus().equalsIgnoreCase("FAIL")) {
//                return 1;
//            } else {
//                return 0;
//            }
//        }
//        double denominator = Math.floor(suites.size() / (double) 2);
//
//        double numerator = 0;
//        int count = 0;
//        SuiteExeDto curr = null;
//        SuiteExeDto prev = null;
//
//        for (SuiteExeDto suite : suites) {
//
//            if (curr == null && prev == null) {
//                if (suite.getStatus().equalsIgnoreCase("FAIL")) {
//                    count++;
//                }
//                curr = suite;
//            } else {
//                if (suite.getStatus().equalsIgnoreCase("FAIL")) {
//                    count++;
//                }
//                prev = curr;
//                curr = suite;
//                if ((prev.getStatus().equalsIgnoreCase("PASS") || prev.getStatus().equalsIgnoreCase("INFO")
//                        || prev.getStatus().equalsIgnoreCase("EXE") || prev.getStatus().equalsIgnoreCase("WARN"))
//                        && (curr.getStatus().equalsIgnoreCase("FAIL") || curr.getStatus().equalsIgnoreCase("ERR"))) {
//                    numerator++;
//                }
//
//            }
//        }
//        if (count == suites.size()) {
//            return 1;
//        }
//        double res = Math.round((float) (numerator / denominator) * 100.0) / 100.0;
//        return res;
//    }

    public static double brokenIndexForTestExe(List<TestExeCommonDto> testExes) {
        if (testExes.isEmpty()) {
            return 0;
        }
        int totalTestExes = testExes.size();
        int failCount = 0;
        int transitions = 0;

        TestExeCommonDto prevTestExe = null;
        for (TestExeCommonDto currentTestExe : testExes) {
            if (currentTestExe.getStatus().equalsIgnoreCase("FAIL")) {
                failCount++;
            }
            if (prevTestExe != null && isTransition(prevTestExe.getStatus(), currentTestExe.getStatus())) {
                transitions++;
            }
            prevTestExe = currentTestExe;
        }
        if (failCount == totalTestExes) {
            return 1.0;
        }
        if (transitions == 0) {
            return 0.0;
        }
        return Math.round((double) transitions / (totalTestExes/(double)2) * 100.0) / 100.0;
    }

    public static double brokenIndexForSuiteExe(List<SuiteExeDto> suites) {
        if (suites.isEmpty()) {
            return 0;
        }
        int totalSuites = suites.size();
        int failCount = 0;
        int transitions = 0;

        SuiteExeDto prevSuite = null;
        for (SuiteExeDto currentSuite : suites) {
            if (currentSuite.getStatus().equalsIgnoreCase("FAIL")) {
                failCount++;
            }
            if (prevSuite != null && isTransition(prevSuite.getStatus(), currentSuite.getStatus())) {
                transitions++;
            }
            prevSuite = currentSuite;
        }
        if (failCount == totalSuites) {
            return 1.0;
        }
        if (transitions == 0) {
            return 0.0;
        }

        return Math.round((double) transitions / (totalSuites/(double)2) * 100.0) / 100.0;
    }

    private static boolean isTransition(String status1, String status2) {
        // Define your transition logic here
        return (isNonFailure(status1) && isFailure(status2));
    }

    private static boolean isNonFailure(String status) {
        return status.equalsIgnoreCase("PASS") || status.equalsIgnoreCase("INFO")
                || status.equalsIgnoreCase("EXE") || status.equalsIgnoreCase("WARN");
    }

    private static boolean isFailure(String status) {
        return status.equalsIgnoreCase("FAIL") || status.equalsIgnoreCase("ERR");
    }

    public static String averageFixTimeForTestExeCommon(List<TestExeCommonDto> testExes) {

        TestExeCommonDto curr = null;
        TestExeCommonDto prev = null;
        List<Long> averageTimes = new ArrayList<>();

        for (TestExeCommonDto testExe : testExes) {

            if (curr == null && prev == null) {

                curr = testExe;
            } else {

                prev = curr;
                curr = testExe;
                if ((curr.getStatus().equalsIgnoreCase("PASS") || curr.getStatus().equalsIgnoreCase("INFO")
                        || curr.getStatus().equalsIgnoreCase("EXE") || curr.getStatus().equalsIgnoreCase("WARN"))
                        && (prev.getStatus().equalsIgnoreCase("FAIL") || prev.getStatus().equalsIgnoreCase("ERR"))) {
                    averageTimes.add(curr.getStart_time() - prev.getStart_time());
                }

            }
        }
        double sum = 0;
        for (double current : averageTimes) {
            sum = sum + current;

        }
        double average = sum / averageTimes.size();
        average = average / 1000;

        String res = "";
        DecimalFormat df = new DecimalFormat("#");
        if (average < 60) {
            res = average + " sec(s)";
        } else if (average >= 60 && average < 3600) {
            String ans = df.format(Math.floor(average / 60)) + " min ";
            if (Math.floor(average % 60) != 0) {
                ans = ans + df.format(Math.floor(average % 60)) + " sec(s)";
            }
            res = ans;
        } else if (average >= 60 && average < 86400) {
            String ans = df.format(Math.floor(average / 3600)) + " hour ";
            if (Math.floor((average % 3600) / 60) != 0) {
                ans = ans + df.format(Math.floor((average % 3600) / 60)) + " min ";
            }
            if (Math.floor((average % 3600) % 60) != 0) {
                ans = ans + df.format(Math.floor((average % 3600) % 60)) + " sec(s)";
            }

            res = ans;
        } else {
            String ans = df.format(Math.floor(average / 86400)) + " days ";
            if (Math.floor((average % 86400) / 3600) != 0) {
                ans = ans + df.format(Math.floor((average % 86400) / 3600)) + " hours ";
            }
            res = ans;
        }

        if (res.equals("") || sum == 0) {
            res = "Never Broken";
        }
        return res;
    }

    public static long averageFixTimeForSuiteExe(List<SuiteExeDto> suites) {

        SuiteExeDto curr = null;
        SuiteExeDto prev = null;
        List<Long> averageTimes = new ArrayList<>();

        for (SuiteExeDto suite : suites) {

            if (curr == null) {

                curr = suite;
            } else {

                prev = curr;
                curr = suite;
                if ((curr.getStatus().equalsIgnoreCase("PASS") || curr.getStatus().equalsIgnoreCase("INFO")
                        || curr.getStatus().equalsIgnoreCase("EXE") || curr.getStatus().equalsIgnoreCase("WARN"))
                        && (prev.getStatus().equalsIgnoreCase("FAIL") || prev.getStatus().equalsIgnoreCase("ERR"))) {
                    averageTimes.add(curr.getS_start_time() - prev.getS_start_time());
                }

            }
        }
        double sum = 0;
        for (double current : averageTimes) {
            sum = sum + current;

        }
        double average = sum / averageTimes.size();
        average = average / 1000;
        return (long) average;

    }

    public static long getDownTimeForSuiteExe(List<SuiteExeDto> suites) {
        if (suites.get(0).getStatus().equalsIgnoreCase("PASS")) {
            return 0;
        } else {
            SuiteExeDto prev = null;
            long firstFailTime = 0;
            for (SuiteExeDto suite : suites) {
                if (suite.getStatus().equalsIgnoreCase("PASS")) {
                    break;
                }
                prev = suite;
            }
            if (prev != null) {
                firstFailTime = prev.getS_start_time();
            }

            Date d = new Date();
            long downTime = (d.getTime() - firstFailTime) / 1000;
            if (downTime < 0) {
                return 0;
            }
            return downTime;
        }
    }

    public static int stabilityIndex(double brokenIndex) {
        return 100 - ((int) (100 * brokenIndex));
    }


    public static String lastRunStatusForTestExeCommon(List<TestExeCommonDto> testExes) {
        return testExes.get(0).getStatus();
    }

    public static String getFailingSinceForTestExeCommon(List<TestExeCommonDto> testExes, double brokenIndex) {
        if (brokenIndex == 0) {
            return NO_ISSUES;
        } else if (brokenIndex == 1) {
            return NEVER_FIXED;
        } else {
            int count = 0;
            for (TestExeCommonDto testExe : testExes) {
                if (!testExe.getStatus().equalsIgnoreCase("FAIL")) {
                    break;
                }
                count++;

            }
            if (count == 0) {
                return NO_ISSUES;
            }
            return "Last " + count + " Runs";
        }
    }

    public static Long getLastPassForTestExeCommon(List<TestExeCommonDto> testExes) {
        for (TestExeCommonDto testExe : testExes) {
            if (testExe.getStatus().equalsIgnoreCase("PASS")) {
                return testExe.getStart_time();
            }

        }
        return 0L;
    }

    public static String getDownTimeForTestExeCommon(List<TestExeCommonDto> testExes) {
        if (testExes.get(0).getStatus().equalsIgnoreCase("PASS")) {
            return NO_ISSUES;
        } else {
            TestExeCommonDto prev = null;
            long firstFailTime = 0;
            for (TestExeCommonDto testExe : testExes) {
                if (testExe.getStatus().equalsIgnoreCase("PASS")) {
                    break;
                }
                prev = testExe;
            }
            if (prev != null) {
                firstFailTime = prev.getEnd_time();
            }

            Date d = new Date();
            long downTime = (d.getTime() - firstFailTime) / 1000;
            if (downTime < 0) {
                return NO_ISSUES;
            }
            String res = "";
            DecimalFormat df = new DecimalFormat("#");
            if (downTime < 60) {
                res = downTime + " sec(s)";
            } else if (downTime >= 60 && downTime < 3600) {
                String ans = df.format(Math.floor(downTime / (double) 60)) + " min ";
                if (Math.floor(downTime % 60) != 0) {
                    ans = ans + df.format(Math.floor(downTime % 60)) + " sec(s)";
                }
                res = ans;
            } else if (downTime >= 60 && downTime < 86400) {
                String ans = df.format(Math.floor(downTime / (double) 3600)) + " hour ";
                if (Math.floor((downTime % 3600) / (double) 60) != 0) {
                    ans = ans + df.format(Math.floor((downTime % 3600) / (double) 60)) + " min ";
                }
                if (Math.floor((downTime % 3600) % 60) != 0) {
                    ans = ans + df.format(Math.floor((downTime % 3600) % 60)) + " sec(s)";
                }

                res = ans;
            } else {
                String ans = df.format(Math.floor(downTime / (double) 86400)) + " days ";
                if (Math.floor((downTime % 86400) / (double) 3600) != 0) {
                    ans = ans + df.format(Math.floor((downTime % 86400) / (double) 3600)) + " hours ";
                }
                res = ans;
            }

            return res;
        }
    }

    public static TestExeCommonDto getTestExeCommonByBasicObjectAndDocument(BasicDBObject testExe, Document suiteExe) {
        TestExeCommonDto testExeDiagnose = new TestExeCommonDto();
        testExeDiagnose.setEnv((String) suiteExe.get("env"));
        testExeDiagnose.setReport_name((String) suiteExe.get("report_name"));
        testExeDiagnose.setProject_name((String) suiteExe.get("project_name"));
        testExeDiagnose.setTc_run_id((String) testExe.get("tc_run_id"));
        testExeDiagnose.setStart_time((Long) testExe.get("start_time"));
        testExeDiagnose.setEnd_time((Long) testExe.get("end_time"));
        testExeDiagnose.setName((String) testExe.get("name"));
        testExeDiagnose.setCategory((String) testExe.get("category"));
        testExeDiagnose.setLog_file((String) testExe.get("log_file"));
        testExeDiagnose.setStatus((String) testExe.get("status"));
        testExeDiagnose.setUser((String) testExe.get("user"));
        testExeDiagnose.setMachine((String) testExe.get("machine"));
        testExeDiagnose.setResult_file((String) testExe.get("result_file"));
        testExeDiagnose.setProduct_type((String) testExe.get("product_type"));
        testExeDiagnose.setIgnore((Boolean) testExe.get("ignore"));
        testExeDiagnose.setSteps((List<Object>) testExe.get("steps"));
        testExeDiagnose.setMiscData((List<Map<String, Object>>) testExe.get("miscData"));
        testExeDiagnose.setUserDefinedData((Map<String, Object>) testExe.get("userDefinedData"));
        testExeDiagnose.setS_run_id((String) testExe.get("s_run_id"));
        testExeDiagnose.setP_id(((Double)suiteExe.get("p_id")).longValue());
        return testExeDiagnose;
    }

    public static Object createDoughnutChart(Map<String, Long> res) {

        Map<String, Object> map = new HashMap<>();
        List<String> labels = new ArrayList<>();
        labels.addAll(res.keySet());

        List<Object> datasets = new ArrayList<>();
        Map<String, Object> datasetValue = new HashMap<>();
        List<Long> data = new ArrayList<>();
        List<String> backgroundColor = new ArrayList<>();
        List<String> borderColor = new ArrayList<>();
        for (String status : labels) {
            data.add(res.get(status));
            backgroundColor.add(StatusColor.valueOf(status).color);
            borderColor.add(StatusColor.valueOf(status).color);

        }
        datasetValue.put("data", data);
        datasetValue.put("borderWidth", 1);
        datasetValue.put("backgroundColor", backgroundColor);
        datasetValue.put("borderColor", borderColor);
        datasets.add(datasetValue);
        map.put("datasets", datasets);
        map.put("labels", labels);
        return map;

    }

    public static double getScore(double brokenIndex) {
        return Math.round((999 - (999 * (0.35 * brokenIndex))) * 100.0) / 100.0;
    }

    public static double getScore(double brokenIndex, long downTime, long averageFixTime, String env,
                                  List<SuiteExeDto> suiteExeList) {
        double averageFixTimeScore = 25;
        double downTimeScore = 15;
        double averageFixTimeCount = 0;
        double downTimeCount = 0;
        double testCaseScore = 15;
        double suiteScore = 10;
        if (env.equalsIgnoreCase("prod")) {
            averageFixTimeCount = Math.floor((Math.floor(averageFixTime / (double) 60)) / 30);
            downTimeCount = Math.floor((Math.floor(downTime / (double) 60)) / 15);
        } else if (env.equalsIgnoreCase("beta") || env.equalsIgnoreCase("uat") || env.equalsIgnoreCase("pre-prod")) {
            averageFixTimeCount = Math.floor((Math.floor(averageFixTime / (double) 60)) / 180);
            downTimeCount = Math.floor((Math.floor(downTime / (double) 60)) / 90);
        } else {
            averageFixTimeCount = Math.floor((Math.floor(averageFixTime / (double) 60)) / 1440);
            downTimeCount = Math.floor((Math.floor(downTime / (double) 60)) / 1440);
        }
        averageFixTimeScore = averageFixTimeScore - averageFixTimeCount;
        downTimeScore = downTimeScore - downTimeCount;
        if (averageFixTimeScore < 0) {
            averageFixTimeScore = 0;
        }
        if (downTimeScore < 0) {
            downTimeScore = 0;
        }
        double count = 0;
        List<String> sRunIdsList = new ArrayList<>();
        for (SuiteExeDto suiteExe : suiteExeList) {
            sRunIdsList.add(suiteExe.getS_run_id());
            if (suiteExe.getStatus().equalsIgnoreCase("FAIL") || suiteExe.getStatus().equalsIgnoreCase("ERR")) {
                count++;
            }
        }
        Map<String, Double> testCaseCountMap = RestApiUtils.getTestCaseCount(sRunIdsList, List.of("ERR", "FAIL"));
        double totalTestCaseCount = testCaseCountMap.get("totalTestCaseCount");
        double failTestCaseCount = testCaseCountMap.get("testCaseCountWithStatus");;
        if (totalTestCaseCount > 0) {
            testCaseScore = testCaseScore - ((failTestCaseCount / totalTestCaseCount) * 15);
        } else {
            testCaseScore = 0;
        }
        if (testCaseScore < 0) {
            testCaseScore = 0;
        }

        if (!suiteExeList.isEmpty()) {
            suiteScore = suiteScore - ((count / suiteExeList.size()) * 10);
        } else {
            suiteScore = 0;
        }
        if (suiteScore < 0) {
            suiteScore = 0;
        }
        brokenIndex = 1 - brokenIndex;

        double brokenIndexWeight = Math.round(((999 * (0.35 * brokenIndex))) * 100) / 100;
        double downTimeWeight = Math.round(999 * (downTimeScore / 100));
        double averageFixTimeWeight = Math.round(999 * (averageFixTimeScore / 100));
        double suiteTestCaseWeight = Math.round(999 * (testCaseScore / 100));
        double suiteWeight = Math.round(999 * (suiteScore / 100));
        return (brokenIndexWeight + downTimeWeight + averageFixTimeWeight + suiteTestCaseWeight + suiteWeight);
    }

    public static long getStatusWiseCount(String s_run_id, String status) {
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where("s_run_id").is(s_run_id));
        if (status.equalsIgnoreCase("OTHER")) {
            List<String> statuses = new ArrayList<String>();
            Collections.addAll(statuses, "PASS", "FAIL");
            criteria.add(Criteria.where("status").not().in(statuses));
        } else if (!(status.equalsIgnoreCase("TOTAL"))) {
            criteria.add(Criteria.where("status").is(status));
        } else {

        }
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[criteria.size()])));

        long count = mongoOperations.count(query, TestExeDto.class);
        return count;
    }

    public Map<String, Long> getStatusWiseCountReportName(String report_name, long starttime, long endtime) {
        Map<String, Long> res = new HashMap<String, Long>();
        Query query = new Query();
        List<Criteria> criteria = new ArrayList<Criteria>();
        criteria.add(Criteria.where("report_name").is(report_name));
        criteria.add(Criteria.where("start_time").gt(starttime));
        criteria.add(Criteria.where("end_time").lt(endtime));

        List<String> distinctStatus = mongoOperations.findDistinct(query, "status", TestExeCommonDto.class, String.class);
        for (String status : distinctStatus) {
            Query queryStatus = new Query();
            List<Criteria> criteriaStatus = new ArrayList<Criteria>();
            criteriaStatus.add(Criteria.where("report_name").is(report_name));
            criteriaStatus.add(Criteria.where("start_time").gt(starttime));
            criteriaStatus.add(Criteria.where("end_time").lt(endtime));
            criteriaStatus.add(Criteria.where("status").is(status));
            queryStatus.addCriteria(new Criteria().andOperator(criteriaStatus.toArray(new Criteria[criteria.size()])));
            res.put(status.toUpperCase(), mongoOperations.count(queryStatus, TestExeCommonDto.class));

        }

        return res;
    }

    public List<String> getDistinctStatusFromSRunId(String sRunId) {
        Query distinctStatus = new Query();
        distinctStatus.addCriteria(Criteria.where("s_run_id").is(sRunId));
        return mongoOperations.findDistinct(distinctStatus, "status", TestExeDto.class, String.class);
    }

    public SuiteRun getSuiteRunFromSRunId(String sRunId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("s_run_id").is(sRunId));
        return mongoOperations.findOne(query, SuiteRun.class);
    }

    public static List<TestExeCommonDto> getSortedListForTestExeCommon(List<TestExeCommonDto> list) {
        List<TestExeCommonDto> testExes = new ArrayList<>();
        testExes.addAll(list);
        Collections.sort(testExes, new TimeComparatorTestExeCommon());
        return testExes;
    }

    public static List<SuiteExeDto> getSortedListForSuiteExe(List<SuiteExeDto> list) {
        List<SuiteExeDto> suites = new ArrayList<>();
        suites.addAll(list);
        Collections.sort(suites, new TimeComparator());
        return suites;
    }

    static class TimeComparator implements Comparator<SuiteExeDto> {

        @Override
        public int compare(SuiteExeDto data, SuiteExeDto data2) {
            if (data.getS_start_time() == data2.getS_start_time())
                return 0;
            else if (data.getS_start_time() < data2.getS_start_time())
                return 1;
            else
                return -1;
        }
    }

    private List<Map<String, Object>> getLinkedSteps(List<Map<String, Object>> data, Long isolatedVersionId, int id,
                                                     boolean finalSteps) {
        if (data == null) {
            return null;
        }
        log.info("working");
        Query query = new Query(Criteria.where("isolatedVersionId").is(isolatedVersionId).and("isDeleted").is(false)
                .and("testcaseType").is(MANUAL.toString()));
        Test manualTestCase = mongoOperations.findOne(query, Test.class);
        if (manualTestCase == null) {
            return null;
        }

        for (int i = 0; i < manualTestCase.getTestSteps().size(); i++) {
            Map<String, Object> map = new HashMap<>();
            if (i == 0) {
                if (manualTestCase.getTestSteps().get(i).getTcIsolatedVersionId() != null) {
                    data = getLinkedSteps(data, manualTestCase.getTestSteps().get(i).getTcIsolatedVersionId(), id,
                            finalSteps);
                    if (data == null) {
                        return null;
                    }
                }
                if (manualTestCase.getTestSteps().get(i).getName() != null
                        && !manualTestCase.getTestSteps().get(i).getName().isEmpty()
                        && manualTestCase.getTestSteps().get(i).getDescription() != null
                        && !manualTestCase.getTestSteps().get(i).getDescription().isEmpty()) {
                    map.put("status", StatusColor.PENDING.toString());
                    map.put("step name", manualTestCase.getTestSteps().get(i).getName());
                    map.put("step description", manualTestCase.getTestSteps().get(i).getDescription());
                    map.put("expected result", manualTestCase.getTestSteps().get(i).getExpectedResult());
                    data.add(map);
                }
            } else {
                if (manualTestCase.getTestSteps().get(i).getTcIsolatedVersionId() != null) {
                    data = getLinkedSteps(data, manualTestCase.getTestSteps().get(i).getTcIsolatedVersionId(), id,
                            finalSteps);
                    if (data == null) {
                        return null;
                    }
                }
                if (manualTestCase.getTestSteps().get(i).getName() != null
                        && !manualTestCase.getTestSteps().get(i).getName().isEmpty()
                        && manualTestCase.getTestSteps().get(i).getDescription() != null
                        && !manualTestCase.getTestSteps().get(i).getDescription().isEmpty()) {
                    map.put("status", StatusColor.PENDING.toString());
                    map.put("step name", manualTestCase.getTestSteps().get(i).getName());
                    map.put("step description", manualTestCase.getTestSteps().get(i).getDescription());
                    map.put("expected result", manualTestCase.getTestSteps().get(i).getExpectedResult());
                    data.add(map);
                }
            }
        }
        return data;
    }

    public static String checkStatusOfTestCaseByStepsIfVarianceIsThere(String tc_run_id,
                                                                       Map<Long, VarianceClassificationDto> data) {
        StepsDto steps = RestApiUtils.getSteps(tc_run_id);
        if (steps.getSteps().size() == 0) {
            return "PASS";
        }
        Set<String> statues = new HashSet<>();
        for (Object step : steps.getSteps()) {
            String status = null;
            Map<String, Object> finalstep = (Map<String, Object>) step;
            if (finalstep.getOrDefault("VARIANCEID", null) != null) {
                Integer intVarianceValue  = (Integer)finalstep.getOrDefault("VARIANCEID", null);
                Long varianceId = intVarianceValue != null ? intVarianceValue.longValue(): null;
                if (data.getOrDefault(varianceId, null) != null) {
                    status = "PASS";
                } else {
                    status = (String) finalstep.get("status");
                }
            } else {
                status = (String) finalstep.get("status");
            }
            statues.add(status);
        }
        String finalStatus = "";
        int priority = Integer.MAX_VALUE;
        for (String status : statues) {
            if (StatusColor.valueOf(status.toUpperCase()).priority < priority) {
                priority = StatusColor.valueOf(status.toUpperCase()).priority;
            }
        }
        for (StatusColor val : StatusColor.values()) {
            if (val.priority == priority) {
                finalStatus = val.name();
            }
        }
        return finalStatus;

    }

    public static boolean checkoneListContainsElementOfAnotherList(List<Long> mainList, List<Long> list) {
        for (Long value : list) {
            if (mainList.contains(value)) {
                return true;
            }
        }
        return false;
    }

    public static List<StatusColor> getStatusColorInSorted() {
        Set<StatusColor> set = new TreeSet<>(new Comparator<StatusColor>() {
            @Override
            public int compare(StatusColor o1, StatusColor o2) {
                int returnValue = 0;
                if (o1.getPriority() < o2.getPriority()) {
                    returnValue = -1;
                } else if (o1.getPriority() > o2.getPriority()) {
                    returnValue = 1;
                }
                return returnValue;
            }
        });
        Collections.addAll(set, StatusColor.values());
        return set.stream().collect(Collectors.toList());
    }

    public static String changeKeyValue(String key) {
        return key.replace('_', ' ').toUpperCase();
    }

    static class TimeComparatorTestExeCommon implements Comparator<TestExeCommonDto> {

        @Override
        public int compare(TestExeCommonDto data, TestExeCommonDto data2) {
            if (data.getStart_time() == data2.getStart_time())
                return 0;
            else if (data.getStart_time() < data2.getStart_time())
                return 1;
            else
                return -1;
        }

    }

    public static List<String> headersDataRefactor(List<String> list) {
        List<String> finalList = new ArrayList<>();
        finalList.addAll(list);

        for (int i = 0; i < finalList.size(); i++) {
            if (finalList.get(i).toLowerCase().equals("name")) {
                Collections.swap(finalList, 0, i);
            }
            if (finalList.get(i).toLowerCase().equals("status") && finalList.size() > 1) {
                Collections.swap(finalList, 1, i);
            }
            if (finalList.get(i).toLowerCase().equals("start_time") && finalList.size() > 2) {
                Collections.swap(finalList, 2, i);
            }
            if (finalList.get(i).toLowerCase().equals("end_time") && finalList.size() > 3) {
                Collections.swap(finalList, 3, i);
            }

        }
        for (int i = 0; i < finalList.size(); i++) {
            finalList.set(i, changeKeyValue(finalList.get(i)));
        }

        return finalList;
    }

    public static String getDuration(long s_start_time, long s_end_time) {
        DecimalFormat df = new DecimalFormat("#");
        float seconds = Float.parseFloat(df.format((float) (s_end_time - s_start_time) / 1000));
        String res = "";
        if (seconds < 60) {
            res = seconds + " sec(s)";
        } else if (seconds >= 60 && seconds < 3600) {
            String ans = df.format(Math.floor(seconds / 60)) + " min ";
            if (Math.floor(seconds % 60) != 0) {
                ans = ans + df.format(Math.floor(seconds % 60)) + " sec(s)";
            }
            res = ans;
        } else if (seconds >= 60 && seconds < 86400) {
            String ans = df.format(Math.floor(seconds / 3600)) + " hour ";
            if (Math.floor((seconds % 3600) / 60) != 0) {
                ans = ans + df.format(Math.floor((seconds % 3600) / 60)) + " min ";
            }
            if (Math.floor((seconds % 3600) % 60) != 0) {
                ans = ans + df.format(Math.floor((seconds % 3600) % 60)) + " sec(s)";
            }

            res = ans;
        } else {
            String ans = df.format(Math.floor(seconds / 86400)) + " days ";
            if (Math.floor((seconds % 86400) / 3600) != 0) {
                ans = ans + df.format(Math.floor((seconds % 86400) / 3600)) + " hours ";
            }

            res = ans;
        }

        return res;
    }

    public static List<String> headersDataStepRefactor(Set<String> list) {
        List<String> finalList = new ArrayList<>();
        finalList.addAll(list);

        for (int i = 0; i < finalList.size(); i++) {
            if (finalList.get(i).toLowerCase().equals("step name")) {
                Collections.swap(finalList, 0, i);
            }
            if (finalList.get(i).toLowerCase().equals("step description")) {
                Collections.swap(finalList, 1, i);
            }
            if (finalList.get(i).toLowerCase().equals("status")) {
                Collections.swap(finalList, finalList.size() - 1, i);
            }

        }
        finalList.replaceAll(ReportUtils::changeKeyValue);

        return finalList;
    }

    public static Map<String, List<SuiteExeDto>> getSuiteNames(String reportName, List<Long> pid, List<String> projects, long startTime, long endTime, List<String> envs) {
        Map<String, List<SuiteExeDto>> map = new HashMap<>();
        List<SuiteExeDto> suiteExeList = RestApiUtils.getSuiteExesForReportName(reportName, pid, projects, startTime, endTime, envs);
        for (SuiteExeDto suiteExe : suiteExeList) {
            StringBuilder key = new StringBuilder();
            key.append(suiteExe.getProject_name()).append(":").append(suiteExe.getReport_name()).append(":").append(suiteExe.getEnv());
            List<SuiteExeDto> data = map.getOrDefault(key.toString(), null);
            if (data == null) {
                List<SuiteExeDto> newList = new ArrayList<>();
                newList.add(suiteExe);
                map.put(key.toString(), newList);
            } else {
                data.add(suiteExe);
                map.put(key.toString(), data);
            }
        }
        return map;
    }

    public static Map<String, Object> last5SuiteRuns(List<SuiteExeDto> getAllSuites) {
        List<SuiteExeDto> suiteExes = new ArrayList<>();
        suiteExes.addAll(getAllSuites);
        // Collections.sort(suiteExes,new TimeComparator());
        suiteExes = suiteExes.subList(0, suiteExes.size() >= 5 ? 5 : suiteExes.size());
        if (suiteExes.size() > 0) {
            List<Long> passCount = new ArrayList<>();
            List<Long> failCount = new ArrayList<>();
            List<Long> warnCount = new ArrayList<>();
            List<Long> infoCount = new ArrayList<>();
            List<Long> errCount = new ArrayList<>();
            List<Long> exeCount = new ArrayList<>();
            List<Long> labels = new ArrayList<>();
            List<String> ids = new ArrayList<>();
            long suiteFailCount = 0L;
            for (SuiteExeDto suiteExe : suiteExes) {
                if (suiteExe.getStatus().toUpperCase().equals("FAIL")) {
                    suiteFailCount++;
                }
//                Query query1 = new Query(Criteria.where("s_run_id").is(suiteExe.getS_run_id()));
                List<TestExeDto> testExeList = RestApiUtils.getTestExeList(suiteExe.getS_run_id());
                Map<String, Long> statusMap = new HashMap<>();
                for (StatusColor statusColor : StatusColor.values()) {
                    statusMap.put(statusColor.toString(), 0L);
                }
                long totalCount = 0L;
                for (TestExeDto testExe : testExeList) {
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.PASS.toString())) {
                        long value = statusMap.get(StatusColor.PASS.toString()) + 1;
                        statusMap.put(StatusColor.PASS.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.FAIL.toString())) {
                        long value = statusMap.get(StatusColor.FAIL.toString()) + 1;
                        statusMap.put(StatusColor.FAIL.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.EXE.toString())) {
                        long value = statusMap.get(StatusColor.EXE.toString()) + 1;
                        statusMap.put(StatusColor.EXE.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.ERR.toString())) {
                        long value = statusMap.get(StatusColor.ERR.toString()) + 1;
                        statusMap.put(StatusColor.ERR.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.INFO.toString())) {
                        long value = statusMap.get(StatusColor.INFO.toString()) + 1;
                        statusMap.put(StatusColor.INFO.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.WARN.toString())) {
                        long value = statusMap.get(StatusColor.WARN.toString()) + 1;
                        statusMap.put(StatusColor.WARN.toString(), value);
                        totalCount++;
                        continue;
                    }

                }

                for (StatusColor statusColor : StatusColor.values()) {
                    switch (statusColor.toString()) {
                        case "PASS":
                            passCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "FAIL":
                            failCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "WARN":
                            warnCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "INFO":
                            infoCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "ERR":
                            errCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "EXE":
                            exeCount.add(statusMap.get(statusColor.toString()));
                            break;
                    }

                }
                labels.add(suiteExe.getS_start_time());
                ids.add(suiteExe.getS_run_id());
            }

            Map<String, Object> suiteBarGraph = new HashMap<>();
            suiteBarGraph.put("labels", labels);
            List<Map<String, Object>> datasets = new ArrayList<>();

            for (StatusColor statusColor : StatusColor.values()) {
                Map<String, Object> datasetmap = new HashMap<>();
                datasetmap.put("label", statusColor.toString());
                switch (statusColor.toString().toUpperCase()) {
                    case "PASS":
                        datasetmap.put("data", passCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "FAIL":
                        datasetmap.put("data", failCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "WARN":
                        datasetmap.put("data", warnCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "INFO":
                        datasetmap.put("data", infoCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "ERR":
                        datasetmap.put("data", errCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "EXE":
                        datasetmap.put("data", exeCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                }
                datasets.add(datasetmap);
            }
            suiteBarGraph.put("ids", ids);
            suiteBarGraph.put("click", true);
            suiteBarGraph.put("datasets", datasets);
            suiteBarGraph.put("size", suiteFailCount);
            return suiteBarGraph;
        } else {
            return null;
        }
    }
    public static Map<String, Long> culprit(List<SuiteExeDto> getAllSuites) {
        if (getAllSuites.isEmpty()) {
            return null;
        }
        List<String> sRunIds = getAllSuites.stream().map(SuiteExeDto::getS_run_id).collect(Collectors.toList());
        List<TestExeDto> testExeList = RestApiUtils.getTestExeListForS_run_ids(sRunIds);
        Map<String, Long> totalCountMap = new HashMap<>();
        Map<String, Long> failCountMap = new HashMap<>();
        Map<String, Long> finalMap = new HashMap<>();
        String testCaseName = null;
        long percentage = 0L;
        long averagePercentage = 0L;

        for (TestExeDto testExe : testExeList) {
            if (testExe.getStatus().equalsIgnoreCase("FAIL") || testExe.getStatus().equalsIgnoreCase("EXE")) {
                failCountMap.put(testExe.getName(), failCountMap.getOrDefault(testExe.getName(), 0L) + 1);
            }
            totalCountMap.put(testExe.getName(), totalCountMap.getOrDefault(testExe.getName(), 0L) + 1);
        }
        if (failCountMap.size() == 0) {
            return null;
        }

        for (String name : totalCountMap.keySet()) {
            long failCount = failCountMap.getOrDefault(name, 0L);
            long totalCount = totalCountMap.getOrDefault(name, 0L);
            long failedPercentage = (failCount * 100) / totalCount;
            if (failedPercentage > percentage) {
                testCaseName = name;
                percentage = failedPercentage;
            }
            if (failedPercentage > 50) {
                finalMap.put(name, failedPercentage);
                averagePercentage = averagePercentage + percentage;
            }
        }

        if (finalMap.size() == 0 && percentage > 0) {
            finalMap.put(testCaseName, percentage);
            averagePercentage = averagePercentage + percentage;
        }
        if (finalMap.size() > 0) {
            averagePercentage = averagePercentage / finalMap.size();
            finalMap.put("average", Long.valueOf((String.valueOf(averagePercentage))));
            return finalMap;
        }
        return null;
    }

    public static double getQAScore(List<SuiteExeDto> getAllSuites) {
        List<String> sRunIdsList = new ArrayList<>();
        double suiteErrCount = 0;
        double falsePositive = 0;

        for (SuiteExeDto suiteExe : getAllSuites) {

            if (suiteExe.getS_run_id().equalsIgnoreCase("TEST-PROJECT_Beta_ce3192d6-9634-424c-97bc-d08caf344401")) {
                System.out.println("truuuuuu");
            }
            sRunIdsList.add(suiteExe.getS_run_id());
            if (suiteExe.getStatus().equalsIgnoreCase("ERR")) {
                suiteErrCount++;
            }
            if (suiteExe.getClassificationDetails() != null
                    && suiteExe.getClassificationDetails().getClassification() != null
                    && suiteExe.getClassificationDetails().isChildFalsePostiveStatus()) {
                falsePositive++;
            }
        }

//        Query errTestCasesCountQuery = new Query(
//                Criteria.where("status").in("ERR").and("s_run_id").in(sRunIdsList));
//        Query testCaseCountQuery = new Query(Criteria.where("s_run_id").in(sRunIdsList));
//
//        Query falsePositiveCountQuery = new Query(
//                Criteria.where("classificationDetails.childFalsePostiveStatus").is(true).and("s_run_id")
//                        .in(sRunIdsList));

//        double totalTestCaseCount = mongoOperations.count(testCaseCountQuery, TestExeDto.class);
//        double errTestCaseCount = mongoOperations.count(errTestCasesCountQuery, TestExeDto.class);

//        double falsePositiveTestCount = mongoOperations.count(falsePositiveCountQuery, TestExeDto.class);
        Map<String, Double> testCaseCountMap = RestApiUtils.getTestCaseCount(sRunIdsList, List.of("ERR"));
        double totalTestCaseCount = testCaseCountMap.get("totalTestCaseCount");
        double errTestCaseCount = testCaseCountMap.get("testCaseCountWithStatus");
        double falsePositiveTestCount = testCaseCountMap.get("falsePositiveTestCaseCount");

        double finalSuiteScore = (((999 * 0.5) * (getAllSuites.size() - suiteErrCount)) / getAllSuites.size());
        double finalTestcaseScore = ((((999 * 0.5) * (totalTestCaseCount - errTestCaseCount)) / totalTestCaseCount));

        if (falsePositive != 0.0) {
            double falsePercentage = (getAllSuites.size() * falsePositive) / 100;
            finalSuiteScore = finalSuiteScore - (399.6 * falsePercentage) / 100;
        }
        if (falsePositiveTestCount != 0.0) {
            double falsePercentage = (totalTestCaseCount * falsePositiveTestCount) / 100;
            finalTestcaseScore = finalTestcaseScore - (399.6 * falsePercentage) / 100;
        }

        return Math.round(finalSuiteScore + finalTestcaseScore);
    }

    public static String convertLongToTime(double seconds) {
        String res = "";
        DecimalFormat df = new DecimalFormat("#");
        if (seconds < 60) {
            res = seconds + " sec(s)";
        } else if (seconds >= 60 && seconds < 3600) {
            String ans = df.format(Math.floor(seconds / 60)) + "min";
            res = ans;
        } else if (seconds >= 60 && seconds < 86400) {
            String ans = df.format(Math.floor(seconds / 3600)) + "hr ";
            if (Math.floor((seconds % 3600) / 60) != 0) {
                ans = ans + df.format(Math.floor((seconds % 3600) / 60)) + "m";
            }
            res = ans;
        } else {
            String ans = df.format(Math.floor(seconds / 86400)) + "d ";
            if (Math.floor((seconds % 86400) / 3600) != 0) {
                ans = ans + df.format(Math.floor((seconds % 86400) / 3600)) + "hr";
            }
            res = ans;
        }
        return res;
    }






    public static Object createExecutionHeadersDataWithVarianceAndFalsePositive(SuiteExeDto getSuite, Map<String, Object> iconMap) {

        Map<String, Object> result = new HashMap<>();
        List<Object> mainData = new ArrayList<>();
        List<String> headers = new ArrayList<>();

        Collections.addAll(headers, "Status", "Project Name", "Env", "Report Name", "Start Time", "End Time", "Duration");
        Map<String, Object> data = new HashMap<String, Object>();

        data.put("Project Name", createCustomObject(StringUtils.capitalize(getSuite.getProject_name()), "text", getSuite.getProject_name(), "center"));
        data.put("Env", createCustomObject(StringUtils.capitalize(getSuite.getEnv()), "text", getSuite.getEnv(), "center"));
        data.put("Report Name", createCustomObject(StringUtils.capitalize(getSuite.getReport_name()), "text", getSuite.getReport_name(), "center"));
        Map<String, Object> timereport = new HashMap<>();
        timereport.put("subType", "datetime");
        data.put("Start Time", createCustomObject(getSuite.getS_start_time(), "date", getSuite.getS_start_time(), "center", timereport));
        if (getSuite.getS_end_time() != 0) {

            data.put("End Time", createCustomObject(getSuite.getS_end_time(), "date", getSuite.getS_end_time(), "center", timereport));
            data.put("Duration", createCustomObject(getDuration(getSuite.getS_start_time(), getSuite.getS_end_time()), "text", ((float) (getSuite.getS_end_time() - getSuite.getS_start_time()) / 1000), "center"));
        } else {
            data.put("End Time", createCustomObject("-", "text", getSuite.getS_end_time(), "center"));
            data.put("Duration", createCustomObject("-", "text", 0, "center"));
        }
        if (iconMap != null && iconMap.size() > 0) {
            data.put("ICON", iconMap);
            data.put("ISCLICKABLE", createCustomObject(false, "text", false, "left"));
        }
        mainData.add(data);
        result.put("headers", headers);
        result.put("data", mainData);
        return result;
    }

    public static Object createInfraHeadersData(SuiteExeDto getSuite) {
        Map<String, Object> result = new HashMap<>();
        List<String> headers = new ArrayList<>();
        List<Object> mainData = new ArrayList<>();
        String user = "";
        String machine = "";
        String run_type = "";
        String run_mode = "";
        Set<String> userSet = new HashSet<>();
        Set<String> machineSet = new HashSet<>();
        Set<String> run_typeSet = new HashSet<>();
        Set<String> run_modeSet = new HashSet<>();
        Collections.addAll(headers, "Executed By", "Executed On", "Run Type", "Run Mode");
        Map<String, Object> data = new HashMap<String, Object>();
        Query query = new Query(Criteria.where("s_run_id").is(getSuite.getS_run_id()));
        List<TestExeDto> testcaseList = mongoOperations.find(query, TestExeDto.class);
        for (TestExeDto t : testcaseList) {
            if (!userSet.contains(t.getInvoke_user())) {
                if (t.getInvoke_user() != null) {
                    user = user + t.getInvoke_user() + ",";
                    userSet.add(t.getInvoke_user());
                }
            }
            if (!run_modeSet.contains(t.getRun_mode())) {
                if (t.getRun_mode() != null) {
                    run_mode = run_mode + t.getRun_mode() + ",";
                    run_modeSet.add(t.getRun_mode());
                }
            }
            if (!run_typeSet.contains(t.getRun_type().toUpperCase(Locale.ROOT))) {
                if (t.getRun_type() != null) {
                    run_type = run_type + t.getRun_type() + ",";
                    run_typeSet.add(t.getRun_type().toUpperCase(Locale.ROOT));
                }
            }
            if (!machineSet.contains(t.getMachine())) {
                if (t.getMachine() != null) {
                    machine = machine + t.getMachine() + ",";
                    machineSet.add(t.getMachine());
                }
            }
        }
        if (!(user.equals("null,") || user.length() == 0)) {
            user = user.substring(0, user.length() - 1);
            data.put("Executed By", createCustomObject(user, "text", user, "left"));
        } else {
            data.put("Executed By", createCustomObject("-", "text", "-", "left"));
        }
        if (!(machine.equals("null,") || machine.length() == 0)) {
            machine = machine.substring(0, machine.length() - 1);
            data.put("Executed On", createCustomObject(machine, "text", machine, "left"));
        } else {
            data.put("Executed On", createCustomObject("-", "text", "-", "left"));
        }
        if (!(run_type.equals("null,") || run_type.length() == 0)) {
            run_type = run_type.substring(0, run_type.length() - 1);
            data.put("Run Type", createCustomObject(run_type, "text", run_type, "left"));
        } else {
            data.put("Run Type", createCustomObject("-", "text", "-", "left"));
        }
        if (!(run_mode.equals("null,") || run_mode.length() == 0)) {
            run_mode = run_mode.substring(0, run_mode.length() - 1);
            data.put("Run Mode", createCustomObject(run_mode, "text", run_mode, "left"));
        } else {
            data.put("Run Mode", createCustomObject("-", "text", "-", "left"));
        }
        mainData.add(data);
        result.put("headers", headers);
        result.put("data", mainData);
        return result;
    }

    public static Map<String, Object> Last5RunsStackedBarChartBySuiteExe(SuiteExeDto getSuite) {
        Query barChartQuery = new Query(Criteria.where("report_name").is(getSuite.getReport_name())
                .andOperator(Criteria.where("env").is(getSuite.getEnv())));
        Pageable barChartPageable = PageRequest.of(0, 5);
        Sort.Order barChartOrder = new Sort.Order(Sort.Direction.DESC, "s_start_time");
        barChartQuery.with(barChartPageable);
        barChartQuery.with(Sort.by(barChartOrder));
        List<SuiteExeDto> suiteExes = mongoOperations.find(barChartQuery, SuiteExeDto.class);
        if (suiteExes.size() > 0) {
            List<Long> passCount = new ArrayList<>();
            List<Long> failCount = new ArrayList<>();
            List<Long> warnCount = new ArrayList<>();
            List<Long> infoCount = new ArrayList<>();
            List<Long> errCount = new ArrayList<>();
            List<Long> exeCount = new ArrayList<>();
            List<Long> labels = new ArrayList<>();
            for (SuiteExeDto suiteExe : suiteExes) {
                Query varianceQuery = new Query(Criteria.where("varianceId").in(suiteExe.getVarianceIds())
                        .and("varianceStatus").is("ACTIVE").and("endDate").gt(new Date().getTime()));
                List<VarianceClassificationDto> varianceClassificationList = mongoOperations.find(varianceQuery,
                        VarianceClassificationDto.class);
                Map<Long, VarianceClassificationDto> variannceList = new HashMap<>();
                List<Long> varinaceIds = new ArrayList<>();
                for (VarianceClassificationDto varianceClassification : varianceClassificationList) {
                    varinaceIds.add(varianceClassification.getVarianceId());
                    variannceList.put(varianceClassification.getVarianceId(), varianceClassification);
                }
                Query query1 = new Query(Criteria.where("s_run_id").is(suiteExe.getS_run_id()));
                List<TestExeDto> testExeList = mongoOperations.find(query1, TestExeDto.class);
                Map<String, Long> statusMap = new HashMap<>();
                for (StatusColor statusColor : StatusColor.values()) {
                    statusMap.put(statusColor.toString(), 0L);
                }
                long totalCount = 0L;
                for (TestExeDto testExe : testExeList) {
                    if (testExe.getVarianceId() != null
                            || (testExe.getStepVarianceIds() != null && testExe.getStepVarianceIds().size() > 0)
                            || testExe.getClassificationDetails() != null) {
                        if (testExe.getVarianceId() != null
                                || (testExe.getStepVarianceIds() != null && testExe.getStepVarianceIds().size() > 0)) {
                            VarianceClassificationDto varianceClassification = variannceList
                                    .getOrDefault(testExe.getVarianceId(), null);
                            if (varianceClassification != null) {
                                testExe.setStatus("PASS");
                            }
                            if (checkoneListContainsElementOfAnotherList(varinaceIds,
                                    testExe.getStepVarianceIds())) {
                                testExe.setStatus(checkStatusOfTestCaseByStepsIfVarianceIsThere(
                                        testExe.getTc_run_id(), variannceList));
                            }
                        }
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.PASS.toString())) {
                        long value = statusMap.get(StatusColor.PASS.toString()) + 1;
                        statusMap.put(StatusColor.PASS.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.FAIL.toString())) {
                        long value = statusMap.get(StatusColor.FAIL.toString()) + 1;
                        statusMap.put(StatusColor.FAIL.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.EXE.toString())) {
                        long value = statusMap.get(StatusColor.EXE.toString()) + 1;
                        statusMap.put(StatusColor.EXE.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.ERR.toString())) {
                        long value = statusMap.get(StatusColor.ERR.toString()) + 1;
                        statusMap.put(StatusColor.ERR.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.INFO.toString())) {
                        long value = statusMap.get(StatusColor.INFO.toString()) + 1;
                        statusMap.put(StatusColor.INFO.toString(), value);
                        totalCount++;
                        continue;
                    }
                    if (testExe.getStatus().toUpperCase().equals(StatusColor.WARN.toString())) {
                        long value = statusMap.get(StatusColor.WARN.toString()) + 1;
                        statusMap.put(StatusColor.WARN.toString(), value);
                        totalCount++;
                        continue;
                    }

                }

                for (StatusColor statusColor : StatusColor.values()) {
                    switch (statusColor.toString()) {
                        case "PASS":
                            passCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "FAIL":
                            failCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "WARN":
                            warnCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "INFO":
                            infoCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "ERR":
                            errCount.add(statusMap.get(statusColor.toString()));
                            break;
                        case "EXE":
                            exeCount.add(statusMap.get(statusColor.toString()));
                            break;
                    }

                }
                labels.add(suiteExe.getS_start_time());
            }

            Map<String, Object> suiteBarGraph = new HashMap<>();
            suiteBarGraph.put("labels", labels);
            List<Map<String, Object>> datasets = new ArrayList<>();

            for (StatusColor statusColor : StatusColor.values()) {
                Map<String, Object> datasetmap = new HashMap<>();
                datasetmap.put("label", statusColor.toString());
                switch (statusColor.toString().toUpperCase()) {
                    case "PASS":
                        datasetmap.put("data", passCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "FAIL":
                        datasetmap.put("data", failCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "WARN":
                        datasetmap.put("data", warnCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "INFO":
                        datasetmap.put("data", infoCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "ERR":
                        datasetmap.put("data", errCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "EXE":
                        datasetmap.put("data", exeCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                }
                datasets.add(datasetmap);
            }
            suiteBarGraph.put("datasets", datasets);
            Map<String, Object> stackedBarChartType = new HashMap<>();
            stackedBarChartType.put("subType", "stacked_bar_chart");
            stackedBarChartType.put("heading", "Last 5 Suite Runs");
            return createCustomObject(suiteBarGraph, "chart", suiteExes.size(), "center",
                    stackedBarChartType);

        }
        return null;
    }

    public static Map<String, Object> testCaseInfoDoughnutChart(List<String> statues) {
        Map<String, Long> pieData = new HashMap<>();
        for (String status : statues) {
            pieData.put(status.toUpperCase(),
                    pieData.getOrDefault(status.toUpperCase(), 0L) + 1);

        }
        if (pieData != null && !pieData.keySet().isEmpty()) {
            List<String> labels = new ArrayList<>();
            labels.addAll(pieData.keySet());
            for (String key : labels) {
                pieData.replace(key, ((Number) pieData.get(key)).longValue());
            }
            long totalcount = statues.size();
            Map<String, Object> doughnutSubType = new HashMap<>();
            doughnutSubType.put("subType", "doughnut_chart");
            doughnutSubType.put("heading", "Total Testcase(s)");
            return createCustomObject(createDoughnutChart(pieData),
                    "chart", totalcount, "center", doughnutSubType);
        }
        return null;
    }

    public static Map<String, Object> categoryStackedBarChartByS_run_id(Map<String, Long> data, Set<String> category) {
        List<Long> passCount = new ArrayList<>();
        List<Long> failCount = new ArrayList<>();
        List<Long> warnCount = new ArrayList<>();
        List<Long> infoCount = new ArrayList<>();
        List<Long> errCount = new ArrayList<>();
        List<Long> exeCount = new ArrayList<>();
        long maxCount = 0;
        if (category.size() > 1) {
            for (String category1 : category) {
                Map<String, Long> barcount = new HashMap<>();
                for (StatusColor statusColor : StatusColor.values()) {
                    barcount.put(statusColor.toString(), 0L);
                    barcount.put(statusColor.toString(),
                            data.getOrDefault(category1.toUpperCase() + "_" + statusColor.toString(), 0L));
                }
                passCount.add(barcount.get(StatusColor.PASS.toString()));
                failCount.add(barcount.get(StatusColor.FAIL.toString()));
                warnCount.add(barcount.get(StatusColor.WARN.toString()));
                infoCount.add(barcount.get(StatusColor.INFO.toString()));
                errCount.add(barcount.get(StatusColor.ERR.toString()));
                exeCount.add(barcount.get(StatusColor.EXE.toString()));
            }
            Map<String, Object> categoryBarGraph = new HashMap<>();

            categoryBarGraph.put("labels", category);
            List<Map<String, Object>> datasets = new ArrayList<>();

            for (StatusColor statusColor : StatusColor.values()) {
                Map<String, Object> datasetmap = new HashMap<>();
                datasetmap.put("label", statusColor.toString());
                switch (statusColor.toString().toUpperCase()) {
                    case "PASS":
                        datasetmap.put("data", passCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "FAIL":
                        datasetmap.put("data", failCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "WARN":
                        datasetmap.put("data", warnCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "INFO":
                        datasetmap.put("data", infoCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "ERR":
                        datasetmap.put("data", errCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                    case "EXE":
                        datasetmap.put("data", exeCount);
                        datasetmap.put("backgroundColor", statusColor.color);
                        break;
                }
                datasets.add(datasetmap);
            }
            categoryBarGraph.put("datasets", datasets);
            Map<String, Object> stackedBarChartType = new HashMap<>();
            stackedBarChartType.put("subType", "stacked_bar_chart");
            stackedBarChartType.put("heading", "Category Wise Status");
            return createCustomObject(categoryBarGraph,
                    "chart", category.size(), "center", stackedBarChartType);
        } else {
            return null;
        }
    }

    public static MultiValueMap<String, String> getAuthHeader() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        return headers;
    }

}
