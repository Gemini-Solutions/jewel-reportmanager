package com.jewel.reportmanager.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SuiteDependencyDto {
    private SuiteExeDto suiteExeDto;
    private List<List<DependencyTree>> dependency;
}
