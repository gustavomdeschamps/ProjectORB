# Etapa A — camada 07_lago

## Dados da camada original (TCC, igual ao baseline 7ede4f4)
- PNG 380 x 270 pixels de arte (recorte do lago.png do TCC, 480 x 270, no período 380).
- Linhas ocupadas: 189 a 269 (o resto é transparente). A camada inteira é desenhada
  em y = 0 com altura 270 x 4 = 1080; o lago cai nas linhas de tela 756 a 1079.
- Escala: 4x exata (PIXEL_SCALE). Filtro: Nearest / ClampToEdge (Assets.load).
- Offsets: nenhum próprio; parallax 0,34 (camada 7 de PARALLAX), sem deriva, tinta
  0,74/0,76/0,88 aplicada a todas as camadas.

## Causa raiz
- (a) Deslocada: NÃO há offset/pivô errado. Posição e âncora são as mesmas das
  outras camadas (canvas 480x270 desenhado em y=0). O que parece "deslocado" é
  a textura em blocos: sem linha de horizonte nítida, a borda de cima (linha 189)
  é um degrau de blocos e a faixa não "assenta" atrás da 08_chao.
- (b) Borrada: filtro e escala estão corretos (Nearest, 4x). O aspecto borrado vem
  do conteúdo: ~708 cores, 704 delas com outra quase igual (diferença < 12),
  ou seja, uma textura suavizada/reduzida, não pixel art de paleta curta.
- (c) Suja: o gerador NÃO acrescenta ruído; ele só recorta o período. A própria
  arte do TCC é uma imagem de baixa resolução: blocos de 9 px de largura e
  4-6 px de altura com tons variados. A 4x viram retângulos de 36 x 16-24 px
  (mosaico).
- (d) Emenda: SIM. Para esconder a emenda (diferença média 28,7), o gerador
  misturava as 24 primeiras colunas com pontilhado Bayer, o que aparece como uma
  coluna pontilhada a cada repetição (380 px de arte).

## Correção (só a 07_lago)
Redesenho nítido em tools/build_orb_assets.py (lake_redraw): mesmo período 380,
mesmas linhas 189-269, mesma sequência de faixas do original (escura, azul,
faixa violeta, azul, azul claro, violeta), 6 tons tirados das cores mais
frequentes do lago original, reflexos horizontais de 1 px escritos à mão e
repetidos com o período (emenda invisível, sem pontilhado). Código de desenho,
posição, parallax, tinta e todas as outras camadas: inalterados.

## Observação (não corrigida — fora do pedido)
A 08_chao tem a mesma mistura pontilhada de 24 colunas na emenda (diferença
média 32,2): ainda aparece uma pequena área pontilhada perto de uma pedra a cada
421 px de arte. Não mexi; aguardando decisão.
