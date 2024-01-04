package com.jewel.reportmanager.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Email {

    private List<String> to;
    private List<String> cc;
    private List<String> bcc;

}
