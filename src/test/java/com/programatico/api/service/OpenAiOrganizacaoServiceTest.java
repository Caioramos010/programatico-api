package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiOrganizacaoServiceTest {

    private final OpenAiOrganizacaoService service = new OpenAiOrganizacaoService(new ObjectMapper());

    @Test
    void semChaveConfiguradaCaiNoFallback() {
        assertFalse(service.isHabilitado());
        // Sem OPENAI_API_KEY → lista vazia, sinalizando o algoritmo determinístico.
        assertTrue(service.organizar(List.of(ex(1L), ex(2L)), usuario(), stats(), 10).isEmpty());
    }

    @Test
    void extrairOrdemParseiaJsonValido() {
        assertEquals(List.of(3L, 1L, 2L), service.extrairOrdem("{\"ordem\":[3,1,2]}"));
    }

    @Test
    void extrairOrdemRetornaVazioParaConteudoInvalido() {
        assertTrue(service.extrairOrdem("isto não é json").isEmpty());
        assertTrue(service.extrairOrdem("{\"outraCoisa\":1}").isEmpty());
        assertTrue(service.extrairOrdem("").isEmpty());
    }

    @Test
    void aplicarOrdemRespeitaOrdemDaIaECompletaORestante() {
        List<Exercise> candidatos = List.of(ex(1L), ex(2L), ex(3L), ex(4L));

        List<Long> ids = service.aplicarOrdem(candidatos, List.of(3L, 1L), 3)
                .stream().map(Exercise::getId).toList();

        assertEquals(List.of(3L, 1L, 2L), ids);
    }

    @Test
    void aplicarOrdemIgnoraIdsInexistentes() {
        List<Exercise> candidatos = List.of(ex(1L), ex(2L));

        List<Long> ids = service.aplicarOrdem(candidatos, List.of(99L, 2L), 2)
                .stream().map(Exercise::getId).toList();

        assertEquals(List.of(2L, 1L), ids);
    }

    @Test
    void organizarComRespostaValidaDaOpenAi() throws Exception {
        String openAiBody = "{\"choices\":[{\"message\":{\"content\":\"{\\\"ordem\\\":[2,1]}\"}}]}";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] bytes = openAiBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            OpenAiOrganizacaoService serviceComChave = new OpenAiOrganizacaoService(new ObjectMapper());
            ReflectionTestUtils.setField(serviceComChave, "apiKey", "sk-test");
            ReflectionTestUtils.setField(serviceComChave, "apiBaseUrl", "http://localhost:" + port + "/v1");
            ReflectionTestUtils.setField(serviceComChave, "model", "gpt-4o-mini");

            assertTrue(serviceComChave.isHabilitado());

            List<Long> ids = serviceComChave.organizar(List.of(ex(1L), ex(2L), ex(3L)), usuario(), stats(), 2)
                    .stream()
                    .map(Exercise::getId)
                    .toList();

            assertEquals(List.of(2L, 1L), ids);
        } finally {
            server.stop(0);
        }
    }

    private Exercise ex(long id) {
        return Exercise.builder().id(id).statement("Q" + id)
                .exerciseType(ExerciseType.MULTIPLE_CHOICE).xpReward(5).build();
    }

    private Usuario usuario() {
        return Usuario.builder().id(1L).username("aluno").build();
    }

    private UserStats stats() {
        return UserStats.builder().totalXp(0).currentStreak(0).build();
    }
}
