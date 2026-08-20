package com.memo.backend.status;

import com.memo.backend.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/deployment-info")
public class DeploymentInfoController {

    @GetMapping
    public ApiResponse<Map<String, String>> getDeploymentInfo() {
        return ApiResponse.of(Map.of(
                "message", "Memo Backend automatic deployment works",
                "version", "1.0"
        ));
    }
}