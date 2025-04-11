package com.example.gameMatrix.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.gameMatrix.service.MatrixService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MatrixController {
    
    private final MatrixService matrixService;
    
    @Autowired
    public MatrixController(MatrixService matrixService) {
        this.matrixService = matrixService;
    }
    
    @PostMapping("/matrix")
    public ResponseEntity<Map<String, Object>> processMatrix(@RequestBody List<List<Double>> matrix) {
        Map<String, Object> solution = matrixService.solveGame(matrix);
        return ResponseEntity.ok(solution);
    }
}