package com.programatico.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.UserStats;
import com.programatico.api.domain.Usuario;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Organização adaptativa dos exercícios de uma sessão usando a OpenAI (provider externo,
 * autenticado por token). Dado o conjunto de exercícios candidatos e o contexto do aluno
 * (nível, XP, streak), pede ao modelo a melhor seleção/ordem para o aprendizado.
 *
 * Falha de forma graciosa: sem {@code OPENAI_API_KEY} ou em qualquer erro, devolve lista
 * vazia, sinalizando ao chamador para usar o algoritmo determinístico de fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiOrganizacaoService {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.api-base-url:https://api.openai.com/v1}")
    private String apiBaseUrl;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public boolean isHabilitado() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * @return exercícios organizados pela IA (até {@code quantidade}), ou lista vazia
     *         quando a IA está desligada/indisponível — sinal para o fallback.
     */
    public List<Exercise> organizar(List<Exercise> candidatos, Usuario usuario, UserStats stats, int quantidade) {
        if (!isHabilitado() || candidatos == null || candidatos.isEmpty()) {
            return List.of();
        }
        try {
            String content = chamarOpenAi(montarMensagens(candidatos, usuario, stats, quantidade));
            List<Long> ordem = extrairOrdem(content);
            List<Exercise> organizados = aplicarOrdem(candidatos, ordem, quantidade);
            if (organizados.isEmpty()) {
                log.warn("OpenAI não retornou ids válidos; usando fallback determinístico.");
            }
            return organizados;
        } catch (Exception e) {
            log.warn("Falha ao organizar exercícios via OpenAI ({}); usando fallback.", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> montarMensagens(List<Exercise> candidatos, Usuario usuario,
                                                      UserStats stats, int quantidade) {
        List<Map<String, Object>> itens = new ArrayList<>();
        for (Exercise e : candidatos) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("tipo", e.getExerciseType() != null ? e.getExerciseType().name() : "");
            item.put("xp", e.getXpReward());
            item.put("assuntos", e.getTags() == null ? "" : e.getTags());
            item.put("enunciado", abreviar(e.getStatement(), 140));
            itens.add(item);
        }

        String nivel = usuario != null && usuario.getNivelHabilidade() != null
                ? usuario.getNivelHabilidade().name() : "DESCONHECIDO";
        int xp = stats != null && stats.getTotalXp() != null ? stats.getTotalXp() : 0;
        int streak = stats != null && stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0;

        String itensJson;
        try {
            itensJson = objectMapper.writeValueAsString(itens);
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível serializar os exercícios para a IA", ex);
        }

        String system = "Você é um tutor que organiza sessões de exercícios de lógica e programação "
                + "para maximizar o aprendizado do aluno.";

        String user = ("Aluno: nível %s, XP total %d, streak %d.%n"
                + "Selecione e ORDENE exatamente %d exercícios da lista, devolvendo apenas os ids.%n"
                + "Regras: variar a dificuldade (xp 3, 5 e 7); evitar dois exercícios do mesmo 'tipo' em sequência; "
                + "começar pelos mais fáceis e progredir; priorizar variedade de assuntos.%n"
                + "Responda APENAS um JSON no formato {\"ordem\": [id, id, ...]} com %d ids.%n"
                + "Exercícios disponíveis:%n%s")
                .formatted(nivel, xp, streak, quantidade, quantidade, itensJson);

        Map<String, Object> mensagemSistema = new LinkedHashMap<>();
        mensagemSistema.put("role", "system");
        mensagemSistema.put("content", system);

        Map<String, Object> mensagemUsuario = new LinkedHashMap<>();
        mensagemUsuario.put("role", "user");
        mensagemUsuario.put("content", user);

        return List.of(mensagemSistema, mensagemUsuario);
    }

    private String chamarOpenAi(List<Map<String, Object>> mensagens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0.3);
        body.put("response_format", Map.of("type", "json_object"));
        body.put("messages", mensagens);

        String responseBody = openAiClient().post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new IllegalStateException("Resposta inválida da OpenAI", e);
        }
    }

    // visível ao pacote para teste
    List<Long> extrairOrdem(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            JsonNode ordem = objectMapper.readTree(content).path("ordem");
            if (!ordem.isArray()) {
                return List.of();
            }
            List<Long> ids = new ArrayList<>();
            for (JsonNode n : ordem) {
                if (n.canConvertToLong()) {
                    ids.add(n.asLong());
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Mapeia os ids devolvidos pela IA de volta para exercícios; completa com os restantes se faltar. */
    // visível ao pacote para teste
    List<Exercise> aplicarOrdem(List<Exercise> candidatos, List<Long> ordem, int quantidade) {
        Map<Long, Exercise> porId = new LinkedHashMap<>();
        for (Exercise e : candidatos) {
            porId.put(e.getId(), e);
        }
        List<Exercise> resultado = new ArrayList<>();
        for (Long id : ordem) {
            Exercise e = porId.get(id);
            if (e != null && !resultado.contains(e)) {
                resultado.add(e);
            }
            if (resultado.size() == quantidade) {
                return resultado;
            }
        }
        for (Exercise e : candidatos) {
            if (resultado.size() == quantidade) {
                break;
            }
            if (!resultado.contains(e)) {
                resultado.add(e);
            }
        }
        return resultado;
    }

    private RestClient openAiClient() {
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        // Timeouts evitam que uma OpenAI lenta/indisponível segure a thread da sessão indefinidamente
        // (a chamada acontece no caminho de iniciar sessão); ao estourar, cai no fallback determinístico.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(base)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static String abreviar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        String t = texto.strip();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
