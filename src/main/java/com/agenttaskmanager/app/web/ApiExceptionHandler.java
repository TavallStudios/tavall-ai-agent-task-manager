package com.agenttaskmanager.app.web;

import com.agenttaskmanager.app.model.PromptRequestNotFoundException;
import com.agenttaskmanager.app.service.TaskNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(TaskNotFoundException.class)
  public Map<String, String> handleTaskNotFound(TaskNotFoundException exception, HttpServletResponse response) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler(PromptRequestNotFoundException.class)
  public Map<String, String> handlePromptNotFound(
      PromptRequestNotFoundException exception,
      HttpServletResponse response
  ) {
    response.setStatus(HttpStatus.NOT_FOUND.value());
    return Map.of("error", exception.getMessage());
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  public Map<String, String> handleBadRequest(Exception exception, HttpServletResponse response) {
    response.setStatus(HttpStatus.BAD_REQUEST.value());
    return Map.of("error", exception.getMessage());
  }
}

