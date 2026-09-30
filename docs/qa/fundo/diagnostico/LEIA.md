# Diagnóstico do fundo (item 7)

Cada PNG aqui é UMA camada de `assets/sprites/background/`, isolada sobre cinza
neutro, repetida duas vezes lado a lado como o jogo repete (a linha vermelha é
a emenda), ampliada.

## Mosaico de blocos coloridos → `07_lago.png`
Vem da arte do TCC `art-source/ProjetoFinal_TCC/fundo/lago.png`, copiada sem
mudança pelo gerador (`build_world`). Ela já é uma textura de blocos grandes
com tons variados ("água" em quadrados); ampliada 4× no jogo, parece ruído
ampliado / mosaico atrás da paisagem.

## Coluna pontilhada vertical → `07_lago.png` e `08_chao.png`
Vem do próprio gerador: para esconder a emenda dessas duas camadas (emenda
grande: 28,7 e 32,2 de diferença média), `build_world` mistura as 24 primeiras
colunas com pontilhado Bayer. Essa faixa de 24 px pontilhada aparece como uma
coluna vertical a cada repetição (ver o começo de cada cópia nos PNGs).

As outras camadas (céu, nuvens, montanhas, estruturas) não têm nenhum dos dois
problemas, mas também são do TCC e serão refeitas junto (Fase 5).
