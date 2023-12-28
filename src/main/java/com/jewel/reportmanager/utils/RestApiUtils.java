package com.jewel.reportmanager.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.jewel.reportmanager.dto.*;
import com.jewel.reportmanager.enums.OperationType;
import com.jewel.reportmanager.exception.CustomDataException;
import com.mongodb.BasicDBObject;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.modelmapper.TypeToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.jewel.reportmanager.enums.OperationType.Failure;

@Slf4j
@Service
public class RestApiUtils {

    private static String projectManagerUrl;
    private static RestTemplate restTemplate;
    private static String insertionManagerUrl;
    @Autowired
    public void setRestTemplate(RestTemplate restTemplate) {
        RestApiUtils.restTemplate = restTemplate;
    }
    @Value("${project.manager.url}")
    public void setProjectManagerUrl(String projectManagerUrl) {
        RestApiUtils.projectManagerUrl = projectManagerUrl;
    }

    @Value("${insertion.manager.url}")
    public void setInsertionManagerUrl(String insertionManagerUrl) {
        RestApiUtils.insertionManagerUrl = insertionManagerUrl;
    }
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Returns a list of project role pid(s) for pid, status and username.
     *
     * @param pid
     * @param status
     * @param username
     * @return List<Long>
     */
    public static List<Long> getProjectRolePidList(List<Long> pid, String status, String username) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  pid.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("pid", pidList);
        uriVariables.put("status", status);
        uriVariables.put("username", username);
        String url =
                projectManagerUrl + "/v2/project/role/pid/status/username?pid={pid}&status={status}&username={username}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if (response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> pidsList = (List<?>) response.getData();
                return pidsList.stream().map(pId -> Long.valueOf((Integer) pId)).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching project role pid(s) for pid: {}", pid);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Project role pid(s) list is empty for pid: {}", pid);
            return List.of();
        }
    }

    /**
     * Returns a project role for pid, status and username.
     *
     * @param pid
     * @param status
     * @param username
     * @return ProjectRoleDto
     */
    public static ProjectRoleDto getProjectRoleEntity(Long pid, String username, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("pid", pid);
        uriVariables.put("username", username);
        uriVariables.put("status", status);
        try {
            ResponseEntity response = restTemplate.exchange(projectManagerUrl + "/v2/project/role/entity?pid={pid}&userName={username}&status={status}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<ProjectRoleDto>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Project role is empty for pid: {}", pid);
            return null;
        }
    }

    /**
     * Returns a project role for pid and username.
     *
     * @param pid
     * @param username
     * @return ProjectRoleDto
     */
    public static ProjectRoleDto getProjectRoleByPidAndUsername(Long pid, String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("pid", pid);
        uriVariables.put("username", username);
        try {
            ResponseEntity response = restTemplate.exchange(projectManagerUrl + "/v2/project/role/pid/username?pid={pid}&username={username}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<ProjectRoleDto>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Project role is empty for pid: {}", pid);
            return null;
        }
    }

    /**
     * Returns a list of project pid(s) for realCompanyName, status and username.
     *
     * @param pid
     * @param status
     * @param realCompanyName
     * @return List<Long>
     */
    public static List<Long> getProjectPidListForRealCompanyNameAndStatus(List<Long> pid, String status, String realCompanyName) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  pid.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("pid", pidList);
        uriVariables.put("status", status);
        uriVariables.put("realCompanyName", realCompanyName);

        String url = projectManagerUrl +
                "/v1/project/pid/status/realCompanyName?pid={pid}&status={status}&realCompanyName={realCompanyName}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if (response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> pidsList = (List<?>) response.getData();
                return pidsList.stream().map(pId -> Long.valueOf((Integer) pId)).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching project role pid(s) for pid: {}", pid);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Project pid(s) list is empty for pid: {}", pid);
            return List.of();
        }
    }

    /**
     * Returns a list of project pid(s) for pid and status.
     *
     * @param pid
     * @param status
     * @return List<Long>
     */
    public static List<Long> getProjectPidList(List<Long> pid, String status) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  pid.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("pid", pidList);
        uriVariables.put("status", status);

        String url = projectManagerUrl + "/v1/project/pids?pid={pid}&status={status}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if (response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> pidsList = (List<?>) response.getData();
                return pidsList.stream().map(pId -> Long.valueOf((Integer) pId)).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching project role pid(s) for pid: {}", pid);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Project pid(s) list is empty for pid: {}", pid);
            return List.of();
        }
    }

    /**
     * Returns a list of project names for pid.
     *
     * @param pid
     * @return List<String>
     */
    public static List<String> getProjectNames(List<Long> pid) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  pid.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("pid", pidList);

        String url = projectManagerUrl + "/v1/project/pid?pid={pid}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if(response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> projectsList = (List<?>) response.getData();
                return projectsList.stream().map(Object::toString).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching s_run_ids for pids: {}", pid);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Project names list is empty for pid: {}", pid);
            return List.of();
        }
    }


    /**
     * Returns a list of report names for pid, env, startTime, endTime and page no.
     *
     * @param p_id
     * @param env
     * @param s_start_time
     * @param s_end_time
     * @param pageNo
     * @return List<String>
     */
    public static List<String> getReportNames(List<Long> p_id, List<String> env, Long s_start_time, Long s_end_time, Integer pageNo) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  p_id.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("p_id", pidList);
        String envList = String.join(",", env);
        uriVariables.put("env", envList);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);
        uriVariables.put("pageNo", pageNo);

        String url = insertionManagerUrl + "/v1/suiteExe/report-names?p_id={p_id}&env={env}" +
                "&s_start_time={s_start_time}&s_end_time={s_end_time}&pageNo={pageNo}";
        try {
            Response response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            List<String> reportNames;
            if(response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> reportNamesList = (List<?>) response.getData();
                reportNames = reportNamesList.stream().map(Object::toString).collect(Collectors.toList());
                return reportNames;
            } else {
                log.error("Something went wrong while fetching report names for pid: {}, " +
                                "env: {}, start time: {}, end time: {} pageNo: {}",
                        p_id, env, s_start_time, s_end_time, pageNo);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Report names list is empty for pid: {}, env: {}, start time: {}, end time: {} and pageNo: {}", p_id, env, s_start_time, s_end_time, pageNo);
            return List.of();
        }
    }

    /**
     * Returns a list of suite exes for pid, env, startTime, endTime, page no., sort and sortedColumn.
     *
     * @param p_id
     * @param env
     * @param s_start_time
     * @param s_end_time
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return List<SuiteExeDto>
     */
    public static List<SuiteExeDto> getSuiteExes(List<Long> p_id, List<String> env, Long s_start_time, Long s_end_time, Integer pageNo, Integer sort, String sortedColumn) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  p_id.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("p_id", pidList);
        String envList = String.join(",", env);
        uriVariables.put("env", envList);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);

        String url = insertionManagerUrl +
                "/v1/suiteExe?p_id={p_id}&env={env}&s_start_time={s_start_time}&s_end_time={s_end_time}" +
                "&pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables).getBody();
            if(response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> suiteExes = (List<?>) response.getData();
                return suiteExes.stream().map(suiteExe -> mapper.convertValue(suiteExe, new TypeReference<SuiteExeDto>() {})).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching suite exes for pid: {}, env: {}, " +
                                "start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}",
                        p_id, env, s_start_time, s_end_time, pageNo, sort, sortedColumn);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Suite exe list is empty for pid: {}, env: {}, start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}", p_id, env, s_start_time, s_end_time, pageNo, sort, sortedColumn);
            return List.of();
        }
    }

    /**
     * Returns a list of s_run_ids for pid, env, startTime, endTime, page no., sort and sortedColumn.
     *
     * @param p_id
     * @param env
     * @param s_start_time
     * @param s_end_time
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return List<String>
     */
    public static List<String> getS_Run_Ids(List<Long> p_id, List<String> env, Long s_start_time, Long s_end_time, Integer pageNo, Integer sort, String sortedColumn) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList = p_id.stream().map(Object::toString).collect(Collectors.joining(","));
        uriVariables.put("p_id", pidList);
        String envList = String.join(",", env);
        uriVariables.put("env", envList);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);

        String url = insertionManagerUrl + "/v1/suiteExe/s_run_ids?p_id={p_id}&env={env}&s_start_time={s_start_time}" +
                "&s_end_time={s_end_time}&pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if(response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> sRunIdList = (List<?>) response.getData();
                return sRunIdList.stream().map(Object::toString).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching s_run_ids for pid: {}, " +
                        "env: {}, start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}",
                        p_id, env, s_start_time, s_end_time, pageNo, sort, sortedColumn);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("s_run_ids list is empty for pid: {}, env: {}, start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}", p_id, env, s_start_time, s_end_time, pageNo, sort, sortedColumn);
            return List.of();
        }
    }


    /**
     * Returns a list of suite exes for pid, category, env, reportName, startTime, endTime, page no., sort and sortedColumn.
     *
     * @param p_id
     * @param category
     * @param env
     * @param reportName
     * @param s_start_time
     * @param s_end_time
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return List<SuiteExeDto>
     */
    public static List<SuiteExeDto> getSuiteExesForSuiteTimeline(Long p_id, String category, String env, String reportName, Long s_start_time, Long s_end_time, Integer pageNo, Integer sort, String sortedColumn) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("p_id", p_id);
        uriVariables.put("category", category);
        uriVariables.put("env", env);
        uriVariables.put("reportName", reportName);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v1/suiteExe/suiteTimeline?p_id={p_id}&category={category}&env={env}&reportName={reportName}&s_start_time={s_start_time}&s_end_time={s_end_time}&pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<List<SuiteExeDto>>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Suite exe list is empty for pid: {}, category: {}, env: {}, reportName: {}, start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}", p_id, category, env, reportName, s_start_time, s_end_time, pageNo, sort, sortedColumn);
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Returns a list of s_run_ids for pid, category, env, reportName, startTime, endTime, page no., sort and sortedColumn.
     *
     * @param p_id
     * @param category
     * @param env
     * @param reportName
     * @param s_start_time
     * @param s_end_time
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return List<String>
     */
    public static List<String> getS_Run_IdsForSuiteTimeline(Long p_id, String category, String env, String reportName, Long s_start_time, Long s_end_time, Integer pageNo, Integer sort, String sortedColumn) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("p_id", p_id);
        uriVariables.put("category", category);
        uriVariables.put("env", env);
        uriVariables.put("reportName", reportName);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v1/suiteExe/s_run_ids/suiteTimeline?p_id={p_id}&category={category}&env={env}&reportName={reportName}&s_start_time={s_start_time}&s_end_time={s_end_time}&pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<List<String>>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("s_run_ids list is empty for pid: {}, category: {}, env: {}, reportName: {}, start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}", p_id, category, env, reportName, s_start_time, s_end_time, pageNo, sort, sortedColumn);
            return Collections.EMPTY_LIST;
        }
    }

    public static Map<String, Object>  getSuiteTimelineDataset(long p_id, String category, String env, String reportName, long startTime, long endTime, Integer pageNo, Integer sort, String sortedColumn) {
        String url = insertionManagerUrl + "/v2/suiteExe/suiteTimeline?p_id={p_id}&category={category}&env={env}&reportName={reportName}&s_start_time={s_start_time}&s_end_time={s_end_time}&pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}";
        HttpEntity httpEntity = new HttpEntity(null, ReportUtils.getAuthHeader());
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("p_id", p_id);
        uriVariables.put("category", category);
        uriVariables.put("env", env);
        uriVariables.put("reportName", reportName);
        uriVariables.put("s_start_time", startTime);
        uriVariables.put("s_end_time", endTime);
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);
        try {
            ResponseEntity response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<Map<String, Object>>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("unable to get suite timeline data for pid: {}, category: {}, env: {}, reportName: {}, "
                            + "start time: {}, end time: {} pageNo: {}, sort: {} and sortedColumn: {}", p_id, category,
                    env, reportName, startTime, endTime, pageNo, sort, sortedColumn);
        }
        throw new CustomDataException("Exeption while getting suite Timeline data", null, Failure, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static Map<String, Object>  getCountByStatusList(List<String> statuses,
                                                             String s_run_id) {
        String url = insertionManagerUrl + "/v1/testExe/testcaseInfo/" + s_run_id;
        HttpEntity<?> httpEntity = new HttpEntity<>(statuses, ReportUtils.getAuthHeader());
        try {
            ResponseEntity<Response> response = restTemplate.exchange(url, HttpMethod.POST, httpEntity, Response.class);
            if(response.getStatusCode() == HttpStatus.OK){
                Gson gson = new Gson();
                String json = gson.toJson(response.getBody());
                Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
                }.getType());
                Object data = convertedMap.get("data");
                Type type = new TypeToken<Map<String, Object>>() {
                }.getType();

                return gson.fromJson(gson.toJson(data), type);
            }
        } catch (HttpClientErrorException ex) {
            log.error("unable to fetch status count for s_run_id: {}", s_run_id);
        }
        throw new CustomDataException("Exception while fetching status count", null, Failure, HttpStatus.INTERNAL_SERVER_ERROR);
    }


    /**
     * Returns a count of suite exe for pid, env, startTime, endTime and page no.
     *
     * @param p_id
     * @param env
     * @param s_start_time
     * @param s_end_time
     * @return Long - count
     */
    public static Long getSuiteExeCount(List<Long> p_id, List<String> env, Long s_start_time, Long s_end_time) {
        Map<String, Object> uriVariables = new HashMap<>();
        String pidList =  p_id.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("p_id", pidList);
        String envList = String.join(",", env);
        uriVariables.put("env", envList);
        uriVariables.put("s_start_time", s_start_time);
        uriVariables.put("s_end_time", s_end_time);

        String url = insertionManagerUrl +
                "/v1/suiteExe/count?p_id={p_id}&env={env}&s_start_time={s_start_time}&s_end_time={s_end_time}";
        Response response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(null, ReportUtils.getAuthHeader()), Response.class, uriVariables).getBody();
        long count = 0;
        if(response != null && response.getOperation().equals(OperationType.Success)) {
            count = Long.valueOf((Integer)response.getData());
        }
        return count;
    }

    /**
     * Returns suiteExe from s_run_id
     *
     * @param s_run_id
     * @return SuiteExe
     */
    public static SuiteExeDto getSuiteExe(String s_run_id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);
        try {
            return restTemplate.exchange(insertionManagerUrl + "/v2/suiteExe?s_run_id={s_run_id}", HttpMethod.GET, httpEntity, SuiteExeDto.class, uriVariables).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Suite exe is empty for s_run_id: {}", s_run_id);
            return null;
        }
    }

    /**
     * Returns suiteRun from s_run_id
     *
     * @param s_run_id
     * @return SuiteRun
     */
    public static SuiteRun getSuiteRun(String s_run_id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v2/suiteRun?s_run_id={s_run_id}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<SuiteRun>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Suite run is empty for s_run_id: {}", s_run_id);
            return null;
        }
    }

    /**
     * Returns test exe from tc_run_id
     *
     * @param tc_run_id
     * @return TestExeDto
     */
    public static TestExeDto getTestExe(String tc_run_id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("tc_run_id", tc_run_id);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v2/testcase?tc_run_id={tc_run_id}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<TestExeDto>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.BadRequest ex) {
            log.info("TestExe not found for tc_run_id: {}", tc_run_id);
            return null;
        }
    }

    /**
     * Returns suite from reportName and status
     *
     * @param reportName
     * @param status
     * @return SuiteRun
     */
    public static SuiteDto getSuiteByReportNameAndStatus(String reportName, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("reportName", reportName);
        uriVariables.put("status", status);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v2/suite/reportName/status?reportName={reportName}&status={status}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<SuiteDto>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Suite is empty for reportName: {} and status: {}", reportName, status);
            return null;
        }
    }

    /**
     * Returns project from realCompanyName, projectName and status.
     *
     * @param realCompanyName
     * @param projectName
     * @param status
     * @return ProjectDto
     */
    public static ProjectDto getProjectByRealCompanyNameAndProjectAndStatus(String realCompanyName, String projectName, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("realCompanyName", realCompanyName);
        uriVariables.put("projectName", projectName);
        uriVariables.put("status", status);
        try {
            ResponseEntity response = restTemplate.exchange(projectManagerUrl + "/v1/project/realCompanyName/projectName/status?realCompanyName={realCompanyName}&projectName={projectName}&status={status}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<ProjectDto>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Project is not found for realCompanyName: {}, projectName: {}, status: {}", realCompanyName, projectName, status);
            return null;
        }
    }

    /**
     * Returns List<TestExeDto> from s_run_id
     *
     * @param s_run_id
     * @return List<TestExeDto>
     */
    public static List<TestExeDto> getTestExeList(String s_run_id) {
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);

        String url = insertionManagerUrl + "/v2/testExe/list?s_run_id={s_run_id}";
        try {
            return restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    new ParameterizedTypeReference<List<TestExeDto>>() {},
                    uriVariables
            ).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Suite run is empty for s_run_id: {}", s_run_id);
            return List.of();
        }
    }

    /**
     * Returns a list of test exes for s_run_ids.
     *
     * @param s_run_ids
     * @return List<TestExeDto>
     */
    public static List<TestExeDto> getTestExeListForS_run_ids(List<String> s_run_ids) {
        Map<String, Object> uriVariables = new HashMap<>();
        String str_s_run_ids = s_run_ids.stream().map(Object::toString).collect(Collectors.joining(","));
        uriVariables.put("s_run_ids", str_s_run_ids);

        String url = insertionManagerUrl + "/v1/testExe?s_run_ids={s_run_ids}";
        try {
            Response response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();
            if(response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> testExes = (List<?>) response.getData();
                return testExes.stream().map(testExe -> mapper.convertValue(testExe, new TypeReference<TestExeDto>() {})).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching Suite exe list for s_run_ids: {}", s_run_ids);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Suite exe list is empty for s_run_ids: {}", s_run_ids);
            return List.of();
        }
    }

    /**
     * Update suite exe for s_run_id.
     *
     * @param s_run_id
     * @param suiteExeDto
     */
    public static void updateSuiteExe(String s_run_id, SuiteExeDto suiteExeDto) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(suiteExeDto, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);
        restTemplate.exchange(insertionManagerUrl + "/v2/suiteExe/update?s_run_id={s_run_id}", HttpMethod.PUT, httpEntity, SuiteExeDto.class, uriVariables).getBody();
    }

    /**
     * Returns project from pid and status.
     *
     * @param pid
     * @param status
     * @return project
     */
    public static ProjectDto getProjectByPidAndStatus(Long pid, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("pid", pid);
        uriVariables.put("status", status);
        return restTemplate.exchange(projectManagerUrl + "/v1/project/pid/status?pid={pid}&status={status}", HttpMethod.GET, httpEntity, ProjectDto.class, uriVariables).getBody();
    }

    /**
     * Returns a list of Variance Classification for varianceId and varianceStatus.
     *
     * @param varianceId
     * @param varianceStatus
     * @return List<VarianceClassificationDto>
     */
    public static List<VarianceClassificationDto> getVarianceClassificationList(Set<Long> varianceId, String varianceStatus) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("varianceId", varianceId.stream().filter(Objects::nonNull).map(Object::toString).collect(Collectors.joining(",")));
        uriVariables.put("varianceStatus", varianceStatus);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v1/variance?varianceId={varianceId}&varianceStatus={varianceStatus}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<List<VarianceClassificationDto>>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Variance Classification list is empty for varianceId: {} and varianceStatus: {}", varianceId, varianceStatus);
            return Collections.EMPTY_LIST;
        }
    }

    /**
     * Returns steps from tc_run_id.
     *
     * @param tc_run_id
     * @return StepsDto
     */
    public static StepsDto getSteps(String tc_run_id) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity<>(null, headers);

        try {
            Response response = restTemplate.exchange(
                    insertionManagerUrl + "/v1/steps?tc_run_id={tc_run_id}",
                    HttpMethod.GET,
                    httpEntity,
                    Response.class,
                    Collections.singletonMap("tc_run_id", tc_run_id)
            ).getBody();
            if(response!= null && response.getOperation().equals(OperationType.Success)){
                return mapper.convertValue(response.getData(), new TypeReference<>() {});
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Steps not found for tc_run_id: {}", tc_run_id);
        }
        return null;
    }

    public static List<TestExeDto> fetchTestExes(String s_run_id, Integer sort, String sortedColumn) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity<?> httpEntity = new HttpEntity<>(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);
        uriVariables.put("sortOrder", sort);
        uriVariables.put("sortedColumn", sortedColumn);
        try {
           return restTemplate.exchange(
                   insertionManagerUrl + "/v1/fetchTestExesList?s_run_id={s_run_id}&sortOrder={sortOrder}&sortedColumn={sortedColumn}",
                   HttpMethod.GET,
                   httpEntity,
                   new ParameterizedTypeReference<List<TestExeDto>>() {},
                   uriVariables).getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            log.info("Test exe list is empty for s_run_id: {}, sort: {} and sortedColumn: {}", s_run_id, sort, sortedColumn);
            return List.of();
        }
    }




    /**
     * Returns a list of suite exes for report name, pid, projects, startTime, endTime, env.
     *
     * @param reportName
     * @param pid
     * @param projects
     * @param startTime
     * @param endTime
     * @param envs
     * @return List<SuiteExeDto>
     */
    public static List<SuiteExeDto> getSuiteExesForReportName(String reportName, List<Long> pid, List<String> projects, long startTime, long endTime, List<String> envs) {
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("report_name", reportName);
        String pidList =  pid.stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        uriVariables.put("p_id", pidList);
        String projectsList = String.join(",", projects);
        uriVariables.put("projects", projectsList);
        uriVariables.put("s_start_time", startTime);
        uriVariables.put("s_end_time", endTime);
        String envList = String.join(",", envs);
        uriVariables.put("env", envList);

        String url = insertionManagerUrl + "/v1/suiteExe/report_name?report_name={report_name}" +
                "&p_id={p_id}&projects={projects}&s_start_time={s_start_time}&s_end_time={s_end_time}&env={env}";
        try {
            Response response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(null, ReportUtils.getAuthHeader()),
                    Response.class,
                    uriVariables
            ).getBody();

            if (response != null && response.getOperation().equals(OperationType.Success)) {
                List<?> suiteExes = (List<?>) response.getData();
                return suiteExes.stream().map(suiteExe -> mapper.convertValue(suiteExe, new TypeReference<SuiteExeDto>() {})).collect(Collectors.toList());
            } else {
                log.error("Something went wrong while fetching SuiteExes for reportName {}, " +
                        "pids: {}, projects: {}, startTime: {}, endTime: {}, envs: {}",
                        reportName, pid, projects, startTime, endTime, envs);
                return List.of();
            }
        } catch (HttpClientErrorException.NotFound ex) {
            log.error("Suite exe list is empty for reportName: {}, pid: {}, projects: {}, start time: {}, end time: {} and env: {}", reportName, pid, projects, startTime, endTime, envs);
            return List.of();
        }
    }

    /**
     * Returns test case count for s_run_id and status.
     *
     * @param s_run_id
     * @param status
     * @return Map<String, Object> - map with test case count.
     */
    public static  Map<String, Double> getTestCaseCount(List<String> s_run_id, List<String> status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        String sRunIdList = s_run_id.stream()
                .collect(Collectors.joining(","));
        uriVariables.put("s_run_id", sRunIdList);
        String statusList = status.stream()
                .collect(Collectors.joining(","));
        uriVariables.put("status", statusList);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v1/testExe/testCase?s_run_id={s_run_id}&status={status}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<Map<String, Double>>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (RestClientException ex) {
            log.info("Empty map return for test case count for s_run_id: {} and status: {}", s_run_id, status);
            return Collections.emptyMap();
        }
    }

    /**
     * Returns test exes and count for ruleApi, pageNo, sort and sortedColumn.
     *
     * @param payload
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return Map<String, Object> - map all testcases and count.
     */
    public static Map<String, Object> getAllTestExesForTcRunId(RuleApi payload, Integer pageNo, Integer sort,
                                                               String sortedColumn) {
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("pageNo", pageNo);
        uriVariables.put("sort", sort);
        uriVariables.put("sortedColumn", sortedColumn);

        String url = insertionManagerUrl + "/v1/testExe/data?pageNo={pageNo}&sort={sort}&sortedColumn={sortedColumn}";
        try {
            ResponseEntity response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(payload, ReportUtils.getAuthHeader()), Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object  data = convertedMap.get("data");
            Type type = new TypeToken<Map<String, Object>>() {
            }.getType();

            convertedMap = gson.fromJson(gson.toJson(data), type);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("count", (long) Math.floor((Double) convertedMap.get("count")));
            data = convertedMap.get("results");
            type = new TypeToken<List<BasicDBObject>>() {
            }.getType();
            List<BasicDBObject> basicDBObjectList = gson.fromJson(gson.toJson(data), type);
            for (BasicDBObject basicDBOBject: basicDBObjectList) {
                Object d = basicDBOBject.get("result");
                gson = new Gson();
                type = new TypeToken<List<Document>>() {
                }.getType();
                basicDBOBject.put("result",gson.fromJson(gson.toJson(d), type));
                basicDBOBject.put("end_time",(long) Math.floor((Double) basicDBOBject.get("end_time")));
                basicDBOBject.put("start_time",(long) Math.floor((Double) basicDBOBject.get("start_time")));
            }

            resultMap.put("results", basicDBObjectList);
            return resultMap;
        } catch (RestClientException ex) {
            log.error("Error occurred due to empty map for payload: {}, pageNo: {}, sort: {} and sortedColumn: {}", payload, pageNo, sort, sortedColumn, ex);
            return Collections.emptyMap();
        }
    }

    public static Long getStatusWiseCount(String s_run_id, String status) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity httpEntity = new HttpEntity(null, headers);
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("s_run_id", s_run_id);
        uriVariables.put("status", status);
        try {
            ResponseEntity response = restTemplate.exchange(insertionManagerUrl + "/v1/testExe/statusCount?s_run_id={s_run_id}&status={status}", HttpMethod.GET, httpEntity, Object.class, uriVariables);
            Gson gson = new Gson();
            String json = gson.toJson(response.getBody());
            Map<String, Object> convertedMap = gson.fromJson(json, new TypeToken<Map<String, Object>>() {
            }.getType());
            Object data = convertedMap.get("data");
            Type type = new TypeToken<Long>() {
            }.getType();

            return gson.fromJson(gson.toJson(data), type);
        } catch (RestClientException ex) {
            log.error("Error while fetching status wise count for s_run_id: {} and status: {} - {}", s_run_id, status,ex.getMessage());
            return null;
        }
    }

    public static Double getTimeRemainingNew(SuiteExeDto suite, List<List<DependencyTree>> dependencies) {
        try{
        String url = insertionManagerUrl + "/v1/getExpectedSuiteExecutionTime";

        SuiteDependencyDto dto = new SuiteDependencyDto();
        dto.setDependency(dependencies);
        dto.setSuiteExeDto(suite);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(SecurityContextHolder.getContext().getAuthentication().getCredentials().toString());
        HttpEntity<?> httpEntity = new HttpEntity<>(dto, headers);
            ResponseEntity<Double> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        httpEntity,
                        Double.class);
            if(response.getStatusCode() == HttpStatus.OK){
                return response.getBody();
            }
        } catch (RestClientException ex) {
            log.error("Internal Service Error: {}, while calculating remaining time for suite with id: {}",
                    ex.getMessage(), suite.getS_run_id() );
        }
        return 60.0;
    }

}
