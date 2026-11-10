package com.example.demo.application.ai;

public interface AiGateway {
    Result ask(String prompt);

    record Result(String answer, boolean fallback) { }
}
