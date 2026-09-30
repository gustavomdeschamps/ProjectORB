# Progresso da rodada de correções

Retomar daqui se a sessão cair. Um commit por etapa. Provas em `docs/qa/<etapa>/`.

| Etapa | Estado | Commit | Provas |
|---|---|---|---|
| A — fundo original + 07_lago | feita e aprovada | `a90752d`, `716eb88` | `docs/qa/fundo/` |
| A+ — 08_chao (emenda), menu original, barras cor do céu, mouse fixo no teste | feita | (ver git log) | `docs/qa/fundo/chao/`, `docs/qa/fundo/menu/`, `docs/qa/fundo/barras/` |
| B — escadinha no lugar da barra | feita | (ver git log) | `docs/qa/escada/` |
| C — Quadrado, Losango, Hexágono hostis | a fazer | | `docs/qa/fase2/` |
| D — Pi decalcado da referência | a fazer | | `docs/qa/pi/` |
| E — chefe (pentágono, escudo, estrela) + Octógono | a fazer | | `docs/qa/chefe/`, `docs/qa/octogono/` |
| F — Octógono no fim da fase + portal animado | a fazer | | `docs/qa/portal/`, `docs/qa/quiz/` |
| G — verificação final | a fazer | | `docs/qa/final/` |

## Decisões que tomei sozinho
- A+: o fundo original do menu (menu.png) também é o fundo de Opções, Vitória, Derrota e "Como jogar" (mesmo arquivo); voltou em todas. Só o fundo mudou nessas telas.
- A+: 08_chao — a pedra que o TCC deixou cortada na coluna 0 ganhou borda esquerda (o degrau da borda direita espelhado); sem isso, a emenda sem pontilhado deixava uma borda reta ou riscos. Tudo dentro das 24 colunas da emenda.
- A+: tools/orb_background.py (só gerava o menu novo) foi para art-source/fundo_reconstruido_descartado/tools/.
- B: a escada NÃO cobre o X exato do portão antigo em todos os portões. A massa fechada fica exatamente no X do portão (endX-88..endX), mas a escada aberta começa uma largura de ORB depois da plataforma anterior (senão o ORB bate a cabeça nela ao pular no 1º degrau) e só passa sob plataformas com espaço para o ORB. Posições: escada 0 x=1764..2028, 1 x=3540..3804, 2 x=5604..5868, 3 x=7392..7656, 4 x=9200..9464.
- B: degrau de 40 px (22% do pulo simples de 180 px) em vez de algo maior: com 48 px o último degrau não passava sob a plataforma seguinte do portão 0.
- B: o ponto de renascimento (X) de cada seção não mudou; se cair em cima da escada aberta, o ORB renasce em pé nela (antes renascia a y=230).
- B: durante o rearranjo (1,25 s) a massa continua sólida; a escada só vira chão quando termina. Projéteis: a massa bloqueia como o portão; a escada aberta se comporta como plataforma.
- B: ganchos de som (AudioManager.stairRumble/stairOpened) sem som ainda (áudio fica para a etapa de som).

## Dúvidas registradas
(preenchido ao longo da rodada)
