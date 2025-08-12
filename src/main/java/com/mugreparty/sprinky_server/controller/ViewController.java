package com.mugreparty.sprinky_server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@Controller
@RequestMapping("/rooms")
public class ViewController {
    @GetMapping("/j/{code}")
    public String joinPage(@PathVariable String code, Model model) {
        model.addAttribute("code", code);
        return "join";
    }
    
    @GetMapping("lobby/{code}")
    public String lobby(@PathVariable String code, Model model) {
        model.addAttribute("code", code);
        model.addAttribute("wsEndpoint", "/ws");
        model.addAttribute("roomTopic", "/topic/rooms/" + code);
        return "lobby";
    }
    
}
