# Auditoria "cara de IA" (rodada 3, etapa 3)

Auditei todas as telas (abertura, menu, como jogar, opções, jogo/HUD, pausa, códex,
diálogo, quiz, vitória, derrota) e todos os textos em `core/src` e `assets/dialogue`,
`assets/quiz`. Para cada item: **onde**, **por que parece IA**, **o que foi feito**.
Provas: `antes/` (capturas antes da etapa) e `depois/`, `feedback/` (quadro a quadro),
`arte/` (folhas de contato 1x/4x em fundo do jogo e claro, GIFs).

## Textos e frases de efeito

| # | Onde | Por que parece IA | O que foi feito |
|---|---|---|---|
| 1 | Menu, rodapé: "EXPLORE · DESCUBRA · CONTINUE" | Tríade de verbos no imperativo, sem função: slogan genérico de template | Removido |
| 2 | Menu, sob o título: "RUÍNAS FLUTUANTES" e "GEOMETRIA EM AÇÃO" | Subtítulo + tagline decorativos, empilhados e centralizados | Removidos; o título virou o logo desenhado à mão |
| 3 | Abertura: "GEOMETRIA EM AÇÃO" sob o título | Mesma tagline | Removida (a abertura inteira é refeita na etapa 4) |
| 4 | Abertura: "SEGURE QUALQUER TECLA PARA PULAR" | Instrução genérica, longa, em caixa alta | "Segure ESC para pular" (só ESC pula) |
| 5 | Abertura: "PRESSIONE QUALQUER TECLA" | Chavão de tela de título | "ENTER" |
| 6 | Jogo: "ACERTOU! - continue nos pontos iluminados" | Frase de assistente narrando o óbvio, com exclamação | Removida. Acerto = alvo âmbar estilhaça (6 quadros), som de cristal que sobe de tom a cada alvo, "+100" pequeno subindo do ponto, contador do HUD pula e acende |
| 7 | Jogo: "ERROU O PONTO - desvie do ataque!" | Idem, dando ordem | Removida. Erro = faísca rosa em X, baque grave, "-25" subindo, tremor curto |
| 8 | Jogo: "PROPRIEDADE RESOLVIDA - novo padrão" | Jargão técnico narrado | Removida: estilhaço + som de rodada |
| 9 | Jogo: "FORMA DESFEITA" / "CHEFE DESESTABILIZADO" | Texto no meio do jogo a cada vitória | Forma comum: só explosão + som. Chefe: a ÚNICA chamada no meio da tela da fase, "NÚCLEO PARTIDO" (2 palavras), letras caindo uma a uma com quique e saindo; som próprio. Em movimento reduzido aparece e some sem movimento |
| 10 | Jogo: "CONCEITO DOMINADO - passagem liberada" | Frase de "conquista" genérica | Removida: a escadinha se rearranjando (som de blocos + tremor, som ao terminar) já avisa |
| 11 | Jogo: "RIFT ESTABILIZADO - atravesse o portal" e "SIGA PARA O PORTAL" | Ordem narrada | Removidas: o portal acende; a dica "E PORTAL" (tecla desenhada) só aparece em cima dele |
| 12 | Jogo: "VIDA PERDIDA - tentativa reiniciada / seção já resolvida" | Log do sistema na tela | Removido: o coração do HUD quebra (animação que já existia) e o ORB renasce piscando |
| 13 | Cartões de seção: "CÂMARA / LOSANGO - VÉRTICES" etc. | Legenda técnica sob o nome | Só o nome. Entrada: letras chegam uma a uma deslizando 3 px com um traço crescendo embaixo; saída: letras somem da esquerda para a direita. As legendas saíram também dos dados (`Section`) |
| 14 | Pausa: "CORREDOR - ALCANCE A CÂMARA" / "RESOLVA A FORMA ..." | Objetivo narrado | Só o nome da seção |
| 15 | Códex: "Descobertas registradas durante esta expedição" | Frase de efeito de "diário de expedição" | Removida; fatos reescritos sem hífen de lista ("4 lados iguais; as diagonais se cruzam a 90°") |
| 16 | Vitória: "RIFT ESTABILIZADO", "Você leu as formas no meio da ação.", "Vértices - Lados - Ângulos - Simetria" | Elogio genérico + lista decorativa | Removidos. Ficam os números (pontos em destaque, tempo, precisão, cristais) e os corações |
| 17 | Derrota: "O Void desestabilizou ORB", "Leia a forma e acerte os pontos marcados." | Frase dramática + conselho de assistente | "CAIU EM" + nome da seção; números da tentativa |
| 18 | Diálogo: dica "FAÇA!" no passo interativo | Palavra de ordem com exclamação | A tecla que o passo pede, desenhada (A/D, ESPAÇO, SHIFT, mouse), piscando devagar |
| 19 | Falas do Pi | Tom de manual/assistente ("a regra mais importante de todas"), exclamação em quase toda frase, explicação longa | Reescritas: curtas, com personalidade (Pi começa a recitar os dígitos e desiste), zero exclamações, zero travessões. Mesmos passos e eventos do tutorial (TutorialCheck passa) |
| 20 | Quiz: "CERTO!" / "QUASE! A CERTA É A 2." | Exclamação e "quase" de assistente | "CERTO" / "ERA A 2" (a tela do quiz é refeita na etapa 7) |
| 21 | Códex/derrota/vitória: separadores " - " entre itens | Lista com travessão/hífen | Colunas: rótulo apagado à esquerda, valor claro à direita |

