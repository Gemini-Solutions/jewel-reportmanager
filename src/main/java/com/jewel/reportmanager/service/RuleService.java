package com.jewel.reportmanager.service;

import com.jewel.reportmanager.dto.*;
import com.jewel.reportmanager.dto.RuleApi;
import com.jewel.reportmanager.enums.StatusColor;
import com.jewel.reportmanager.exception.CustomDataException;
import com.jewel.reportmanager.utils.ReportUtils;
import com.jewel.reportmanager.utils.RestApiUtils;
import com.mongodb.BasicDBObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.jewel.reportmanager.enums.OperationType.*;
import static com.jewel.reportmanager.enums.StatusColor.*;
import static com.jewel.reportmanager.enums.UserRole.*;
import static com.jewel.reportmanager.utils.ReportResponseConstants.NEVER_FIXED;
import static com.jewel.reportmanager.utils.ReportResponseConstants.*;

@Slf4j
@Service
public class RuleService {

    @Autowired
    private SimpMessageSendingOperations simpMessagingTemplate;

    @Autowired
    private ReportUtils reportUtils;

    @Autowired
    private JwtHelperService jwtHelper;

    /**
     * @param payload
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @return Response
     * @throws ParseException
     */
    public Response getRuleReport(RuleApi payload, Integer pageNo,
            Integer sort, String sortedColumn) throws ParseException {

        if ((sort != null && sortedColumn == null) || (sort == null && sortedColumn != null)) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(BOTH_PARAMETERS_REQUIRED, null, Failure, HttpStatus.OK);
        }
        if (sort != null && sort != -1 && sort != 0 && sort != 1) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(INVALID_SORT_VALUE, null, Failure, HttpStatus.OK);
        }

        UserDto user = ReportUtils.getUserDtoFromServetRequest();
        String username = user.getUsername();

        List<Long> allPids = new ArrayList<>(payload.getProjectid());
        List<Long> accessPids;
        if (user.getRole().equalsIgnoreCase(USER.toString())) {
            accessPids = RestApiUtils.getProjectRolePidList(payload.getProjectid(), ACTIVE_STATUS, username);
        } else if (user.getRole().equalsIgnoreCase(ADMIN.toString())) {
            accessPids = RestApiUtils.getProjectPidListForRealCompanyNameAndStatus(payload.getProjectid(),
                    ACTIVE_STATUS, user.getRealCompany().toUpperCase());
        } else {
            accessPids = RestApiUtils.getProjectPidList(payload.getProjectid(), ACTIVE_STATUS);
        }
        payload.setProjectid(accessPids);
        allPids.removeAll(accessPids);

        List<String> errors = new ArrayList<>();
        if (!allPids.isEmpty()) {
            List<String> projectNames = RestApiUtils.getProjectNames(payload.getProjectid());
            for (String projectName : projectNames) {
                errors.add("You don't have access for " + projectName.toUpperCase());
            }
        }

        switch ((int) payload.getReportid()) {
            case 1:
                return createSuiteRunReport(payload, pageNo, sort, sortedColumn, errors);
            case 2:
                return createSuiteSummaryReport(payload, pageNo, errors);
            case 3:
                return createSuiteDiagnoseReport(payload, pageNo, errors);
            case 4:
                return createTestCaseRunReport(payload, pageNo, sort, sortedColumn, errors);
            case 5:
                return createTestCaseSummaryReport(payload, pageNo, sort, sortedColumn, errors);
            case 6:
                return createTestCaseDiagnoseReport(payload, pageNo, sort, sortedColumn, errors);
            default:
                log.error("Error occurred due to records not found");
                throw new CustomDataException(REPORT_ID_NOT_VALID, null, Failure, HttpStatus.OK);
        }

    }

    /**
     * Creates suite run report.
     *
     * @param payload
     * @param pageNo
     * @param sort
     * @param sortedColumn
     * @param errors
     * @return
     * @throws ParseException
     */

    private Response createSuiteRunReport(RuleApi payload, Integer pageNo,
            Integer sort, String sortedColumn, List<String> errors) throws ParseException {

        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "Report Name", "Environment", "Status", "Executed By", "Action",
                "Duration", "Testcase Summary");
        result.put("headers", headers);
        List<Map<String, Object>> data = new ArrayList<>();
        long startTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getStartTime()).getTime();
        long endTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getEndTime()).getTime()
                + (1000 * 60 * 60 * 24);
        List<String> projects = payload.getProject();
        projects.replaceAll(String::toLowerCase);
        List<String> envs = payload.getEnv();
        List<Long> p_ids = payload.getProjectid();
        envs.replaceAll(String::toLowerCase);

        long count = RestApiUtils.getSuiteExeCount(p_ids, envs, startTime, endTime);
        if (count == 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }
        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }

        List<SuiteExeDto> suiteReports = RestApiUtils.getSuiteExes(p_ids, envs, startTime, endTime, pageNo, sort,
                sortedColumn);
        if (suiteReports.isEmpty()) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NUMBER_IS_ABOVE_TOTAL_PAGES, null, Failure, HttpStatus.OK);
        }
        List<String> sRunIds = RestApiUtils.getS_Run_Ids(p_ids, envs, startTime, endTime, pageNo, sort, sortedColumn);

        List<TestExeDto> testExeDtoList = RestApiUtils.getTestExeListForS_run_ids(sRunIds);

        for (SuiteExeDto suiteExeDto : suiteReports) {
            data.add(createSuiteExeReport(testExeDtoList, suiteExeDto));
        }

        Collections.reverse(data);

        result.put("data", data);
        result.put("totalElements", count);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return new Response(result, count + " Records found", Success);
    }

    /**
     * creates suite exe report for list of suite exes.
     *
     * @param testExeDtoList
     * @param suiteExeDto
     * @return
     */
    private Map<String, Object> createSuiteExeReport(List<TestExeDto> testExeDtoList, SuiteExeDto suiteExeDto) {
        Map<String, Object> temp = new HashMap<>();
        Map<String, Long> statusMap = new HashMap<>();
        Set<String> users = new HashSet<>();

        for (StatusColor statusColor : StatusColor.values()) {
            statusMap.put(statusColor.toString(), 0L);
        }
        if (!testExeDtoList.isEmpty()) {
            temp = getTestExeStatusForSuiteExe(testExeDtoList, suiteExeDto, users, statusMap);
        }

        createActionReportForSuiteExe(temp, suiteExeDto, users);
        return temp;
    }

    /**
     * Return test exe status for suite exe.
     *
     * @param testExeDtoList
     * @param suiteExeDto
     * @param users
     * @param statusMap
     * @return
     */
    private Map<String, Object> getTestExeStatusForSuiteExe(List<TestExeDto> testExeDtoList, SuiteExeDto suiteExeDto,
            Set<String> users, Map<String, Long> statusMap) {
        Map<String, Object> temp = new HashMap<>();
        long totalCount = 0L;
        for (TestExeDto testExeDto : testExeDtoList) {
            if (!testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                continue;
            }
            if (testExeDto.getInvoke_user() != null) {
                users.add(testExeDto.getInvoke_user());
            }
            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                String status = testExeDto.getStatus().toUpperCase();
                switch (status) {
                    case "PASS":
                    case "FAIL":
                    case "EXE":
                    case "ERR":
                    case "INFO":
                    case "WARN":
                        long value = statusMap.get(status) + 1;
                        statusMap.put(status, value);
                        totalCount++;
                }
            }
        }
        if (totalCount > 0) {
            Map<String, Object> doughnutSubType = new HashMap<>();
            doughnutSubType.put("subType", "doughnut_chart");
            doughnutSubType.put("heading", "Total Testcase(s)");
            temp.put("Testcase Summary",
                    ReportUtils.createCustomObject(ReportUtils.createDoughnutChart(statusMap),
                            "chart", totalCount, "center", doughnutSubType));
        }
        return temp;
    }

    /**
     * Creates action report for suite exe to generate rule report.
     *
     * @param temp
     * @param suiteExeDto
     * @param users
     */
    private void createActionReportForSuiteExe(Map<String, Object> temp, SuiteExeDto suiteExeDto, Set<String> users) {
        Map<String, Object> actionReport = new HashMap<>();
        actionReport.put("subType", "execution_report");
        temp.put("Action",
                ReportUtils.createCustomObject(suiteExeDto.getS_run_id(), "action", suiteExeDto.getS_run_id(), "center",
                        actionReport));
        temp.put("Report Name",
                ReportUtils.createCustomObject(suiteExeDto.getReport_name(), "text", suiteExeDto.getReport_name(),
                        "left"));
        temp.put("Project Name",
                ReportUtils.createCustomObject(StringUtils.capitalize(suiteExeDto.getProject_name()), "text",
                        suiteExeDto.getProject_name(),
                        "left"));
        temp.put("P ID",
                ReportUtils.createCustomObject((suiteExeDto.getP_id()), "text",
                        suiteExeDto.getP_id(),
                        "left"));
        temp.put("Environment",
                ReportUtils.createCustomObject(StringUtils.capitalize(suiteExeDto.getEnv()), "text",
                        suiteExeDto.getEnv(),
                        "left"));
        if (!users.isEmpty()) {
            temp.put("Executed By", ReportUtils.createCustomObject(users,
                    "pills", users, "left"));
        } else {
            temp.put("Executed By", ReportUtils.createCustomObject("-",
                    "text", "-", "center"));
        }
        temp.put("Status",
                ReportUtils.createCustomObject(suiteExeDto.getStatus(), "status", suiteExeDto.getStatus(), "center"));

        if (suiteExeDto.getS_end_time() != 0) {
            Map<String, Object> subtype = new HashMap<>();
            subtype.put("subType", "duration");
            Map<String, Object> values = new HashMap<>();
            values.put("start_time", suiteExeDto.getS_start_time());
            values.put("end_time", suiteExeDto.getS_end_time());
            temp.put("Duration",
                    ReportUtils.createCustomObject(values, "date",
                            ((float) (suiteExeDto.getS_end_time() - suiteExeDto.getS_start_time())),
                            "center", subtype));
        } else {
            temp.put("Duration", ReportUtils.createCustomObject("-", "text", suiteExeDto.getS_end_time(), "center"));
        }
    }

    private Response createSuiteSummaryReport(RuleApi payload, Integer pageNo, List<String> errors)
            throws ParseException {

        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "Report Name", "Environment", "Suite Summary", "Last 5 Runs",
                "Stability Index",
                "Average Fix Time", "App Stability Score", "Automation Stability Score", "Analysis");
        result.put("headers", headers);

        long startTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getStartTime()).getTime();
        long endTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getEndTime()).getTime()
                + (1000 * 60 * 60 * 24);
        List<String> projects = payload.getProject();
        projects.replaceAll(String::toLowerCase);
        List<String> envs = payload.getEnv();
        envs.replaceAll(String::toLowerCase);
        List<Long> p_ids = payload.getProjectid();

        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }

        List<String> reportNames = RestApiUtils.getReportNames(p_ids, envs, startTime, endTime, pageNo);
        if (reportNames.isEmpty()) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }

        long count = getReportDetailsToCreateSuiteSummaryReport(reportNames, p_ids, projects, startTime,
                endTime, envs, data);

        result.put("data", data);
        result.put("totalElements", count);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }

        return new Response(result, count + " Records found", Success);
    }

    private Long getReportDetailsToCreateSuiteSummaryReport(List<String> reportNames, List<Long> p_ids,
            List<String> projects, long startTime, long endTime, List<String> envs, List<Map<String, Object>> data) {
        long count = 0;
        for (String reportName : reportNames) {
            Map<String, List<SuiteExeDto>> suiteMap = ReportUtils.getSuiteNames(reportName, p_ids, projects, startTime,
                    endTime, envs);
            count = count + suiteMap.size();
            for (Map.Entry<String, List<SuiteExeDto>> entry : suiteMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                List<SuiteExeDto> getAllSuites = entry.getValue();
                Map<String, Long> statusMap = new HashMap<>();
                for (StatusColor statusColor : StatusColor.values()) {
                    statusMap.put(statusColor.toString(), 0L);
                }

                long totalCount = getStatusMapForAllSuites(getAllSuites, statusMap);

                data.add(getReportDataForSuiteSummaryReport(getAllSuites, reportName, totalCount, statusMap));
            }
        }
        return count;
    }

    private Long getStatusMapForAllSuites(List<SuiteExeDto> getAllSuites, Map<String, Long> statusMap) {
        long totalCount = 0L;
        for (SuiteExeDto suiteExeDto : getAllSuites) {
            String status = suiteExeDto.getStatus().toUpperCase();
            switch (status) {
                case "PASS":
                case "FAIL":
                case "EXE":
                case "ERR":
                case "INFO":
                case "WARN":
                    long value = statusMap.get(status) + 1;
                    statusMap.put(status, value);
                    totalCount++;
            }
        }
        return totalCount;
    }

    private Map<String, Object> getReportDataForSuiteSummaryReport(List<SuiteExeDto> getAllSuites, String reportName,
            Long totalCount, Map<String, Long> statusMap) {
        String env = getAllSuites.get(0).getEnv();
        List<SuiteExeDto> sortedList = ReportUtils.getSortedListForSuiteExe(getAllSuites);
        double brokenIndex = ReportUtils.brokenIndexForSuiteExe(getAllSuites);
        int stabilityIndex = ReportUtils.stabilityIndex(brokenIndex);
        long averageFixTime = ReportUtils.averageFixTimeForSuiteExe(getAllSuites);
        long downTime = ReportUtils.getDownTimeForSuiteExe(sortedList);
        Map<String, Object> last5SuiteRuns = ReportUtils.last5SuiteRuns(getAllSuites);
        Map<String, Long> culprit = ReportUtils.culprit(getAllSuites);

        double devScore = ReportUtils.getScore(brokenIndex, downTime, averageFixTime, env, getAllSuites);
        double qaScore = ReportUtils.getQAScore(getAllSuites);
        String averageFixTimeStr;
        if (brokenIndex == 1) {
            averageFixTimeStr = NEVER_FIXED;
        } else {
            averageFixTimeStr = ReportUtils.convertLongToTime(averageFixTime);
        }
        Map<String, Object> temp = new HashMap<>();
        if (last5SuiteRuns != null) {
            Map<String, Object> stackedBarChartType = new HashMap<>();
            stackedBarChartType.put("subType", "stacked_bar_chart");
            stackedBarChartType.put("heading", "Last 5 Runs");
            long size = (long) last5SuiteRuns.get("size");
            last5SuiteRuns.remove("size");
            temp.put("Last 5 Runs", ReportUtils.createCustomObject(last5SuiteRuns, "chart", size, "center",
                    stackedBarChartType));
        } else {
            temp.put("Last 5 Runs",
                    ReportUtils.createCustomObject("-", "text", "-", "left"));
        }
        temp.put("Report Name",
                ReportUtils.createCustomObject(StringUtils.capitalize(reportName), "text", reportName,
                        "left"));
        temp.put("Project Name",
                ReportUtils.createCustomObject(
                        StringUtils.capitalize(getAllSuites.get(0).getProject_name()), "text",
                        getAllSuites.get(0).getProject_name(), "left"));
        temp.put("Environment",
                ReportUtils.createCustomObject(StringUtils.capitalize(getAllSuites.get(0).getEnv()), "text",
                        getAllSuites.get(0).getEnv(), "left"));
        Map<String, Object> doughnutSubType = new HashMap<>();
        doughnutSubType.put("subType", "doughnut_chart");
        doughnutSubType.put("heading", "Total Suite(s)");
        temp.put("Suite Summary",
                ReportUtils.createCustomObject(ReportUtils.createDoughnutChart(statusMap), "chart",
                        totalCount, "center", doughnutSubType));
        temp.put("Stability Index",
                ReportUtils.createCustomObject(stabilityIndex + "%", "text", stabilityIndex, "center"));
        temp.put("Average Fix Time",
                ReportUtils.createCustomObject(averageFixTimeStr, "text", averageFixTimeStr, "center"));
        if (culprit != null) {
            String averagePercentage = culprit.get("average") + "%";
            culprit.remove("average");
            temp.put("Analysis",
                    ReportUtils.createCustomObject(culprit, "tabs", averagePercentage, "left"));
        } else {
            temp.put("Analysis",
                    ReportUtils.createCustomObject("-", "text", "-", "left"));
        }
        temp.put("App Stability Score",
                ReportUtils.createCustomObject(devScore, "score", devScore, "center"));
        temp.put("Automation Stability Score",
                ReportUtils.createCustomObject(qaScore, "score", qaScore, "center"));
        temp.put("P ID", ReportUtils.createCustomObject(getAllSuites.get(0).getP_id(), "text",
                getAllSuites.get(0).getP_id(), "center"));
        return temp;
    }

    private Response createSuiteDiagnoseReport(RuleApi payload, Integer pageNo, List<String> errors)
            throws ParseException {

        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "Report Name", "Environment", "Last Run Status", "Failing Since",
                "Stability Index", "Downtime", "Average Fix Time", "Last Pass", "Last Status Details",
                "Analysis");
        result.put("headers", headers);

        long startTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getStartTime()).getTime();
        long endTime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.getEndTime()).getTime()
                + (1000 * 60 * 60 * 24);

        List<String> projects = payload.getProject();
        projects.replaceAll(String::toLowerCase);
        List<String> envs = payload.getEnv();
        List<Long> p_ids = payload.getProjectid();
        envs.replaceAll(String::toLowerCase);

        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }

        List<String> reportNames = RestApiUtils.getReportNames(p_ids, envs, startTime, endTime, pageNo);
        if (reportNames.isEmpty()) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }

        long count = getReportDetailsToCreateSuiteDiagnoseReport(reportNames, p_ids, projects, startTime,
                endTime, envs, data);

        result.put("data", data);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        result.put("totalElements", count);

        return new Response(result, count + " Records found", Success);
    }

    private Long getReportDetailsToCreateSuiteDiagnoseReport(List<String> reportNames, List<Long> pIds,
            List<String> projects, long startTime, long endTime, List<String> envs, List<Map<String, Object>> data) {
        long count = 0;
        for (String reportName : reportNames) {
            Map<String, List<SuiteExeDto>> suiteMap = ReportUtils.getSuiteNames(reportName, pIds, projects, startTime,
                    endTime, envs);
            count = count + suiteMap.size();
            for (Map.Entry<String, List<SuiteExeDto>> entry : suiteMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                List<SuiteExeDto> getAllSuites = entry.getValue();
                List<SuiteExeDto> sortedList = ReportUtils.getSortedListForSuiteExe(getAllSuites);
                double brokenIndex = ReportUtils.brokenIndexForSuiteExe(getAllSuites);
                int stabilityIndex = ReportUtils.stabilityIndex(brokenIndex);
                String failingSince = getFailingSinceForSuiteExe(sortedList, brokenIndex);
                String lastRunStatus = sortedList.get(0).getStatus();
                Long lastPass = getLastPassForSuiteExe(sortedList);
                long downTime = ReportUtils.getDownTimeForSuiteExe(sortedList);
                Map<String, Long> culprit = ReportUtils.culprit(getAllSuites);

                Map<String, Long> statusMap = lastStatusDetails(sortedList);
                long totalCount = 0;
                for (Map.Entry<String, Long> entry1 : statusMap.entrySet()) {
                    totalCount = totalCount + entry1.getValue();
                }
                long averageFixTime = ReportUtils.averageFixTimeForSuiteExe(getAllSuites);
                String downTimeStr;
                String averageFixTimeStr;
                if (brokenIndex == 1) {
                    averageFixTimeStr = NEVER_FIXED;
                } else {
                    averageFixTimeStr = ReportUtils.convertLongToTime(averageFixTime);
                }

                if (downTime == 0) {
                    downTimeStr = "No Issues";
                } else {
                    downTimeStr = ReportUtils.convertLongToTime(downTime);
                }

                data.add(getDataForSuiteExeToCreateSuiteDiagnoseReport(reportName, getAllSuites, stabilityIndex,
                        failingSince, lastRunStatus, lastPass,
                        culprit, downTimeStr, averageFixTimeStr, statusMap, totalCount));
            }
        }
        return count;
    }

    private Map<String, Object> getDataForSuiteExeToCreateSuiteDiagnoseReport(String reportName,
            List<SuiteExeDto> getAllSuites, int stabilityIndex, String failingSince, String lastRunStatus,
            Long lastPass,
            Map<String, Long> culprit, String downTimeStr, String averageFixTimeStr, Map<String, Long> statusMap,
            long totalCount) {
        Map<String, Object> temp = new HashMap<>();
        temp.put("Report Name",
                ReportUtils.createCustomObject(StringUtils.capitalize(reportName), "text", reportName,
                        "left"));

        temp.put("Project Name",
                ReportUtils.createCustomObject(
                        StringUtils.capitalize(getAllSuites.get(0).getProject_name()), "text",
                        getAllSuites.get(0).getProject_name(), "left"));
        if (culprit != null) {
            String averagePercentage = culprit.get("average") + "%";
            culprit.remove("average");
            temp.put("Analysis",
                    ReportUtils.createCustomObject(culprit, "tabs", averagePercentage, "left"));
        } else {
            temp.put("Analysis",
                    ReportUtils.createCustomObject("-", "text", "-", "left"));
        }
        temp.put("Environment",
                ReportUtils.createCustomObject(StringUtils.capitalize(getAllSuites.get(0).getEnv()), "text",
                        getAllSuites.get(0).getEnv(), "left"));
        Map<String, Object> doughnutSubType = new HashMap<>();
        doughnutSubType.put("heading", "Total Testcase(s)");
        doughnutSubType.put("subType", "doughnut_chart");
        temp.put("Last Status Details",
                ReportUtils.createCustomObject(ReportUtils.createDoughnutChart(statusMap), "chart",
                        totalCount, "center", doughnutSubType));
        temp.put("Stability Index",
                ReportUtils.createCustomObject(stabilityIndex + "%", "text", stabilityIndex, "center"));
        temp.put("Average Fix Time",
                ReportUtils.createCustomObject(averageFixTimeStr, "text", averageFixTimeStr, "center"));
        temp.put("Last Run Status",
                ReportUtils.createCustomObject(lastRunStatus, "status", lastRunStatus, "center"));
        if (lastPass > 0) {
            Map<String, Object> timereport = new HashMap<>();
            timereport.put("subType", "datetime");
            temp.put("Last Pass",
                    ReportUtils.createCustomObject(lastPass, "date", lastPass, "center", timereport));
        } else {
            temp.put("Last Pass", ReportUtils.createCustomObject("-", "text", "-", "center"));
        }
        temp.put("Failing Since",
                ReportUtils.createCustomObject(failingSince, "text", failingSince, "center"));
        temp.put("Downtime", ReportUtils.createCustomObject(downTimeStr, "text", downTimeStr, "center"));
        temp.put("P ID", ReportUtils.createCustomObject(getAllSuites.get(0).getP_id(), "text",
                getAllSuites.get(0).getP_id(), "center"));
        return temp;
    }

    private Map<String, Long> lastStatusDetails(List<SuiteExeDto> suites) {
        String sRunId = suites.get(0).getS_run_id();
        List<TestExeDto> testcaseDetails = RestApiUtils.getTestExeList(sRunId);
        Map<String, Long> statusMap = new HashMap<>();
        for (StatusColor statusColor : StatusColor.values()) {
            statusMap.put(statusColor.toString(), 0L);
        }
        for (TestExeDto testExe : testcaseDetails) {
            String status = testExe.getStatus().toUpperCase();
            switch (status) {
                case "PASS":
                case "FAIL":
                case "EXE":
                case "ERR":
                case "INFO":
                case "WARN":
                    long value = statusMap.get(status) + 1;
                    statusMap.put(status, value);
            }
        }
        return statusMap;
    }

    private Long getLastPassForSuiteExe(List<SuiteExeDto> suites) {
        for (SuiteExeDto suite : suites) {
            if (suite.getStatus().equalsIgnoreCase("PASS")) {
                return suite.getS_start_time();
            }
        }
        return 0L;
    }

    private String getFailingSinceForSuiteExe(List<SuiteExeDto> suites, double brokenIndex) {
        if (brokenIndex == 0) {
            return NO_ISSUES;
        } else if (brokenIndex == 1) {
            return NEVER_FIXED;
        } else {
            int count = 0;
            for (SuiteExeDto suite : suites) {
                if (!suite.getStatus().equalsIgnoreCase("FAIL")) {
                    break;
                }
                count++;
            }
            if (count == 0) {
                return NO_ISSUES;
            }
            return new StringBuilder().append("Last ").append(count).append(" Runs").toString();
        }
    }

    private Response createTestCaseRunReport(RuleApi payload, Integer pageNo, Integer sort,
            String sortedColumn, List<String> errors) {
        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "TestCase Name", "Environment", "Status", "Action",
                "Product Type",
                "Duration");
        result.put("headers", headers);
        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }
        Map<String, Object> resultMap = RestApiUtils.getAllTestExesForTcRunId(payload, pageNo, sort,
                sortedColumn);
        long count = (long) resultMap.get("count");
        List<BasicDBObject> results = (List<BasicDBObject>) resultMap.get("results");
        if (count == 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }
        List<Map<String, Object>> data = getDataForTestCaseRunReport(results);
        Collections.reverse(data);
        result.put("data", data);
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        result.put("totalElements", count);
        return new Response(result, count + " Records found", Success);
    }

    private List<Map<String, Object>> getDataForTestCaseRunReport(List<BasicDBObject> results) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (BasicDBObject testExe : results) {
            List<Document> suiteExeList = (List<Document>) testExe.get("result");
            Document suiteExe = suiteExeList.get(0);
            Map<String, Object> temp = new HashMap<>();
            Map<String, Object> actionReport = new HashMap<>();
            actionReport.put("subType", "teststep_report");
            temp.put("Action",
                    ReportUtils.createCustomObject(testExe.get("tc_run_id"), "action", testExe.get("tc_run_id"),
                            "center", actionReport));
            temp.put("TestCase Name",
                    ReportUtils.createCustomObject(testExe.get("name"), "text", testExe.get("name"), "left"));
            temp.put("P ID",
                    ReportUtils.createCustomObject(
                            StringUtils.capitalize(String.valueOf(suiteExe.get("p_id"))), "text",
                            suiteExe.get("p_id"), "left"));
            temp.put("Project Name",
                    ReportUtils.createCustomObject(
                            StringUtils.capitalize(String.valueOf(suiteExe.get("project_name"))), "text",
                            suiteExe.get("project_name"), "left"));
            temp.put("Environment",
                    ReportUtils.createCustomObject(StringUtils.capitalize(String.valueOf(suiteExe.get("env"))),
                            "text", suiteExe.get("env"), "left"));
            temp.put("Status",
                    ReportUtils.createCustomObject(testExe.get("status"), "status", testExe.get("status"),
                            "center"));
            temp.put("Product Type", ReportUtils.createCustomObject(testExe.get("product_type"), "text",
                    testExe.get("product_type"), "left"));
            temp.put("P ID",
                    ReportUtils.createCustomObject(suiteExe.get("p_id"), "text",
                            suiteExe.get("p_id"), "left"));

            if (((long) testExe.get("end_time")) != 0) {
                Map<String, Object> subtype = new HashMap<>();
                subtype.put("subType", "duration");
                Map<String, Object> values = new HashMap<>();
                values.put("start_time", (testExe.get("start_time")));
                values.put("end_time", (testExe.get("end_time")));
                temp.put("Duration",
                        ReportUtils.createCustomObject(values, "date",
                                ((float) (((long) testExe.get("end_time")) - ((long) testExe.get("start_time")))),
                                "center", subtype));
                temp.put("End Time",
                        ReportUtils.createCustomObject(((long) testExe.get("end_time")), "date",
                                (testExe.get("end_time")), "center"));
            } else {
                temp.put("Duration",
                        ReportUtils.createCustomObject("-", "text", (testExe.get("end_time")), "center"));
            }
            data.add(temp);
        }
        return data;
    }

    private Response createTestCaseSummaryReport(RuleApi payload, Integer pageNo, Integer sort,
            String sortedColumn, Object errors) {
        Map<String, Object> result = new HashMap<>();

        List<Object> headers = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "TestCase Name", "TestCase Summary",
                "Broken Index", "Average Fix Time");
        result.put("headers", headers);
        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }
        Map<String, Object> resultMap = RestApiUtils.getAllTestExesForTcRunId(payload, pageNo, sort,
                sortedColumn);
        long count = (long) resultMap.get("count");
        List<BasicDBObject> results = (List<BasicDBObject>) resultMap.get("results");
        if (count == 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }
        Map<String, List<TestExeCommonDto>> listMap = new HashMap<>();
        for (BasicDBObject testExeDto : results) {
            List<Document> suiteExeList = (List<Document>) testExeDto.get("result");
            Document suiteExeDto = suiteExeList.get(0);
            TestExeCommonDto testExeDtoSummery = ReportUtils.getTestExeCommonByBasicObjectAndDocument(testExeDto,
                    suiteExeDto);
            StringBuilder key = new StringBuilder();
            key.append(testExeDtoSummery.getName()).append(":").append(testExeDtoSummery.getReport_name())
                    .append(":").append(testExeDtoSummery.getEnv()).append(":")
                    .append(testExeDtoSummery.getProject_name());
            List<TestExeCommonDto> list = listMap.getOrDefault(key.toString(), null);
            if (list == null) {
                List<TestExeCommonDto> testExeDtoSummeryList = new ArrayList<>();
                testExeDtoSummeryList.add(testExeDtoSummery);
                listMap.put(String.valueOf(key), testExeDtoSummeryList);
            } else {
                list.add(testExeDtoSummery);
                listMap.put(String.valueOf(key), list);
            }
        }

        for (Map.Entry<String, List<TestExeCommonDto>> entry : listMap.entrySet()) {

            List<TestExeCommonDto> testExeCommonDtoSummeryList = entry.getValue();

            double brokenIndex = ReportUtils.brokenIndexForTestExe(testExeCommonDtoSummeryList);
            String averageFixTime;
            if (brokenIndex == 1) {
                averageFixTime = NEVER_FIXED;
            } else {
                averageFixTime = ReportUtils.averageFixTimeForTestExeCommon(testExeCommonDtoSummeryList);
            }
            long totalCount = 0;
            long passCount = 0, failCount = 0, errCount = 0, warnCount = 0, infoCount = 0,
                    exeCount = 0;

            Map<String, Long> statusCount = new HashMap<>();
            for (TestExeCommonDto testExeDtovar : testExeCommonDtoSummeryList) {

                if (testExeDtovar.getStatus().equalsIgnoreCase("PASS")) {
                    passCount = passCount + 1;

                } else if (testExeDtovar.getStatus().equalsIgnoreCase("FAIL")) {
                    failCount = failCount + 1;

                } else if (testExeDtovar.getStatus().equalsIgnoreCase("ERR")) {
                    errCount = errCount + 1;

                } else if (testExeDtovar.getStatus().equalsIgnoreCase("WARN")) {
                    warnCount = warnCount + 1;

                } else if (testExeDtovar.getStatus().equalsIgnoreCase("INFO")) {
                    infoCount = infoCount + 1;

                } else if (testExeDtovar.getStatus().equalsIgnoreCase("EXE")) {
                    exeCount = exeCount + 1;

                }
                totalCount = totalCount + 1;
            }

            statusCount.put("PASS", passCount);
            statusCount.put("FAIL", failCount);
            statusCount.put("ERR", errCount);
            statusCount.put("WARN", warnCount);
            statusCount.put("INFO", infoCount);
            statusCount.put("EXE", exeCount);
            Map<String, Object> temp = new HashMap<>();
            temp.put("Project Name",
                    ReportUtils.createCustomObject(
                            StringUtils.capitalize(testExeCommonDtoSummeryList.get(0).getProject_name()), "text",
                            testExeCommonDtoSummeryList.get(0).getProject_name(), "left"));
            temp.put("TestCase Name",
                    ReportUtils.createCustomObject(testExeCommonDtoSummeryList.get(0).getName(), "text",
                            testExeCommonDtoSummeryList.get(0).getName(), "left"));

            Map<String, Object> doughnutSubType = new HashMap<>();
            doughnutSubType.put("subType", "doughnut_chart");
            doughnutSubType.put("heading", "Total Testcase(s)");
            temp.put("TestCase Summary",
                    ReportUtils.createCustomObject(ReportUtils.createDoughnutChart(statusCount),
                            "chart", totalCount, "center", doughnutSubType));

            temp.put("Broken Index", ReportUtils.createCustomObject(brokenIndex, "text", brokenIndex, "center"));
            temp.put("Average Fix Time",
                    ReportUtils.createCustomObject(averageFixTime, "text", averageFixTime, "center"));
            temp.put("P ID",
                    ReportUtils.createCustomObject(testExeCommonDtoSummeryList.get(0).getP_id(), "text",
                            testExeCommonDtoSummeryList.get(0).getP_id(), "left"));
            data.add(temp);
        }

        result.put("data", data);
        if (errors != null) {
            result.put("errors", errors);
        }
        result.put("totalElements", listMap.size());

        return new Response(result, listMap.size() + " Records found", Success);
    }

    private Response createTestCaseDiagnoseReport(RuleApi payload, Integer pageNo, Integer sort,
            String sortedColumn, Object errors) {

        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        List<Map<String, Object>> data = new ArrayList<>();
        Collections.addAll(headers, "Project Name", "TestCase Name", "Environment", "Report Name", "Last Run Status",
                "Failing Since", "Broken Index", "Downtime", "Average Fix Time", "Last Pass");
        result.put("headers", headers);
        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }
        Map<String, Object> resultMap = RestApiUtils.getAllTestExesForTcRunId(payload, pageNo, sort,
                sortedColumn);
        long count = (long) resultMap.get("count");
        List<BasicDBObject> results = (List<BasicDBObject>) resultMap.get("results");
        if (count == 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }
        Map<String, List<TestExeCommonDto>> listMap = new HashMap<>();
        for (BasicDBObject testExeDto : results) {
            List<Document> ob = (List<Document>) testExeDto.get("result");
            Document suiteExeDto = ob.get(0);
            TestExeCommonDto testExeDtoDiagnose = ReportUtils.getTestExeCommonByBasicObjectAndDocument(testExeDto,
                    suiteExeDto);
            String key = testExeDtoDiagnose.getName() + ":" + testExeDtoDiagnose.getReport_name() + ":"
                    + testExeDtoDiagnose.getEnv() + ":" + testExeDtoDiagnose.getProject_name();
            List<TestExeCommonDto> list = listMap.getOrDefault(key, null);
            if (list == null) {
                List<TestExeCommonDto> testExeDtoDiagnoseList = new ArrayList<>();
                testExeDtoDiagnoseList.add(testExeDtoDiagnose);
                listMap.put(key, testExeDtoDiagnoseList);
            } else {
                list.add(testExeDtoDiagnose);
                listMap.put(key, list);
            }
        }

        for (Map.Entry<String, List<TestExeCommonDto>> entry : listMap.entrySet()) {
            List<TestExeCommonDto> testExeCommonDtoDiagnoseList = entry.getValue();
            List<TestExeCommonDto> sortedList = ReportUtils.getSortedListForTestExeCommon(testExeCommonDtoDiagnoseList);
            double brokenIndex = ReportUtils.brokenIndexForTestExe(testExeCommonDtoDiagnoseList);
            String failingSince = ReportUtils.getFailingSinceForTestExeCommon(sortedList, brokenIndex);
            String lastRunStatus = ReportUtils.lastRunStatusForTestExeCommon(sortedList);
            Long lastPass = ReportUtils.getLastPassForTestExeCommon(sortedList);
            String downTime = ReportUtils.getDownTimeForTestExeCommon(sortedList);
            String averageFixTime;
            if (brokenIndex == 1) {
                averageFixTime = NEVER_FIXED;
            } else if (brokenIndex == 0) {
                averageFixTime = "Never Broken";
            } else {
                averageFixTime = ReportUtils.averageFixTimeForTestExeCommon(testExeCommonDtoDiagnoseList);
            }
            Map<String, Object> temp = new HashMap<>();
            temp.put("TestCase Name",
                    ReportUtils.createCustomObject(testExeCommonDtoDiagnoseList.get(0).getName(), "text",
                            testExeCommonDtoDiagnoseList.get(0).getName(), "left"));
            temp.put("Report Name",
                    ReportUtils.createCustomObject(
                            StringUtils.capitalize(testExeCommonDtoDiagnoseList.get(0).getReport_name()), "text",
                            testExeCommonDtoDiagnoseList.get(0).getReport_name(), "left"));
            temp.put("Project Name",
                    ReportUtils.createCustomObject(
                            StringUtils.capitalize(testExeCommonDtoDiagnoseList.get(0).getProject_name()), "text",
                            testExeCommonDtoDiagnoseList.get(0).getProject_name(), "left"));
            temp.put("Environment",
                    ReportUtils.createCustomObject(StringUtils.capitalize(testExeCommonDtoDiagnoseList.get(0).getEnv()),
                            "text",
                            testExeCommonDtoDiagnoseList.get(0).getEnv(), "left"));
            temp.put("Broken Index", ReportUtils.createCustomObject(brokenIndex, "text", brokenIndex, "center"));
            temp.put("Average Fix Time",
                    ReportUtils.createCustomObject(averageFixTime, "text", averageFixTime, "center"));
            temp.put("Last Run Status",
                    ReportUtils.createCustomObject(lastRunStatus, "status", lastRunStatus, "center"));
            temp.put("P ID",
                    ReportUtils.createCustomObject(testExeCommonDtoDiagnoseList.get(0).getP_id(), "text",
                            testExeCommonDtoDiagnoseList.get(0).getP_id(), "left"));
            if (lastPass > 0) {
                Map<String, Object> timereport = new HashMap<>();
                timereport.put("subType", "datetime");
                temp.put("Last Pass",
                        ReportUtils.createCustomObject(lastPass, "date", lastPass, "center", timereport));
            } else {
                temp.put("Last Pass", ReportUtils.createCustomObject("-", "text", "-", "center"));
            }
            temp.put("Failing Since", ReportUtils.createCustomObject(failingSince, "text", failingSince, "center"));
            temp.put("Downtime", ReportUtils.createCustomObject(downTime, "text", downTime, "center"));
            data.add(temp);
        }

        result.put("data", data);
        if (errors != null) {
            result.put("errors", errors);
        }
        result.put("totalElements", listMap.size());

        return new Response(result, listMap.size() + " Records found", Success);
    }

    public Response getRuleActionReportV3(String s_run_id, String tc_run_id, Integer pageNo, Integer sort,
            String sortedColumn) {
        if (tc_run_id == null) {

            if ((sort != null && sortedColumn == null) || (sort == null && sortedColumn != null)) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(BOTH_PARAMETERS_REQUIRED, null, Failure, HttpStatus.OK);
            }

            if (sort != null && !List.of(-1, 0, 1).contains(sort)) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(INVALID_SORT_VALUE, null, Failure, HttpStatus.OK);
            }

            if (pageNo != null && pageNo <= 0) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
            }

            SuiteExeDto getSuite = RestApiUtils.getSuiteExe(s_run_id);

            if (getSuite == null) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
            }

            List<VarianceClassificationDto> varianceClassificationList = RestApiUtils
                    .getVarianceClassificationList(getSuite.getVarianceIds(), ACTIVE_STATUS);
            Map<Long, VarianceClassificationDto> varianceList = new HashMap<>();
            List<Long> varianceIds = new ArrayList<>();
            for (VarianceClassificationDto varianceClassification : varianceClassificationList) {
                varianceIds.add(varianceClassification.getVarianceId());
                varianceList.put(varianceClassification.getVarianceId(), varianceClassification);
            }

            UserDto user1 = ReportUtils.getUserDtoFromServetRequest();

            ProjectDto project = RestApiUtils.getProjectByPidAndStatus(getSuite.getP_id(), ACTIVE_STATUS);
            if (project == null) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);
            }
            if (!ReportUtils.validateRoleWithViewerAccess(user1, project)) {
                log.error("Error occurred due to records not found");
                throw new CustomDataException(USER_NOT_ACCESS_TO_PROJECT, null, Failure, HttpStatus.NOT_ACCEPTABLE);
            }

            Map<String, Object> result = new HashMap<>();
            if (getSuite.getStatus().equalsIgnoreCase("EXE")) {
                String expectedStatus = "PASS";
                int currentPriority = Integer.MAX_VALUE;
                result.put("status", getSuite.getStatus());

                Map<String, Object> testcaseDetails = new HashMap<>();
                Map<String, List<Map<String, Object>>> statusFilterMap = new HashMap<>();

                List<Map<String, Object>> testcaseDetailsData = new ArrayList<>();
                Set<String> testcaseDetailsHeaders = new LinkedHashSet<>();

                Map<String, Object> statusSubType = new HashMap<>();
                statusSubType.put("subType", "falseVariance");
                result.put("Execution Info", ReportUtils.createExecutionInfoHeaders(getSuite));
                HashMap<String, Object> timeReport = new HashMap<>();
                timeReport.put("Start Time", getSuite.getS_start_time());
                timeReport.put("Duration", null);
                result.put("Time Details", timeReport);
                List<Map<String, Long>> testcaseLegend = new ArrayList<>();
                Map<String, Object> data = new HashMap<>();
                Map<String, Long> initialTestcaseInfo = new HashMap<>();
                initialTestcaseInfo.put("TOTAL", getSuite.getExpected_testcases());
                data.put("value", ReportUtils.createDoughnutChart(initialTestcaseInfo));
                testcaseLegend.add(initialTestcaseInfo);
                data.put("legend", testcaseLegend);

                List<TestExeDto> tempTest = RestApiUtils.fetchTestExes(s_run_id, sort, sortedColumn);
                if (!tempTest.isEmpty()) {
                    reportUtils.populateResultWithTestExes(
                            tempTest,
                            varianceList,
                            varianceIds,
                            testcaseDetailsHeaders,
                            testcaseDetails,
                            statusSubType,
                            statusFilterMap,
                            testcaseDetailsData,
                            getSuite,
                            currentPriority,
                            expectedStatus,
                            result,
                            project);
                } else {
                    Map<String, Object> exe_data = new HashMap<>();
                    Map<String, Object> testcase_progress = new HashMap<>();
                    Map<String, Object> expected_status = new HashMap<>();
                    expected_status.put("expected", expectedStatus);
                    expected_status.put("current", getSuite.getStatus());
                    testcase_progress.put("expected",
                            getSuite.getExpected_testcases() != null ? getSuite.getExpected_testcases() : 0);
                    testcase_progress.put("executed",
                            getSuite.getTestcase_details() != null ? getSuite.getTestcase_details().size() : 0);

                    // Initial Doughnut chart data
                    exe_data.put("testcase_info", data);

                    SuiteRun suiteRunData = RestApiUtils.getSuiteRun(getSuite.getS_run_id());
                    List<List<DependencyTree>> ans = new ArrayList<>();
                    assert suiteRunData != null;
                    for (SuiteRunValues suiteRunValues : suiteRunData.getValues()) {
                        if (suiteRunValues.getExpected_testcases() != null) {
                            ans.addAll(suiteRunValues.getExpected_testcases());
                        }
                    }

                    exe_data.put("testcase_progress", testcase_progress);
                    exe_data.put("expected_status", expected_status);
                    exe_data.put("expected_completion",
                            Math.round(ReportUtils.getTimeRemainingNew(getSuite, ans)));
                    result.put("Infra Headers",
                            ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "infraDetails"));
                    result.put("User Details",
                            ReportUtils.createInfraAndUserHeaders(tempTest, getSuite, "userDetails"));
                    result.put("Execution details", ReportUtils.createExecutionDetailsHeaders(tempTest));
                    testcase_progress.put("executed", testcaseDetailsData.size());
                    result.put("exe_data", exe_data);
                    result.put("TestCase_Details", null);
                }

                return new Response(result, EXE_REPORT_SUCCESSFULLY_FETCHED, Success);
            } else {
                return reportUtils.populateResultWithoutTestExes(
                        getSuite,
                        result,
                        pageNo,
                        s_run_id,
                        sort,
                        sortedColumn,
                        varianceList,
                        varianceIds,
                        project,
                        user1.getUsername());
            }

        } else {
            return reportUtils.getResultWithTcRunId(tc_run_id);
        }
    }

    public Response updateBuildDetails(String s_run_id, String buildId, String sprint_name) {

        UserDto user = ReportUtils.getUserDtoFromServetRequest();
        ProjectDto project;
        SuiteExeDto suiteExeDto = RestApiUtils.getSuiteExe(s_run_id);

        if (suiteExeDto != null && user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString())) {
            project = RestApiUtils.getProjectByPidAndStatus(suiteExeDto.getP_id(), ACTIVE_STATUS);
        } else {
            project = RestApiUtils.getProjectByRealCompanyNameAndProjectAndStatus(
                    user.getRealCompany(), suiteExeDto.getProject_name(), ACTIVE_STATUS);
        }
        if (project == null && !user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString())) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        }

        ProjectRoleDto projectRole = RestApiUtils.getProjectRoleEntity(project.getPid(), user.getUsername(),
                ACTIVE_STATUS);

        if (projectRole == null &&
                !((user.getRole().equalsIgnoreCase(ADMIN.toString())
                        && project.getRealcompanyname().equalsIgnoreCase(user.getRealCompany()))
                        || user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString()))) {

            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ACCESS_TO_INSERT_DATA, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        } else if (projectRole != null && !projectRole.getRole().equalsIgnoreCase("ADMIN")) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ADMIN_ACCESS_TO_PROJECT, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        } else {

            if (buildId != null || sprint_name != null) {

                if (buildId != null && !buildId.trim().isEmpty()) {
                    suiteExeDto.setBuild_id(buildId);

                }
                if (sprint_name != null && !sprint_name.trim().isEmpty()) {
                    suiteExeDto.setSprint_name(sprint_name);
                }
            } else {
                throw new CustomDataException("build id or sprint name is missing, one of these is mandatory ", null,
                        Failure, HttpStatus.BAD_REQUEST);
            }
            RestApiUtils.updateSuiteExe(s_run_id, suiteExeDto);
            Map<String, Object> messageMap = Map.of(s_run_id, "Updated");
            simpMessagingTemplate.convertAndSendToUser(String.valueOf(project.getPid()), "/private", messageMap);

            return new Response(null, REPORT_UPDATED_SUCCESSFULLY, Success);
        }
    }

    public Response getBuildDetails(String s_run_id, HttpServletRequest request) {

        UserDto user = ReportUtils.getUserDtoFromServetRequest();
        ProjectDto project;
        SuiteExeDto suiteExeDto = RestApiUtils.getSuiteExe(s_run_id);

        if (suiteExeDto != null && user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString())) {
            project = RestApiUtils.getProjectByPidAndStatus(suiteExeDto.getP_id(), ACTIVE_STATUS);
        } else {
            project = RestApiUtils.getProjectByRealCompanyNameAndProjectAndStatus(
                    user.getRealCompany(), suiteExeDto.getProject_name(), ACTIVE_STATUS);
        }
        if (project == null && !user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString())) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        }

        ProjectRoleDto projectRole = RestApiUtils.getProjectRoleEntity(project.getPid(), user.getUsername(),
                ACTIVE_STATUS);

        if (projectRole == null &&
                !((user.getRole().equalsIgnoreCase(ADMIN.toString())
                        && project.getRealcompanyname().equalsIgnoreCase(user.getRealCompany()))
                        || user.getRole().equalsIgnoreCase(SUPER_ADMIN.toString()))) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ACCESS_TO_INSERT_DATA, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        } else if (projectRole != null && !projectRole.getRole().equalsIgnoreCase("ADMIN")) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ADMIN_ACCESS_TO_PROJECT, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        } else {
            Map<String, Object> data = new HashMap<>();
            data.put("Build ID", suiteExeDto.getBuild_id());
            data.put("Sprint Name", suiteExeDto.getSprint_name());

            return new Response(data, DETAILS_FETCHED_SUCCESSFULLY, Success);
        }

    }

    public Response getSuiteTimeline(Map<String, Object> payload, HttpServletRequest request, String category,
            String search, Integer pageNo, Integer sort, String sortedColumn) throws ParseException {

        UserDto user1 = ReportUtils.getUserDtoFromServetRequest();

        SuiteExeDto getSuite = RestApiUtils.getSuiteExe((String) payload.get("s_run_id"));

        if (getSuite == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }

        ProjectDto project = RestApiUtils.getProjectByPidAndStatus(getSuite.getP_id(), ACTIVE_STATUS);
        if (project == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);

        }
        if (!ReportUtils.validateRoleWithViewerAccess(user1, project)) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ACCESS_TO_PROJECT, null, Info, HttpStatus.NOT_ACCEPTABLE,
                    REQUEST_ACCESS);
        }

        Map<String, Object> result = new HashMap<>();
        List<Object> headers = new ArrayList<>();
        Collections.addAll(headers, "Start Time", "Status", "Action", "Testcases", "Run Type", "Run Mode", "Token User",
                "Base User");
        result.put("headers", headers);
        List<Map<String, Object>> data = new ArrayList<>();
        long starttime = new SimpleDateFormat("MM/dd/yyyy").parse(payload.get("start_time").toString()).getTime();
        long endtime = new SimpleDateFormat("MM/dd/yyyy").parse((String) payload.get("end_time")).getTime()
                + (1000 * 60 * 60 * 24);

        if (pageNo != null && pageNo <= 0) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PAGE_NO_CANNOT_BE_NEGATIVE_OR_ZERO, null, Failure, HttpStatus.OK);
        }

        List<SuiteExeDto> suiteReports = RestApiUtils.getSuiteExesForSuiteTimeline(getSuite.getP_id(), category,
                getSuite.getEnv(), getSuite.getReport_name(), starttime, endtime, pageNo, sort, sortedColumn);
        if (suiteReports.isEmpty()) {
            result.put("data", data);
            return new Response(result, NO_RECORDS_FOUND, Success);
        }
        List<String> sRunIds = RestApiUtils.getS_Run_IdsForSuiteTimeline(getSuite.getP_id(), category,
                getSuite.getEnv(), getSuite.getReport_name(), starttime, endtime, pageNo, sort, sortedColumn);

        List<TestExeDto> testcaseDetails = RestApiUtils.getTestExeListForS_run_ids(sRunIds);
        SuiteDto suiteData = RestApiUtils.getSuiteByReportNameAndStatus(getSuite.getReport_name(), ACTIVE_STATUS);
        if (suiteData != null) {
            result.put("s_id", suiteData.getS_id());
        }
        for (SuiteExeDto suiteExeDto : suiteReports) {
            HashMap<String, Object> temp = new HashMap<>();
            Map<String, Object> timeMap = new HashMap<>();
            timeMap.put("subType", "datetime");
            temp.put("Start Time", ReportUtils.createCustomObject(suiteExeDto.getS_start_time(), "date",
                    suiteExeDto.getS_start_time(), "left", timeMap));
            temp.put("Status",
                    ReportUtils.createCustomObject(suiteExeDto.getStatus(), "status", suiteExeDto.getStatus(), "left"));
            HashMap<String, Object> actionMap = new HashMap<>();
            actionMap.put("subType", "execution_report");
            temp.put("Action", ReportUtils.createCustomObject(suiteExeDto.getS_run_id(), "action",
                    suiteExeDto.getS_run_id(), "center", actionMap));

            Set<String> baseUserSet = new HashSet<>();
            Set<String> tokenUserSet = new HashSet<>();
            Set<String> runTypeSet = new HashSet<>();
            Set<String> runModeSet = new HashSet<>();
            Map<String, Object> statusMap = new HashMap<>();

            for (StatusColor statusColor : StatusColor.values()) {
                statusMap.put(statusColor.toString(), 0L);
            }

            if (!testcaseDetails.isEmpty()) {
                long totalCount = 0L;
                for (TestExeDto testExeDto : testcaseDetails) {
                    if (!testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                        continue;
                    }
                    switch (testExeDto.getStatus().toUpperCase()) {
                        case "PASS":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(PASS.toString()).toString()) + 1;
                                statusMap.put(PASS.toString(), value);
                                totalCount++;
                            }
                            break;
                        case "FAIL":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(FAIL.toString()).toString()) + 1;
                                statusMap.put(FAIL.toString(), value);
                                totalCount++;
                            }
                            break;
                        case "EXE":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(EXE.toString()).toString()) + 1;
                                statusMap.put(EXE.toString(), value);
                                totalCount++;
                            }
                            break;
                        case "ERR":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(ERR.toString()).toString()) + 1;
                                statusMap.put(ERR.toString(), value);
                                totalCount++;
                            }
                            break;
                        case "INFO":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(StatusColor.INFO.toString()).toString()) + 1;
                                statusMap.put(StatusColor.INFO.toString(), value);
                                totalCount++;
                            }
                            break;
                        case "WARN":
                            if (testExeDto.getS_run_id().equals(suiteExeDto.getS_run_id())) {
                                long value = Long.parseLong(statusMap.get(WARN.toString()).toString()) + 1;
                                statusMap.put(WARN.toString(), value);
                                totalCount++;
                            }
                            break;
                    }

                    if (testExeDto.getBase_user() != null)
                        baseUserSet.add(testExeDto.getBase_user());
                    if (testExeDto.getToken_user() != null)
                        tokenUserSet.addAll(testExeDto.getToken_user());
                    runTypeSet.add(testExeDto.getRun_type());
                    runModeSet.add(testExeDto.getRun_mode());

                }
                if ((search != null && !(search.equals("") || search.equalsIgnoreCase("null"))
                        && !verifySearch(search, tokenUserSet))) {
                    continue;
                }
                HashMap<String, Object> statusSubType = new HashMap<>();
                statusSubType.put("subType", "timeline_tc");
                statusMap.put("TOTAL", totalCount);
                if (suiteExeDto.getStatus().equalsIgnoreCase("EXE")
                        && totalCount != suiteExeDto.getExpected_testcases()) {
                    statusMap.put("EXE", suiteExeDto.getExpected_testcases() - totalCount);
                }
                if (suiteExeDto.getStatus().equalsIgnoreCase("ERR")
                        && totalCount != suiteExeDto.getExpected_testcases()) {
                    statusMap.put("ERR", Long.parseLong(statusMap.get(ERR.toString()).toString())
                            + Math.abs(suiteExeDto.getExpected_testcases() - totalCount));
                }
                temp.put("Testcases",
                        ReportUtils.createCustomObject(statusMap, "crud", statusMap, "left", statusSubType));

            }
            temp.put("Token User", ReportUtils.createCustomObject(tokenUserSet, "text", tokenUserSet, "left"));
            temp.put("Base User", ReportUtils.createCustomObject(baseUserSet, "text", baseUserSet, "left"));
            temp.put("Run Type", ReportUtils.createCustomObject(runTypeSet, "text", runTypeSet, "left"));
            temp.put("Run Mode", ReportUtils.createCustomObject(runModeSet, "text", runModeSet, "left"));
            data.add(temp);
        }
        Collections.reverse(data);
        result.put("data", data);

        return new Response(result, data.size() + " record(s) fetched successfully", Success);
    }

    private boolean verifySearch(String search, Set<String> tokenUserSet) {
        for (String name : tokenUserSet) {
            if (name.contains(search)) {
                return true;
            }
        }
        return false;
    }

    public Response getTickets(String s_run_id) {

        UserDto user = ReportUtils.getUserDtoFromServetRequest();

        SuiteExeDto getSuite = RestApiUtils.getSuiteExe(s_run_id);
        if (getSuite == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(SUITE_DETAILS_NOT_FOUND, null, Failure, HttpStatus.NOT_FOUND);
        }

        ProjectDto project = RestApiUtils.getProjectByPidAndStatus(getSuite.getP_id(), ACTIVE_STATUS);
        if (project == null) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(PROJECT_NOT_EXISTS, null, Failure, HttpStatus.NOT_ACCEPTABLE);
        }
        if (!ReportUtils.validateRoleWithViewerAccess(user, project)) {
            log.error("Error occurred due to records not found");
            throw new CustomDataException(USER_NOT_ACCESS_TO_PROJECT, null, Info, HttpStatus.NOT_ACCEPTABLE,
                    REQUEST_ACCESS);
        }

        String azureUrl = "https://dev.azure.com/GEM-QualityEngineering/_workitems/edit/";
        String jiraUrl = "https://gemecosystem.atlassian.net/browse/";

        HashMap<String, Object> type = new HashMap<>();
        List<HashMap<String, Object>> jiraList = new ArrayList<>();
        List<HashMap<String, Object>> azureList = new ArrayList<>();
        if (getSuite.getMeta_data() != null) {
            List<Map<String, Object>> data = new ArrayList<>(getSuite.getMeta_data());
            if (!data.isEmpty()) {
                for (Map<String, Object> map : data) {
                    HashMap<String, Object> jiraTickets = new HashMap<>();
                    HashMap<String, Object> azureTickets = new HashMap<>();
                    if (map.containsKey("Jira_id")) {
                        jiraTickets.put("ID", map.get("Jira_id"));
                        jiraTickets.put("Type", "Suite (" + getSuite.getReport_name() + ")");
                        jiraTickets.put("Link", jiraUrl + map.get("Jira_id"));
                        jiraList.add(jiraTickets);
                    }
                    if (map.containsKey("Azure_id")) {
                        azureTickets.put("ID", map.get("Azure_id"));
                        azureTickets.put("Type", "Suite (" + getSuite.getReport_name() + ")");
                        azureTickets.put("Link", azureUrl + map.get("Azure_id"));
                        azureList.add(azureTickets);
                    }
                }
            }
        }
        List<TestExeDto> testList = RestApiUtils.getTestExeList(s_run_id);
        if (!testList.isEmpty()) {

            for (TestExeDto testExeDto : testList) {
                HashMap<String, Object> jiraTickets = new HashMap<>();
                HashMap<String, Object> azureTickets = new HashMap<>();
                for (Map<String, Object> map : testExeDto.getMeta_data()) {
                    if (map.containsKey("Jira_id")) {
                        jiraTickets.put("ID", map.get("Jira_id"));
                        jiraTickets.put("Type", "Testcase (" + testExeDto.getName() + ")");
                        jiraTickets.put("Link", jiraUrl + map.get("Jira_id"));
                        jiraList.add(jiraTickets);
                    }
                    if (map.containsKey("Azure_id")) {
                        azureTickets.put("ID", map.get("Azure_id"));
                        azureTickets.put("Type", "Testcase (" + testExeDto.getName() + ")");
                        azureTickets.put("Link", azureUrl + map.get("Azure_id"));
                        azureList.add(azureTickets);
                    }
                }
            }
        }
        if (jiraList.isEmpty() && azureList.isEmpty()) {
            return new Response(null, NO_TICKETS_FOUND, Success);
        }
        type.put("Jira", jiraList);
        type.put("Azure", azureList);

        return new Response(type, TICKETS_FETCHED_SUCCESSFULLY, Success);
    }

}