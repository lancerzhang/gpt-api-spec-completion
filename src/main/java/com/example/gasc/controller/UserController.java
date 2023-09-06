package com.example.gasc.controller;

import com.example.gasc.entity.User;
import com.example.gasc.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam("searchString") String searchString) {
        return userService.searchUsers(searchString);
    }

}
