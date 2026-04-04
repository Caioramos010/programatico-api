package com.programatico.api.exception;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void deveMapearResourceNotFoundPara404() {
        ResponseEntity<Map<String, String>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("Usuario nao encontrado"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Usuario nao encontrado", response.getBody().get("mensagem"));
    }

    @Test
    void deveMapearBadRequestPara400() {
        ResponseEntity<Map<String, String>> response =
                handler.handleBadRequest(new BadRequestException("Dados invalidos"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Dados invalidos", response.getBody().get("mensagem"));
    }

    @Test
    void deveMapearDataIntegrityPara400() {
        ResponseEntity<Map<String, String>> response =
                handler.handleDataIntegrity(new DataIntegrityViolationException("constraint"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("mensagem"));
    }

    @Test
    void deveMapearErroValidacaoPara400ComCampos() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "nome", "Nome e obrigatorio"));

        Method method = DummyValidator.class.getDeclaredMethod("validar", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody().get("mensagem"));
        @SuppressWarnings("unchecked")
        Map<String, String> erros = (Map<String, String>) response.getBody().get("erros");
        assertEquals("Nome e obrigatorio", erros.get("nome"));
    }

    @Test
    void deveMapearErroGenericoPara500() {
        ResponseEntity<Map<String, String>> response =
                handler.handleGeneric(new RuntimeException("falha inesperada"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Erro interno. Tente novamente.", response.getBody().get("mensagem"));
    }

    static class DummyValidator {
        public void validar(String nome) {
        }
    }
}
