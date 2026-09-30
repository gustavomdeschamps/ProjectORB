package com.delmartec.projectorb.level;

import com.delmartec.projectorb.utils.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fase demonstrativa de aproximadamente 5 minutos.
 * Geometria propositalmente simples para manter leitura visual e saltos justos.
 */
public class LevelDemo {
    private final List<Platform> platforms = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();

    public LevelDemo() {
        buildSections();
        buildGeometry();
    }

    private void buildSections() {
        sections.add(new Section(0, "CORREDOR", "Movimento - Salto - Dash - Mira", 0, 1800, 180));
        sections.add(new Section(1, "CÂMARA", "Losango - Vértices", 1800, 3600, 1940));
        sections.add(new Section(2, "PONTE", "Triângulos - Vértices", 3600, 5700, 3740));
        sections.add(new Section(3, "SALA", "Quadrado - Lados", 5700, 7500, 5840));
        sections.add(new Section(4, "PASSAGEM", "Hexágono - Ângulos", 7500, 9300, 7640));
        sections.add(new Section(5, "ARENA", "Núcleo Geométrico - Simetria e rotação", 9300, 12000, 9480));
    }

    private void buildGeometry() {
        // Um único piso físico evita pequenas frestas entre segmentos.
        platforms.add(new Platform(0, 0, Constants.WORLD_WIDTH, Constants.FLOOR_Y));

        // Rodada 3 (etapa 2): as plataformas vizinhas de cada escadinha ficam a
        // pelo menos 3 larguras do ORB da escada (fechada ou aberta), nunca por
        // cima nem por baixo dela. Só as plataformas das pontas das seções
        // andaram; alturas e larguras são as mesmas. Ver StairGate.CLEARANCE.

        // 01 — corredor: três saltos confortáveis e um descanso antes da câmara.
        platforms.add(new Platform(580, 230, 288, 64));
        platforms.add(new Platform(930, 340, 288, 64));
        platforms.add(new Platform(1248, 235, 192, 64));

        // 02 — câmara do losango: arena limpa e simétrica.
        platforms.add(new Platform(2162, 250, 288, 64));
        platforms.add(new Platform(2950, 250, 288, 64));

        // 03 — ponte dos triângulos: níveis alternados sem bloquear a linha de tiro.
        platforms.add(new Platform(3880, 235, 288, 64));
        platforms.add(new Platform(4680, 315, 288, 64));
        platforms.add(new Platform(5148, 235, 192, 64));

        // 04 — quadrado: dois pontos de reposicionamento laterais.
        platforms.add(new Platform(6060, 250, 288, 64));
        platforms.add(new Platform(6852, 250, 288, 64));

        // 05 — hexágono: espaço central livre para mudar o ângulo dos disparos.
        platforms.add(new Platform(7780, 240, 288, 64));
        platforms.add(new Platform(8652, 305, 288, 64));

        // 06 — arena do chefe: ampla, limpa e com plataformas apenas nas bordas.
        platforms.add(new Platform(9580, 250, 288, 64));
        platforms.add(new Platform(11510, 250, 288, 64));
    }

    public List<Platform> getPlatforms() { return Collections.unmodifiableList(platforms); }
    public List<Section> getSections() { return Collections.unmodifiableList(sections); }
    public Section getSection(int index) { return sections.get(index); }
}
