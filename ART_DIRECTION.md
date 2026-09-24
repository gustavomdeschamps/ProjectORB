# Project ORB — direção de arte e pipeline

## Linguagem visual

- Pixel art de fantasia das ruínas flutuantes. Silhuetas simples e legíveis durante o combate; detalhes finos ficam no cenário e na interface.
- ORB é roxa/magenta e arredondada; triângulo vermelho, quadrado azul, losango amarelo, hexágono verde e sentinela do Void vermelho-escuro. Não trocar as cores por estado de animação.
- Contorno marinho quase preto, luz principal de cima à esquerda, reflexos de cristal em ciano/magenta. Evitar halos transparentes, blur e interpolação linear.
- Cenário: pedra lavanda, musgo verde, cristais de duas cores. Primeiro plano com contraste maior; fundo atmosférico com contraste reduzido.
- Interface: molduras de pedra e pequenas vinhas, centro escuro e desobstruído para o texto. Estados selecionados ganham ênfase magenta/ciano sem esconder o rótulo.

## Contrato técnico

- Fontes editáveis estão em `assets/source_remade/`; arte original em `assets/source_final/` não é sobrescrita.
- `py tools/process_orb_assets.py --remade` gera os recortes em `assets/processed_remade/`. O carregador LibGDX usa esses arquivos; os antigos em `assets/processed/` continuam como backup.
- Quadros de cada ator usam canvas constante (224×224; chefe 384×384), pivô horizontal central e base alinhada a 12 px (chefe 25 px). O recorte nunca deve separar corpo e arma/efeito intencional.
- Pixels são RGBA sem halo; filtro `Nearest` e wrap `ClampToEdge` em runtime. As texturas de efeito e UI são recortadas de atlas separados, não de quadros de animação.
- Hitboxes pertencem à lógica física, não ao retângulo transparente do PNG. Uma mudança visual não deve alterar colisões sem revisão explícita.

## Verificação

1. Rodar o processador e `py tools/audit_orb_assets.py --remade`.
2. Inspecionar folhas de contato e ataques, mortes e chefe individualmente; a auditoria automática não detecta todas as falhas de desenho.
3. Compilar o projeto e percorrer menu, opções, gameplay, pausa, derrota e vitória; verificar legibilidade em 1280×720 e escala nativa.

Estado atual: o pacote redesenhado está integrado e passa na auditoria de dimensões/transparência, mas a consistência de movimento quadro a quadro e o balanceamento de hitboxes ainda exigem playtest humano prolongado antes de chamar o jogo de finalizado.
