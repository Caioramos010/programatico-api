package com.programatico.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import org.junit.jupiter.api.Test;

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
