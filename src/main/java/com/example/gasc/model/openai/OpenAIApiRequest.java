package com.example.gasc.model.openai;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;

@Getter
@Setter
public class OpenAIApiRequest {
    private ArrayList<Message> messages;
    private double temperature = 0.1;
}
