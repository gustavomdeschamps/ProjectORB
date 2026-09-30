# Progresso — Rodada 3

Retomar daqui se a sessão cair. Um commit por etapa (git criado nesta rodada:
commit base `ad2aedd` "base rodada 3"). Provas em `docs/qa/rodada3/<etapa>/`.
Testes: `bash tools/run_checks.sh` (+ VisualSmokeLauncher). ffmpeg instalado via
winget (Gyan.FFmpeg 9.0.2).

| Etapa | Estado | Provas |
|---|---|---|
| 1 — fundo: parte de baixo da paisagem | feita | `docs/qa/rodada3/etapa1/` |
| 2 — limpeza de restos (barra antiga, escada longe das plataformas) | feita | `docs/qa/rodada3/etapa2/` |
| 3 — "cara de IA": auditoria e troca | a fazer | `docs/qa/rodada3/anti_ia/` |
| 4 — abertura nova + key art + vídeo | a fazer | `docs/qa/rodada3/etapa4/` |
| 5 — Pi decalcado da referência | a fazer | `docs/qa/rodada3/etapa5/` |
| 6 — chefe no estilo dos inimigos | a fazer | `docs/qa/rodada3/etapa6/` |
| 7 — Octógono + quiz no fim da fase | a fazer | `docs/qa/rodada3/etapa7/` |
| 8 — portal do fim | a fazer | `docs/qa/rodada3/etapa8/` |
| 9 — qualidade geral de sprites/animações/texturas | a fazer | `docs/qa/rodada3/qualidade/` |
| 10 — verificação final + vídeo de gameplay | a fazer | `docs/qa/rodada3/final/` |

## Etapa 1 — feito
- `tools/fundo_rodada3.py` (roda depois de `tools/build_orb_assets.py`); biblioteca `tools/pxlib.py`.
- Camadas de cima (céu, nuvens, montanhas) intactas. 06_estruturas: a estrutura
  circular virou ruína de arco de cristal quebrado na mesma caixa (x 309-479,
  y 75-216), mesmas cores; torre e ruína ganharam base (degraus, pé, escombros).
- Lago novo: `07_lago` (água em faixas + ondulações quebradas), `07_margem`
  (margem irregular de pedra com linha molhada), `05_reflexo`/`06_reflexo`
  (reflexo espelhado em 2 tons, linhas quebradas, ondulando por linha em código),
  brilhos da água em código, `09_nevoa` (faixas sem degradê), `08_chao` com borda
  de pedras molhadas. Tudo desenhado por `utils/Backdrop.java`.
- `menu.png` recomposto (tela de início, opções, vitória, derrota, como jogar).
- Provas: `antes/` e `depois/` (4 câmeras + menu, captura determinística
  `BackgroundCompare`), `comparacao/*_heatmap.png` + `relatorio.txt`,
  `agua_animada.gif`, folhas de contato `folha_*`, `smoke/`.

## Etapa 2 — feito
- Barra/portão antigo: o jogo não desenhava mais `world/gate.png`, mas o arquivo
  ainda era carregado (Assets.gate), gerado (build_orb_assets.py) e havia duas
  cópias em assets/ (`sprites/world/gate.png`, `world/gate.png`) e a parede
  `world/wall.png`. Tudo foi para `art-source/descartado_rodada3/portao_antigo/`;
  campo, carregamento, gerador e `gateRect` (sem uso) removidos.
- A "coluna magenta com marcas" das capturas do Quadrado era a MASSA FECHADA da
  escada 2: o VisualSmokeLauncher pulava de seção sem vencer as anteriores. Ele
  agora vence as seções anteriores e abre as escadas delas, como no jogo.
- `OldGateCheck` (novo, no run_checks): troca o SpriteBatch por um que registra
  cada textura desenhada, percorre as 6 seções (escada fechada/abrindo/aberta),
  pausa e códex e FALHA se a arte antiga (por conteúdo SHA-256 ou nome) for
  desenhada ou existir em assets/. Prova de que falha: `etapa2/oldgate_prova_falha.txt`.
- Escadinha: escada aberta centrada na massa; plataformas das pontas das seções
  movidas (alturas e larguras iguais) para ficarem a >= 174 px (3 larguras do
  ORB) da escada fechada e aberta. `StairGateCheck` confere as 3 regras (folga
  horizontal, nada por cima/por baixo, topo a >= 1 altura do ORB) e que o ORB
  atravessa (ida/volta) e não fica preso. Menor folga: 184 px.
- Provas: `etapa2/escadas/escada{0-4}_{fechada,aberta}[_medidas].png`,
  `todas_as_escadas.png`, `escadas.txt`, `oldgate_*.txt`, `smoke/`.

## Decisões que tomei sozinho
- E1: a janela em "O" da torre (um anel de luz) virou uma fresta de cristal em
  losango: a regra "sem círculo/anel" vale para tudo e era o único outro anel
  do fundo. Mudança de ~20x16 px de arte; o resto da torre não mudou.
- E1: a água começa na linha 225 (antes 189) e a margem cobre a borda de cima
  dela em todas as colunas; o reflexo é desenhado no parallax da camada
  refletida (montanhas 0,16; estruturas 0,26) para ficar sempre embaixo do
  objeto refletido.
- E1: as camadas antigas foram guardadas em `art-source/descartado_rodada3/fundo/`.

- E2: mexi nas plataformas (não na escada), como pedido ("o que mexer menos"):
  10 plataformas andaram de 50 a 262 px na horizontal; nenhuma mudou de altura
  ou largura. A da câmara ficou simétrica em torno do centro (2700).
- E2: "largura do ORB" = caixa de colisão (58 px) e "altura do ORB" = 78 px.

## Onde parei (para retomar)
- Etapas 1 e 2 commitadas. Próxima: Etapa 3.