## Visual

| # | Onde | Por que parece IA | O que foi feito |
|---|---|---|---|
| 22 | Menu: caixa central com borda neon ciano sobre o fundo | Card centralizado de template | Sem caixa. Logo "PROJECT ORB" desenhado à mão em cima à esquerda (letras geométricas com extrusão 3D, contorno seletivo, bisel e brilho; o "O" é o próprio ORB). Botões só em texto, alinhados à esquerda sobre uma faixa escura de borda pontilhada (sem degradê). Estados: normal (texto apagado), selecionado (claro, 2 px à direita, traço embaixo), pressionado (afunda 1 px, cor de destaque). Nenhum ícone ao lado |
| 23 | Menu: ORB parado ao lado da caixa | Mascote decorativo "colado" | Removido: o ORB está no logo (na etapa 4, também na imagem-chave) |
| 24 | Opções, pausa, vitória, derrota, como jogar, códex: todas com a mesma caixa neon centralizada | Mesmo template repetido | Mesmo estilo do menu em todas. Opções: rótulo à esquerda, valor em coluna, volume em 10 segmentos. Como jogar: teclas desenhadas mantidas, alinhadas à esquerda |
| 25 | Neon ciano + magenta em tudo, simetria perfeita e tudo centralizado | Paleta "cyberpunk de template" | Uma cor de destaque por tela: menu e pausa lilás, opções azul-petróleo, vitória verde-menta, derrota rosa, códex azul-petróleo. Composição assimétrica: texto à esquerda, arte à direita, hierarquia de tamanho (item principal 2x maior) |
| 26 | HUD: contador de alvos em amarelo, mira amarela sobre o alvo | Amarelo-âmbar fora dos alvos | Contador em branco (lilás quando pula), mira lilás. O âmbar ficou só nos alvos |
| 27 | Alvos de ponto fraco: quadrados com anéis concêntricos magenta/ciano | Anéis concêntricos (proibidos) e neon | Cristal âmbar facetado (losango de 4 faces, núcleo claro, contorno), 3 tamanhos, sem anel; estilhaça ao ser acertado |
| 28 | Plataformas "[][][][]" com linha neon em cima | Placeholder de UI (caixinhas) | Laje de pedra flutuante: topo gasto e aceso, blocos com juntas, textura em marcas curtas, rachaduras, cristal incrustado, base irregular com pontas de rocha. Pontas esquerda/direita desenhadas separadas (luz sempre do alto à esquerda). 5 miolos (3 de 16 px, 2 de 12 px), escolhidos pela posição, sem repetir o vizinho. Zonas alternadas em pedra azulada com cristal ciano |
| 29 | Chão de tijolo liso repetido | Ladrilho único repetido | 5 variantes 32x32 de lajotas gastas + duas fiadas de pedra, com lascas, rachaduras, cristal e pedrinhas; bordas iguais em todas (emendam em qualquer ordem); variante escolhida por hash da posição |
| 30 | Diálogo: nome do falante em magenta | Neon | Cor por personagem (Pi azul-petróleo claro; Octógono azul-royal claro) |

## Som (novo, para substituir texto)
`tools/build_orb_audio.py` agora gera: `shatter` (cristal partindo), `miss` (baque do erro),
`stair_rumble`/`stair_open` (a escadinha, que antes não tinha som), `ui_move`/`ui_confirm`
(menus) e `callout` (momento raro).

## O que ficou para outras etapas (e por quê)
- Abertura (textos restantes, anéis do Rift): a abertura inteira é descartada e refeita na etapa 4.
- Tela do quiz (caixa com borda): refeita na etapa 7, quando o quiz entra na fase.
- Caixa de diálogo: continua uma caixa (é convenção de jogo e precisa de área de leitura);
  só perdeu o neon e o "FAÇA!".
- Blocos da escadinha (magenta/ciano com marcas) e efeitos antigos em anel (acerto/void):
  etapa 9 (qualidade geral).
- Falas do Octógono: escritas na etapa 7 com as mesmas regras.

## Verificação
- `bash tools/run_checks.sh`: 12 checagens PASS + 4 capturas OK (inclui TutorialCheck com as
  falas novas e ResumeInputCheck clicando no item novo da pausa).
- Busca nos textos de `core/src` e `assets/dialogue|quiz`: nenhuma frase com "!" ou "—"
  visível ao jogador; os únicos " - " restantes estão em textos de pedido do chefe que não são
  desenhados em lugar nenhum (o chefe é refeito na etapa 6).
