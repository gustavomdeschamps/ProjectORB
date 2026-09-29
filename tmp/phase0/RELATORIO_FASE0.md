# Fase 0 — Ingestão de assets/projetofinal-29/

Encontrada em `assets/projetofinal-29/` (já commitada por você em `f2dd5e1 Atualizado`). O zip
`Downloads/projetofinal-29-20260929T183733Z-1-001.zip` tem os mesmos 10 arquivos (lá o piscando
se chama `" principal piscando.png"`, com espaço no início).

## 1. Inventário (todos RGBA, alfa binário, 32×32, frame único — nenhum spritesheet nem sequência)

| Arquivo | Conteúdo (bbox) | Cores | Categoria |
|---|---|---|---|
| personagem principal/Personagem principal.png | ORB parado (2,3)-(29,30) | 40 | personagem |
| personagem principal/principal piscando.png | ORB de olhos fechados | 38 | personagem |
| personagem principal/principal correndo.png | ORB correndo (braços/pernas abertos) | 339 | personagem |
| personagem principal/principal pulando.png | ORB no ar, pés recolhidos | 283 | personagem |
| personagem principal/principal atirando.png | ORB com braço estendido | 344 | personagem |
| tiros/tiro1.png | bolinha roxa com rastro curto | 169 | efeitos |
| tiros/tiro2.png | projétil alongado | 183 | efeitos |
| tiros/tiro3.png | bola grande com faíscas | 292 | efeitos |
| tiros/tiro4.png | três pulsos em sequência | 315 | efeitos |
| cristal.png | cristal lilás facetado (7,2)-(21,31) | 130 | mundo |

Folhas de contato: `tmp/phase0/contato_personagem.png`, `tmp/phase0/contato_efeitos_mundo.png`.
Sem NPCs, inimigos, fundo, UI, áudio nem fontes.

## 2. Comparação

| Asset novo | Situação |
|---|---|
| Personagem principal.png | **duplicado**: byte a byte igual a ProjetoFinal_TCC/player/Personagem principal.png |
| principal piscando.png | **novo** (hoje o piscar é gerado por script) — mesma paleta do idle (0 cores fora) |
| principal correndo.png | **substitui** ProjetoFinal_TCC/player/correndo.png (outro desenho/cores) e o walk gerado |
| principal pulando.png | **substitui / conflita** com ProjetoFinal_TCC/player/principal pulando.png (mesmo nome, arte diferente) |
| principal atirando.png | **novo** (hoje attack é gerado) |
| tiros/tiro1..4 | **substituem** os tiros gerados (fx/player_shot etc.) — ver pergunta 4 |
| cristal.png | **conflita** com ProjetoFinal_TCC/word/crystal.png (64×80, outro estilo); substitui pela prioridade |

## 3. Necessidades do jogo × cobertura

| Necessidade | projetofinal-29 | ProjetoFinal_TCC | Resto |
|---|---|---|---|
| ORB idle | sim (igual ao TCC) | sim | — |
| ORB walk | 1 pose (correndo) | pose antiga incoerente | gerar ciclo de 6 a partir das poses |
| ORB jump | 1 pose (pulando) | pose antiga incoerente | gerar subida/ápice/descida |
| ORB dash | não | não | gerar |
| ORB attack | 1 pose (atirando) | não | gerar frames em volta da pose |
| ORB hurt / death | não | não | gerar |
| ORB piscar (idle) | sim | não | — |
| Triângulo, losango (square.png), círculo, pentágono | não | sim (1 frame cada) | animações geradas (já existem) |
| Quadrado, hexágono, boss | não | não | gerados (já existem) |
| NPC Pi, NPC Octógono | não | não | **gerar** (C1/E1) |
| Camadas de fundo | não | sim (9) | — |
| HUD / painéis | não | sim (hud, enemy, dialog, life_orb) | redesenho na A3 |
| Efeitos (explosões, dash) | não | não | gerados |
| Mira, marcador de ponto fraco | não | não | gerados |
| Tiros | sim (4) | não | — |
| Plataformas, portal, portão, parede | não | sim | — |
| Cristal | sim | sim (conflito) | — |
| Áudio | não | não | manter os .wav atuais |
| Fonte | não | não | fonte pixel OFL na A3 |

## 4. Conflito de estilo: tamanho do pixel

Tamanho do pixel da arte em pixels de mundo, hoje:

| Grupo | Nativo | Escala | Pixel |
|---|---|---|---|
| ORB, cristal novo, tiros novos, fundo 480×270 | 32 px / 480 px | 4× | **4** |
| Inimigos TCC (128 px, antialiasados) e gerados | 144 px | 2× | **2** |
| Plataforma, portão, portal, parede, painéis TCC | 1× | 1× | **1** (hud_panel é 8) |

Só 4 px/pixel serve para o jogo inteiro (o ORB e o fundo são seus e já estão nessa escala). Mas os
inimigos, plataformas, portal, portão e painéis do TCC não são pixel art "grossa": têm linhas de 1 px e
antialiasing. Reduzi-los para 1/2 ou 1/4 e ampliar 4× **borra** (anéis dos vértices viram borrões,
linhas somem): ver `tmp/phase0/escala_comparacao.png`.
