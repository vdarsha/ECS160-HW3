package com.ecs160.controller;

import com.ecs160.BlueSkySchema.Post;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModerationController {

    @PostMapping("/moderate")
    public Post moderate(@RequestBody Post request) {
        // Moderation logic
        
    }

    static class MyRequest {
        private String postContent;

        public String getPostContent() {
            return postContent;
        }

        public void setPostContent(String postContent) {
            this.postContent = postContent;
        }
    }
}