# Progresso da rodada de correções

Retomar daqui se a sessão cair. Um commit por etapa. Provas em `docs/qa/<etapa>/`.

| Etapa | Estado | Commit | Provas |
|---|---|---|---|
| A — fundo original + 07_lago | feita e aprovada | `a90752d`, `716eb88` | `docs/qa/fundo/` |
| A+ — 08_chao (emenda), menu original, barras cor do céu, mouse fixo no teste | feita | (ver git log) | `docs/qa/fundo/chao/`, `docs/qa/fundo/menu/`, `docs/qa/fundo/barras/` |
| B — escadinha no lugar da barra | a fazer | | `docs/qa/escada/` |
| C — Quadrado, Losango, Hexágono hostis | a fazer | | `docs/qa/fase2/` |
| D — Pi decalcado da referência | a fazer | | `docs/qa/pi/` |
| E — chefe (pentágono, escudo, estrela) + Octógono | a fazer | | `docs/qa/chefe/`, `docs/qa/octogono/` |
| F — Octógono no fim da fase + portal animado | a fazer | | `docs/qa/portal/`, `docs/qa/quiz/` |
| G — verificação final | a fazer | | `docs/qa/final/` |

## Decisões que tomei sozinho
- A+: o fundo original do menu (menu.png) também é o fundo de Opções, Vitória, Derrota e "Como jogar" (mesmo arquivo); voltou em todas. Só o fundo mudou nessas telas.
- A+: 08_chao — a pedra que o TCC deixou cortada na coluna 0 ganhou borda esquerda (o degrau da borda direita espelhado); sem isso, a emenda sem pontilhado deixava uma borda reta ou riscos. Tudo dentro das 24 colunas da emenda.
- A+: tools/orb_background.py (só gerava o menu novo) foi para art-source/fundo_reconstruido_descartado/tools/.

## Dúvidas registradas
(preenchido ao longo da rodada)
