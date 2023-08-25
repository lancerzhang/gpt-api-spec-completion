package com.example.gasc.model.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenAIApiResponse {
    private String id;
    private String object;
    private double created;
    private String model;
    private List<Choice> choices;
    private Usage usage;
}

