# AutoClicker (Android)

Autoclicker para jogos clicker/idle. Mostra um alvo arrastável e um painel flutuante; ao tocar em ▶, executa toques repetidos no centro do alvo no intervalo configurado.

## Como funciona

- **AccessibilityService** (`AutoClickService`): desenha os overlays com `TYPE_ACCESSIBILITY_OVERLAY` (não precisa da permissão "Sobrepor a outros apps") e injeta os toques com `dispatchGesture()`.
- **MainActivity**: define o intervalo (ms) e abre as configurações de acessibilidade.
- Até 8 alvos numerados (**+** / **−** no painel). Cada alvo tem o seu intervalo: na tela inicial use `100,30000` para o alvo 1 a cada 100 ms e o alvo 2 a cada 30 s. Alvos além da lista usam o último valor.
- Posições dos alvos e do painel são salvas e restauradas; há um botão para redefini-las.
- Parada automática: limite de cliques e/ou de minutos na tela inicial (0 = sem limite). Vale o que ocorrer primeiro.
- Intervalo mínimo: 20 ms (`Prefs.MIN_INTERVAL_MS`).

## Como compilar e instalar

1. Abra a pasta `autoclicker` no **Android Studio** (Koala ou mais novo). Ele baixa o Gradle e o SDK 34 sozinho.
2. Conecte o celular com depuração USB ativa e clique em **Run**.
3. No app, toque em **Ativar serviço de acessibilidade** e habilite o AutoClicker.
   - Android 13+ com APK instalado fora da Play Store: se a opção estiver cinza, vá em *Info do app → ⋮ → Permitir configurações restritas*.
4. Volte ao app, ajuste o intervalo e toque em **Mostrar controles na tela**.
5. Arraste os círculos numerados até os pontos desejados, abra o jogo e toque em ▶.

## Limitações

- Alguns jogos e apps (bancos, por exemplo) bloqueiam toques gerados por serviços de acessibilidade.
- Muitos jogos proíbem automação nos termos de uso; use por sua conta e risco.
- O Android só aceita `dispatchGesture` com o serviço ativo; se o sistema o desligar (economia de bateria), reative-o nas configurações.
