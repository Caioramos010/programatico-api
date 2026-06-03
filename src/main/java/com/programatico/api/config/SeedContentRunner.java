package com.programatico.api.config;

import com.programatico.api.domain.ContentBlock;
import com.programatico.api.domain.Exercise;
import com.programatico.api.domain.Mission;
import com.programatico.api.domain.Modulo;
import com.programatico.api.domain.TeoriaPagina;
import com.programatico.api.domain.Track;
import com.programatico.api.domain.enums.ExerciseType;
import com.programatico.api.domain.enums.LayoutType;
import com.programatico.api.domain.enums.ModuleType;
import com.programatico.api.repository.ContentBlockRepository;
import com.programatico.api.repository.ExerciseRepository;
import com.programatico.api.repository.MissionRepository;
import com.programatico.api.repository.ModuloRepository;
import com.programatico.api.repository.TeoriaPaginaRepository;
import com.programatico.api.repository.TrackRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Popula o banco com conteúdo de exemplo (trilha + módulos teóricos e de atividade
 * + exercícios + missões) na primeira subida.
 *
 * Idempotente: se já existe alguma trilha, pula. Atrás da flag SEED_CONTENT_ENABLED.
 */
@Component
@Order(300)
@RequiredArgsConstructor
public class SeedContentRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedContentRunner.class);

    private final TrackRepository trackRepository;
    private final ModuloRepository moduloRepository;
    private final TeoriaPaginaRepository teoriaPaginaRepository;
    private final ContentBlockRepository contentBlockRepository;
    private final ExerciseRepository exerciseRepository;
    private final MissionRepository missionRepository;

    @Value("${seed.content.enabled:false}")
    private boolean enabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Seed de conteúdo desativado. Setar SEED_CONTENT_ENABLED=true pra rodar.");
            return;
        }
        if (trackRepository.count() > 0) {
            log.info("Trilha já existe — seed pulado (idempotente).");
            return;
        }
        log.info("Iniciando seed de conteúdo...");
        Track track = seedTrack();
        seedMissions();
        log.info("Seed concluído. Track id={}", track.getId());
    }

    private Track seedTrack() {
        Track track = trackRepository.save(Track.builder()
                .title("Fundamentos da Programação")
                .description("Comece aqui. Variáveis, operadores, condicionais e seus primeiros algoritmos.")
                .displayOrder(1)
                .icon(null)
                .build());

        // 1. STUDY: Variáveis e Tipos
        Modulo m1 = saveModulo(track, "Variáveis e Tipos", ModuleType.STUDY, 1,
                "Entenda como o computador guarda informações com você.");
        TeoriaPagina p1a = savePagina(m1, "O que é uma variável", "A caixa rotulada da memória.", 1);
        saveTextBlock(m1, p1a, 1,
                "Uma variável é um espaço na memória do computador que guarda um valor. Imagine uma caixa com uma etiqueta — a etiqueta é o nome da variável, e o conteúdo da caixa é o valor.\n\nExemplo:\n\nidade = 25\n\nAqui criamos uma variável chamada 'idade' que guarda o número 25.");
        saveTextBlock(m1, p1a, 2,
                "Variáveis podem mudar de valor durante a execução do programa. Por isso o nome 'variável' — o conteúdo varia.\n\nidade = 25\nidade = 26  // agora vale 26");
        TeoriaPagina p1b = savePagina(m1, "Tipos primitivos", "Os blocos de construção dos dados.", 2);
        saveTextBlock(m1, p1b, 1,
                "Cada variável tem um tipo, que indica que espécie de valor ela guarda. Os principais tipos primitivos são:\n\n• inteiro (int) — números sem casas decimais. Ex: 1, 42, -7\n• decimal (float/double) — números com casas decimais. Ex: 3.14, -0.5\n• texto (string) — sequências de caracteres. Ex: \"olá\", \"João\"\n• booleano (boolean) — apenas dois valores: verdadeiro ou falso");
        saveTextBlock(m1, p1b, 2,
                "Saber o tipo é importante porque define o que você pode fazer com a variável. Você pode somar dois inteiros, mas tentar somar um inteiro com uma string causa um erro na maioria das linguagens.");

        // 2. ACTIVITY: Praticando Variáveis
        Modulo m2 = saveModulo(track, "Praticando Variáveis", ModuleType.ACTIVITY, 2,
                "Coloque em prática o que aprendeu sobre variáveis.");
        saveExercise(m2, ExerciseType.MULTIPLE_CHOICE,
                "Qual o tipo de uma variável que guarda apenas 'verdadeiro' ou 'falso'?",
                "{\"options\":[" +
                        "{\"description\":\"int\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"string\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"boolean\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"double\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "variaveis,tipos");
        saveExercise(m2, ExerciseType.MULTIPLE_CHOICE,
                "Qual valor abaixo é do tipo decimal (float)?",
                "{\"options\":[" +
                        "{\"description\":\"42\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"3.14\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"\\\"olá\\\"\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"true\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "variaveis,tipos");
        saveExercise(m2, ExerciseType.PAIRS,
                "Combine cada valor ao seu tipo.",
                "{\"lefts\":[\"7\",\"\\\"casa\\\"\",\"false\",\"2.5\"]," +
                        "\"rights\":[\"string\",\"decimal\",\"inteiro\",\"booleano\"]," +
                        "\"pairs\":[" +
                        "{\"left\":\"7\",\"right\":\"inteiro\"}," +
                        "{\"left\":\"\\\"casa\\\"\",\"right\":\"string\"}," +
                        "{\"left\":\"false\",\"right\":\"booleano\"}," +
                        "{\"left\":\"2.5\",\"right\":\"decimal\"}" +
                        "]}",
                5, "variaveis,tipos");

        // 3. STUDY: Operadores
        Modulo m3 = saveModulo(track, "Operadores", ModuleType.STUDY, 3,
                "Como fazer contas e comparações.");
        TeoriaPagina p3a = savePagina(m3, "Operadores aritméticos", "As contas básicas que o computador faz.", 1);
        saveTextBlock(m3, p3a, 1,
                "Operadores aritméticos fazem operações matemáticas. Os principais:\n\n+ soma\n- subtração\n* multiplicação\n/ divisão\n% resto da divisão (módulo)\n\nExemplo:\n\nresultado = 10 + 5    // resultado vale 15\nresto = 10 % 3         // resto vale 1");
        TeoriaPagina p3b = savePagina(m3, "Operadores de comparação", "Comparando valores.", 2);
        saveTextBlock(m3, p3b, 1,
                "Operadores de comparação retornam true ou false. Os principais:\n\n== igual\n!= diferente\n>  maior que\n<  menor que\n>= maior ou igual\n<= menor ou igual\n\nExemplo:\n\n5 > 3   // true\n5 == 6  // false");

        // 4. ACTIVITY: Praticando Operadores
        Modulo m4 = saveModulo(track, "Praticando Operadores", ModuleType.ACTIVITY, 4,
                "Teste o que aprendeu sobre operações.");
        saveExercise(m4, ExerciseType.MULTIPLE_CHOICE,
                "Qual o resultado de 10 % 3?",
                "{\"options\":[" +
                        "{\"description\":\"0\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"1\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"3\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"10\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "operadores");
        saveExercise(m4, ExerciseType.MULTIPLE_CHOICE,
                "Qual expressão resulta em true?",
                "{\"options\":[" +
                        "{\"description\":\"5 == 6\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"5 < 3\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"10 >= 10\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"7 != 7\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "operadores");
        // DRAG_DROP: o service usa "items" como a ordem correta (e embaralha pra exibir).
        // Itens armazenados na ordem correta de precedência: *, %, +
        saveExercise(m4, ExerciseType.DRAG_DROP,
                "Ordene os operadores do menor pro maior valor de precedência (multiplicação tem prioridade sobre soma).",
                "{\"items\":[\"*\",\"%\",\"+\"]}",
                5, "operadores");

        // 5. STUDY: Condicionais (if/else)
        Modulo m5 = saveModulo(track, "Condicionais (if/else)", ModuleType.STUDY, 5,
                "Fazendo o programa tomar decisões.");
        TeoriaPagina p5a = savePagina(m5, "Estrutura if/else", "Decisões baseadas em condições.", 1);
        saveTextBlock(m5, p5a, 1,
                "A estrutura if (se) permite que o programa execute código diferente dependendo de uma condição:\n\nse (idade >= 18) então\n  imprimir(\"maior de idade\")\nsenão\n  imprimir(\"menor de idade\")\nfim\n\nA condição entre parênteses deve ser uma expressão booleana — algo que dê true ou false.");
        saveTextBlock(m5, p5a, 2,
                "Você pode encadear vários ifs com else if:\n\nse (nota >= 7) então\n  imprimir(\"aprovado\")\nsenão se (nota >= 5) então\n  imprimir(\"recuperação\")\nsenão\n  imprimir(\"reprovado\")\nfim");

        // 6. ACTIVITY: Praticando Condicionais
        Modulo m6 = saveModulo(track, "Praticando Condicionais", ModuleType.ACTIVITY, 6,
                "Exercícios sobre estruturas de decisão.");
        saveExercise(m6, ExerciseType.MULTIPLE_CHOICE,
                "Dado idade = 16, o que será impresso?\n\nse (idade >= 18) então\n  imprimir(\"adulto\")\nsenão\n  imprimir(\"menor\")\nfim",
                "{\"options\":[" +
                        "{\"description\":\"adulto\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"menor\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"nada\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"erro\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "condicionais");
        saveExercise(m6, ExerciseType.MULTIPLE_CHOICE,
                "Qual operador é usado pra 'igualdade' em comparações?",
                "{\"options\":[" +
                        "{\"description\":\"=\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\"==\",\"image\":\"\",\"correct\":true}," +
                        "{\"description\":\"!=\",\"image\":\"\",\"correct\":false}," +
                        "{\"description\":\">=\",\"image\":\"\",\"correct\":false}" +
                        "]}",
                5, "condicionais");

        log.info("Track '{}' criada com 6 módulos.", track.getTitle());
        return track;
    }

    private void seedMissions() {
        missionRepository.save(Mission.builder()
                .title("Complete 1 módulo")
                .objectiveType("COMPLETE_MODULES")
                .xpReward(10)
                .quantidade(1)
                .build());
        missionRepository.save(Mission.builder()
                .title("Acerte 3 exercícios")
                .objectiveType("CORRECT_ANSWERS")
                .xpReward(15)
                .quantidade(3)
                .build());
        missionRepository.save(Mission.builder()
                .title("Estude 1 página teórica")
                .objectiveType("READ_PAGES")
                .xpReward(10)
                .quantidade(1)
                .build());
        log.info("3 missões diárias criadas.");
    }

    private Modulo saveModulo(Track track, String title, ModuleType type, int order, String description) {
        return moduloRepository.save(Modulo.builder()
                .track(track)
                .title(title)
                .moduleType(type)
                .displayOrder(order)
                .description(description)
                .build());
    }

    private TeoriaPagina savePagina(Modulo modulo, String title, String description, int order) {
        return teoriaPaginaRepository.save(TeoriaPagina.builder()
                .modulo(modulo)
                .title(title)
                .description(description)
                .displayOrder(order)
                .build());
    }

    private void saveTextBlock(Modulo modulo, TeoriaPagina pagina, int order, String text) {
        contentBlockRepository.save(ContentBlock.builder()
                .modulo(modulo)
                .pagina(pagina)
                .layoutType(LayoutType.TEXT)
                .textContent(text)
                .imageUrl(null)
                .displayOrder(order)
                .build());
    }

    private void saveExercise(Modulo modulo, ExerciseType type, String statement,
                              String exerciseData, int xpReward, String tags) {
        // Exercise não tem displayOrder — a ordem segue id asc (findByModuloOrderByIdAsc),
        // então a sequência de saves aqui determina a ordem de apresentação.
        exerciseRepository.save(Exercise.builder()
                .modulo(modulo)
                .statement(statement)
                .exerciseType(type)
                .exerciseData(exerciseData)
                .xpReward(xpReward)
                .tags(tags)
                .build());
    }
}
