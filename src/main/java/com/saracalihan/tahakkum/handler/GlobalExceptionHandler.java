package com.saracalihan.tahakkum.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.saracalihan.tahakkum.exception.ResponseException;
import com.saracalihan.tahakkum.exception.ResponseException.ErrorObject;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleInvalidRequestException(MissingServletRequestParameterException e) {
        HashMap<String, String> res = new HashMap<String, String>();
        res.put("errors", e.getMessage());
        res.put("statusCode", "400");
        System.out.println(res);
        return new ResponseEntity<>(res, HttpStatus.BAD_REQUEST);
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        HashMap<String, Object> errorMsg = new HashMap<>();
        ArrayList<ErrorObject> errors = new ArrayList<>();

        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName;
            try {
                fieldName = ((FieldError) error).getField();

            } catch (ClassCastException ex) {
                fieldName = error.getObjectName();
            }
            String message = error.getDefaultMessage();
            errors.add(new ErrorObject(String.format("%s, %s", fieldName, message), Optional.of(fieldName)));
        });
        errorMsg.put("errors", errors);
        errorMsg.put("statusCode", "400");
        return new ResponseEntity<>(errorMsg,
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseException.class)
    public ResponseEntity<Object> handleResponseException(ResponseException e) {
        HashMap<String, Object> res = new HashMap<>();

        res.put("errors", e.errors);
        res.put("statusCode", Integer.toString( e.statusCode.value()));
        System.out.println(res);
        return new ResponseEntity<>(res, e.statusCode);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleResponseBodyException(HttpMessageNotReadableException e) {
        HashMap<String, Object> res = new HashMap<>();

        res.put("errors", e.getMessage());
        res.put("statusCode", Integer.toString( 400));
        System.out.println(res);
        return new ResponseEntity<>(res, HttpStatusCode.valueOf(400));
    }
}
