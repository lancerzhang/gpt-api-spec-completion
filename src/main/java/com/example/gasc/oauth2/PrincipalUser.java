package com.example.gasc.oauth2;


import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PrincipalUser {

    private final Long id;
    private final String employeeId;
    private final String displayName;
    private final String email;
    private List<Integer> delegators;

    public PrincipalUser(Long id, String employeeId, String displayName, String email) {
        this.id = id;
        this.employeeId = employeeId;
        this.displayName = displayName;
        this.email = email;
    }

    @Override
    public String toString() {
        return "PrincipalUser{" +
                "id=" + id +
                ", employeeId='" + employeeId + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
