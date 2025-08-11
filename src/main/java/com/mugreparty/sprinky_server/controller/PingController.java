package com.mugreparty.sprinky_server.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;


@RestController //Interfaz
public class PingController {
    @GetMapping("/ping")    
    public String ping() {
        return "pong";
    }
    
}
