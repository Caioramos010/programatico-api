package com.programatico.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.programatico.api.domain.Usuario;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.domain.enums.TipoUsuario;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TrackRepository;
import com.programatico.api.repository.UsuarioRepository;
import com.programatico.api.security.JwtUtil;
import com.programatico.api.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminCrudIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TrackRepository trackRepository;
    @Autowired private ModuloRepository moduloRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String token;

    @BeforeEach
    void setUp() {
        exerciseRepository.deleteAll();
        moduloRepository.deleteAll();
        trackRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .username("admin-crud")
                .email("admin-crud@email.com")
                .senha(passwordEncoder.encode("Senha@123"))
                .idade(30)
                .ativo(true)
                .role(TipoUsuario.ADMIN)
                .build());

        token = jwtUtil.gerarToken(admin.getUsername(), admin.getId());
    }

    @Test
    void deveCriarTrilhaModuloEExercicioFimAFim() throws Exception {
        // 1) cria trilha
        String trilhaPayload = """
                {"title":"Lógica de Programação","description":"Trilha introdutória","icon":"logic"}
                """;
        MvcResult trilhaResult = mockMvc.perform(post("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(trilhaPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Lógica de Programação"))
                .andReturn();
        long trilhaId = readId(trilhaResult);

        // 2) cria módulo na trilha
        String moduloPayload = """
                {"title":"Variáveis","moduleType":"ACTIVITY","description":"Conceitos básicos"}
                """;
        MvcResult moduloResult = mockMvc.perform(post("/api/admin/trilhas/" + trilhaId + "/modulos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moduloPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.trackId").value(trilhaId))
                .andExpect(jsonPath("$.moduleType").value("ACTIVITY"))
                .andReturn();
        long moduloId = readId(moduloResult);

        // 3) cria exercício no módulo
        String exercicioPayload = """
                {"statement":"Qual o tipo de uma variável?","exerciseType":"MULTIPLE_CHOICE",
                 "exerciseData":"{\\"options\\":[{\\"description\\":\\"int\\",\\"correct\\":true}]}","xpReward":10}
                """;
        mockMvc.perform(post("/api/admin/modulos/" + moduloId + "/exercises")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exercicioPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.moduloId").value(moduloId))
                .andExpect(jsonPath("$.xpReward").value(10));

        // GET de listagem confirma a persistência
        mockMvc.perform(get("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Lógica de Programação"));

        mockMvc.perform(get("/api/admin/trilhas/" + trilhaId + "/modulos")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Variáveis"));

        mockMvc.perform(get("/api/admin/modulos/" + moduloId + "/exercises")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statement").value("Qual o tipo de uma variável?"));

        // verificação direta no repositório
        assertEquals(1, trackRepository.count());
        assertEquals(1, moduloRepository.count());
        assertEquals(1, exerciseRepository.count());
        assertEquals(ModuleType.ACTIVITY, moduloRepository.findById(moduloId).orElseThrow().getModuleType());
        assertEquals(ExerciseType.MULTIPLE_CHOICE,
                exerciseRepository.findAll().get(0).getExerciseType());
    }

    @Test
    void deveRetornar404AoCriarModuloEmTrilhaInexistente() throws Exception {
        String moduloPayload = """
                {"title":"Órfão","moduleType":"ACTIVITY","description":"sem trilha"}
                """;
        mockMvc.perform(post("/api/admin/trilhas/999999/modulos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moduloPayload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());

        assertEquals(0, moduloRepository.count());
    }

    @Test
    void deveRetornar400AoCriarTrilhaComPayloadInvalido() throws Exception {
        // title e description em branco violam @NotBlank
        String invalido = """
                {"title":"","description":""}
                """;
        mockMvc.perform(post("/api/admin/trilhas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("Dados inválidos"));

        assertEquals(0, trackRepository.count());
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(node.has("id"), "resposta deve conter id");
        return node.get("id").asLong();
    }
}
