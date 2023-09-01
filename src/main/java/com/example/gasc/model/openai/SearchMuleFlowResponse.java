package com.example.gasc.model.openai;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchMuleFlowResponse {
    private String respDwContent;
    private String respDwlFile;
    private String respJavaClasses;
    private String reqDwContent;
    private String reqDwlFile;
    private String reqJavaClasses;

}
