# Progresso da rodada de correções

Retomar daqui se a sessão cair. Um commit por etapa. Provas em `docs/qa/<etapa>/`.

| Etapa | Estado | Commit | Provas |
|---|---|---|---|
| A — fundo original + 07_lago | feita e aprovada | `a90752d`, `716eb88` | `docs/qa/fundo/` |
| A+ — 08_chao (emenda), menu original, barras cor do céu, mouse fixo no teste | feita | (ver git log) | `docs/qa/fundo/chao/`, `docs/qa/fundo/menu/`, `docs/qa/fundo/barras/` |
| B — escadinha no lugar da barra | feita | (ver git log) | `docs/qa/escada/` |
| C — Quadrado, Losango, Hexágono hostis | EM ANDAMENTO (não commitada) | — | `docs/qa/fase2/inimigos_4_folha_fundo_*.png`, `docs/qa/fase2/*_jogo/` |
| D — Pi decalcado da referência | arte pronta em tools/ (não integrada) | — | `docs/qa/pi/sobreposicao.png` |
| E — chefe (pentágono, escudo, estrela) + Octógono | arte em rascunho em tools/ (não integrada) | — | — |
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

## Onde parei (para retomar)
- Etapa C: sprites dos 3 inimigos gerados e integrados (EnemyType atualizado, WeakPointAlignmentCheck PASS nos 4).
  A bateria completa foi INTERROMPIDA pelo sistema por falta de memória (11 checagens já tinham passado; faltavam as 4 capturas).
- PENDENTE na C: o Losango virou losango de verdade (meia-largura 26, meia-altura 33) porque o de antes era um
  quadrado girado 45° e o teste de silhuetas únicas (tools/audit_shapes.py) reprovaria. Já feito: grade
  tools/sprite_grids/diamond.txt e espinhos (tools/orb_hostiles_more.py). FALTA: rodar o gerador, atualizar
  EnemyType/GeoEnemy (alvos em elipse) e o WeakPointAlignmentCheck para o losango, rodar a bateria, commitar a C.
- D: tools/orb_pi.py + tools/sprite_grids/pi.txt + tools/audit_pi.py (IoU 0,975). FALTA integrar (Assets/GameScreen/diálogo).
- E: tools/orb_octo.py e tools/orb_boss.py (+ grades boss_*.txt, octagon.txt) em rascunho. FALTA integração Java.
- F e G: não começadas.
